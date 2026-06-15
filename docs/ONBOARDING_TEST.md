# Local Testing & Verification Guide (PostgreSQL Production Match)

This document provides step-by-step instructions to locally test the PG CRM application using PostgreSQL. It covers two setup paths:
* **Approach A**: Testing using host-installed developer tools (JDK 23, Maven, Node, and local PostgreSQL).
* **Approach B**: Testing using **Docker Compose only** (requires only Docker installed on the host).

It utilizes the strict four-tier role architecture for all validation scenarios.

---

## 1. Environment Selection & Active Profiles

The system supports three active profiles for local testing:

1. **Development Profile (`dev`)**: 
   - Activated by setting `SPRING_PROFILES_ACTIVE=dev` in your `.env`.
   - **Schema Validation & Parity**: Configures `spring.jpa.hibernate.ddl-auto=validate` to align database schema validations with production.
   - **Flyway Migrations Enabled**: Enables Flyway migrations (`spring.flyway.enabled=true`) to construct and upgrade schemas from versioned SQL scripts.
   - **Database Seeding**: Runs `DatabaseSeeder` to seed the default Super Admin owner account if empty, and conditionally triggers the `DataSeeder` based on demo seeder properties.
2. **Production Profile (`prod`)**:
   - Activated by setting `SPRING_PROFILES_ACTIVE=prod` in your `.env`.
   - **Schema Protection**: Configures `spring.jpa.hibernate.ddl-auto=validate` to ensure no database schema elements are dropped or altered automatically.
   - Runs Flyway migrations automatically.
   - Skips the `DatabaseSeeder` class entirely (`@Profile("!prod")`).
3. **Test Profile (`test`)**:
   - Activated by setting `SPRING_PROFILES_ACTIVE=test` in your `.env` or during test runs.
   - **Pure Empty Database Reset**: Configures `spring.jpa.hibernate.ddl-auto=create` to physically drop all tables and recreate them completely empty.
   - **Flyway Disabled**: Disables Flyway migrations (`spring.flyway.enabled=false`) to bypass all migration SQL scripts.
   - **Legacy Seeders Muted**: Prevents the legacy demo data seeder (`DataSeeder`) from running, guaranteeing that no mock guest records, invoice logs, or transaction histories populate the database.
   - **Master DatabaseSeeder Active**: The master `DatabaseSeeder` remains **active** (as it runs on `!prod`), automatically provisioning the super-admin Owner account using dynamic environment variables if the database is empty, keeping the blank environment fully secure and authenticable.

---

## 2. Approach A: Host-Based Local Testing

### Prerequisites
- **Java JDK 23**
- **Node.js v24+**
- **Apache Maven 3.9.16+** (provided binary in `/apache-maven-3.9.16` can be used)
- **Local PostgreSQL 18** (or run containerized via Docker)

### Database Setup
1. Spin up the postgres container:
   ```bash
   docker compose up postgres -d
   ```
2. Drop and recreate the local database to ensure a clean slate:
   ```sql
   DROP DATABASE IF EXISTS pgcrmdb;
   CREATE DATABASE pgcrmdb;
   ```
3. Configure your local `.env` file in the workspace root:
   ```ini
   SPRING_PROFILES_ACTIVE=dev      # Set to 'dev' to run Flyway migrations and seed initial accounts
   APP_SEED-DEMO=false             # Set to true if you want to seed mock guest records
   DB_PASSWORD=pgcrm123            # Match your local Postgres password
   ```
4. Run the launcher script:
   ```bash
   start_project.bat
   ```
   - **Frontend URL**: `http://localhost:5173`
   - **Backend API URL**: `http://localhost:8080`
   - **Postgres URL**: `localhost:5432`

---

## 3. Approach B: Docker-Only Local Testing (No Tooling Setup)

Use this approach if your testing environment **only has Docker / Docker Compose** installed. All builds, compilations, and runs occur inside containerized layers.

### Prerequisites
- **Docker & Docker Compose** installed and running.

### Setup & Launch
1. Configure your local `.env` file in the workspace root:
   ```ini
   SPRING_PROFILES_ACTIVE=prod
   APP_SEED-DEMO=false             # Set to true to seed mock guests; false for a clean system
   DB_PASSWORD=pgcrm123            # Database password used by compose containers
   ```
