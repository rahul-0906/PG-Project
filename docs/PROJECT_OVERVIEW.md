# PG CRM — Project Overview

## What is this project?

PG CRM is a **single-tenant property management system** for Paying Guest (PG) and hostel operators. Each PG business gets its own dedicated, isolated deployment with its own PostgreSQL database — there is no shared schema or multi-tenant routing.

The system is a single module: **`[PG-CORE]`**, a full-stack Spring Boot + React application that handles all daily PG operations.

---

## What problem does it solve?

Managing a PG or hostel operation traditionally involves fragmented tools, manual billing, and complicated calculations. PG CRM solves this with:

1. **Absolute Data Privacy**: Each client deployment is physically isolated — a dedicated PostgreSQL database and application instance per property.
2. **Automated Flat-Rate Billing**: Strict flat-rate monthly rent (no daily pro-rating). Active guests during any part of a billing month are billed the full monthly rate. Invoices are auto-generated on the 1st of each month.
3. **Electricity Sub-Meter Split**: Automated EB bill calculations — Equal Split, Per-Bed flat rate, Sub-Meter usage (`currentReading - previousReading`), or Manager Manual entry.
4. **Data Compliance**: Automated daily PII anonymization sweep at 3:00 AM, scrubbing guest names and phone numbers for checkouts over 365 days old, while preserving all financial and audit records.
5. **Operational Efficiency**: 1-Click Excel Bulk Import and an Interactive Visual Building Builder Wizard for rapid property setup.
6. **Online Payments**: Razorpay integration with HMAC-SHA256 webhook verification for secure rent collection.
7. **Multi-Channel Notifications**: WhatsApp (Meta Cloud API), email (SMTP), and in-app notifications for invoices, payment reminders, and welcome messages.

---

## Key Modules & Features

| Feature | Description |
| :--- | :--- |
| Guest Check-In/Out | Full check-in with bed allocation, deposit, meal preferences |
| Monthly Invoicing | Auto-generated arrears billing on the 1st of each month |
| EB Bill Split | Equal, Per-Bed, Meter-Based, or Manual entry |
| Meal Calendar | Daily opt-in/out with configurable lockout times |
| Excel Bulk Import | Batch onboarding of rooms, beds, and guests from spreadsheets |
| Visual Building Wizard | Step-by-step floor, block, room, and bed configuration |
| Razorpay Payments | Online rent collection with HMAC webhook verification |
| Maintenance Tickets | Guest-reported issues tracked to resolution |
| Audit Trail | Full audit log of all business operations |
| PII Anonymization | Scheduled daily compliance sweep (GDPR-ready) |
