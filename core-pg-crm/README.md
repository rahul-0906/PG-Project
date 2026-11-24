# PG CRM Core Module

The `core-pg-crm` module contains the business implementation of the PG CRM property management platform:
- **`backend/`**: Spring Boot 3.2.5, Java 23 REST API and multi-tenant persistence layer.
- **`frontend/`**: React 18, Vite, Tailwind CSS web portal (resident, manager, owner, super admin).

For complete system architecture, deployment modes, and documentation, refer to the root [README.md](../README.md).

---

## 1. Module Structure

```
core-pg-crm/
├── backend/
│   ├── src/main/java/com/pgcrm/       # Business services, controllers, multitenancy
│   ├── src/main/resources/
│   │   ├── application.yml            # Base application configuration
│   │   ├── application-dev.yml        # Local development profile
│   │   ├── application-prod.yml       # Production deployment profile
│   │   └── db/migration/
│   │       ├── platform/              # Platform registry migrations (V1)
│   │       └── tenant/                # Business schema migrations (V1..V19)
│   ├── Dockerfile                     # Multi-stage JDK 23 container build
│   └── pom.xml                        # Maven dependencies & build plugins
└── frontend/
    ├── src/                           # React 18 application source code
    │   ├── api/                       # Axios client & JWT interceptors
    │   ├── components/                # Shared layout and UI components
    │   ├── context/                   # Auth & SystemConfig React contexts
    │   └── pages/                     # Role-separated dashboards & portals
    ├── Dockerfile                     # Multi-stage Nginx static web container build
    ├── vite.config.js                 # Vite build & PWA plugin configuration
    └── package.json                   # NPM dependencies & scripts
```

---

## 2. Local Development Execution

### Backend (Port 8080)
```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE="dev"
"C:\Program Files\apache-maven-3.9.16\bin\mvn" spring-boot:run
```

### Frontend (Port 5173)
```powershell
cd frontend
npm install
npm run dev
```

---

## 3. Database & Migrations

Database migrations are managed via Flyway (`MultiTenantFlywayConfig.java`):
1. **Platform Migration**: Runs `db/migration/platform/` once against the `platform` schema to establish the tenant registry.
2. **Tenant Migrations**: Runs `db/migration/tenant/` against every active tenant schema (`t_<slug>`).

> [!IMPORTANT]
> Never edit existing migration scripts. Examine the latest version in `db/migration/tenant/` and create an incremented migration: `V{N+1}__{description}.sql`.
