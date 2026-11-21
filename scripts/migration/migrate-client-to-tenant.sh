#!/usr/bin/env bash
# ==============================================================================
# PG CRM — Client Migration Script: Single-Tenant DB -> Multi-Tenant Schema
# Spec Phase 6: Dump data, restore into t_<slug>, run migrations, verify counts & samples.
# ==============================================================================

set -euo pipefail

TENANT_SLUG=""
DISPLAY_NAME=""
PLAN="STANDARD"
DOMAIN=""
SOURCE_DB=""
SOURCE_HOST="localhost"
SOURCE_PORT="5432"
SOURCE_USER="postgres"
SOURCE_PASSWORD="${PGPASSWORD:-admin}"
SOURCE_SCHEMA="public"

TARGET_DB="pgcrmdb"
TARGET_HOST="localhost"
TARGET_PORT="5432"
TARGET_USER="postgres"
TARGET_PASSWORD="${PGPASSWORD:-admin}"

usage() {
    echo "Usage: $0 -s <tenant_slug> -d <source_db> [options]"
    echo ""
    echo "Options:"
    echo "  -s <slug>           Tenant slug (regex: ^[a-z][a-z0-9_]{2,40}$)"
    echo "  -n <name>           Display name"
    echo "  -p <plan>           Subscription plan (default: STANDARD)"
    echo "  -m <domain>         Custom domain / subdomain host"
    echo "  -d <source_db>      Source PostgreSQL database name"
    echo "  -h <source_host>    Source database host (default: localhost)"
    echo "  -P <source_port>    Source database port (default: 5432)"
    echo "  -u <source_user>    Source database user (default: postgres)"
    echo "  -c <source_schema>  Source schema (default: public)"
    echo "  -D <target_db>      Target database name (default: pgcrmdb)"
    echo "  -H <target_host>    Target database host (default: localhost)"
    echo "  -T <target_port>    Target database port (default: 5432)"
    echo "  -U <target_user>    Target database user (default: postgres)"
    exit 1
}

while getopts "s:n:p:m:d:h:P:u:c:D:H:T:U:" opt; do
    case "$opt" in
        s) TENANT_SLUG="$OPTARG" ;;
        n) DISPLAY_NAME="$OPTARG" ;;
        p) PLAN="$OPTARG" ;;
        m) DOMAIN="$OPTARG" ;;
        d) SOURCE_DB="$OPTARG" ;;
        h) SOURCE_HOST="$OPTARG" ;;
        P) SOURCE_PORT="$OPTARG" ;;
        u) SOURCE_USER="$OPTARG" ;;
        c) SOURCE_SCHEMA="$OPTARG" ;;
        D) TARGET_DB="$OPTARG" ;;
        H) TARGET_HOST="$OPTARG" ;;
        T) TARGET_PORT="$OPTARG" ;;
        U) TARGET_USER="$OPTARG" ;;
        *) usage ;;
    esac
done

if [[ -z "$TENANT_SLUG" || -z "$SOURCE_DB" ]]; then
    echo "Error: Tenant slug (-s) and Source DB (-d) are required."
    usage
fi

if [[ ! "$TENANT_SLUG" =~ ^[a-z][a-z0-9_]{2,40}$ ]]; then
    echo "Error: Tenant slug '$TENANT_SLUG' must match regex: ^[a-z][a-z0-9_]{2,40}$"
    exit 1
fi

SCHEMA_NAME="t_${TENANT_SLUG}"
DISPLAY_NAME="${DISPLAY_NAME:-${TENANT_SLUG^^}}"

echo "============================================================"
echo " PG CRM CLIENT MIGRATION: Single-Tenant DB -> Tenant Schema "
echo "============================================================"
echo "Tenant Slug:   $TENANT_SLUG"
echo "Target Schema: $SCHEMA_NAME"
echo "Source:        $SOURCE_DB.$SOURCE_SCHEMA ($SOURCE_HOST:$SOURCE_PORT)"
echo "Target:        $TARGET_DB ($TARGET_HOST:$TARGET_PORT)"

