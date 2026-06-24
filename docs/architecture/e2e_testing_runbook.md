# End-to-End (E2E) Testing Runbook

This runbook guides Quality Assurance (QA) and DevOps engineers through executing end-to-end integration and verification checks across the entire **PG CRM SaaS** ecosystem. The testing simulates a full customer journey from the marketing landing page registration up to a guest onboarding in an isolated tenant portal.

---

## Phase 1: Environment & Services Verification

Before starting, verify the backend servers, web gateway routing, database access points, and frontend apps are active.

- [ ] **Verify PostgreSQL Services**
  - Start the local PostgreSQL instance.
  - Connect via CLI (`psql`) or UI client (PgAdmin/DBeaver) and verify the following databases exist:
    * `controlplane_db` (For central billing, client accounts, and subscriptions)
    * `pgcrmdb` / `pgcrm_core` (Base database mapping dynamic tenant schemas)
  - *Expected Result:* Databases are active, online, and responding to connections.

- [ ] **Verify Subdomain DNS Hosts Configuration**
  - Open your operating system's `hosts` file (on Windows: `C:\Windows\System32\drivers\etc\hosts`; on Unix: `/etc/hosts`).
  - Verify the following local loopback entries exist:
    ```hosts
    127.0.0.1       test.pgcrm.com
    127.0.0.1       demo.pgcrm.com
    ```
  - *Expected Result:* Pinging `test.pgcrm.com` resolves successfully to `127.0.0.1`.

- [ ] **Start and Verify Spring Boot Backend App (Control Plane)**
  - Navigate to `master-control-plane/backend/` and run the Spring Boot app.
  - Verify that the server starts up on port `8090` without exceptions.
  - *Expected Result:* The terminal outputs `Started ControlPlaneApplication in X seconds` and listens on `http://localhost:8090`.

- [ ] **Start and Verify Spring Boot Backend App (PG Core)**
  - Navigate to `core-pg-crm/backend/` and start the PG Core server.
  - *Expected Result:* Server runs on port `8080`.

- [ ] **Start and Verify Control Plane Frontend App**
  - Navigate to `master-control-plane/frontend/` and run:
    ```bash
    npm run dev
    ```
  - *Expected Result:* Development server runs on `http://localhost:5176` (or configured port).

- [ ] **Start and Verify PG Core Frontend App**
  - Navigate to `core-pg-crm/frontend/` and run `npm run dev`.
  - *Expected Result:* App runs on `http://localhost:5173`.

---

## Phase 2: Master Control Plane & Provisioning Flow

This phase verifies the client acquisition funnel: sign up, payment capture, background automation, and welcome notifications.

- [ ] **Navigate Landing Page & Onboarding Signup**
  - Open a web browser and access `http://localhost:5176/`.
  - Verify the landing page loads the dark-mode SaaS theme and branding elements.
  - Click on the **Get Started** / **Sign Up** action button.
  - *Expected Result:* The browser routes to `/signup` displaying the registration form.

- [ ] **Submit Multi-Step Onboarding Form**
  - Enter the administrator registration details (e.g., Owner Name, Email, Password).
  - Move to the next steps and enter PG branding properties:
    * **PG Name:** `Test PG`
    * **Short Title:** `TPG`
    * **Custom Subdomain:** `test` (resulting in `test.pgcrm.com`)
    * **Contact Email:** `testowner@mailtrap.io` (or a sandbox email address)
    * **Plan Term:** Select `MONTHLY` or `YEARLY`.
  - Submit the onboarding form.
  - *Expected Result:* Form validates with no input errors. The client profile registers successfully in the DB as `PENDING_PAYMENT`, and the user is redirected to the checkout screen.

- [ ] **Simulate Webhook Payment Confirmation**
  - Make a mock HTTP POST request to the webhook endpoint mimicking a successful Razorpay order payment:
    * **URL:** `http://localhost:8090/api/webhooks/razorpay`
    * **Headers:** 
      * `Content-Type: application/json`
      * `x-razorpay-signature: sandbox_mock_signature`
    * **Body:**
      ```json
      {
        "event": "payment.captured",
        "payload": {
          "payment": {
            "entity": {
              "id": "pay_MOCK123456",
              "amount": 1500000,
              "currency": "INR",
              "status": "captured",
              "notes": {
                "tenant_id": "<INSERT_TENANT_UUID_FROM_STEP_2>"
              }
            }
          }
        }
      }
      ```
  - *Expected Result:* The endpoint responds with `200 OK`. The backend console logs print signature verification bypass, transition the tenant status to `PROVISIONING`, and launch the provisioning worker thread.

- [ ] **Monitor Live Provisioning Tracker**
  - Access `http://localhost:5176/provisioning` in the client's browser (logged in with the client's auth credentials).
  - Verify that the progress bar advances from `0%` toward `90%`, periodically updating progress step logs (e.g., "Allocating dedicated PostgreSQL database schema...", "Running database migrations...").
  - *Expected Result:* Once the provisioning script executes successfully in the backend (exit code `0`), the polling endpoint `/api/tenant/me` returns status `LIVE`. The tracker jumps to `100%`, shows "Workspace Ready", and reveals a pulsing **Enter Workspace ->** link.

