# PG CRM — End-to-End (E2E) Testing & Verification Runbook

This runbook guides QA, developers, and DevOps engineers through deploying the stack (locally or via Docker Compose) and executing end-to-end integration and verification checks for the **PG CRM** application.

---

## 1. Stack Launch Options

### Option A: Local Development Stack (Bare-Metal)
1. **Database**: PostgreSQL 18 running on `localhost:5432` (`pgcrmdb`).
2. **Backend**:
   ```powershell
   cd core-pg-crm/backend
   $env:SPRING_PROFILES_ACTIVE="dev"
   "C:\Program Files\apache-maven-3.9.16\bin\mvn" spring-boot:run
   ```
   *Expected Result:* `Started PgCrmApplication in X.XXX seconds` on port `8080`.
3. **Frontend**:
   ```powershell
   cd core-pg-crm/frontend
   npm install
   npm run dev
   ```
   *Expected Result:* Vite dev server running on `http://localhost:5173`.

### Option B: Full Docker Compose Stack
1. Ensure Docker Desktop is running and ports `5432`, `8080`, and `5173` are free.
2. Ensure root `.env` is configured (copy from `.env.example`).
3. Build and launch:
   ```powershell
   docker compose up --build -d
   ```
4. Verify container health:
   ```powershell
   docker compose ps
   ```
   *Expected Result:*
   - `pgcore-db` (healthy) on `5432`
   - `pgcore-backend` on `8080`
   - `pgcore-frontend` on `5173`

---

## 2. System Health & Sanity Verification

- [ ] **Public Health & Branding Probe**
  ```powershell
  curl http://localhost:8080/api/config/public
  ```
  *Expected Result:* `200 OK` JSON with `pgName`, `primaryColor`, `shortTitle`.

- [ ] **Deep Uptime & Connection Pool Health Probe**
  ```powershell
  curl http://localhost:8080/api/health
  ```
  *Expected Result:* `200 OK` with database status `UP`, response latency, HikariCP connection pool metrics, and active tenant count.

- [ ] **Frontend Load Check**
  - Open `http://localhost:5173` in a browser.
  - *Expected Result:* The PG CRM login screen displays with customized tenant branding.

---

## 3. End-to-End Workflow Verification

### Phase 1: Authentication & RBAC Flow
- [ ] **Login as SUPER_ADMIN**
  ```http
  POST http://localhost:8080/api/auth/login
  Content-Type: application/json

  { "email": "<super-admin-email>", "password": "<password>" }
  ```
  *Expected Result:* `200 OK` with `accessToken`, `refreshToken`, role `SUPER_ADMIN`.
- [ ] **Login as PG_OWNER**: Returns role `PG_OWNER`.
- [ ] **Login as PG_MANAGER**: Returns role `PG_MANAGER`.
- [ ] **Invalid Credentials**: Returns `401 Unauthorized`.

### Phase 2: Building & Property Inventory
- [ ] **List Buildings & Layout**
  ```http
  GET http://localhost:8080/api/inventory/buildings
  Authorization: Bearer <token>
  ```
  *Expected Result:* Hierarchy returned: `Building → Floor → Block → Room → Bed`.
- [ ] **Check Bed Availability**: Vacant beds return `status: VACANT`.

### Phase 3: Guest Check-In & Bed Allocation
- [ ] **Check In New Guest**
  ```http
  POST http://localhost:8080/api/manager/guests/check-in
  ```
  *Expected Result:* `201 Created` with guest ID; allocated bed status transitions from `VACANT` to `OCCUPIED`.
- [ ] **Resident Portal Access**: Logged-in guest can view assigned room, check-in date, and invoices on their dashboard.

### Phase 4: Monthly Billing & Calculations
- [ ] **Generate Monthly Invoices**
  ```http
  POST http://localhost:8080/api/manager/invoices/generate
  ```
  *Expected Result:* Invoices generated using strict flat-rate monthly rent (no pro-rated daily rates). Active guests in the billing month are charged the full monthly rate.
- [ ] **Electricity Sub-Meter Split**: Test split calculation against configured `EbSplitMethod` (`EQUAL_SPLIT`, `PER_BED`, `METER_BASED`, or `MANAGER_MANUAL`).

### Phase 5: Online Payments & Webhook Verification
- [ ] **Create Razorpay Order**
  ```http
  POST http://localhost:8080/api/payments/create-order
  ```
  *Expected Result:* Returns Razorpay order ID.
- [ ] **Webhook Signature Verification**: Send webhook payload to `POST /api/webhooks/razorpay` with valid HMAC-SHA256 signature; invoice status transitions to `PAID`. Invalid HMAC signatures are rejected.

### Phase 6: Automated Background Schedulers
- [ ] **MonthlyBillingScheduler**: Fires at midnight on the 1st (`0 0 0 1 * *`).
- [ ] **PaymentReminderScheduler**: Fires daily at 9:00 AM (`0 0 9 * * *`).
- [ ] **DataRetentionScheduler (PII Anonymization)**: Fires daily at 3:00 AM (`0 0 3 * * ?`), scrubbing personal names/phones for checkouts >365 days while preserving financial records.

### Phase 7: Guest Checkout & Settlement
- [ ] **Initiate and Confirm Checkout**
  ```http
  POST http://localhost:8080/api/manager/guests/{guestId}/checkout
  ```
  *Expected Result:* Net settlement computed (advance deposit minus dues), bed status released back to `VACANT`.

---

## 4. Disaster Recovery & Backup Verification

- [ ] **Execute Automated Nightly Backup & Tested Restore Drill**
  ```powershell
  # PowerShell (Windows)
  powershell -ExecutionPolicy Bypass -File scripts/ops/backup-all-tenants.ps1

  # POSIX Bash (Linux/macOS)
  ./scripts/ops/backup-all-tenants.sh
  ```
  *Expected Result:*
  1. Dumps `platform` and all active `t_<slug>` schemas.
  2. Generates SHA-256 checksums (`.sha256`).
  3. Automatically runs a temporary restore drill (`restore_drill_<uuid>`), confirms recovered table counts, and cleans up the drill schema.

---

## 5. Useful Operational & Docker Commands

```powershell
# View streaming logs
docker compose logs -f pgcore-backend
docker compose logs -f pgcore-frontend

# Restart a specific service
docker compose restart pgcore-backend

# Stop the stack
docker compose down

# Stop and wipe volumes (CAUTION: wipes database storage)
docker compose down -v

# Interactive shell in backend container
docker exec -it pgcore-backend /bin/bash

# Direct PostgreSQL shell
docker exec -it pgcore-db psql -U postgres -d pgcrmdb
```
