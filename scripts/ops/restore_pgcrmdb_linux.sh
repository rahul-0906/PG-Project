#!/bin/bash
# ─────────────────────────────────────────────────────────────
#  restore_pgcrmdb.sh — Restore a PG CRM backup to a TEST database
#  Usage: ./restore_pgcrmdb.sh <path/to/backup.dump>
#  IMPORTANT: Restores to pgcrmdb_restore_test, NOT the live DB
# ─────────────────────────────────────────────────────────────

DB_USER="postgres"
TEST_DB="pgcrmdb_restore_test"

if [ -z "$1" ]; then
    echo ""
    echo "Usage: $0 <path/to/backup.dump>"
    echo "Example: $0 /opt/pgcrm/backups/pgcrmdb_2026-11-19_02-00.dump"
    echo ""
    exit 1
fi

DUMP_FILE="$1"

if [ ! -f "$DUMP_FILE" ]; then
    echo "[ERROR] Dump file not found: $DUMP_FILE"
    exit 1
fi

echo ""
echo "========================================================="
echo " PG CRM Restore Utility"
echo "========================================================="
echo " Source: $DUMP_FILE"
echo " Target: $TEST_DB (NOT the live database)"
echo "========================================================="
echo ""
read -p "This will DROP and recreate '$TEST_DB'. Continue? (y/N): " CONFIRM

if [[ ! "$CONFIRM" =~ ^[Yy]$ ]]; then
    echo "Restore cancelled."
    exit 0
fi

echo ""
echo "[1/3] Dropping test database (if exists)..."
dropdb -U "$DB_USER" --if-exists "$TEST_DB" 2>/dev/null

echo "[2/3] Creating fresh test database..."
if ! createdb -U "$DB_USER" "$TEST_DB"; then
    echo "[ERROR] Failed to create $TEST_DB. Is PostgreSQL running?"
    exit 1
fi

echo "[3/3] Restoring from dump..."
if pg_restore -U "$DB_USER" -d "$TEST_DB" --no-owner --role="$DB_USER" "$DUMP_FILE"; then
    echo ""
    echo "[SUCCESS] Restore completed successfully!"
else
    echo ""
    echo "[WARNING] pg_restore completed with warnings. Some objects may need review."
fi

echo ""
echo "  Test database: $TEST_DB"
echo "  Verify with:   psql -U $DB_USER -d $TEST_DB -c '\dt'"
echo ""