- [ ] **Verify Workspace Welcome Email Delivery**
  - Open your local SMTP console or Mailtrap dashboard.
  - Locate the email with the subject: `"Your PG CRM Workspace is Live! 🚀"`.
  - Inspect the HTML welcome email.
  - *Expected Result:* The email contains a professional layout with `Test PG` details, a link pointing to `https://test.pgcrm.com`, and a reminder to log in using the credentials defined at registration.

---

## Phase 3: Core App Initialization & Property Setup

Log in to the newly provisioned tenant instance and set up the structural layouts of the PG property.

- [ ] **Login to Tenant Portal**
  - Click on the workspace link or browse to `http://test.pgcrm.com:5173/` (subdomain-mapped address).
  - Log in using the email (`testowner@mailtrap.io`) and the password created during onboarding.
  - *Expected Result:* Authentication token resolves, and the user is redirected to the main PG owner dashboard.

- [ ] **Create Property Building Infrastructure**
  - Navigate to **Property Management** / **Buildings**.
  - Create a new building configuration:
    * **Building Name:** `Alpha Block`
    * **Floors Count:** `2`
    * **Rooms Per Floor:** `3`
  - *Expected Result:* Database writes succeed. The UI updates to display `Alpha Block` containing two floors.

- [ ] **Configure Room & Beds Layout**
  - Navigate to Floor 1 -> Room 101.
  - Add bed configurations to Room 101:
    * **Beds Count:** `2` (Double sharing arrangement)
    * **Bed Labels:** `101-A` and `101-B`
  - Mark Bed `101-A` and `101-B` as `AVAILABLE`.
  - *Expected Result:* Beds are recorded successfully in the system. The dashboard metrics update to show `2 Available Beds`.

- [ ] **Configure Rental Pricing Rules**
  - Set pricing rules for beds in Room 101:
    * **Monthly Rental Charge:** `₹8,500`
    * **One-time Security Deposit:** `₹10,000`
    * **Billing Cycle:** Calendar-month billing.
  - *Expected Result:* Price matrices are stored.

---

## Phase 4: Guest Registration & Lifecycle

This phase verifies room booking, room-sharing state changes, and automated invoice generations.

- [ ] **Execute Guest Onboarding (Check-in)**
  - Navigate to the **Guests** / **Check-In** section.
  - Initiate a walk-in guest check-in flow:
    * **Guest Name:** `Alice Smith`
    * **Contact Number:** `+91 98765 43210`
    * **ID Proof Type:** Aadhaar / Passport
    * **Assigned Bed:** Select `Alpha Block` -> Floor 1 -> Room 101 -> Bed `101-A`
    * **Check-In Date:** Select today's date.
  - Click **Confirm Onboarding**.
  - *Expected Result:* Alice Smith is registered as an active guest. The room allocation details are updated.

- [ ] **Verify Bed Status Transition**
  - Go to the Rooms dashboard and check Room 101.
  - *Expected Result:* Bed `101-A` status transitions from `AVAILABLE` to `OCCUPIED`. Bed `101-B` remains `AVAILABLE`.

- [ ] **Verify Automatic Initial Invoice Generation**
  - Go to the **Billing** / **Invoices** section for Alice Smith.
  - Check the newly generated invoice details.
  - *Expected Result:* The system automatically generates a pending onboarding invoice containing:
    * Rent for the current cycle: `₹8,500`
    * Security deposit: `₹10,000`
    * Total outstanding balance: `₹18,500`

---

## Phase 5: Multi-Tenant Data Isolation

This critical phase verifies that tenant database schemas are completely isolated and data from one tenant is never visible to another.

- [ ] **Register a Second Tenant ("Demo PG")**
  - Go back to the Control Plane signup page (`http://localhost:5176/signup`).
  - Register a new client profile with these settings:
    * **PG Name:** `Demo PG`
    * **Custom Subdomain:** `demo` (resulting in `demo.pgcrm.com`)
    * **Contact Email:** `demoowner@mailtrap.io`
  - Complete the checkout payment webhook trigger for `Demo PG`.
  - Wait for the provisioning tracker to set status to `LIVE`.
  - *Expected Result:* Tenant database schema `tenant_demo_db` is created. Welcome email is received for `Demo PG`.

- [ ] **Access and Login to Demo PG**
  - Browse to `http://demo.pgcrm.com:5173/` (mapped subdomain for the new tenant).
  - Log in using the `Demo PG` owner credentials.
  - *Expected Result:* Login succeeds. User enters the dashboard for `Demo PG`.

- [ ] **Verify Data Isolation Constraints**
  - Browse through the **Buildings**, **Rooms**, **Beds**, and **Guests** tabs inside `Demo PG`.
  - *Expected Result:* 
    * The Buildings list contains `0` records (No sign of `Alpha Block` from `Test PG`).
    * The Guests roster contains `0` registered guests (No sign of `Alice Smith`).
    * The Rooms list shows no bed configurations.
    * **Database Verification:** Execute direct database query on the `tenant_demo_db` schema and confirm that `buildings` and `guests` tables are completely empty, while the `tenant_test_db` schema tables contain the `Alpha Block` and `Alice Smith` rows.
