# PG CRM Ecosystem - Project Overview

## What is this project about?
The PG CRM Ecosystem is a comprehensive, decoupled B2B SaaS Enterprise Monorepo designed specifically for Paying Guest (PG) and hostel management. The ecosystem is built on a Hybrid Multi-Instance Single-Tenant Database Isolation model, providing a robust architecture that physically separates customer-facing property operations from centralized administrative billing and infrastructure management.

The system is composed of two primary modules:
1. **`[PG-CORE]` (Single-Tenant Property Management)**: A white-labeled property management software deployed uniquely for each PG brand/hostel branch. It manages daily operations including guest check-ins, automated daily arrears billing, electricity sub-meter calculations, visual meal calendars, and maintenance ticketing.
2. **`[CONTROL-PLANE]` (Central B2B SaaS Billing & Provisioning)**: The centralized command center for the software provider. It handles B2B client onboarding, automated instance deployment, subscription renewals, Razorpay payment processing, and license suspensions.

## What problem does this solve?
Managing a PG or hostel operation traditionally involves fragmented tools, manual billing, and privacy concerns when scaling to multiple branches or attempting to offer the software as a SaaS product. This project solves these challenges by offering:

1. **Absolute Data Privacy & Security**: By utilizing physical database isolation (each client gets an independent containerized stack and dedicated PostgreSQL database), cross-tenant data leaks are impossible. Customers maintain full autonomy over their data and backups.
2. **Automated Provisioning & Onboarding**: The onboarding process is zero-touch. Upon successful payment verification via Razorpay webhooks (secured with HMAC-SHA256), a script automatically provisions a new isolated database and Docker stack, assigning it a custom port and reverse proxy configuration.
3. **Simplified Complex Billing**: It automates complex physical calculations such as Electricity (EB) Bill Sub-Meter Splits (Equal Split, Per-Bed flat rate, or Sub-Meter usage differences) and enforces strict flat-rate monthly rent policies.
4. **Data Compliance**: Features an automated Data Compliance Scheduler that performs daily PII Anonymization sweeps, scrubbing names and phone numbers of guests checked out for over 365 days while maintaining transactional and financial integrity.
5. **Operational Efficiency**: Features like the 1-Click Excel Bulk Import and Interactive Visual Building Builder Wizard allow for rapid migration and setup of physical property layouts (blocks, sharing capacities, AC/Non-AC status).
