# Standard Onboarding & Deployment Procedure (SOP)
### Unified Operations Reference for Local Testing, UAT, and Production Deployments

This document serves as the single source of truth for onboarding new white-labeled single-tenant instances of the PG CRM platform. It provides the setup instructions, environment specifications, and deployment checklists for developers, testing engineers, and DevOps/System Administrators.

---

## 1. Access Architecture & SaaS System Boundaries

### 1.1 Four-Tier Access Hierarchy
Every deployment runs under a decoupled database schema governed by four access tiers:
1. **Tier 1: Guest (`GUEST`)**: The resident resident. Has portal access to manage their default preferences, schedule daily meals, submit maintenance requests, and pay invoices.
2. **Tier 2: Manager / Branch Admin (`PG_MANAGER`)**: Scoped operator. Operates assigned buildings (check-ins, checkouts, bed layout allocations, daily ledger add-ons, utility splits, and ticket resolutions) scoped to buildings mapped to their user profile.
3. **Tier 3: Owner / Super Admin (`PG_OWNER`)**: Global administrator. Enterprise owner who sets up buildings, registers Manager accounts, and reviews overall revenue reports.
4. **Tier 4: Super Super Admin (Software Provider)**: Systems developer and DevOps engineer. Manages server containers, edits whitelist parameters, and provisions virtual machines.

### 1.2 SaaS Control Plane vs. Tenant Operations
* **Central Control Plane (SaaS Billing)**: A separate, centralized administrative portal that registers B2B clients (PG Owners), handles signup checkout (setup fees), triggers instance auto-provisioning scripts, tracks Annual Maintenance Contracts (AMC), sends automated AMC expiry email alerts (30, 7, and 1 day prior), and suspends client instances on expiration.
* **Tenant Operations (This Application)**: The isolated application container running for a single client (e.g., at `client.pgcrm.com`). Governs all local PG activities, guest-facing invoices, daily meal rosters, and processes guest rent payments via Razorpay.

---

## 2. Pre-Deployment: Client Parameter Gathering

Before initiating any deployment phase, collect the following configuration parameters from the client:

| Parameter Key | Description | Example / Format |
| :--- | :--- | :--- |
| **PG Name** | Full branding name displayed on dashboards and browser tabs. | `Sri Sai Luxury PG` |
| **PG Short Name** | Abbreviated name used for system-wide headers. | `Sri Sai` |
| **Primary Theme Color** | Hex code driving UI styling variables. | `#2563eb` |
| **Target Domain** | Domains assigned for UAT and production environments. | UAT: `uat.srisaipg.in`<br>Prod: `portal.srisaipg.in` |
| **Razorpay API Keys** | Credentials for payment gateway transactions. | Test: `rzp_test_...` / `sec_test_...`<br>Live: `rzp_live_...` / `sec_live_...` |
| **Meta WhatsApp API Keys** | Meta Developer configurations for mobile messages. | `META_WHATSAPP_PHONE_NUMBER_ID`<br>`META_WHATSAPP_ACCESS_TOKEN` |

---

## 3. Local Development & Testing

This section guides local testing to match the production PostgreSQL database setup.

### Approach A: Host-Based Local Setup
#### Prerequisites
- **Java JDK 23**
- **Node.js v24+**
- **Apache Maven 3.9.16+** (provided binary in `/apache-maven-3.9.16` can be used)
- **Local PostgreSQL 18** (installed or containerized)

#### Database Setup
1. Spin up the local PostgreSQL instance.
2. Initialize the blank databases for both the Core Application and the Control Plane:
   ```sql
   -- For Core Hostel Management (PG-CORE)
   DROP DATABASE IF EXISTS pgcrmdb;
   CREATE DATABASE pgcrmdb;

   -- For Centralized SaaS Billing Portal (CONTROL-PLANE)
   DROP DATABASE IF EXISTS controlplane_db;
   CREATE DATABASE controlplane_db;
   ```
3. Create a `.env` configuration file in the project root:
   ```ini
   SPRING_PROFILES_ACTIVE=dev      # Set to 'dev' to run Flyway migrations and seed initial accounts
   APP_SEED-DEMO=false             # Set to true if you want to seed mock guest records
   DB_PASSWORD=pgcrm123            # Match your local Postgres password
   ```
4. Execute the launcher script:
   ```bash
   start_project.bat
   ```
   - **Frontend URL**: `http://localhost:5173`
   - **Backend API URL**: `http://localhost:8080`

### Approach B: Docker-Only Local Setup (Zero Tooling)
Use this option if only Docker and Docker Compose are installed on the host machine.
1. Create a `.env` file in the project root:
   ```ini
   SPRING_PROFILES_ACTIVE=prod
   APP_SEED-DEMO=false             # Set to true to seed mock guests; false for a clean system
   DB_PASSWORD=pgcrm123            # Database password used by compose containers
   ```
