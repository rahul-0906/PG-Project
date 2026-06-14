# Production Onboarding & Deployment Guide

This document outlines the step-by-step procedure to deploy the PG CRM system in a production-ready, clean state for a PG Owner (Tenant).

---

## 1. Branding & White-Label Customization
The application uses `tenant-config.yml` in the project root to load whitelabel branding on boot.

1. Open `tenant-config.yml` in the project root.
2. Edit the branding fields to match the client's PG hostel:
   ```yaml
   pg:
     system:
       branding:
         name: "Sri Sai Luxury PG"        # Full name displayed on login & headers
         short-title: "Sri Sai"           # Short title/abbreviation prefix
   ```
3. Save the file. (The docker container mounts this file dynamically, so changes apply without rebuilding backend JARs).

---

## 2. Database Preparation & Security Safeguards

To ensure production database integrity, the system implements profile-specific database configurations and seeder guards:

1. **Schema Protection**:
   * The `prod` profile (activated via `SPRING_PROFILES_ACTIVE=prod`) explicitly configures `spring.jpa.hibernate.ddl-auto=validate`. This ensures that Hibernate only validates the database schema against JPA entities and never executes destructive schema updates or drop commands.
   * Flyway migrations are enabled to safely run incremental database changes.
2. **Seeder Component Guard**:
   * The development `DatabaseSeeder` component is annotated with `@Profile("!prod")` and will **never** execute when the `prod` profile is active, protecting production tables from arbitrary user updates.
3. **Demo Data Toggle**:
   * In your production `.env` file, set the seeder toggle to `false` to prevent mock transaction data (invoices, logs, check-ins) from being seeded:
     ```ini
     APP_SEED-DEMO=false
     ```
   * *Note: The system `DataSeeder` will still automatically create the root Owner admin account and build the physical building, floor, room, and bed layout skeleton, but all transaction history and guest profiles will remain completely clean.*

---

## 3. Configure Production Credentials & Integrations
Create an `.env` file in the root directory on the production server (VPS/Cloud VM) with secure production keys:

```ini
# Core Environment
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080
JWT_SECRET=YOUR_SUPER_SECRET_RANDOM_KEY_AT_LEAST_256_BITS_LONG

# Database Configuration
DB_PASSWORD=YourSecurePostgreSqlPasswordHere

# SMTP Mail Server (Used for Verification OTPs & Invoices)
MAIL_ENABLED=true
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=owner-notifications@yourdomain.com
MAIL_PASSWORD=your_gmail_app_password

# Razorpay Integration (For Live Guest Payments)
RAZORPAY_ENABLED=true
RAZORPAY_KEY_ID=rzp_live_xxxxxxxxxxxxxx
RAZORPAY_KEY_SECRET=xxxxxxxxxxxxxxxxxxxxxxxx

# Meta WhatsApp Cloud API Integration (For Automated Reminders & Webhooks)
META_WHATSAPP_PHONE_NUMBER_ID=your_phone_number_id
META_WHATSAPP_ACCESS_TOKEN=your_meta_access_token
META_WEBHOOK_VERIFY_TOKEN=your_custom_webhook_verify_token
```

---

## 4. Server & Reverse Proxy Setup (HTTPS/SSL)
Deploy an Nginx reverse proxy on your server to handle domain routing (e.g. `srisaipg.in`) and SSL certification.

1. **Point DNS**: Point the client's domain A-record to your server IP.
2. **Install Nginx & Certbot**:
   ```bash
   sudo apt update
   sudo apt install nginx certbot python3-certbot-nginx -y
   ```
3. **Configure Nginx Site**: Create `/etc/nginx/sites-available/pgcrm`:
   ```nginx
   server {
       server_name srisaipg.in www.srisaipg.in;

       location / {
           proxy_pass http://localhost:80; # Points to React Nginx container
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
       }

       location /api {
           proxy_pass http://localhost:8080; # Points to Spring Boot backend
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
       }
   }
   ```
4. **Enable SSL**: Enable the configuration and generate an SSL certificate via Let's Encrypt:
   ```bash
   sudo ln -s /etc/nginx/sites-available/pgcrm /etc/nginx/sites-enabled/
   sudo nginx -t && sudo systemctl restart nginx
   sudo certbot --nginx -d srisaipg.in -d www.srisaipg.in
   ```

---

## 5. Launch the Production Application
Launch the stack using Docker Compose:

```bash
docker compose up -d --build
```
*This starts the PostgreSQL database container (running flyway migrations), starts the backend API service, compiles clean production frontend assets, and serves them.*

---

## 6. Vite Production Build Security (Log & Debugger Stripping)

To ensure the highest standard of security for production client browsers and prevent data leakage (such as authentication JWT tokens, session metadata, or customer PII) into browser developer tools:

1. **Native Esbuild Log Stripping**: The production build pipeline compiles frontend assets using Vite's native `esbuild` minifier, configured to completely drop logging and debugging statements.
2. **Configuration Block**:
   ```javascript
   esbuild: {
     drop: ['console', 'debugger'],
   }
   ```
3. **Automatic Compilation Action**: During `npm run build`, all occurrences of `console.log`, `console.warn`, `console.error`, and `debugger` statements are stripped from the resulting production bundles.

---

## 7. Dynamic Owner Onboarding Credentials Injection

When onboarding a new tenant, initial administrative credentials do not need to be hardcoded or seeded via SQL scripts. Instead:
1. **Dynamic Environment Injections**: The system reads the variables `PG_DEFAULT_OWNER_EMAIL`, `PG_DEFAULT_OWNER_NAME`, and `PG_DEFAULT_OWNER_PASSWORD` from the `.env` configuration file on startup.
2. **Seeder Verification**: In the `dev` or `test` profiles, the master `DatabaseSeeder` component reads these values via Spring's `@Value` annotation:
   - `@Value("${pg.default-owner.email:owner@pgcrm.com}")`
   - `@Value("${pg.default-owner.name:System Owner}")`
   - `@Value("${pg.default-owner.password:Owner@123}")`
3. **Fallback Defaults**: Safe fallbacks are provided directly inside the annotations to ensure the system boots cleanly even if environment variables are missing.

---

## 8. Client Handoff Procedure
Perform the following steps immediately after launching the application:

1. **Initial Login**: Log in with the dynamically injected owner credentials (configured in `.env`). If none were specified, use the fallback defaults:
   - **Email**: `owner@pgcrm.com`
   - **Password**: `Owner@123` (or `Admin@123` depending on profile)
2. **Update Admin Settings**:
   - Go to Profile Settings and change the email to the client's email (e.g., `owner@srisaipg.in`).
   - Change the password to a secure custom password.
3. **Register Managers**: Go to **Manager Management** and create logins for their property managers.
4. **Layout Check**: Verify the room and bed structure looks correct on the dashboard.
5. **Begin Guest Onboarding**: Hand over the credentials to the PG owner and manager to begin registering and checking in guests.
