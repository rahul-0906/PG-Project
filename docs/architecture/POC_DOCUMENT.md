# PG CRM — Proof of Concept (POC) Specification

This document details the architecture, database structures, core business logic modules, and deployment setup for the **PG CRM Core Application** — a single-tenant property management system for Paying Guest (PG) and hostel operators.

---

## 1. Executive Summary

PG CRM is a white-labeled, single-tenant property management platform. Each PG operator gets a fully isolated deployment: one Spring Boot backend, one React frontend, one PostgreSQL database. There is no shared schema and no cross-tenant data exposure.

---

## 2. Architecture Overview

```
                    ┌──────────────────────────────────┐
                    │   PG CRM Core Instance            │
                    │   (One per PG Operator)           │
                    ├──────────────────────────────────┤
                    │  Frontend: React 18 (Port 5173)   │
                    │  Backend:  Spring Boot (Port 8080) │
                    │  Database: PostgreSQL (pgcrmdb)    │
                    └──────────────────────────────────┘
```

**Spatial hierarchy**: `Building → Floor → Block → Room → Bed`

---

## 3. Role-Based Access Control

| Role | Scope |
| :--- | :--- |
| `GUEST` | Own portal: invoices, meal calendar, maintenance |
| `PG_MANAGER` | Guest operations, EB bills, invoice generation |
| `PG_OWNER` | Building setup, manager accounts, revenue reports |
| `SUPER_ADMIN` | Full system: RBAC, audit logs, financial overrides |

---

## 4. Core Business Logic Modules

### 4.1 Flat-Rate Billing Engine

- Guests are billed the **full monthly rate** if active during any part of the billing month — no daily pro-rating.
- Formula: `Base Rent = guest.getMonthlyRent()`
- Whole Room Bookings: `Adjusted Rent = guest.getMonthlyRent() × room.getSharingType()`
- Invoices auto-generated on the 1st of each month by `MonthlyBillingScheduler`.

### 4.2 Electricity Sub-Meter Split

Configured per-building via `BuildingConfig.getEbSplitMethod()`:

| Method | Formula |
| :--- | :--- |
| `EQUAL_SPLIT` | `totalBill ÷ activeGuests` |
| `PER_BED` | `fixedRatePerBed × guestBedCount` |
| `METER_BASED` | `(currentReading − previousReading) × ratePerUnit` |
| `MANAGER_MANUAL` | Manager enters per-guest amount |

### 4.3 Razorpay Payment Flow

1. Guest opens invoice → `POST /api/payments/create-order` → Razorpay order created
2. Guest pays via Razorpay modal (UPI/Card/NetBanking)
3. Razorpay fires webhook → `POST /api/webhooks/razorpay`
4. Backend verifies HMAC-SHA256 signature → invoice updated to `PAID`

### 4.4 PII Anonymization Scheduler

- `DataRetentionScheduler` runs daily at 3:00 AM
- Scrubs `fullName` → random token, `phone` → random token for guests checked out >365 days
- Financial records, invoices, and audit logs are **always preserved**

---

## 5. Database Schema Summary

| Table | Purpose |
| :--- | :--- |
| `users` | Auth accounts (BCrypt passwords, roles, JWT) |
| `guests` | Resident records (soft-delete, check-in/out dates) |
| `buildings`, `floors`, `blocks`, `rooms`, `beds` | Property spatial hierarchy |
| `building_configs` | Per-property configuration (split method, lockout, pricing) |
| `invoices`, `invoice_line_items` | Monthly billing records (soft-delete) |
| `eb_bills`, `eb_bill_guests` | Electricity billing sessions and allocations |
| `daily_logs` | Per-guest daily meal opt-in/out |
| `maintenance_tickets` | Guest-reported issues |
| `audit_logs` | Full operation audit trail |
| `pricing_configs` | Dynamic pricing overrides |
| `notifications` | In-app notification log |

---

## 6. Deployment Architecture

```
                         ┌──── Nginx Reverse Proxy ────┐
                         │  /api  → Port 8080           │
                         │  /     → Port 5173 (static)  │
                         └────────────────────────────-─┘
                                        │
                    ┌───────────────────┴───────────────────┐
                    │          Docker Compose Stack           │
                    ├───────────────────────────────────────┤
                    │  pgcore-frontend  (nginx:alpine)       │
                    │  pgcore-backend   (eclipse-temurin:23) │
                    │  pgcore-db        (postgres:18-alpine)  │
                    └────────────────────────────────────────┘
```

All secrets (DB password, JWT secret, API keys) are passed via `.env` environment variables — never hardcoded.

---

## 7. Success Criteria Checklist

- [x] Guest can be checked in and assigned to a specific bed
- [x] Monthly invoices auto-generate with correct flat-rate amounts
- [x] EB bill split calculates correctly for all 4 split methods
- [x] Razorpay webhook verifies HMAC-SHA256 and marks invoice PAID
- [x] DB wipe is blocked in `prod` profile (`@Profile` guard + `flyway.clean-disabled: true`)
- [x] BCrypt password hashing confirmed in `SecurityConfig.java`
- [x] PII anonymization scheduler operational (daily 3AM)
- [x] Audit trail captures all business operations
- [x] Role-based access enforced at the endpoint level
