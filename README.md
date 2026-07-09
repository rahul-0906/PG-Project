# PG CRM Ecosystem
### Decoupled B2B SaaS Enterprise Monorepo for Paying Guest (PG) & Hostel Management

This repository operates as a **Monorepo**, housing the complete decoupled software suite required to run a multi-tenant, white-labeled B2B SaaS business for Paying Guest (PG) and hostel operators. 

The architecture separates customer-facing property operations from the centralized administrative billing and automated provisioning infrastructure to guarantee tenant security, high availability, and horizontal scalability.

---

## 1. Dual-Module System Architecture

The ecosystem consists of two core applications, each containing its own isolated backend and frontend stacks:

### 🏢 `[PG-CORE]` (Single-Tenant Property Management)
**Path:** `/core-pg-crm/`
The property management software deployed uniquely for each PG brand/hostel branch.
* **Scope**: Daily property operations. Manages guest check-ins, automated daily arrears billing, electricity (EB) sub-meter calculations, visual meal calendars, and maintenance ticketing.
* **Security & Tenancy**: Multi-tenant via physical isolation. Each client runs an independent containerized stack (Vite + Spring Boot) pointing to a dedicated PostgreSQL database. Cross-tenant data leaks are physically impossible.

### ⚙️ `[CONTROL-PLANE]` (Central B2B SaaS Billing & Provisioning)
**Path:** `/master-control-plane/`
The centralized command center and automated public front door for the software provider.
* **Scope**: B2B client onboarding, Razorpay payment capture (Setup Fees & Annual Maintenance Contracts), automatic instance deployment, subscription renewal, status monitoring, and license suspension.
* **Security**: Enforces strict HMAC-SHA256 signature verification on Razorpay webhook notifications to trigger automated deployments only after successful payment captures.

### 🚀 Core Operational & Financial Features

* **1-Click Excel Bulk Import**: Instant batch-onboarding of hundreds of rooms and guests using Apache POI. Features self-healing floor/room auto-creation, overflow bed provisioning, and historical data migration (Opening Rent Arrears, initial EB Sub-Meter Readings, and Meal Plan preferences).
* **Flat-Rate / Fixed Rent Model**: Strict flat-rate monthly billing that eliminates pro-rated daily rates. Active status during a billing month bills the exact monthly rate (with full room capacity multiplication for Whole Room Bookings). Exit-month checkout settlements charge the full exit month (Option A).
* **Electricity (EB) Bill Sub-Meter Split**: Automated calculations matching physical configurations—Equal Split among active residents, Per-Bed flat rate, or Sub-Meter usage differences (`currentReading - previousReading`).
* **Automated Data Compliance Scheduler**: Programmatic daily PII Anonymization sweep at 3:00 AM. Scrubs guests checked out over 365 days ago, overwriting names and phone numbers with random tokens while preserving financial, billing, and transactional integrity.

---

## 2. Ports and Routing Registry

To allow concurrent local development and clear production DNS mapping, the following port assignments are standard:

| Component | Module | Context | Port | Default URL |
| :--- | :--- | :--- | :--- | :--- |
| **`[PG-CORE] Backend`** | `core-pg-crm/backend` | Java 23 REST API | **8080** | `http://localhost:8080` |
| **`[PG-CORE] Frontend`** | `core-pg-crm/frontend` | React 18 App | **5173** | `http://localhost:5173` |
| **`[CONTROL-PLANE] Backend`** | `master-control-plane/backend` | Java 23 REST API | **8090** | `http://localhost:8090` |
| **`[CONTROL-PLANE] Frontend`** | `master-control-plane/frontend` | React 18 Admin | **5176** | `http://localhost:5176` |

*Note: Dynamically provisioned single-tenant tenant instances are assigned ports incrementally starting from **8081** (e.g. `8081`, `8082`, etc.) and mapped via Nginx reverse proxies to custom client subdomains (e.g. `srisaipg.pgcrm.com`).*

---

## 3. Monorepo Directory Layout

The workspace is organized as follows:

```
.
├── core-pg-crm/                    # Standalone, White-Labeled Single-Tenant PG Core Module
│   ├── backend/                    # Spring Boot 3.2.5 Java 23 Backend (Port 8080)
│   │   ├── src/                    # Controller, Service, Repository layers
│   │   └── pom.xml                 # Core backend dependencies (Flyway, JPA, Web)
│   └── frontend/                   # React 18 / Vite / Tailwind Frontend (Port 5173)
├── master-control-plane/           # Central Billing, Client Management, & Auto-Provisioning Engine
│   ├── backend/                    # Spring Boot 3.2.5 Central Engine (Port 8090)
│   │   ├── src/                    # Webhook, Billing, and ProcessBuilder services
│   │   └── pom.xml                 # Control Plane backend dependencies (Razorpay SDK)
│   └── frontend/                   # React 18 Admin Dashboard (Port 5176)
├── docs/                           # Architectural, Workflows, and SOP Documentation
│   ├── ARCHITECTURE.md             # System Architecture & SaaS Lifecycle Flow
│   ├── CALCULATIONS_ENGINE.md      # Billing Formulas and Logic Reference
│   ├── FILE_ARCHITECTURE.md        # File Registry & Module Registry Reference
│   ├── ONBOARDING.md               # DevOps Tenant Onboarding Procedures
│   ├── WORKFLOWS.md                # System Sequence Diagrams & State Transitions
│   ├── control_plane_architecture.md # SaaS Central Control Plane Architecture Design
│   └── sop.md                      # Unified Technical Reference & DevOps SOP
├── scripts/                        # Operational and Tenant Automation Scripts
│   └── provision_tenant.sh         # Bash script executing automated dockerized stack creation
├── .env.example                    # Template Environment File
├── docker-compose.yml              # Local Development Shared Containers config (PostgreSQL)
├── start_control.bat               # Windows launcher for CONTROL-PLANE services
└── start_core.bat                  # Windows launcher for PG-CORE services
```

---

## 4. Development Quick Start & Startup Commands

### Prerequisites
* **JDK 23** installed and configured on your system PATH.
* **Node.js (v24+)** and **npm** installed.
* **PostgreSQL 18** database running locally.

### Database Initialization
Before running either application locally, connect to your PostgreSQL instance and initialize the target databases:
```sql
-- Create database for Core Hostel Management (PG-CORE)
CREATE DATABASE pgcrmdb;

-- Create database for Central SaaS Billing (CONTROL-PLANE)
CREATE DATABASE controlplane_db;
```

### Quick Run via Batch Scripts (Windows)
Double-click the respective launcher scripts in the root directory:
* Run `start_core.bat` to launch **`[PG-CORE]`** services.
* Run `start_control.bat` to launch **`[CONTROL-PLANE]`** services.

### Manual Commands (Cross-Platform)

#### 1. Running `[PG-CORE]`
```bash
# Start Backend (Port 8080)
cd core-pg-crm/backend
# On PowerShell:
$env:SPRING_PROFILES_ACTIVE="dev"; ../../apache-maven-3.9.16/bin/mvn spring-boot:run

# Start Frontend (Port 5173)
cd ../frontend
npm install
npm run dev
```

#### 2. Running `[CONTROL-PLANE]`
```bash
# Start Backend (Port 8090)
cd master-control-plane/backend
# On PowerShell:
$env:SPRING_PROFILES_ACTIVE="dev"; ../../apache-maven-3.9.16/bin/mvn spring-boot:run

# Start Frontend (Port 5176)
cd ../frontend
npm install
npm run dev
```

---

## 5. Deployment & Documentation Index

For exhaustive execution SOPs, mathematical formulas, and lifecycle details, consult the following references:
* **Local Development & Deployment SOP**: [sop.md](file:///E:/Antigravity%20Project/PG%20Project/docs/sop.md)
* **SaaS Tenant Lifecycle & System Topology**: [docs/ARCHITECTURE.md](file:///E:/Antigravity%20Project/PG%20Project/docs/ARCHITECTURE.md)
* **Mathematical Calculation Models**: [docs/CALCULATIONS_ENGINE.md](file:///E:/Antigravity%20Project/PG%20Project/docs/CALCULATIONS_ENGINE.md)
