# PG CRM
### Single-Tenant Property Management System for Paying Guest (PG) & Hostel Operators

A full-stack B2B SaaS application for PG and hostel management, covering guest operations, automated billing, electricity sub-meter calculations, meal tracking, and maintenance ticketing.

---

## 1. System Architecture

**`[PG-CORE]`** is a single-tenant property management application deployed uniquely per PG brand or hostel branch. It manages all daily operations and guest lifecycle.

### Role Architecture & Permission Model

| Role | Display Name | Portal Entry |
| :--- | :--- | :--- |
| `GUEST` | PG Resident | `/guest/dashboard` |
| `PG_MANAGER` | Branch Manager | `/manager/dashboard` |
| `PG_OWNER` | Property Owner | `/owner/dashboard` |
| `SUPER_ADMIN` | Software Provider | `/super-admin` |

### Core Features

- **1-Click Excel Bulk Import**: Instant batch-onboarding of rooms and guests using Apache POI. Features self-healing floor/room auto-creation, overflow bed provisioning, and historical data migration (Opening Rent Arrears, EB Sub-Meter Readings, Meal Plan preferences).
- **Interactive Visual Building Builder Wizard**: Step-by-step floor layout designer. Configures floor maps, blocks, single/double/triple/quad sharing rooms, AC/Non-AC status, and base rent rates.
- **Flat-Rate / Fixed Rent Model**: Strict flat-rate monthly billing with no pro-rated daily rates. Active status during a billing month bills the exact monthly rate.
- **Electricity (EB) Bill Sub-Meter Split**: Automated calculations — Equal Split, Per-Bed flat rate, or Sub-Meter usage (`currentReading - previousReading`), or Manager Manual entry.
- **Automated Data Compliance Scheduler**: Daily PII anonymization sweep at 3:00 AM for guests checked out over 365 days, preserving financial integrity.
- **Razorpay Payment Integration**: Online rent collection with HMAC-SHA256 webhook verification.
- **WhatsApp & Email Notifications**: Guest welcome messages, invoice reminders, and payment confirmations.

---

## 2. Tech Stack

| Domain | Technology | Version |
| :--- | :--- | :--- |
| Java JDK | OpenJDK | Java 23 |
| Backend Framework | Spring Boot | 3.2.5 |
| Build Tool | Apache Maven | 3.9.16 |
| Database | PostgreSQL | 18 |
| DB Migrations | Flyway | Auto on startup |
| Frontend | React | 18.x |
| Build System | Vite | Latest |
| CSS Framework | Tailwind CSS | Latest |
| Node.js | Node.js / npm | v24+ |

---

## 3. Project Directory Layout

```
.
├── core-pg-crm/                    # PG-CORE Single-Tenant Module
│   ├── backend/                    # Spring Boot 3.2.5 Java 23 Backend (Port 8080)
│   │   ├── src/
│   │   │   └── main/
│   │   │       ├── java/com/pgcrm/   # Controllers, Services, Repositories, Entities
│   │   │       └── resources/
│   │   │           ├── application.yml
│   │   │           └── db/migration/ # Flyway SQL migrations (V1–V17+)
│   │   └── pom.xml
│   └── frontend/                   # React 18 / Vite / Tailwind Frontend (Port 5173)
│       ├── src/
│       │   ├── api/index.js         # Axios instance with JWT interceptor
│       │   ├── context/             # AuthContext, SystemConfigContext
│       │   ├── pages/               # Role-separated page components
│       │   └── components/          # Shared components (Sidebar, TopHeader)
│       └── package.json
├── docs/                           # Architectural and Reference Documentation
│   ├── architecture/
│   │   ├── ARCHITECTURE.md
│   │   ├── CALCULATIONS_ENGINE.md
│   │   ├── FILE_ARCHITECTURE.md
│   │   ├── WORKFLOWS.md
│   │   └── sop.md
│   ├── API_REFERENCE.md
│   ├── CODING_STANDARDS.md
│   ├── DATA_MODEL.md
│   ├── DEVELOPER_SETUP.md
│   └── SECURITY.md
├── scripts/                        # Operational Scripts
│   ├── dev/
│   │   └── start_core.bat          # Windows launcher for PG-CORE services
│   └── ops/
│       └── backup.sh               # Database backup script
├── infrastructure/                 # Infrastructure configuration
├── .env.example                    # Template Environment File
├── docker-compose.yml              # Docker Compose for local development
├── AGENTS.md                       # AI Agent Operational Manual
└── README.md
```

---

## 4. Development Quick Start

### Prerequisites

- **JDK 23** installed and on your system PATH
- **Node.js v24+** and **npm** installed
- **PostgreSQL 18** running locally

### Database Initialization

```sql
CREATE DATABASE pgcrmdb;
```

### Running Locally

#### Backend (Port 8080)
```powershell
cd core-pg-crm/backend
$env:SPRING_PROFILES_ACTIVE="dev"; ..\..\apache-maven-3.9.16\bin\mvn spring-boot:run
```

#### Frontend (Port 5173)
```powershell
cd core-pg-crm/frontend
npm install
npm run dev
```

### Running via Docker Compose

```powershell
# Copy and fill in .env values first
copy .env.example .env
# Edit .env with your values, then:
docker compose up -d
```

---

## 5. Ports Reference

| Component | Path | Port | URL |
| :--- | :--- | :--- | :--- |
| **Backend** | `core-pg-crm/backend` | **8080** | `http://localhost:8080` |
| **Frontend** | `core-pg-crm/frontend` | **5173** | `http://localhost:5173` |

---

## 6. Documentation Index

### Core References
- 🤖 **AI Agent Manual**: [AGENTS.md](AGENTS.md) — Mandatory rules and architecture guide for AI agents
- 📖 **System Overview**: [README.md](README.md) — This file

### Architecture & Design
- 🏗️ **System Architecture**: [docs/architecture/ARCHITECTURE.md](docs/architecture/ARCHITECTURE.md)
- 🔄 **System Workflows**: [docs/architecture/WORKFLOWS.md](docs/architecture/WORKFLOWS.md)

### Data & Calculations
- 🗄️ **Data Model**: [docs/DATA_MODEL.md](docs/DATA_MODEL.md)
- 🧮 **Calculations Engine**: [docs/architecture/CALCULATIONS_ENGINE.md](docs/architecture/CALCULATIONS_ENGINE.md)
- 📂 **File Architecture**: [docs/architecture/FILE_ARCHITECTURE.md](docs/architecture/FILE_ARCHITECTURE.md)

### API & Development
- 🌐 **API Reference**: [docs/API_REFERENCE.md](docs/API_REFERENCE.md)
- 💻 **Developer Setup**: [docs/DEVELOPER_SETUP.md](docs/DEVELOPER_SETUP.md)
- 🎨 **Coding Standards**: [docs/CODING_STANDARDS.md](docs/CODING_STANDARDS.md)

### Operations & Security
- 🔒 **Security Guide**: [docs/SECURITY.md](docs/SECURITY.md)
- ⚙️ **SOP Reference**: [docs/architecture/sop.md](docs/architecture/sop.md)
