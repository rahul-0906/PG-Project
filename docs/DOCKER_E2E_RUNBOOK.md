# Docker E2E Runbook: PG-Core Local Stack

This runbook guides you through running the full PG-Core stack locally using Docker Compose, including database, backend, and frontend containers.

---

## 1. Pre-Flight Checks

Before starting the Docker stack, ensure:

- **Docker Desktop is running** on your system.
- **No process is using ports 5432, 8080, or 5173** on your host. Check with:
  ```powershell
  netstat -ano | findstr ":5432 :8080 :5173"
  ```
- **Stop any bare-metal dev servers** (Spring Boot or Vite) that may be running on those ports.

---

## 2. Environment Setup (One-Time)

Copy and fill in the environment file:

```powershell
# From project root
copy .env.example .env
notepad .env
```

Minimum required values:

```dotenv
DB_PASSWORD=your_secure_password
JWT_SECRET=your_256bit_secret_here
```

> [!IMPORTANT]
> Never set `DB_WIPE_ON_STARTUP=true` when using the `prod` profile. The `application-prod.yml` enforces `flyway.clean-disabled: true` as an additional safeguard.

---

## 3. Build and Launch the Stack

```powershell
# From project root
docker compose up --build -d
```

> [!NOTE]
> The multi-stage Dockerfiles compile the Spring Boot backend and React frontend fully inside build containers — no local Maven or npm runs required.

Verify all services are healthy:

```powershell
docker compose ps
```

Expected output:

```
NAME                STATUS          PORTS
pgcore-db           Up (healthy)    0.0.0.0:5432->5432/tcp
pgcore-backend      Up              0.0.0.0:8080->8080/tcp
pgcore-frontend     Up              0.0.0.0:5173->80/tcp
```

---

## 4. Verification Steps

### Step 1: Check the Backend API

```powershell
curl http://localhost:8080/api/config/public
```

Expected: JSON with `pgName`, `primaryColor`, `shortTitle`.

### Step 2: Check the Frontend

Open [http://localhost:5173](http://localhost:5173) in your browser. You should see the PG CRM login page.

### Step 3: Login and Verify

Use the SUPER_ADMIN credentials seeded by `DatabaseSeeder.java`. On first login you'll be prompted to change your password.

---

## 5. Useful Docker Commands

```powershell
# View logs (follow)
docker compose logs -f pgcore-backend
docker compose logs -f pgcore-frontend

# Restart a single service
docker compose restart pgcore-backend

# Stop the stack
docker compose down

# Stop and wipe volumes (CAUTION: deletes all PostgreSQL data)
docker compose down -v

# Open a shell inside the backend container
docker exec -it pgcore-backend /bin/bash

# Connect to the database directly
docker exec -it pgcore-db psql -U postgres -d pgcrmdb
```

---

## 6. Running Tests Inside Docker

The backend test suite uses an in-memory H2 database (configured in `application-test.yml`). To run tests inside a build container:

```powershell
# From project root
docker run --rm -v ${PWD}/core-pg-crm/backend:/app -w /app \
  eclipse-temurin:23-jdk \
  /app/../../apache-maven-3.9.16/bin/mvn test
```

Or simply run tests locally (no DB required since they use H2):

```powershell
cd core-pg-crm/backend
"C:\Program Files\apache-maven-3.9.16\bin\mvn" test
```

---

## 7. Backup & Restore

See [OPERATIONS.md](OPERATIONS.md) for backup scripts and restore procedures.

The key endpoint for uptime monitoring is:

```
GET http://localhost:8080/api/config/public
```

This endpoint is public (no auth required) and returns `200 OK` when the backend is healthy.
