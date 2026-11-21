#!/usr/bin/env bash
# ==============================================================================
# PG CRM — Tenant Export Script: Shared Multi-Tenant Schema -> Dedicated Instance
# Spec Section 1: "A tenant can move from shared to dedicated by exporting its
# schema (pg_dump --schema) and restoring it on a new instance."
# ==============================================================================

set -euo pipefail

TENANT_SLUG=""
SOURCE_DB="pgcrmdb"
SOURCE_HOST="localhost"
SOURCE_PORT="5432"
SOURCE_USER="postgres"
SOURCE_PASSWORD="${PGPASSWORD:-admin}"
OUTPUT_DIR=""

usage() {
    echo "Usage: $0 -s <tenant_slug> [options]"
    echo ""
    echo "Options:"
    echo "  -s <slug>           Tenant slug to export"
    echo "  -d <source_db>      Source database name (default: pgcrmdb)"
    echo "  -h <source_host>    Source database host (default: localhost)"
    echo "  -p <source_port>    Source database port (default: 5432)"
    echo "  -u <source_user>    Source database user (default: postgres)"
    echo "  -o <output_dir>     Output directory for dump and dedicated bundle"
    exit 1
}

while getopts "s:d:h:p:u:o:" opt; do
    case "$opt" in
        s) TENANT_SLUG="$OPTARG" ;;
        d) SOURCE_DB="$OPTARG" ;;
        h) SOURCE_HOST="$OPTARG" ;;
        p) SOURCE_PORT="$OPTARG" ;;
        u) SOURCE_USER="$OPTARG" ;;
        o) OUTPUT_DIR="$OPTARG" ;;
        *) usage ;;
    esac
done

if [[ -z "$TENANT_SLUG" ]]; then
    echo "Error: Tenant slug (-s) is required."
    usage
fi

if [[ ! "$TENANT_SLUG" =~ ^[a-z][a-z0-9_]{2,40}$ ]]; then
    echo "Error: Tenant slug '$TENANT_SLUG' must match regex: ^[a-z][a-z0-9_]{2,40}$"
    exit 1
fi

SCHEMA_NAME="t_${TENANT_SLUG}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="${OUTPUT_DIR:-${SCRIPT_DIR}/exports/${TENANT_SLUG}}"
mkdir -p "$OUTPUT_DIR"

echo "============================================================"
echo " PG CRM TENANT EXPORT: Shared Schema -> Dedicated Instance "
echo "============================================================"
echo "Tenant Slug:   $TENANT_SLUG"
echo "Schema:        $SCHEMA_NAME"
echo "Source DB:     $SOURCE_DB ($SOURCE_HOST:$SOURCE_PORT)"
echo "Output Dir:    $OUTPUT_DIR"

# 1. Verify existence
STATUS=$(PGPASSWORD="$SOURCE_PASSWORD" psql -h "$SOURCE_HOST" -p "$SOURCE_PORT" -U "$SOURCE_USER" -d "$SOURCE_DB" \
    -t -A -c "SELECT status FROM platform.tenants WHERE slug = '$TENANT_SLUG';" || true)

if [[ -z "$STATUS" ]]; then
    echo "Error: Tenant '$TENANT_SLUG' not found in platform.tenants on $SOURCE_DB."
    exit 1
fi
echo "✓ Verified tenant '$TENANT_SLUG' (Status: $STATUS)"

TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
DUMP_FILE="${OUTPUT_DIR}/${TENANT_SLUG}_export_${TIMESTAMP}.dump"

echo "Step 1: Exporting schema '$SCHEMA_NAME'..."
PGPASSWORD="$SOURCE_PASSWORD" pg_dump -h "$SOURCE_HOST" -p "$SOURCE_PORT" -U "$SOURCE_USER" -d "$SOURCE_DB" \
    --schema="$SCHEMA_NAME" -F c -b -v -f "$DUMP_FILE"

echo "Step 2: Verifying dump file integrity..."
pg_restore --list "$DUMP_FILE" > /dev/null
echo "✓ Dump file valid."

echo "Step 3: Generating dedicated deployment templates..."
cat <<EOF > "${OUTPUT_DIR}/docker-compose.dedicated.yml"
version: '3.8'

services:
  pgcrm-db:
    image: postgres:18-alpine
    container_name: pgcrm-dedicated-db
    restart: unless-stopped
    environment:
      POSTGRES_DB: pgcrmdb
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: \${DB_PASSWORD:-admin}
    volumes:
      - pgcrm_data:/var/lib/postgresql/data
    ports:
      - "5432:5432"

  pgcrm-backend:
    image: pgcrm-backend:latest
    container_name: pgcrm-dedicated-backend
    restart: unless-stopped
    depends_on:
      - pgcrm-db
    environment:
      SPRING_PROFILES_ACTIVE: prod
      APP_DEPLOYMENT_MODE: dedicated
      APP_DEDICATED_TENANT: ${TENANT_SLUG}
      APP_ENCRYPTION_KEY: \${APP_ENCRYPTION_KEY}
      DB_URL: jdbc:postgresql://pgcrm-db:5432/pgcrmdb
      DB_USERNAME: postgres
      DB_PASSWORD: \${DB_PASSWORD:-admin}
    ports:
      - "8080:8080"

volumes:
  pgcrm_data:
EOF

cat <<EOF > "${OUTPUT_DIR}/restore-dedicated.sh"
#!/usr/bin/env bash
set -euo pipefail

TARGET_DB="\${1:-pgcrmdb}"
TARGET_HOST="\${2:-localhost}"
TARGET_PORT="\${3:-5432}"
TARGET_USER="\${4:-postgres}"
TARGET_PASSWORD="\${5:-admin}"

echo "Restoring ${DUMP_FILE} into dedicated database \${TARGET_DB}..."
PGPASSWORD="\${TARGET_PASSWORD}" pg_restore -h "\${TARGET_HOST}" -p "\${TARGET_PORT}" -U "\${TARGET_USER}" -d "\${TARGET_DB}" -v "${DUMP_FILE}"

echo "Registering tenant in dedicated platform schema..."
PGPASSWORD="\${TARGET_PASSWORD}" psql -h "\${TARGET_HOST}" -p "\${TARGET_PORT}" -U "\${TARGET_USER}" -d "\${TARGET_DB}" -c "
CREATE SCHEMA IF NOT EXISTS platform;
CREATE TABLE IF NOT EXISTS platform.tenants (
    id VARCHAR(255) PRIMARY KEY,
    slug VARCHAR(50) UNIQUE NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    schema_name VARCHAR(50) UNIQUE NOT NULL,
    plan VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    renewal_date DATE
);
INSERT INTO platform.tenants (id, slug, display_name, schema_name, plan, status)
VALUES ('tenant-dedicated-001', '${TENANT_SLUG}', '${TENANT_SLUG} Dedicated', '${SCHEMA_NAME}', 'DEDICATED', 'ACTIVE')
ON CONFLICT (slug) DO UPDATE SET status = 'ACTIVE';
"

echo "Dedicated database ready. Run with APP_DEPLOYMENT_MODE=dedicated and APP_DEDICATED_TENANT=${TENANT_SLUG}"
EOF
chmod +x "${OUTPUT_DIR}/restore-dedicated.sh"

echo ""
echo "================================================================================"
echo " EXPORT COMPLETE: Tenant '$TENANT_SLUG' package ready!                          "
echo " Dump File: $DUMP_FILE                                                          "
echo " Deployment Config: ${OUTPUT_DIR}/docker-compose.dedicated.yml                  "
echo "================================================================================"
