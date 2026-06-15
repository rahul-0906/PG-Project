# Standard Operating Procedure (SOP): Single-Tenant Client Onboarding
### Official Deployment Reference for Tier 4 Super Super Admins (Software Providers)

This document defines the step-by-step Standard Operating Procedure (SOP) for deploying and onboarding new clients to isolated, single-tenant instances of the PG CRM platform. It is designed to be executed by **Tier 4 Super Super Admins (Software Providers)** using the standardized configuration templates in the `deploy/` directory to provision the PostgreSQL database, compile the frontend and backend, and secure the proxy routing.

---

## 1. The Four-Tier Access Architecture

Every deployment runs on a decoupled database structure governed by four distinct security layers:

1. **Tier 1: Guest (`GUEST`)**: The resident resident. Has access to settings, invoices, meals, and tickets.
2. **Tier 2: Admin - PG Owner (`PG_MANAGER`)**: The property administrator/operator. Manages guest check-ins, sets room pricing overrides, records EB utility readings, and resolves tickets.
3. **Tier 3: Super Admin - Owner's Super Admin (`PG_OWNER`)**: The global property administrator. Access to setup buildings, register Admin (PG Owner) accounts, and monitor business analytics.
4. **Tier 4: Super Super Admin - Software Provider**: System operator. Handles container provisioning, whitelist/color token adjustments, and server reverse proxies.

---

## 2. Pre-Deployment: Client Parameter Gathering

Before initiating the onboarding pipeline, the Tier 4 Super Super Admin must collect the following whitelabel configuration parameters from the client:

| Parameter Key | Description | Example / Format |
| :--- | :--- | :--- |
| **PG Name** | Full branding name displayed on dashboards and browser tabs. | `Sri Sai Luxury PG` |
| **PG Short Name** | Abbreviated name used for system-wide headers. | `Sri Sai` |
| **Primary Theme Color** | Hex code driving UI styling variables. | `#2563eb` |
| **Target Domain** | Domains assigned for UAT and production environments. | UAT: `uat.srisaipg.in`<br>Prod: `portal.srisaipg.in` |
| **Razorpay API Keys** | Credentials for payment gateway transactions. | Test: `rzp_test_...` / `sec_test_...`<br>Live: `rzp_live_...` / `sec_live_...` |
| **Meta WhatsApp API Keys** | Meta Developer configurations for mobile messages. | `META_WHATSAPP_PHONE_NUMBER_ID`<br>`META_WHATSAPP_ACCESS_TOKEN` |

---

## 3. Infrastructure Setup & Directory Provisioning

1. Connect to the target Linux VPS / virtual machine (VM) via SSH.
2. Initialize the project workspace directory structure under `/opt/pgcrm`:
   ```bash
   sudo mkdir -p /opt/pgcrm/deploy
   sudo chown -R $USER:$USER /opt/pgcrm
   ```
3. Copy the production deployment artifacts from the repository's `deploy/` root directory to the host VM directory `/opt/pgcrm/deploy`:
   - `deploy/docker-compose.prod.yml`
   - `deploy/.env.example`
   - `deploy/nginx-site.conf`
4. Copy the application `Dockerfile` and `tenant-config.yml` from the project root into `/opt/pgcrm/` so they are accessible by the Docker build context.

---

## 4. Phase 1: Testing / UAT Environment Deployment

Deploying the UAT environment on a staging subdomain (e.g. `uat.srisaipg.in`) allows client testing, manager training, and verification using mock API keys.

### Step 4.1: Configure UAT Environment (`.env`)
1. Create a copy of the template file in your UAT directory:
   ```bash
   cp /opt/pgcrm/deploy/.env.example /opt/pgcrm/deploy/.env
   ```
2. Open `/opt/pgcrm/deploy/.env` and update the variables for the UAT scope:
   ```ini
   SPRING_PROFILES_ACTIVE=dev      # Runs Flyway migrations and seeder runs
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
   
   # Optional SMTP and WhatsApp credentials for UAT notification testing
   MAIL_ENABLED=false
   ```