2. Build and run the entire stack:
   ```bash
   docker compose up --build -d
   ```
   - **Frontend App**: `http://localhost` (Port 80)
   - **Backend API**: `http://localhost:8080`
   - **Postgres Database**: `localhost:5432`

---

## 4. Phase 1: Testing / UAT Environment Deployment

Deploying the UAT environment on a staging subdomain (e.g. `uat.srisaipg.in`) allows client testing, manager training, and verification using mock API keys.

### Step 4.1: Workspace Provisioning
1. Connect to the target staging Linux VPS/VM via SSH.
2. Initialize the project workspace directory structure under `/opt/pgcrm/client-name/uat`:
   ```bash
   sudo mkdir -p /opt/pgcrm/client-name/uat/deploy
   sudo chown -R $USER:$USER /opt/pgcrm/client-name/uat
   ```
3. Copy the deployment configurations from the repository `deploy/` directory to `/opt/pgcrm/client-name/uat/deploy/` and the root `Dockerfile` to `/opt/pgcrm/client-name/uat/`.

### Step 4.2: Configure UAT Environment (`.env`)
Create `/opt/pgcrm/client-name/uat/deploy/.env` and update the variables for the UAT scope:
```ini
SPRING_PROFILES_ACTIVE=dev      # Runs Flyway migrations and seeder runs on startup
DB_URL=jdbc:postgresql://postgres:5432/pgcrmdb
DB_USERNAME=postgres
DB_PASSWORD=SecureUatPassword2026!
JWT_SECRET=UatJwtSecretKeyPlaceholderAtLeast256BitsLong

PG_NAME="Sri Sai Luxury PG (UAT)"
PG_SHORT_NAME="Sri Sai UAT"
PG_PRIMARY_COLOR="#2563eb"

# Dynamic UAT Initial Super Admin (Tier 3) Credentials
PG_DEFAULT_OWNER_EMAIL=owner.uat@srisaipg.in
PG_DEFAULT_OWNER_NAME="UAT System Owner"
PG_DEFAULT_OWNER_PASSWORD="UatPassword@123!"

RAZORPAY_ENABLED=true
RAZORPAY_KEY_ID=rzp_test_UatKeyPlaceholder
RAZORPAY_KEY_SECRET=UatSecretPlaceholder

MAIL_ENABLED=false
```

### Step 4.3: Run UAT Container Stack
1. Start the containers:
   ```bash
   docker compose -f /opt/pgcrm/client-name/uat/deploy/docker-compose.prod.yml --env-file /opt/pgcrm/client-name/uat/deploy/.env up -d --build
   ```
2. Verify seeder runs and migration console prints:
   ```bash
   docker logs -f pgcrm-backend-prod
   ```

### Step 4.4: Nginx Reverse Proxy & SSL Setup
1. Route staging domain to Nginx reverse proxy configuration at `/etc/nginx/sites-available/uat.srisaipg.in`:
   ```nginx
   server {
       listen 80;
       server_name uat.srisaipg.in;

       location / {
           proxy_pass http://127.0.0.1:8081; # Port mapped in docker compose
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
           proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
           proxy_set_header X-Forwarded-Proto $scheme;
       }
   }
   ```
2. Enable routing and restart Nginx:
   ```bash
   sudo ln -s /etc/nginx/sites-available/uat.srisaipg.in /etc/nginx/sites-enabled/
   sudo nginx -t && sudo systemctl reload nginx
   ```
3. Secure the domain with Let's Encrypt SSL:
   ```bash
   sudo certbot --nginx -d uat.srisaipg.in
   ```

---

## 5. Phase 2: Production Deployment & Go-Live

The production deployment runs on the client's official portal subdomain (e.g. `portal.srisaipg.in`) with real database validation, live API keys, and permanent security configurations.

### Step 5.1: Workspace Provisioning
Initialize the workspace directory structure for production under `/opt/pgcrm/client-name/prod`:
```bash
sudo mkdir -p /opt/pgcrm/client-name/prod/deploy
sudo chown -R $USER:$USER /opt/pgcrm/client-name/prod
```
Copy all deployment artifacts inside `/opt/pgcrm/client-name/prod/deploy` and `Dockerfile` to the root folder.

