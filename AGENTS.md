# AGENTS.md — AI Agent Guidelines & Architecture Guide

Welcome, AI Agent! This document defines the operational rules, system architecture, core domain constraints, tech stack specifications, and development workflows for working on the **PG CRM Ecosystem** workspace.

---

## 1. Executive Summary & Architecture Overview

The **PG CRM Ecosystem** is a decoupled B2B SaaS Enterprise Monorepo designed for Paying Guest (PG) and hostel management. It separates tenant-facing property operations from centralized administrative billing and automated tenant provisioning.

### Dual-Module Monorepo Architecture

1. **`[PG-CORE]` (Single-Tenant Property Management)**
   - **Path**: `file:///e:/PG-Project/core-pg-crm/`
   - **Scope**: Manages daily PG operations including guest onboarding, check-ins, daily arrears billing, electricity (EB) sub-meter split calculations, visual meal calendars, and maintenance ticketing.
   - **Tenancy Model**: Physical database isolation. Each client runs an independent containerized stack pointing to a dedicated PostgreSQL database. Cross-tenant data leaks are physically impossible.

2. **`[CONTROL-PLANE]` (Central B2B SaaS Billing & Provisioning)**
   - **Path**: `file:///e:/PG-Project/master-control-plane/`
   - **Scope**: B2B client onboarding, Razorpay payment processing (Setup Fees & AMCs), automated tenant stack provisioning, subscription renewal monitoring, and tenant suspensions.
   - **Security**: HMAC-SHA256 signature verification on Razorpay webhooks to guarantee cryptographically validated tenant provisioning.

---

## 2. Tech Stack & Environmental Specifications

| Domain | Technology / Tool | Version / Details |
| :--- | :--- | :--- |
| **Java JDK** | OpenJDK | Java 23 |
| **Backend Framework** | Spring Boot | 3.2.5 |
| **Build Tool** | Apache Maven | Embedded / Local (3.9.16) |
| **Database** | PostgreSQL | PostgreSQL 18 (Local dev default) |
| **Database Migrations** | Flyway | Executed automatically on Spring Boot bootup |
| **Frontend Framework** | React | 18.x |
| **Build System** | Vite | Latest |
| **Styling** | Tailwind CSS | Latest |
| **Runtime Environment** | Node.js / npm | Node v24+ |

### Port & Routing Registry

| Component | Target Location | Default Port | Local URL |
| :--- | :--- | :--- | :--- |
| **`[PG-CORE] Backend`** | `core-pg-crm/backend` | **8080** | `http://localhost:8080` |
| **`[PG-CORE] Frontend`** | `core-pg-crm/frontend` | **5173** | `http://localhost:5173` |
| **`[CONTROL-PLANE] Backend`** | `master-control-plane/backend` | **8090** | `http://localhost:8090` |
| **`[CONTROL-PLANE] Frontend`** | `master-control-plane/frontend` | **5176** | `http://localhost:5176` |

*Dynamically provisioned single-tenant instances use ports `8081`, `8082`, etc., proxied via Nginx.*

---

## 3. Strict Agent Operational Rules & Guidelines

When operating within this workspace, AI agents **MUST** follow these rules without exception:

### 🛡️ File Placement & Sandbox Integrity
- **Always write project documentation, design plans, SOPs, and checklists directly to the project locations** (e.g., project root `/`, `/docs`, or relevant subdirectories within the active project workspace) instead of creating them exclusively in hidden system configuration directories or temporary directories (`C:\Users\Admin\.gemini\antigravity\brain\...`).
- Maintain all architectural resources and operational artifacts inside the project structure to ensure Git tracking and team visibility.

### 📐 Business Domain Rules & Calculation Logic
- **Fixed/Flat-Rate Rent Billing Model**: Strictly enforce flat monthly rent. Do NOT apply pro-rated daily calculations. Active status during a billing month bills the full monthly rate.
- **Electricity (EB) Calculations**: Respect the configured split mode:
  1. *Equal Split*: Total bill divided equally among active occupants.
  2. *Per-Bed Flat Rate*: Standard fixed charge per bed.
  3. *Sub-Meter Usage*: Computed via `(currentReading - previousReading) * ratePerUnit`.
- **Data Compliance & Anonymization**: Preserve financial integrity when anonymizing guest records. Scrub PII (name, phone) for checkouts >365 days while retaining transactional audit logs.

### 💻 Code Modifications & Migrations
- **Flyway Migrations**: Never modify existing, applied SQL migration scripts in `src/main/resources/db/migration`. Always create a new versioned migration file (e.g., `V1_x__description.sql`).
- **Spring Profile**: Ensure local dev execution explicitly sets `SPRING_PROFILES_ACTIVE=dev`.

---

## 4. Development & Build Commands

### Backend (Spring Boot 3.2.5 - Java 23)
```powershell
# PG-CORE Backend (Port 8080)
cd core-pg-crm/backend
$env:SPRING_PROFILES_ACTIVE="dev"; ../../apache-maven-3.9.16/bin/mvn spring-boot:run

# CONTROL-PLANE Backend (Port 8090)
cd master-control-plane/backend
$env:SPRING_PROFILES_ACTIVE="dev"; ../../apache-maven-3.9.16/bin/mvn spring-boot:run
```

### Frontend (React 18 + Vite)
```powershell
# PG-CORE Frontend (Port 5173)
cd core-pg-crm/frontend
npm install
npm run dev

# CONTROL-PLANE Frontend (Port 5176)
cd master-control-plane/frontend
npm install
npm run dev
```

---

## 5. Documentation Directory Map

For deeper architectural context, consult the documentation index:
- 📖 [README.md](file:///e:/PG-Project/README.md) - System overview and quickstart guide.
- 🏗️ [docs/architecture/ARCHITECTURE.md](file:///e:/PG-Project/docs/architecture/ARCHITECTURE.md) - Core system architecture and lifecycle.
- 🧮 [docs/architecture/CALCULATIONS_ENGINE.md](file:///e:/PG-Project/docs/architecture/CALCULATIONS_ENGINE.md) - Mathematical calculation models.
- 📂 [docs/architecture/FILE_ARCHITECTURE.md](file:///e:/PG-Project/docs/architecture/FILE_ARCHITECTURE.md) - Directory layout and file reference.
- ⚙️ [docs/architecture/sop.md](file:///e:/PG-Project/docs/architecture/sop.md) - Unified DevOps SOP & troubleshooting.
