# PG CRM — Proof of Concept (POC) Document

## 1. Objective

To validate and demonstrate the feasibility of the **PG CRM Core** single-tenant property management system. The POC proves that the system can handle the full daily lifecycle of a PG property — from guest check-in through automated billing, electricity sub-meter splits, payment collection, and data compliance.

---

## 2. Scope of the POC

The POC covers the following flows end-to-end:

- **Guest Lifecycle**: Check-in → bed allocation → daily meal/add-on logging → invoice generation → payment → checkout
- **Electricity Billing**: Sub-meter reading entry and automated per-guest split calculation
- **Online Payments**: Razorpay order creation + HMAC-SHA256 webhook verification → invoice marked PAID
- **Scheduled Automation**: Monthly billing cron, daily payment reminders, PII anonymization compliance sweep
- **Role-Based Access Control**: GUEST / PG_MANAGER / PG_OWNER / SUPER_ADMIN isolation

---

## 3. Architecture Components

| Component | Technology | Port |
| :--- | :--- | :--- |
| **Backend** | Spring Boot 3.2.5, Java 23 | 8080 |
| **Frontend** | React 18 + Vite + Tailwind CSS | 5173 |
| **Database** | PostgreSQL 18 | 5432 |

**Single-tenant model**: one dedicated database (`pgcrmdb`) per PG operator deployment. No shared schemas, no cross-tenant data risk.

---

## 4. POC Workflow Demonstration

### A. Guest Check-In

1. Manager logs in (`PG_MANAGER` role) → opens Guest Check-In form.
2. Selects room and bed(s), enters deposit, rent, check-in date, meal preferences.
3. System creates `Guest` record, assigns `Bed` to `OCCUPIED`, creates `User` with temp password.
4. WhatsApp/email welcome notification sent.

### B. Monthly Invoice Generation

1. `MonthlyBillingScheduler` fires on the 1st of each month at midnight.
2. For each active guest: flat-rate rent + EB share + add-on totals = invoice.
3. Invoices created with status `PENDING` and due date.

### C. Electricity Bill Split

1. Manager enters current and previous meter readings.
2. System calculates split per active guest using configured `EbSplitMethod`:
   - `EQUAL_SPLIT`: total ÷ active guests
   - `PER_BED`: fixed rate × beds
   - `METER_BASED`: (current − previous) × rate per unit
3. EB charges appended to current month's invoices.

### D. Online Payment (Razorpay)

1. Guest opens invoice → clicks "Pay Online".
2. Frontend calls `POST /api/payments/create-order` → Razorpay order ID returned.
3. Guest completes payment in Razorpay modal.
4. Razorpay fires webhook → `POST /api/webhooks/razorpay`.
5. Backend verifies HMAC-SHA256 signature → invoice updated to `PAID`.

---

## 5. Success Criteria

- [x] Guest can be checked in and assigned to a bed
- [x] Monthly invoices auto-generate with correct flat-rate amounts
- [x] EB bill split calculates correctly for all split methods
- [x] Razorpay payment webhook verifies signature and marks invoice PAID
- [x] Manager can manually record cash/UPI payments
- [x] SUPER_ADMIN can view audit trail of all operations
- [x] PII anonymization scheduler runs daily at 3AM and scrubs stale guest data
- [x] DB wipe is blocked in `prod` profile by both `@Profile` guard and `flyway.clean-disabled: true`

---

## 6. Test Accounts (Dev Seed)

| Role | Email | Default Password |
| :--- | :--- | :--- |
| `SUPER_ADMIN` | Configured in `DatabaseSeeder.java` | `TempPass@123` |
| `PG_OWNER` | Configured in `DatabaseSeeder.java` | `TempPass@123` |

> [!WARNING]
> All seeded accounts have `mustChangePassword = true`. These credentials are dev-only and must never appear in production.
