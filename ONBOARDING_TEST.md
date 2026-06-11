# Local Testing & Verification Guide (PostgreSQL Production Match)

This document provides step-by-step instructions to locally test the PG CRM application using PostgreSQL and a clean database setup that mirrors production exactly from start to finish.

---

## 1. Local Test Environment Setup
Ensure you have the required prerequisites installed on your local development machine:
- **Java JDK 23**
- **Node.js v24+**
- **Apache Maven 3.9.16+** (provided binary in `/apache-maven-3.9.16` can be used)
- **Local PostgreSQL 18** (or run containerized via Docker)

---

## 2. PostgreSQL Test Database Setup
To test the application in a production-identical state, you will use a local PostgreSQL database with a clean, unseeded transaction database.

1. **Start PostgreSQL**: If you are using Docker, spin up the database container:
   ```bash
   docker compose up postgres -d
   ```
2. **Clear Existing Schemas**: Drop and recreate the local test database to ensure a clean slate:
   ```sql
   DROP DATABASE IF EXISTS pgcrmdb;
   CREATE DATABASE pgcrmdb;
   ```
3. **Configure Local Environment Settings**: Create or edit the `.env` file in your workspace root:
   ```ini
   SPRING_PROFILES_ACTIVE=prod
   APP_SEED-DEMO=false             # Ensures database remains clean (no mock users/invoices/logs)
   DB_PASSWORD=pgcrm123            # Match your local Postgres password
   ```

---

## 3. Launch the Application
Start the backend and frontend dev servers locally using the start script:

```bash
start_project.bat
```
*The database migrations will run automatically via Flyway, creating the clean database schemas, the default PG Owner administrator, and the physical room layout skeleton.*

---

## 4. End-to-End Fresh Onboarding Test Flow
Walk through these scenarios manually to test the application in a clean environment matching the customer's production state.

### Scenario 1: Initial Log In & Admin Security
1. Open your browser and navigate to `http://localhost:5173`.
2. Log in using the default root admin credentials:
   - **Email**: `owner@pgcrm.com`
   - **Password**: `Owner@123`
3. Navigate to **Profile Settings** and update the owner account:
   - Update the email to a test owner email (e.g. `testowner@domain.com`).
   - Change the password to a secure custom password.
4. Log out and verify you can log back in with the updated credentials.

### Scenario 2: Manager Registration
1. Log in as the updated **PG Owner**.
2. Go to **Manager Management** on the sidebar.
3. Click **Add New Manager** and register a manager account (e.g. `testmanager@domain.com`).
4. Log out.
5. Log in as the **Manager** you just created (using the default password `Manager@123`).
6. Enforce password change if prompted, or log in successfully to verify manager access levels.

### Scenario 3: Real Guest Onboarding & Check-in
1. Log in as the **Manager**.
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
   - An audit log is recorded under Owner logs.
   - Log out, then log in as the newly created **Guest** (`testguest@domain.com` / `Guest@123`).

### Scenario 4: Invoice Generation & Payment Processing
1. Log in as the **Manager**.
2. Go to **Billing & Invoices**.
3. Select the check-in guest and click **Generate Invoice** for the current month.
4. Click **View Invoice** to inspect the items (Rent, EB splits, etc.).
5. **Verify Payment Simulator**:
   - Log out and log in as the **Guest**.
   - Navigate to **My Invoices** and click **Pay Online** next to the open invoice.
   - Run the simulation transaction to successful state.
   - Log back in as the **Manager** and verify the status has updated to **PAID**.

### Scenario 5: Guest Services (Meals & Tickets)
1. Log in as the **Guest**.
2. Go to **Meal Planner** and toggle your daily preference (e.g., breakfast/lunch opt-outs) for the upcoming days.
3. Go to **Maintenance Portal** -> click **Raise Ticket** -> describe a mock issue (e.g. "Geyser not working").
4. Log in as the **Manager**.
5. **Verify**:
   - In **Daily Logs**, verify the guest's meal headcount updates.
   - In **Maintenance Desk**, change the status of the guest's ticket to **Resolved**.
   - Verify the guest portal dynamically updates to show the resolved state.

---

## 5. Verify the Cleanup State
Before shipping the build, drop and recreate the PostgreSQL database one final time (`DROP DATABASE` -> `CREATE DATABASE`) and start the server with `APP_SEED-DEMO=false` to ensure the customer receives a completely fresh instance.