2. Compile and launch the entire stack:
   ```bash
   docker compose up --build -d
   ```

### Exposed Ports & Access URLs
When running via Docker Compose, Nginx acts as the frontend web server, routing requests.
- **Web Frontend**: Access via **`http://localhost`** (Port `80` on host).
- **Backend REST API**: Access via **`http://localhost:8080`**.
- **PostgreSQL Database**: Accessible via host port **`5432`** (Username: `pgcrm`, Database: `pgcrmdb`, Password: from `.env`).

### Resetting Database to Clean State
To drop all test data, schema tables, and start completely fresh:
```bash
# Shutdown containers and destroy the persistent database volume
docker compose down -v

# Relaunch with clean schemas
docker compose up -d
```

---

## 4. End-to-End Fresh Onboarding Test Flow

Perform these manual test scenarios to verify that the core system works correctly (using either URL `http://localhost:5173` for Host-Based or `http://localhost` for Docker-Only):

### Scenario 1: Initial Log In & Super Admin Security (Tier 3)
1. Navigate to the application in your browser.
2. Log in using the default seeded Super Admin credentials:
   - **Email**: `owner@pgcrm.com`
   - **Password**: `Admin@123` (if seeded under `dev` profile) or `Owner@123` (if seeded under `prod` profile)
3. Navigate to **Profile Settings** and update the account:
   - Update the email to a test owner email (e.g. `testowner@domain.com`).
   - Change the password to a secure custom password.
4. Log out and verify you can log back in with the updated credentials.

### Scenario 2: Admin - PG Owner Registration (Tier 2)
1. Log in as the updated **Tier 3 Super Admin**.
2. Go to **Manager Management** on the sidebar.
3. Click **Add New Manager** and register a Tier 2 Admin account (e.g. `testadmin@domain.com`).
4. Log out.
5. Log in as the **Tier 2 Admin** you just created (using the default password `Manager@123`).

### Scenario 3: Real Guest Onboarding & Check-in (Tier 1)
1. Log in as the **Tier 2 Admin**.
2. Go to the **Room Layout** view.
3. Choose a room and click on a vacant bed (marked green).
4. Click **Check In Guest** and fill in the details:
   - **Full Name**: Enter a test guest name.
   - **Email**: Enter a test email (e.g. `testguest@domain.com`).
   - **Phone**: Enter a 10-digit number.
   - **Advance Deposit**: Enter the deposit amount (e.g. `5000`).
   - **Base Rent**: Set the custom monthly rent.
5. Click **Confirm Check-in**.
6. **Verify**:
   - The bed indicator changes to OCCUPIED (red).
   - Log out, then log in as the newly created **Tier 1 Guest** (`testguest@domain.com` / `Guest@123`).

### Scenario 4: Invoice Generation & Payment Processing
1. Log in as the **Tier 2 Admin**.
2. Go to **Billing & Invoices**.
3. Select the check-in guest and click **Generate Invoice** for the current month.
4. Click **View Invoice** to inspect the items (Rent, EB splits, etc.).
5. **Verify Payment Simulator**:
   - Log out and log in as the **Tier 1 Guest**.
   - Navigate to **My Invoices** and click **Pay Online** next to the open invoice.
   - Run the simulation transaction to a successful state.
   - Log back in as the **Tier 2 Admin** and verify the status has updated to **PAID**.

### Scenario 5: Guest Services (Meals & Tickets)
1. Log in as the **Tier 1 Guest**.
2. Go to **Meal Planner** and toggle your daily preference (e.g., breakfast/lunch opt-outs) for the upcoming days.
3. Go to **Maintenance Portal** -> click **Raise Ticket** -> describe a mock issue (e.g. "Geyser not working").
4. Log in as the **Tier 2 Admin**.
5. **Verify**:
   - In **Daily Logs**, verify the guest's meal headcount updates.
   - In **Maintenance Desk**, change the status of the guest's ticket to **Resolved**.
   - Verify the guest portal dynamically updates to show the resolved state.

---

## 5. Final Verification Before Handover
Once you are done testing:
1. For Docker-Only: Shut down and purge the database volume (`docker compose down -v`).
2. Make sure `APP_SEED-DEMO=false` is set in `.env`.
3. Restart the containers (`docker compose up -d`).
4. Log in as the default Owner to confirm the dashboard is blank, with all room beds marked vacant and ready for delivery.
