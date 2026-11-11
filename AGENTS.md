# AGENTS.md — AI Agent Guidelines & Architecture Guide

Welcome, AI Agent! This document defines the operational rules, system architecture, core domain constraints, tech stack specifications, and development workflows for working on the **PG CRM** workspace.

---

## 1. Executive Summary & Architecture Overview

**PG CRM** is a single-tenant property management application for Paying Guest (PG) and hostel operators. It manages all daily property operations, guest lifecycle, billing, electricity sub-meter calculations, meal tracking, and maintenance ticketing.

### Module: `[PG-CORE]` (Single-Tenant Property Management)

- **Path**: `file:///e:/PG-Project/core-pg-crm/`
- **Scope**: Guest onboarding, check-ins, daily arrears billing, electricity (EB) sub-meter split calculations, visual meal calendars, and maintenance ticketing.
- **Tenancy Model**: Physical database isolation. Each PG client runs an independent containerized stack with its own PostgreSQL database. Cross-tenant data leaks are physically impossible.

### Spatial Hierarchy

```
Building → Floor → Block → Room → Bed
```

---

## 2. Tech Stack & Environmental Specifications

| Domain | Technology / Tool | Version / Details |
| :--- | :--- | :--- |
| **Java JDK** | OpenJDK | Java 23 |
| **Backend Framework** | Spring Boot | 3.2.5 |
| **Build Tool** | Apache Maven | Local (3.9.16 at `apache-maven-3.9.16/`) |
| **Database** | PostgreSQL | 18 |
| **Database Migrations** | Flyway | Auto-executed on Spring Boot startup |
| **Frontend Framework** | React | 18.x |
| **Build System** | Vite | Latest |
| **Styling** | Tailwind CSS | Latest |
| **Runtime** | Node.js / npm | v24+ |

### Port Registry

| Component | Path | Port | Local URL |
| :--- | :--- | :--- | :--- |
| **Backend** | `core-pg-crm/backend` | **8080** | `http://localhost:8080` |
| **Frontend** | `core-pg-crm/frontend` | **5173** | `http://localhost:5173` |

---

## 3. Strict Agent Operational Rules

When operating in this workspace, AI agents **MUST** follow these rules without exception:

