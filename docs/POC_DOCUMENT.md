# PG CRM Ecosystem - Proof of Concept (POC) Document

## 1. Objective
To validate and demonstrate the feasibility of a Hybrid Multi-Instance Single-Tenant architecture for a B2B SaaS application tailored for Paying Guest (PG) and hostel management. The POC aims to prove that a centralized control plane can securely and automatically provision isolated instances for individual tenants.

## 2. Scope of the POC
The POC covers the end-to-end lifecycle of a new B2B client acquisition, focusing on:
- Centralized B2B client onboarding and payment processing.
- Automated creation of an isolated database and application environment (Docker container).
- Dynamic routing using Nginx to map custom subdomains/TLDs to the isolated tenant instances.

## 3. Architecture Components
- **`[CONTROL-PLANE]`**: The central SaaS command center.
  - **Backend (Port 8090)**: Manages subscriptions, webhooks, and orchestrates the deployment script.
  - **Frontend (Port 5176)**: Admin dashboard and onboarding portal.
- **`[PG-CORE]`**: The isolated property management instance.
  - **Backend (Port 8080/8081+)**: Dedicated instance for a specific PG operator.
  - **Frontend (Port 5173)**: React application for property operations.

## 4. POC Workflow Demonstration
1. **Client Registration**: A PG owner registers via the Landing Page (`/signup`).
2. **Payment Processing**: A Razorpay order is initiated for the Setup Fee.
3. **Webhook Reconciliation**: Upon payment, Razorpay sends a webhook to the Control Plane Backend, validated via HMAC-SHA256.
4. **Automated Provisioning**: The backend asynchronously triggers `provision_tenant.sh`, which:
   - Provisions a dedicated PostgreSQL database.
   - Generates unique configuration files (`.env`).
   - Spins up a dedicated Docker container stack for the tenant (e.g., mapped to Port `8081`).
5. **Ingress Setup**: The Nginx reverse proxy routes the tenant's specific subdomain (e.g., `client-a.pgcrm.com`) to their uniquely assigned container port.

## 5. Success Criteria
- [ ] A tenant can successfully register and complete a simulated payment.
- [ ] The system automatically provisions a unique database and application container without human intervention.
- [ ] The newly provisioned instance is accessible via its designated custom domain/subdomain.
- [ ] Data entered in Tenant A's instance is completely invisible and isolated from Tenant B's instance.
