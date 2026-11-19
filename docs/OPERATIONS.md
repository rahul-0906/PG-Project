# PG CRM — Operations Manual

This document covers backup procedures, restore testing, health checks, and uptime monitoring for a PG CRM deployment.

---

## 1. Backup

### 1.1 Scripts

| Script | Platform | Location |
| :--- | :--- | :--- |
| `backup_pgcrmdb.bat` | Windows (dev/staging) | `scripts/ops/backup_pgcrmdb.bat` |
| `backup_pgcrmdb.sh` | Linux (production) | `scripts/ops/backup_pgcrmdb.sh` |

### 1.2 Running a Backup

**Windows:**
```powershell
E:\PG-Project\scripts\ops\backup_pgcrmdb.bat
```

**Linux (production server):**
```bash
chmod +x /opt/pgcrm/scripts/ops/backup_pgcrmdb.sh
/opt/pgcrm/scripts/ops/backup_pgcrmdb.sh
```

### 1.3 What It Produces

- **Format**: PostgreSQL custom dump (`.dump`) — compressed, restoreable with `pg_restore`
- **Location**: `E:\PG-Project\backups\` (Windows) or `/opt/pgcrm/backups/` (Linux)
- **Filename**: `pgcrmdb_YYYY-MM-DD_HH-MM.dump`
- **Retention**: Files older than 30 days are automatically deleted

### 1.4 Scheduled Backups (Linux Production)

Add to crontab to run daily at 2:00 AM:

```bash
crontab -e
# Add this line:
0 2 * * * /opt/pgcrm/scripts/ops/backup_pgcrmdb.sh >> /opt/pgcrm/backups/backup.log 2>&1
```

---

## 2. Restore

> [!CAUTION]
> The restore scripts always restore to a **test database** (`pgcrmdb_restore_test`), never to the live `pgcrmdb`. This is intentional — to verify backup integrity without touching production data.

### 2.1 Scripts

| Script | Platform |
| :--- | :--- |
| `restore_pgcrmdb.bat` | Windows |
| `restore_pgcrmdb_linux.sh` | Linux |

### 2.2 Running a Restore Test

**Windows:**
```powershell
E:\PG-Project\scripts\ops\restore_pgcrmdb.bat E:\PG-Project\backups\pgcrmdb_2026-11-19_02-00.dump
```

**Linux:**
```bash
chmod +x /opt/pgcrm/scripts/ops/restore_pgcrmdb_linux.sh
/opt/pgcrm/scripts/ops/restore_pgcrmdb_linux.sh /opt/pgcrm/backups/pgcrmdb_2026-11-19_02-00.dump
```

### 2.3 Verifying the Restore

After restore completes, verify the test database has data:

```bash
psql -U postgres -d pgcrmdb_restore_test -c "\dt"
psql -U postgres -d pgcrmdb_restore_test -c "SELECT COUNT(*) FROM guests;"
```

### 2.4 Backup Test Log

Record manual backup test results here:

| Date | Backup File | Restore Status | Verified Tables | Notes |
| :--- | :--- | :--- | :--- | :--- |
| 2026-11-19 | `pgcrmdb_test_2026-11-19_22-48.dump` (68 KB) | ✅ SUCCESS | 20 tables restored | First restore test — all 20 PG CRM tables verified via `\dt` |

---

## 3. Health Check

### 3.1 Scripts

| Script | Platform |
| :--- | :--- |
| `healthcheck.bat` | Windows |
| `healthcheck.sh` | Linux |

### 3.2 Running a Health Check

**Windows:**
```powershell
E:\PG-Project\scripts\ops\healthcheck.bat
```

**Linux:**
```bash
chmod +x /opt/pgcrm/scripts/ops/healthcheck.sh
/opt/pgcrm/scripts/ops/healthcheck.sh
```

**Expected output (all healthy):**
```
=========================================================
  PG CRM Health Check - 2026-11-19 10:00:00
=========================================================
  [DB]      OK    PGCRMDB UP   - PostgreSQL is healthy
  [BACKEND] OK    API UP       - http://localhost:8080/api/config/public responded 200
========================================================
```

### 3.3 Scheduled Health Check (Linux)

```bash
# Add to crontab — check every 5 minutes
*/5 * * * * /opt/pgcrm/scripts/ops/healthcheck.sh >> /opt/pgcrm/logs/healthcheck.log 2>&1
```

---

## 4. Uptime Monitoring

The health endpoint used for monitoring:

```
GET http://<server-ip>:8080/api/config/public
```

This endpoint:
- Requires **no authentication**
- Returns `200 OK` with branding JSON when the backend is healthy
- Responds within milliseconds (no DB queries)

### 4.1 Recommended: UptimeRobot (Free)

1. Go to [uptimerobot.com](https://uptimerobot.com) and create a free account
2. Click **Add New Monitor**
3. Configure:
   - **Monitor Type**: HTTP(s)
   - **Friendly Name**: PG CRM Backend
   - **URL**: `http://<your-server-ip>:8080/api/config/public`
   - **Monitoring Interval**: 5 minutes
4. Add an alert contact (email/Telegram/Slack)
5. UptimeRobot will notify you within 5 minutes of any downtime

> [!NOTE]
> UptimeRobot's free tier supports up to 50 monitors with 5-minute check intervals. This is sufficient for a single-stack PG CRM deployment.

### 4.2 Alternative: Simple Cron Alert

If the server is a Linux VPS without external monitoring, use this cron job to send an email alert on failure:

```bash
# In crontab:
*/5 * * * * /opt/pgcrm/scripts/ops/healthcheck.sh || echo "PG CRM DOWN at $(date)" | mail -s "🚨 PG CRM Alert" admin@yourdomain.com
```

---

## 5. Log Locations

| Log | Location |
| :--- | :--- |
| Spring Boot application log | `docker logs -f pgcore-backend` |
| Backup log | `/opt/pgcrm/backups/backup.log` |
| Health check log | `/opt/pgcrm/logs/healthcheck.log` |
| Nginx access log | `/var/log/nginx/access.log` |
| Nginx error log | `/var/log/nginx/error.log` |

---

## 6. Emergency Procedures

### Database Connection Failure

```bash
# Check PostgreSQL is running
docker compose ps pgcore-db

# Restart the database container
docker compose restart pgcore-db

# Wait 10 seconds then restart backend
sleep 10 && docker compose restart pgcore-backend
```

### Backend Out of Memory / Crashed

```bash
# Check container status
docker compose ps

# View recent logs
docker compose logs --tail=100 pgcore-backend

# Restart backend only
docker compose restart pgcore-backend
```

### Full Stack Restart

```bash
cd /opt/pgcrm
docker compose down
docker compose up -d
```
