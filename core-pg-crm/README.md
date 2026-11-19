# PG CRM Core Application

The **PG CRM Core Application** is a **single-tenant** property management system for Paying Guest (PG) and hostel operators. Each PG business gets its own dedicated, isolated deployment of this application pointing to its own PostgreSQL database.

---

## 1. System Overview

This application manages the full daily lifecycle of a PG property:

- **Guest Operations**: Check-in, bed allocation (single/shared/whole-room), checkout, KYC tracking
- **Automated Billing**: Monthly flat-rate invoicing, electricity sub-meter splits, add-on charges
- **Meal Management**: Daily food preference tracking with configurable lockout times
- **Payments**: Razorpay online collection + manual cash/UPI recording
- **Notifications**: In-app, email (SMTP), and WhatsApp (Meta Cloud API) alerts
- **Reporting**: Occupancy rates, revenue trends, and arrears tracking
- **Maintenance**: Ticket logging and resolution by managers

### Role Hierarchy

| Role | Display Name | Scope |
| :--- | :--- | :--- |
| `GUEST` | PG Resident | Own portal: invoices, meal calendar, maintenance requests |
| `PG_MANAGER` | Branch Manager | Guest operations, EB bills, daily logs, invoice generation |
| `PG_OWNER` | Property Owner | Building setup, manager accounts, revenue reports |
| `SUPER_ADMIN` | Software Provider | Full system access, user RBAC, audit forensics |

---

## 2. Local Development

### Database Setup

```sql
CREATE DATABASE pgcrmdb;
```

### Backend (Port 8080)

```powershell
cd core-pg-crm/backend
$env:SPRING_PROFILES_ACTIVE="dev"; ..\..\apache-maven-3.9.16\bin\mvn spring-boot:run
```

### Frontend (Port 5173)

```powershell
cd core-pg-crm/frontend
npm install
npm run dev
```

---

## 3. Data Seeding Architecture

### A. `DatabaseSeeder.java` — System Bootstrap
- Runs in **all profiles** (dev, test, prod)
- Creates the root `SUPER_ADMIN` account if none exists
- Safe: "insert only if missing" logic — never overwrites existing records

### B. `DataSeeder.java` — Demo Data
- Runs only in `dev` and `test` profiles
- Seeds mock buildings, guests, invoices, and meal history
- Completely bypassed in `prod`

---

## 4. Database Wipe (Dev Only)

> [!CAUTION]
> **NEVER activate `DB_WIPE_ON_STARTUP=true` in production. It drops all tables and all data.**

The `FlywayConfig.java` bean is annotated `@Profile("dev | test")` — it cannot load in production.
A separate `FlywayProdConfig.java` is used in `prod` and only ever calls `flyway.migrate()`.

| Profile | Wipe Allowed | Config class |
| :--- | :--- | :--- |
| `dev` | ✅ When `DB_WIPE_ON_STARTUP=true` | `FlywayConfig.java` |
| `test` | ✅ When `DB_WIPE_ON_STARTUP=true` | `FlywayConfig.java` |
| `prod` | ❌ Never | `FlywayProdConfig.java` |

Also enforced at the YAML level: `application-prod.yml` sets `spring.flyway.clean-disabled: true`.

---

## 5. Flyway Migrations

Migrations live in `src/main/resources/db/migration/` and are run automatically on startup.

**Rules:**
- **NEVER modify** an existing migration file — it will cause a checksum failure
- Always create a new file with the next version: `V{N+1}__description.sql`