### 🛡️ File Placement & Sandbox Integrity
- **Always write project documentation, design plans, SOPs, and checklists directly to the project locations** (`E:\PG-Project\docs\`, project root, or relevant subdirectories).
- **NEVER** write project files to `C:\Users\Admin\.gemini\antigravity\brain\...` or any hidden system directory. These are for internal agent scratch only.
- Maintain all artifacts inside the project for Git tracking and team visibility.

### 🗄️ Flyway Migration Immutability
- **NEVER modify** any existing SQL file under `src/main/resources/db/migration/`.
- Always **create a new file**: examine the highest existing version and increment it (e.g., `V18__description.sql`).
- Naming convention: `V{version}__{snake_case_description}.sql`

### 💰 Flat-Rate Billing Model (Business-Critical)
- **NEVER apply pro-rated or daily rent calculations.**
- If a guest is active for any portion of a billing month, they are billed the **full monthly rate**.
- Formula: `Base Rent Charge = guest.getMonthlyRent()`
- For Whole Room Bookings: `Adjusted Rent = guest.getMonthlyRent() × room.getSharingType()`
- See `docs/architecture/CALCULATIONS_ENGINE.md` for the complete specification.

### ⚡ Electricity Bill Split Logic
Bills are split using the building-configured `EbSplitMethod`:
1. **`EQUAL_SPLIT`**: `perGuestShare = totalBillAmount / activeGuestCount`
2. **`PER_BED`**: `perGuestShare = fixedRatePerBed × guestBedCount`
3. **`METER_BASED`**: `perGuestShare = (currentReading − previousReading) × ratePerUnit`
4. **`MANAGER_MANUAL`**: Manager enters each guest's amount individually.

Always read from `BuildingConfig.getEbSplitMethod()`. Never hardcode a different calculation.

### 🔐 Security & RBAC
Spring Security enforces these URL-to-role mappings:

| URL Pattern | Required Roles |
| :--- | :--- |
| `/api/auth/**` | Public (except `/api/auth/change-password`) |
| `/api/super-admin/**` | `SUPER_ADMIN` only |
| `/api/owner/**` | `PG_OWNER`, `SUPER_ADMIN` |
| `/api/manager/**` | `PG_MANAGER`, `PG_OWNER`, `SUPER_ADMIN` |
| `/api/guest/**` | `GUEST`, `PG_MANAGER`, `PG_OWNER`, `SUPER_ADMIN` |
| `/api/reports/**` | `PG_OWNER`, `PG_MANAGER`, `SUPER_ADMIN` |
| `/api/inventory/**` | `PG_MANAGER`, `PG_OWNER`, `SUPER_ADMIN` |
| `/api/payments/**` | `PG_MANAGER`, `PG_OWNER`, `SUPER_ADMIN` |
| `/api/webhooks/**` | Public (Razorpay webhook) |
| `/api/config/public` | Public (frontend branding) |

**NEVER add a wildcard `permitAll()` on `/**`**. Any new endpoint must follow the table above.

### 🔒 Security Anti-Patterns (Never Do These)
- **SEC-01**: Never hardcode JWT secrets, API keys, or passwords in code or YAML defaults.
- **SEC-02**: Never use CORS `allowedOriginPatterns("*")` with `allowCredentials(true)`.
- **SEC-03**: Never add a `/**` `permitAll()` wildcard to SecurityConfig.
- **SEC-04**: Never log raw passwords, tokens, or personal data.
- **SEC-05**: Never disable Razorpay HMAC signature verification.

### 💾 Soft Deletes (Never Hard Delete Guests or Invoices)
- `Guest` and `Invoice` entities use soft-delete (`is_deleted = true`).
- **NEVER call `repository.deleteById()`** on Guest or Invoice records.
- Use the service-layer checkout/deletion methods.

### 🔒 Owner Permission Guard
- Data modifications for `PG_OWNER` accounts are protected by `users.allow_super_admin_access`.
- Always check this flag before proceeding. Do not remove this guard.

### 📅 Data Retention & PII Anonymization
- Guests checked out for **>365 days** are automatically anonymized by `DataRetentionScheduler.java`.
- Anonymization scrubs: `fullName` → random token, `phone` → random token.
- **Financial records, audit logs, and invoices are PRESERVED — never delete them.**
- Scheduler runs daily at 3:00 AM (`0 0 3 * * ?`).

### 🏗️ DDL & Schema Changes
- `spring.jpa.hibernate.ddl-auto` is set to **`validate`** — Hibernate will NOT auto-create/alter tables.
- ALL schema changes go through **Flyway migration files only**.
- Never change `ddl-auto` to `create`, `create-drop`, or `update` in non-test profiles.

---

## 4. Development & Build Commands

### Backend (Spring Boot — Java 23 + Maven)
```powershell
cd E:\PG-Project\core-pg-crm\backend
$env:SPRING_PROFILES_ACTIVE="dev"; ..\..\apache-maven-3.9.16\bin\mvn spring-boot:run
```

### Frontend (React 18 + Vite)
```powershell
cd E:\PG-Project\core-pg-crm\frontend
npm install
npm run dev
```

### Database Initialization
```sql
CREATE DATABASE pgcrmdb;
```

---

## 5. Documentation Directory Map

- 📖 [README.md](file:///e:/PG-Project/README.md) — System overview and quickstart guide
- 🏗️ [docs/architecture/ARCHITECTURE.md](file:///e:/PG-Project/docs/architecture/ARCHITECTURE.md) — Core system architecture and lifecycle
- 🧮 [docs/architecture/CALCULATIONS_ENGINE.md](file:///e:/PG-Project/docs/architecture/CALCULATIONS_ENGINE.md) — Mathematical calculation models
- 📂 [docs/architecture/FILE_ARCHITECTURE.md](file:///e:/PG-Project/docs/architecture/FILE_ARCHITECTURE.md) — Directory layout and file reference
- ⚙️ [docs/architecture/sop.md](file:///e:/PG-Project/docs/architecture/sop.md) — Unified DevOps SOP & troubleshooting
- 🌐 [docs/API_REFERENCE.md](file:///e:/PG-Project/docs/API_REFERENCE.md) — Complete REST endpoint documentation
- 🔒 [docs/SECURITY.md](file:///e:/PG-Project/docs/SECURITY.md) — Security policies and threat model
