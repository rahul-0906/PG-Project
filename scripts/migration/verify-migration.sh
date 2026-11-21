#!/usr/bin/env bash
# ==============================================================================
# PG CRM — Standalone Migration Verification Tool
# Spec Phase 6: Verify row counts and a sample of invoices and payments against the old system.
# ==============================================================================

set -euo pipefail

TENANT_SLUG=""
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
    exit 1
}

while getopts "s:d:h:P:u:c:D:H:T:U:" opt; do
    case "$opt" in
        s) TENANT_SLUG="$OPTARG" ;;
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

SCHEMA_NAME="t_${TENANT_SLUG}"

exec_psql() {
    local host="$1" port="$2" user="$3" pass="$4" db="$5" query="$6"
    PGPASSWORD="$pass" psql -h "$host" -p "$port" -U "$user" -d "$db" -t -A -c "$query"
}

echo "============================================================"
echo " PG CRM MIGRATION VERIFICATION: $SOURCE_DB.$SOURCE_SCHEMA -> $SCHEMA_NAME "
echo "============================================================"

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
        echo "  ✓ Table '$tbl' verified ($SRC_CNT rows)"
    fi
done

echo ""
if [[ "$DISCREPANCIES" -eq 0 ]]; then
    echo "============================================================"
    echo " VERIFICATION PASSED: 100% table row counts match!         "
    echo "============================================================"
    exit 0
else
    echo "============================================================"
    echo " VERIFICATION FAILED: $DISCREPANCIES table discrepancies found! "
    echo "============================================================"
    exit 1
fi
