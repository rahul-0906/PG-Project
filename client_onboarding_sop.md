# Standard Operating Procedure (SOP): Single-Tenant Client Onboarding

This document defines the Standard Operating Procedure (SOP) for deploying and onboarding new clients to the single-tenant PG CRM platform. It serves as the official deployment reference for DevOps Engineers and Infrastructure Architects to ensure consistent, secure, and isolated client instances.

---

## Pre-Deployment Gathering: Client Parameters Checklist

Before beginning any deployment phase, the onboarding coordinator must collect the following configurations from the client:

| Parameter Key | Description | Example / Format |
| :--- | :--- | :--- |
| **PG Name** | The full display name of the PG Accommodation. | `Sri Sai PG Residency` |
| **PG Short Name** | Abbreviation used for SMS/WhatsApp headers. | `Sri Sai` |
| **Primary Theme Color** | Hex code for buttons, headers, and UI components. | `#4F46E5` |
| **Target Domains** | Domains assigned for UAT and Production. | UAT: `uat.srisaipg.in`<br>Prod: `portal.srisaipg.in` |
| **Razorpay API Keys** | Key ID and Key Secret credentials. | Test: `rzp_test_...` / `sec_test_...`<br>Live: `rzp_live_...` / `sec_live_...` |

---

## Phase 1: Testing/UAT Environment Deployment

The Testing/UAT phase deploys the application to a staging subdomain to allow client review, training, and configuration validation using dummy data and mock payments.

### 1. UAT Environment Setup (`.env`)
1. Create a dedicated directory on the target staging virtual machine (VM) at `/opt/pgcrm/uat`.
2. Save the following configuration as `/opt/pgcrm/uat/.env`. Modify placeholders with the gathered client parameters:

```bash
# ─── Environment Profile ──────────────────────────────────────────
SPRING_PROFILES_ACTIVE=dev

# ─── Database Credentials ─────────────────────────────────────────
DB_URL=jdbc:postgresql://postgres-db-uat:5432/pgcrmdb_uat
DB_USERNAME=pgcrm_uat_user
DB_PASSWORD=SecureUatPassword2026

# ─── Razorpay Credentials (Mock/Test Keys) ─────────────────────────
RAZORPAY_KEY_ID=rzp_test_ClientMockKeyId
RAZORPAY_KEY_SECRET=ClientMockKeySecret123
RAZORPAY_ENABLED=true

# ─── Tenant Branding ──────────────────────────────────────────────
PG_NAME="Sri Sai PG Residency (UAT)"
PG_SHORT_NAME="Sri Sai Staging"
PG_PRIMARY_COLOR="#4F46E5"

# ─── Initial Owner/Super Admin Credentials ───────────────────────
PG_DEFAULT_OWNER_EMAIL=owner.test@srisaipg.in
PG_DEFAULT_OWNER_NAME="System Owner"
PG_DEFAULT_OWNER_PASSWORD="TemporaryUatPassword123"
```

### 2. Docker Compose Deployment
1. Create `/opt/pgcrm/uat/docker-compose.yml` with the following configuration:

```yaml
version: '3.8'

services:
  postgres-db-uat:
    image: postgres:15-alpine
    container_name: pgcrm-db-uat
    restart: always
    environment:
      POSTGRES_DB: pgcrmdb_uat
      POSTGRES_USER: pgcrm_uat_user
      POSTGRES_PASSWORD: SecureUatPassword2026
    volumes:
      - uat_pg_data:/var/lib/postgresql/data
    networks:
      - pgcrm-uat-network

  pgcrm-backend-uat:
    image: pgcrm-backend:latest
    container_name: pgcrm-app-uat
    restart: always
    ports:
      - "8081:8080"
    env_file:
      - .env
    depends_on:
      - postgres-db-uat
    networks:
      - pgcrm-uat-network

volumes:
  uat_pg_data:

networks:
  pgcrm-uat-network:
    driver: bridge
```

2. Run the deployment container stack:
   ```bash
   docker-compose -f /opt/pgcrm/uat/docker-compose.yml up -d
   ```
3. Verify the container status and inspect the backend logs to confirm the database seeder executed:
   ```bash
   docker logs -f pgcrm-app-uat
   ```
   > [NOTE]
   > You should see a startup console printout indicating that the database was initialized and the default owner account was seeded with the credentials provided in the `.env` file.

### 3. Nginx Reverse Proxy Setup
1. Create a configuration file at `/etc/nginx/sites-available/uat.srisaipg.in`:

```nginx
server {
    listen 80;
    server_name uat.srisaipg.in;

    location / {
        proxy_pass http://127.0.0.1:8081;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

2. Enable the site and restart the Nginx service:
   ```bash
   ln -s /etc/nginx/sites-available/uat.srisaipg.in /etc/nginx/sites-enabled/
   nginx -t
   systemctl restart nginx
   ```

### 4. Let's Encrypt SSL Configuration
1. Run Certbot to generate and apply SSL certificates automatically for the subdomain:
   ```bash
   sudo certbot --nginx -d uat.srisaipg.in
   ```
2. Follow the prompt to automatically redirect HTTP traffic to HTTPS.

### 5. Handover Procedure
1. Verify the site loads at `https://uat.srisaipg.in`.
2. Provide the client with their temporary staging login credentials:
   - **URL:** `https://uat.srisaipg.in`
   - **Username:** `owner.test@srisaipg.in` (matching `PG_DEFAULT_OWNER_EMAIL`)
   - **Password:** `TemporaryUatPassword123` (matching `PG_DEFAULT_OWNER_PASSWORD`)
3. **WARNING: Inform the client that all data in this environment is temporary and will be cleared upon the final production release.**

---

## Phase 2: Production Deployment & Go-Live

The Production Phase deploys the application to the live server on the client's official portal subdomain. It runs with real database validation, live Razorpay integrations, and permanent security keys.

### 1. Production Environment Setup (`.env`)
1. Create a directory on the production server at `/opt/pgcrm/prod`.
2. Save the following configuration as `/opt/pgcrm/prod/.env`. Input the client's official production details:

```bash
# ─── Environment Profile ──────────────────────────────────────────
SPRING_PROFILES_ACTIVE=prod

# ─── Database Credentials (Production Isolated) ────────────────────
DB_URL=jdbc:postgresql://postgres-db-prod:5432/pgcrmdb_prod
DB_USERNAME=pgcrm_prod_admin
DB_PASSWORD=SuperComplexProductionDbPassword987!

# ─── Razorpay Credentials (Live Account API Keys) ─────────────────
RAZORPAY_KEY_ID=rzp_live_ClientRealKeyId
RAZORPAY_KEY_SECRET=ClientRealKeySecret456
RAZORPAY_ENABLED=true

# ─── Tenant Branding ──────────────────────────────────────────────
PG_NAME="Sri Sai PG Residency"
PG_SHORT_NAME="Sri Sai"
PG_PRIMARY_COLOR="#4F46E5"

# ─── Initial Production Owner Credentials ────────────────────────
PG_DEFAULT_OWNER_EMAIL=owner@srisaipg.in
PG_DEFAULT_OWNER_NAME="Sri Sai Accommodation Owner"
PG_DEFAULT_OWNER_PASSWORD="LiveDefaultInitPassword99!"
```

### 2. Docker Compose Deployment
1. Create `/opt/pgcrm/prod/docker-compose.yml` with the following configuration:

```yaml
version: '3.8'

services:
  postgres-db-prod:
    image: postgres:15-alpine
    container_name: pgcrm-db-prod
    restart: always
    environment:
      POSTGRES_DB: pgcrmdb_prod
      POSTGRES_USER: pgcrm_prod_admin
      POSTGRES_PASSWORD: SuperComplexProductionDbPassword987!
    volumes:
      - prod_pg_data:/var/lib/postgresql/data
    networks:
      - pgcrm-prod-network

  pgcrm-backend-prod:
    image: pgcrm-backend:latest
    container_name: pgcrm-app-prod
    restart: always
    ports:
      - "8080:8080"
    env_file:
      - .env
    depends_on:
      - postgres-db-prod
    networks:
      - pgcrm-prod-network

volumes:
  prod_pg_data:

networks:
  pgcrm-prod-network:
    driver: bridge
```

2. Run the deployment container stack:
   ```bash
   docker-compose -f /opt/pgcrm/prod/docker-compose.yml up -d
   ```
3. Confirm Flyway database migrations successfully completed by running:
   ```bash
   docker logs pgcrm-app-prod | grep "Flyway"
   ```
   > [!IMPORTANT]
   > Ensure that the application console log shows `Flyway Community Edition ... Schema is up to date` and that no migrations failed.

### 3. Nginx Reverse Proxy Setup
1. Create a configuration file at `/etc/nginx/sites-available/portal.srisaipg.in`:

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

2. Enable the site and reload Nginx:
   ```bash
   ln -s /etc/nginx/sites-available/portal.srisaipg.in /etc/nginx/sites-enabled/
   nginx -t
   systemctl restart nginx
   ```

### 4. Let's Encrypt SSL Configuration
1. Run Certbot for SSL certificate generation:
   ```bash
   sudo certbot --nginx -d portal.srisaipg.in
   ```
2. Test the auto-renewal process:
   ```bash
   sudo certbot renew --dry-run
   ```

### 5. Production Handover Procedure
1. Navigate to `https://portal.srisaipg.in` in an incognito window.
2. Verify that the login page displays the correct title (`Sri Sai PG Residency`) and that the primary theme color is applied to buttons/elements.
3. Hand over the production super admin account details using a secure vault/password sharing manager:
   - **URL:** `https://portal.srisaipg.in`
   - **Admin Username/Email:** `owner@srisaipg.in`
   - **Admin Password:** `LiveDefaultInitPassword99!`
4. **CAUTION: Instruct the owner to log in immediately and navigate to Settings to change this password.**