### Step 4.2: Run UAT Container Stack
1. Start the stack from `/opt/pgcrm`:
   ```bash
   docker compose -f /opt/pgcrm/deploy/docker-compose.prod.yml --env-file /opt/pgcrm/deploy/.env up -d --build
   ```
2. Verify startup execution logs:
   ```bash
   docker logs -f pgcrm-backend-prod
   ```
   *Verify that the log outputs show the DatabaseSeeder executing and printing the dynamic credentials to the console.*

### Step 4.3: Configure Nginx Routing
1. Copy `deploy/nginx-site.conf` to Nginx directories:
   ```bash
   sudo cp /opt/pgcrm/deploy/nginx-site.conf /etc/nginx/sites-available/uat.srisaipg.in
   ```
2. Open the file and update `server_name` to target `uat.srisaipg.in`.
3. Enable the config:
   ```bash
   sudo ln -s /etc/nginx/sites-available/uat.srisaipg.in /etc/nginx/sites-enabled/
   sudo nginx -t && sudo systemctl restart nginx
   ```
4. Generate SSL Certificates using Certbot:
   ```bash
   sudo certbot --nginx -d uat.srisaipg.in
   ```

---

## 5. Phase 2: Production Deployment & Go-Live

The production deployment runs on the client's official portal subdomain (e.g. `portal.srisaipg.in`) with real database validation, live Razorpay integrations, and permanent security keys.

### Step 5.1: Configure Production Environment (`.env`)
1. Create a production directory `/opt/pgcrm/prod/deploy` and copy the artifacts inside.
2. Initialize `/opt/pgcrm/prod/deploy/.env` with secure production values:
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
   
   # Live Integrations
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

### Step 5.2: Build and Launch Production Stack
1. Start the production containers:
   ```bash
   docker compose -f /opt/pgcrm/prod/deploy/docker-compose.prod.yml --env-file /opt/pgcrm/prod/deploy/.env up -d --build
   ```
2. Confirm Flyway database migrations successfully completed:
   ```bash
   docker logs pgcrm-backend-prod | grep "Flyway"
   ```

### Step 5.3: Configure Production Domain and SSL
1. Set up the Nginx configuration file at `/etc/nginx/sites-available/portal.srisaipg.in`:
   - Copy `deploy/nginx-site.conf` and update `server_name` to target `portal.srisaipg.in`.
2. Enable and reload Nginx:
   ```bash
   sudo ln -s /etc/nginx/sites-available/portal.srisaipg.in /etc/nginx/sites-enabled/
   sudo nginx -t && sudo systemctl restart nginx
   ```
3. Generate the live SSL Certificate:
   ```bash
   sudo certbot --nginx -d portal.srisaipg.in
   ```
4. Test certificate auto-renewal:
   ```bash
   sudo certbot renew --dry-run
   ```

---

## 6. System Handoff & Security Checklist

Before delivering credentials to the client, the Tier 4 Super Super Admin must verify the following security checkpoints:

- [ ] **HTTPS Enforced**: Accessing `http://portal.srisaipg.in` redirects automatically to `https://portal.srisaipg.in`.
- [ ] **Log Stripping Active**: Inspect the Chrome Developer Console on the login page. Verify that no logs, warnings, or debug messages appear.
- [ ] **Database Profile Validation**: Verify backend startup logs read `ddl-auto=validate` (not `create`).
- [ ] **Secure Temporary Password**: Log in as the Super Admin (Tier 3) (`owner@srisaipg.in` / `SecureProductionInitialPassword99!`).
- [ ] **Verify Forced Change-Password Check**: Verify the UI directs the owner to `/change-password` immediately upon authentication.
- [ ] **Register Tier 2 Admin Accounts**: Verify the owner creates separate profiles for their property admins under the **Manager Management** desk.
