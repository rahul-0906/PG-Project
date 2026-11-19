# End-to-End (E2E) Testing Runbook

This runbook guides QA and DevOps engineers through executing end-to-end integration and verification checks for the **PG CRM Core Application**. The testing simulates a full workflow from system startup through guest operations, billing, and payment.

---

## Phase 1: Environment & Services Verification

Before starting, verify that the backend, database, and frontend are all running.

- [ ] **Verify PostgreSQL Service**
  - Start the local PostgreSQL instance.
  - Connect via `psql` or a GUI client (PgAdmin/DBeaver) and verify:
    * `pgcrmdb` exists and is accessible
  - *Expected Result:* Database is online and responding.

- [ ] **Start and Verify PG-Core Backend**
  - Navigate to `core-pg-crm/backend/` and start the Spring Boot app:
    ```powershell
    $env:SPRING_PROFILES_ACTIVE="dev"
    "C:\Program Files\apache-maven-3.9.16\bin\mvn" spring-boot:run
    ```
  - *Expected Result:* Terminal outputs `Started PgCrmApplication in X.XXX seconds` on port `8080`.

- [ ] **Start and Verify PG-Core Frontend**
  - Navigate to `core-pg-crm/frontend/` and run:
    ```bash
    npm run dev
    ```
  - *Expected Result:* Vite dev server runs on `http://localhost:5173`.

---

## Phase 2: Health Check

- [ ] **API Health Check**
  ```powershell
  curl http://localhost:8080/api/config/public
  ```
  *Expected Result:* `200 OK` with JSON containing `pgName`, `primaryColor`, `shortTitle`.

- [ ] **Frontend Load Check**
  - Open `http://localhost:5173` in browser.
  - *Expected Result:* PG CRM login page loads with correct branding.

---

## Phase 3: Authentication Flow

- [ ] **Login as SUPER_ADMIN**
  ```
  POST http://localhost:8080/api/auth/login
  { "email": "<super-admin-email>", "password": "<password>" }
  ```
  *Expected Result:* `200 OK` with `accessToken`, `refreshToken`, `role: SUPER_ADMIN`.

- [ ] **Login as PG_OWNER**
  *Expected Result:* `200 OK` with `role: PG_OWNER`.

- [ ] **Login as PG_MANAGER**
  *Expected Result:* `200 OK` with `role: PG_MANAGER`.

- [ ] **Invalid Login**
  *Expected Result:* `401 Unauthorized`.

---

## Phase 4: Building & Inventory

- [ ] **List Buildings**
  ```
  GET http://localhost:8080/api/inventory/buildings
  Authorization: Bearer <token>
  ```
  *Expected Result:* Array of building objects with floors, blocks, rooms, beds.

- [ ] **Check Bed Availability**
  *Expected Result:* Vacant beds show `status: VACANT`.

---

## Phase 5: Guest Check-In

- [ ] **Check-In a New Guest**
  ```
  POST http://localhost:8080/api/manager/guests/check-in
  ```
  *Expected Result:* `201 Created` with guest ID, bed status changes to `OCCUPIED`.

- [ ] **View Guest Dashboard (as Guest)**
  *Expected Result:* Guest sees their room, check-in date, invoice count.

---

## Phase 6: Invoice Generation

- [ ] **Generate Monthly Invoice**
  ```
  POST http://localhost:8080/api/manager/invoices/generate
  ```
  *Expected Result:* Invoices created for all active guests with correct flat-rate amounts.

- [ ] **View Invoice as Guest**
  *Expected Result:* Guest sees their invoice with line items (rent, EB share, add-ons).

---

## Phase 7: Payment Flow

- [ ] **Initiate Razorpay Payment (if enabled)**
  ```
  POST http://localhost:8080/api/payments/create-order
  ```
  *Expected Result:* Razorpay order ID returned.

- [ ] **Verify Webhook Signature**
  *Expected Result:* Webhook endpoint validates HMAC-SHA256 and updates invoice to `PAID`.

---

## Phase 8: Scheduler Verification

- [ ] **Confirm Monthly Billing Scheduler is registered**
  - Check Spring Boot logs on startup for: `Scheduled task: MonthlyBillingScheduler`
  - Cron: `0 0 0 1 * *` (midnight on the 1st of each month)

- [ ] **Confirm Payment Reminder Scheduler**
  - Cron: `0 0 9 * * *` (daily at 9AM)

- [ ] **Confirm Data Retention Scheduler**
  - Cron: `0 0 3 * * ?` (daily at 3AM)
  - Anonymizes PII for guests checked out >365 days ago.

---

## Phase 9: Guest Checkout

- [ ] **Check Out a Guest**
  ```
  POST http://localhost:8080/api/manager/guests/{guestId}/checkout
  ```
  *Expected Result:* Guest marked as checked out, bed status returns to `VACANT`, settlement calculated.

---

## Phase 10: Backup Verification

- [ ] **Run Backup Script**
  ```powershell
  E:\PG-Project\scripts\ops\backup_pgcrmdb.bat
  ```
  *Expected Result:* `.dump` file created in `E:\PG-Project\backups\`.

- [ ] **Test Restore to Test Database**
  ```powershell
  E:\PG-Project\scripts\ops\restore_pgcrmdb.bat E:\PG-Project\backups\<latest.dump>
  ```
  *Expected Result:* `pgcrmdb_restore_test` database created and populated successfully.