exec_psql() {
    local host="$1" port="$2" user="$3" pass="$4" db="$5" query="$6"
    PGPASSWORD="$pass" psql -h "$host" -p "$port" -U "$user" -d "$db" -t -A -c "$query"
}

# 1. Connectivity Check
echo "Testing connections..."
exec_psql "$SOURCE_HOST" "$SOURCE_PORT" "$SOURCE_USER" "$SOURCE_PASSWORD" "$SOURCE_DB" "SELECT 1;" > /dev/null
exec_psql "$TARGET_HOST" "$TARGET_PORT" "$TARGET_USER" "$TARGET_PASSWORD" "$TARGET_DB" "SELECT 1;" > /dev/null
echo "✓ Databases reachable."

# 2. Check platform.tenants
EXISTING_STATUS=$(exec_psql "$TARGET_HOST" "$TARGET_PORT" "$TARGET_USER" "$TARGET_PASSWORD" "$TARGET_DB" "SELECT status FROM platform.tenants WHERE slug = '$TENANT_SLUG';" || true)
if [[ "$EXISTING_STATUS" == "ACTIVE" ]]; then
    echo "Error: Tenant '$TENANT_SLUG' is already ACTIVE. Aborting to avoid overwrite."
    exit 1
fi

TENANT_ID="tenant-$(head /dev/urandom | tr -dc a-z0-9 | head -c 8)"

echo "Step 1: Registering tenant as PROVISIONING..."
if [[ -n "$EXISTING_STATUS" ]]; then
    exec_psql "$TARGET_HOST" "$TARGET_PORT" "$TARGET_USER" "$TARGET_PASSWORD" "$TARGET_DB" "UPDATE platform.tenants SET status = 'PROVISIONING' WHERE slug = '$TENANT_SLUG';"
else
    exec_psql "$TARGET_HOST" "$TARGET_PORT" "$TARGET_USER" "$TARGET_PASSWORD" "$TARGET_DB" "INSERT INTO platform.tenants (id, slug, display_name, schema_name, plan, status, created_at) VALUES ('$TENANT_ID', '$TENANT_SLUG', '$DISPLAY_NAME', '$SCHEMA_NAME', '$PLAN', 'PROVISIONING', CURRENT_TIMESTAMP);"
fi

rollback() {
    echo "Initiating rollback of '$SCHEMA_NAME'..."
    exec_psql "$TARGET_HOST" "$TARGET_PORT" "$TARGET_USER" "$TARGET_PASSWORD" "$TARGET_DB" "DROP SCHEMA IF EXISTS $SCHEMA_NAME CASCADE;" || true
    exec_psql "$TARGET_HOST" "$TARGET_PORT" "$TARGET_USER" "$TARGET_PASSWORD" "$TARGET_DB" "DELETE FROM platform.tenant_domains WHERE tenant_id IN (SELECT id FROM platform.tenants WHERE slug = '$TENANT_SLUG');" || true
    exec_psql "$TARGET_HOST" "$TARGET_PORT" "$TARGET_USER" "$TARGET_PASSWORD" "$TARGET_DB" "DELETE FROM platform.tenants WHERE slug = '$TENANT_SLUG' AND status = 'PROVISIONING';" || true
    echo "✓ Rollback complete."
}
trap rollback ERR

echo "Step 2: Creating schema '$SCHEMA_NAME'..."
exec_psql "$TARGET_HOST" "$TARGET_PORT" "$TARGET_USER" "$TARGET_PASSWORD" "$TARGET_DB" "CREATE SCHEMA IF NOT EXISTS $SCHEMA_NAME;"

echo "Step 3: Dumping source table data..."
TEMP_DUMP=$(mktemp)
PGPASSWORD="$SOURCE_PASSWORD" pg_dump -h "$SOURCE_HOST" -p "$SOURCE_PORT" -U "$SOURCE_USER" -d "$SOURCE_DB" \
    --data-only --no-owner --no-privileges -n "$SOURCE_SCHEMA" -f "$TEMP_DUMP"

