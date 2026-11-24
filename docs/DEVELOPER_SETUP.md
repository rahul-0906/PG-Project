# PG CRM — Environment Setup & Developer Guide
### Complete local development environment configuration reference

---

## 1. Prerequisites

Ensure the following are installed and available on your system `PATH` before starting:

| Tool | Required Version | Download |
| :--- | :--- | :--- |
| Java JDK (OpenJDK) | **23** | [Adoptium](https://adoptium.net/) |
| Node.js | **v24+** | [nodejs.org](https://nodejs.org/) |
| npm | **v10+** (bundled with Node) | Bundled with Node.js |
| PostgreSQL | **18** | [postgresql.org](https://www.postgresql.org/download/) |
| Git | Any recent version | [git-scm.com](https://git-scm.com/) |
| Docker Desktop (optional) | Latest | [docker.com](https://www.docker.com/products/docker-desktop/) |

> [!NOTE]
> Maven is **NOT** required to be installed globally. The project uses the locally bundled `apache-maven-3.9.16/` in the workspace root.

---

## 2. Initial Setup

### 2.1 Clone the Repository

```powershell
git clone <repository-url>
cd PG-Project
```

### 2.2 Database Initialization

Open a PostgreSQL shell (`psql`) and create the application database:

```sql
CREATE DATABASE pgcrmdb;
```

Default connection settings (local dev):
- Host: `localhost`
- Port: `5432`
- User: `postgres`
- Password: `admin`

> [!TIP]
> If your local PostgreSQL uses a different password, update the `DB_PASSWORD` environment variable or set it in `application-dev.yml`.

### 2.3 Environment Configuration

```powershell
# Copy the template environment file
copy .env.example .env

# Edit .env with your actual values
notepad .env
```

Minimum required values to edit in `.env`:
```dotenv
DB_PASSWORD=your_postgres_password
JWT_SECRET=ThisIsAVeryLongSecretKeyForPGCRMAppThatShouldBeAtLeast256BitsForHS256
```

For email functionality, also set:
```dotenv
MAIL_ENABLED=true
MAIL_USERNAME=your-gmail@gmail.com
MAIL_PASSWORD=your-gmail-app-password   # Google App Password, not your Gmail password
```

---

## 3. Starting the Applications

### 3.1 PG-CORE (`[PG-CORE]` — Port 8080 + 5173)

```powershell
# Terminal 1: Start Backend
cd E:\PG-Project\core-pg-crm\backend
$env:SPRING_PROFILES_ACTIVE="dev"; ..\..\apache-maven-3.9.16\bin\mvn spring-boot:run
```

Wait for the log: `Started PgCrmApplication in X.XXX seconds`

```powershell
# Terminal 2: Start Frontend
cd E:\PG-Project\core-pg-crm\frontend
npm install
npm run dev
```

Frontend will be available at: **http://localhost:5173**

---

## 4. Spring Profiles

| Profile | When Used | Database | Debug Logging |
| :--- | :--- | :--- | :--- |
| `dev` | Local development | Local PostgreSQL | `DEBUG` |
| `prod` | Docker production | PostgreSQL container | `INFO` |
| `test` | Unit/integration tests | (test config) | `INFO` |

> [!IMPORTANT]
> Always set `SPRING_PROFILES_ACTIVE=dev` for local development. The backend defaults to `dev` if the variable is unset, but it's best practice to set it explicitly.

### Profile-Specific Configuration Files

| File | Profile | Purpose |
| :--- | :--- | :--- |
| `application.yml` | Base (all profiles) | Common configuration + defaults |
| `application-dev.yml` | `dev` | Local dev overrides (DB URL, debug flags) |
| `application-prod.yml` | `prod` | Production settings (strict security) |
| `application-test.yml` | `test` | Test settings (H2 in-memory) |

---

## 5. Flyway Database Migrations

Flyway automatically runs **all pending migrations** when Spring Boot starts:
- `db/migration/platform/` applied once to the `platform` registry schema.
- `db/migration/tenant/` applied to each active tenant schema (`t_<slug>`).

### Migration Directories

```
core-pg-crm/backend/src/main/resources/db/migration/
├── platform/
│   └── V1__init_platform.sql
└── tenant/
    ├── V1__init_schema.sql
    ├── ...
    ├── V18__add_invoice_overdue_audit_action.sql
    └── V19__create_tenant_settings.sql
```

### 5.1 Dev Profile: Database Reset & Persistence Strategy

During development, it is often necessary to reset the local database to a clean, well-defined initial state with demo data. However, for subsequent development runs, you want database modifications and user data to persist across application restarts.

The database clean-and-migration behavior is controlled by the `DB_WIPE_ON_STARTUP` environment variable, which is parsed by Spring Boot under the `dev` profile.

> [!CAUTION]
> **`DB_WIPE_ON_STARTUP=true` is ONLY effective in the `dev` and `test` profiles.**
> `MultiTenantFlywayConfig.java` strictly forbids wiping when running under the `prod` profile, and `application-prod.yml` enforces `spring.flyway.clean-disabled: true` as an additional safeguard.

#### Phase 1: The Wipe (Initial Boot)
To completely wipe your local `pgcrmdb` and populate it with fresh schema structures and seeded data:
1. Open your root `.env` file at `E:\PG-Project\.env`.
2. Set:
   ```env
   DB_WIPE_ON_STARTUP=true
   ```
3. Start the application (`scripts/dev/start_core.bat` or `mvn spring-boot:run`).
4. On startup, Flyway drops all existing tables and views, runs migrations from scratch, and triggers `DatabaseSeeder` to load default administrative credentials.

#### Phase 2: Persistence (Subsequent Development Runs)
Once your database is initialized, preserve your data across restarts:
1. Open `.env`.
2. Set:
   ```env
   DB_WIPE_ON_STARTUP=false
   ```
3. Restart the backend. Flyway skips the clean operation, only applies new migrations, and preserves all existing records.

### 5.2 Adding a New Migration
Examine the highest existing version under `db/migration/tenant/` (currently `V19`) and increment:
```powershell
New-Item "E:\PG-Project\core-pg-crm\backend\src\main\resources\db\migration\tenant\V20__your_description.sql"
```
Write your SQL and restart the backend. Never modify existing migration files.

---

## 6. Default Credentials (Development Only)

After first startup with `SEED_DEMO=true`, demo accounts are created:

| Role | Email | Default Password |
| :--- | :--- | :--- |
| `SUPER_ADMIN` | (configured in seeder) | `TempPass@123` |
| `PG_OWNER` | (configured in seeder) | `TempPass@123` |

> [!WARNING]
> All accounts have `mustChangePassword = true` on first login.
> These credentials must **NEVER be used in production**.

---

## 7. Branding Configuration (`tenant-config.yml`)

Each deployed PG-CORE instance can be white-labeled via `tenant-config.yml`:

```yaml
pg:
  system:
    branding:
      name: "Sri Sai PG"           # Full name shown in the browser tab
      short-title: "SSPG"          # Short abbreviation
      primary-color: "#7c3aed"     # Hex color for UI theming
```

The frontend loads these values from `GET /api/config/public` on startup and applies:
- `document.title` ← `pgName`
- `--brand-primary` CSS custom property ← `primaryColor`
- `--brand-primary-hover` ← auto-darkened by 10% via JavaScript

---

## 8. Windows Batch Launcher Scripts

For convenience, double-click these scripts in the project root:

| Script | Function |
| :--- | :--- |
| `scripts/dev/start_core.bat` | Starts both PG-CORE backend (8080) and frontend (5173) |

---

## 9. Docker Compose (Full Stack)

The `docker-compose.yml` in the project root spins up the full PG-Core stack (database + backend + frontend) for containerised testing:

```powershell
# From project root — start full stack
docker compose up -d

# View logs
docker compose logs -f pgcore-backend

# Stop all services
docker compose down

# Stop and remove volumes (CAUTION: deletes all data)
docker compose down -v
```

Services in docker-compose:
| Service | Container | Port |
| :--- | :--- | :--- |
| PG-Core DB | `pgcore-db` | `5432:5432` |
| PG-Core Backend | `pgcore-backend` | `8080:8080` |
| PG-Core Frontend | `pgcore-frontend` | `5173:80` |

---

## 10. Running Tests

### Backend Tests

```powershell
cd E:\PG-Project\core-pg-crm\backend
..\..\apache-maven-3.9.16\bin\mvn test
```

Tests use the `test` profile automatically (configured in `application-test.yml`).

### Frontend Tests

```powershell
cd E:\PG-Project\core-pg-crm\frontend
npm test
```

---

## 11. Common Troubleshooting

| Problem | Likely Cause | Fix |
| :--- | :--- | :--- |
| Backend fails to start with Flyway checksum error | Existing migration file was modified | Restore the original file from Git |
| Backend fails with `validate` mode schema error | New column added without migration | Create a new Flyway migration |
| 401 on all API requests | JWT secret mismatch between restarts | Ensure same `JWT_SECRET` is used |
| Frontend shows blank page / config not loading | Backend not running or CORS misconfigured | Check `APP_CORS_ALLOWED_ORIGINS` includes frontend URL |
| Razorpay payments fail | Test/live key mismatch or `RAZORPAY_ENABLED=false` | Check Razorpay env vars and `RAZORPAY_ENABLED=true` |
| Email not sending | `MAIL_ENABLED=false` or wrong App Password | Set `MAIL_ENABLED=true` and use a Gmail App Password |
| `port 8080 already in use` | Another process using port 8080 | Kill the process: `netstat -ano \| findstr :8080` then `taskkill /PID <pid>` |

---

## 12. IDE Setup Recommendations

### IntelliJ IDEA

1. Open project root `E:\PG-Project` as a Maven project.
2. Set SDK to Java 23 in Project Structure.
3. Enable annotation processing (for Lombok and MapStruct): Settings → Build → Compiler → Annotation Processors → Enable.
4. Install Lombok plugin.
5. Run configurations:
   - Add `SPRING_PROFILES_ACTIVE=dev` to Environment Variables for each Spring Boot run config.

### VS Code (Frontend)

1. Install extensions: `ESLint`, `Tailwind CSS IntelliSense`, `Prettier`.
2. Open `core-pg-crm/frontend` as workspace root.
