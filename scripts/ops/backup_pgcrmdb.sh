#!/usr/bin/env bash
# ============================================================
# backup_pgcrmdb.sh — PG CRM PostgreSQL Backup Script (Linux/bash)
# Database : pgcrmdb
# Retention : 30 days (older .dump files are auto-deleted)
# Format   : pg_dump custom format (-F c), restore with pg_restore
# Log      : E:\PG-Project\backups\backup.log  (adjust path on server)
# ============================================================

set -euo pipefail

# ── Configuration ────────────────────────────────────────────
DB_NAME="pgcrmdb"
DB_USER="postgres"
BACKUP_DIR="${BACKUP_DIR:-/opt/pg-project/backups}"
LOG_FILE="${BACKUP_DIR}/backup.log"

# ── Ensure backup directory exists ───────────────────────────
mkdir -p "${BACKUP_DIR}"

# ── Build datetime string ─────────────────────────────────────
DATETIME=$(date +"%Y-%m-%d_%H-%M")
DUMP_FILE="${BACKUP_DIR}/pgcrmdb_${DATETIME}.dump"

# ── Log header ───────────────────────────────────────────────
echo "[$(date '+%Y-%m-%d %H:%M:%S')] Starting backup of ${DB_NAME} ..." | tee -a "${LOG_FILE}"

# ── Run pg_dump ───────────────────────────────────────────────
if pg_dump -U "${DB_USER}" -F c -f "${DUMP_FILE}" "${DB_NAME}"; then
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] ✅ Backup succeeded: ${DUMP_FILE}" | tee -a "${LOG_FILE}"
else
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] ❌ Backup FAILED — check PostgreSQL connectivity and credentials." | tee -a "${LOG_FILE}"
    exit 1
fi

# ── Rotate: delete .dump files older than 30 days ────────────
echo "[$(date '+%Y-%m-%d %H:%M:%S')] Cleaning up dump files older than 30 days..." | tee -a "${LOG_FILE}"
find "${BACKUP_DIR}" -name "*.dump" -mtime +30 -delete
echo "[$(date '+%Y-%m-%d %H:%M:%S')] Cleanup complete." | tee -a "${LOG_FILE}"

exit 0