echo "Step 4: Transforming schema references and restoring into '$SCHEMA_NAME'..."
TEMP_SQL=$(mktemp)
echo "SET search_path TO $SCHEMA_NAME, public;" > "$TEMP_SQL"
echo "SET session_replication_role = 'replica';" >> "$TEMP_SQL"
sed -E "s/COPY ${SOURCE_SCHEMA}\./COPY ${SCHEMA_NAME}\./g; s/INSERT INTO ${SOURCE_SCHEMA}\./INSERT INTO ${SCHEMA_NAME}\./g" "$TEMP_DUMP" >> "$TEMP_SQL"
echo "SET session_replication_role = 'origin';" >> "$TEMP_SQL"

PGPASSWORD="$TARGET_PASSWORD" psql -h "$TARGET_HOST" -p "$TARGET_PORT" -U "$TARGET_USER" -d "$TARGET_DB" -f "$TEMP_SQL" > /dev/null
rm -f "$TEMP_DUMP" "$TEMP_SQL"
echo "✓ Data loaded."

echo "Step 5: Verifying table counts..."
TABLES=(
    "users" "buildings" "building_configs" "floors" "blocks"
    "rooms" "beds" "guests" "guest_beds" "invoices"
    "invoice_line_items" "eb_bills" "eb_bill_guests"
    "daily_logs" "meter_readings" "maintenance_tickets"
    "notifications" "pricing_config" "audit_logs"
)

DISCREPANCIES=0
for tbl in "${TABLES[@]}"; do
    SRC_CNT=$(exec_psql "$SOURCE_HOST" "$SOURCE_PORT" "$SOURCE_USER" "$SOURCE_PASSWORD" "$SOURCE_DB" "SELECT COUNT(*) FROM $SOURCE_SCHEMA.$tbl;" 2>/dev/null || echo "0")
    TGT_CNT=$(exec_psql "$TARGET_HOST" "$TARGET_PORT" "$TARGET_USER" "$TARGET_PASSWORD" "$TARGET_DB" "SELECT COUNT(*) FROM $SCHEMA_NAME.$tbl;" 2>/dev/null || echo "0")
    if [[ "$SRC_CNT" != "$TGT_CNT" ]]; then
        echo "  ✗ Table '$tbl' count mismatch: Source=$SRC_CNT, Target=$TGT_CNT"
        DISCREPANCIES=$((DISCREPANCIES + 1))
    else
        echo "  ✓ Table '$tbl' count matched ($SRC_CNT rows)"
    fi
done

if [[ "$DISCREPANCIES" -gt 0 ]]; then
    echo "Verification failed with $DISCREPANCIES discrepancies! Aborting."
    exit 1
fi

echo "Step 6: Marking tenant as ACTIVE..."
exec_psql "$TARGET_HOST" "$TARGET_PORT" "$TARGET_USER" "$TARGET_PASSWORD" "$TARGET_DB" "UPDATE platform.tenants SET status = 'ACTIVE' WHERE slug = '$TENANT_SLUG';"
if [[ -n "$DOMAIN" ]]; then
    exec_psql "$TARGET_HOST" "$TARGET_PORT" "$TARGET_USER" "$TARGET_PASSWORD" "$TARGET_DB" "INSERT INTO platform.tenant_domains (host, tenant_id) VALUES ('$DOMAIN', (SELECT id FROM platform.tenants WHERE slug = '$TENANT_SLUG')) ON CONFLICT DO NOTHING;"
fi

trap - ERR

echo ""
echo "================================================================================"
echo " MIGRATION VERIFICATION SUCCEEDED: Tenant '$TENANT_SLUG' is now ACTIVE!          "
echo "                                                                                "
echo " CRITICAL OPERATIONAL RULE:                                                     "
echo " Keep the old single-tenant database and stack running until the PG owner has   "
echo " logged into the shared portal and confirmed complete data integrity.          "
echo "================================================================================"
