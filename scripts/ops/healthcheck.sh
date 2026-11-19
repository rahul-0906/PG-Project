#!/bin/bash
# ─────────────────────────────────────────────────────────────
#  healthcheck.sh — PG CRM Health Check
#  Checks PostgreSQL connectivity and backend API availability
# ─────────────────────────────────────────────────────────────

DB_USER="postgres"
DB_NAME="pgcrmdb"
BACKEND_URL="http://localhost:8080/api/config/public"
LOG_FILE="/opt/pgcrm/logs/healthcheck.log"
TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')

echo ""
echo "========================================================"
echo " PG CRM Health Check — $TIMESTAMP"
echo "========================================================"

OVERALL_OK=true

# ─── Check 1: PostgreSQL ────────────────────────────────────
if pg_isready -U "$DB_USER" -d "$DB_NAME" -h localhost -q 2>/dev/null; then
    echo "  [DB]      ✅ PGCRMDB UP   — PostgreSQL is healthy"
    DB_STATUS="UP"
else
    echo "  [DB]      ❌ PGCRMDB DOWN — PostgreSQL not responding"
    DB_STATUS="DOWN"
    OVERALL_OK=false
fi

# ─── Check 2: Backend API ───────────────────────────────────
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BACKEND_URL" 2>/dev/null)
if [ "$HTTP_CODE" = "200" ]; then
    echo "  [BACKEND] ✅ API UP     — $BACKEND_URL responded 200"
    API_STATUS="UP"
else
    echo "  [BACKEND] ❌ API DOWN   — $BACKEND_URL returned HTTP $HTTP_CODE"
    API_STATUS="DOWN"
    OVERALL_OK=false
fi

echo "========================================================"
echo ""

# ─── Log result ─────────────────────────────────────────────
mkdir -p "$(dirname "$LOG_FILE")" 2>/dev/null
echo "$TIMESTAMP | DB=$DB_STATUS | API=$API_STATUS" >> "$LOG_FILE" 2>/dev/null

# Exit non-zero if any check failed (useful for cron alerting)
if [ "$OVERALL_OK" = false ]; then
    exit 1
fi
exit 0
