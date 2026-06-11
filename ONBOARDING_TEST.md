# Local Testing & Verification Guide

This document provides step-by-step instructions to locally test the PG CRM application with a clean or mock database before providing it to end users.

---

## 1. Local Test Environment Setup
Ensure you have the required prerequisites installed on your local development machine:
- **Java JDK 23**
- **Node.js v24+**
- **Apache Maven 3.9.16+** (provided binary in `/apache-maven-3.9.16` can be used)

---

## 2. Fresh Database Launch Options

### Option A: Test with In-Memory H2 Database (Recommended for Quick Checks)
Running under the `dev` profile uses an in-memory H2 database. Every time you restart the application, the database starts completely fresh.

1. Open `start_project.bat` or use standard CLI commands.
2. In `backend/src/main/resources/application.yml`, verify the active profile or launch with `dev` active.
3. To populate rich mock data (8 guests, logs, transactions), keep the following in your `.env` or properties:
   ```ini
   APP_SEED-DEMO=true
   ```
4. Run `start_project.bat` in the root folder.
   - *This starts the backend on port `8080` (H2 DB) and the React frontend on `http://localhost:5173`.*

### Option B: Test with a Fresh Local PostgreSQL Instance
If you want to test with a real PostgreSQL database but keep it isolated:

1. Spin up the postgres container in docker-compose:
   ```bash
   docker compose up postgres -d
   ```
2. Drop/recreate your local database schemas to ensure a clean start:
   ```sql
   DROP DATABASE IF EXISTS pgcrmdb;
   CREATE DATABASE pgcrmdb;
   ```
3. Set your local `.env` file values:
   ```ini
   SPRING_PROFILES_ACTIVE=prod
   APP_SEED-DEMO=true
   DB_PASSWORD=pgcrm123
   ```
4. Start backend and frontend services.

---

## 3. Step-by-Step Test Scenarios

### Scenario 1: Authentication & Role Validation
1. Open your browser and navigate to `http://localhost:5173`.
2. **Test Owner Access**:
   - **Log in**: `owner@pgcrm.com` / `Owner@123`
   - **Verify**: You can see multi-building analytics, audit logs, and register managers.
3. **Test Manager Access**:
   - **Log in**: `manager@pgcrm.com` / `Manager@123`
   - **Verify**: You can see room layouts, check-in guests, and record daily logs/meters.
4. **Test Guest Access**:
   - **Log in**: `guest@pgcrm.com` / `Guest@123`
   - **Verify**: You can see billing records, personal meal planner calendars, and maintenance tickets.

### Scenario 2: Guest Check-in & Bed Occupancy
1. Log in as **Manager**.
2. Go to **Room Layout** on the sidebar.
3. Find a room with a vacant bed (e.g. green bed indicator).
4. Click on the bed and select **Check In Guest**.
5. Fill in the details (Mock Name, Email, Phone, Advance Deposit, Rent).
6. Click **Check-In**.
7. **Verify**:
   - The bed status changes to OCCUPIED (red).
   - An audit trail log is generated in the system.
   - Try logging in with the newly checked-in guest email and password `Guest@123`.

### Scenario 3: Billing & Invoicing Flow
1. Log in as **Manager**.
2. Go to **Billing & Invoices**.
3. Generate invoices for the current month.
4. Locate the newly checked-in guest's invoice.
5. Click **View Invoice**.
6. **Verify Payments**:
   - Click **Record Manual Payment** -> Choose **UPI** -> Enter amount -> **Confirm**.
   - Verify the invoice status updates to **PAID**.
   - If `RAZORPAY_ENABLED=false`, test online checkouts. The system will launch a mock payment modal allowing you to simulate success/failure.

### Scenario 4: Meal Log Opt-ins & Meal Planner
1. Log in as **Guest**.
2. Go to **Meal Planner**.
3. Toggle breakfast/lunch/dinner preferences on the calendar and save.
4. Log in as **Manager** or **Owner**.
5. Go to **Daily Logs** or **Reports**.
6. **Verify**: The guest's updated meal preference count is reflected in the chef's daily headcount sheet.

### Scenario 5: Maintenance Ticket Workflow
1. Log in as **Guest**.
2. Go to **Maintenance Tickets** -> Click **Raise New Ticket**.
3. Select priority (e.g., High), category (e.g., Wi-Fi), add description, and submit.
4. Log in as **Manager**.
5. Go to **Maintenance Desk**.
6. Locate the open ticket, change status to **In Progress** -> **Resolved**.
7. Log back in as **Guest** and check that your ticket updates dynamically.

---

## 4. Final Verification Before Handover
Once you are done testing:

1. Drop the local test database schemas so they are completely fresh.
2. In `.env`, switch:
   ```ini
   APP_SEED-DEMO=false
   ```
3. Boot the application one last time.
4. Log in as **Owner** (`owner@pgcrm.com` / `Owner@123`).
5. **Verify**:
   - The dashboard metrics are all `0`.
   - The **Guest List** is empty.
   - The **Room Layout** displays all beds as **Vacant** (green).
6. The application is now fully verified and ready for production handoff!