### Step 5.2: Configure Production Environment (`.env`)
Initialize `/opt/pgcrm/client-name/prod/deploy/.env` with secure production values:
```ini
SPRING_PROFILES_ACTIVE=prod     # Enforces database validation (ddl-auto: validate)
DB_URL=jdbc:postgresql://postgres:5432/pgcrmdb
DB_USERNAME=postgres
DB_PASSWORD=SuperComplexProductionDbPassword987!
JWT_SECRET=CryptographicallySecureRandom256BitHexCodeForProductionSigning

PG_NAME="Sri Sai Luxury PG"
PG_SHORT_NAME="Sri Sai"
PG_PRIMARY_COLOR="#2563eb"

# Production Super Admin (Tier 3)
PG_DEFAULT_OWNER_EMAIL=owner@srisaipg.in
PG_DEFAULT_OWNER_NAME="Sri Sai Owner"
PG_DEFAULT_OWNER_PASSWORD="SecureProductionInitialPassword99!"

# Live API Integrations
RAZORPAY_ENABLED=true
RAZORPAY_KEY_ID=rzp_live_LiveKeyIdFromRazorpayDashboard
RAZORPAY_KEY_SECRET=LiveSecretFromRazorpayDashboard

MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=notifications@srisaipg.in
MAIL_PASSWORD=ProductionGmailAppPassword
MAIL_FROM=noreply@srisaipg.in
MAIL_ENABLED=true

META_WHATSAPP_PHONE_NUMBER_ID=MetaPhoneID
META_WHATSAPP_ACCESS_TOKEN=MetaSystemToken
META_WEBHOOK_VERIFY_TOKEN=CustomWebhookToken
```

### Step 5.3: Build and Launch Production Stack
Start the production containers:
```bash
docker compose -f /opt/pgcrm/client-name/prod/deploy/docker-compose.prod.yml --env-file /opt/pgcrm/client-name/prod/deploy/.env up -d --build
```
Verify that database migrations successfully completed by running:
```bash
docker logs pgcrm-backend-prod | grep "Flyway"
```

### Step 5.4: Nginx Production Routing & SSL Configuration
1. Setup the Nginx configuration file at `/etc/nginx/sites-available/portal.srisaipg.in`:
   ```nginx
   server {
       listen 80;
       server_name portal.srisaipg.in;

       location / {
           proxy_pass http://127.0.0.1:8080;
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
           proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
           proxy_set_header X-Forwarded-Proto $scheme;
       }
   }
   ```
2. Enable site configuration and reload Nginx:
   ```bash
   sudo ln -s /etc/nginx/sites-available/portal.srisaipg.in /etc/nginx/sites-enabled/
   sudo nginx -t && sudo systemctl reload nginx
   ```
3. Generate the live SSL Certificate:
   ```bash
   sudo certbot --nginx -d portal.srisaipg.in
   ```
4. Verify certbot auto-renewal:
   ```bash
   sudo certbot renew --dry-run
   ```

---

## 6. End-to-End Verification Scenarios

Perform these manual test scenarios to verify that the core system works correctly:

### Scenario 1: Initial Log In & Super Admin Setup (Tier 3)
1. Navigate to the application URL.
2. Log in using the default Super Admin credentials configured in `.env`.
3. Navigate to **Profile Settings** and update the account credentials (email and password).
4. Verify login succeeds with the updated password.

### Scenario 2: Manager Registration (Tier 2)
1. Log in as the updated **Tier 3 Super Admin**.
2. Go to **Manager Management** on the sidebar.
3. Click **Add New Manager** and register a Tier 2 Manager account (using default password `Manager@123`).
4. Log out.
5. Log in as the newly created **Tier 2 Manager**.

### Scenario 3: Real Guest Onboarding & Check-in (Tier 1)
1. Log in as the **Tier 2 Manager**.
2. Go to the **Room Layout** view.
3. Click on a vacant bed (green) and click **Check In Guest**.
4. Fill in the Guest details (email, advance deposit, rent) and click **Confirm Check-in**.
5. Verify the bed indicator changes to OCCUPIED (red).
6. Verify you can log in as the newly created **Tier 1 Guest** (password: `Guest@123`).

### Scenario 4: Invoice Billing & Payment Verification
1. Log in as the **Tier 2 Manager**.
2. Go to **Billing & Invoices**.
3. Select the checked-in guest and click **Generate Invoice** for the current month.
4. Log in as the **Tier 1 Guest**.
5. Go to **My Invoices** -> click **Pay Online** -> complete payment simulation.
6. Log back in as the **Tier 2 Manager** and verify invoice status updates to **PAID**.

---

## 7. Handover Security Checklist

Before delivering credentials to the client, the Software Provider must verify the following security checkpoints:
- [ ] **HTTPS Enforced**: Accessing http redirects automatically to https.
- [ ] **Log Stripping Active**: Developer tools show no logs/warnings. Confirmed by `esbuild` configuration stripping out `console.log` statements in frontend bundle.
- [ ] **Database Profile Validation**: Verify backend startup logs read `ddl-auto=validate` (not `create`).
- [ ] **First Login Forced Reset**: Verify the UI directs the owner and managers to `/change-password` immediately upon first login.
- [ ] **Staging Database Purge**: Purge staging databases before final production handover to clean all dummy data.
