# PG CRM Core Application

The **PG CRM Core Application** is the multi-tenant SaaS property management engine. It is the application system used by property owners, managers, and tenants to manage rooms, collect rent, handle issues, and configure tenant portals.

---

## 1. System Overview

The core application is a multitenant deployment designed to scale to thousands of users. It uses dynamic connection routing to partition database requests per tenant schema, ensuring high-performance isolation.

---

## 2. Local Development & Schema Clean Strategy

During active development, it is often necessary to start with a fresh database setup. The application uses a custom Spring Bean `FlywayMigrationStrategy` wrapper that triggers depending on environment configurations.

### Database Wipe on Startup:
- **Configuration Parameter:** `DB_WIPE_ON_STARTUP` (Boolean env flag)
- **Strategy Execution:**
  - If set to `true`, Spring boot invokes a Flyway clean execution on start, dropping all existing tables, views, and schemas inside the target schema before applying Flyway migration scripts.
  - If set to `false` (default for staging/production), the cleanup phase is bypassed, and migrations are applied incrementally.

> [!CAUTION]  
> **Danger of Data Loss:**
> Never activate `DB_WIPE_ON_STARTUP=true` in production configurations, as it drops all user data.

---

## 3. Data Seeding Architecture

To ensure consistent application states, data seeding is split into two specialized components:

### A. System Seeds (`DatabaseSeeder.java`)
- **Responsibility:** Inserts system-critical data that the core framework requires to function.
- **Rules:** Evaluates "Insert Only If Missing" checks. For example, it confirms if the default System Super Admin account exists in the user registry. If not, it creates it; if yes, it does not overwrite the record.
- **Scope:** Runs in all environments (development, testing, staging, and production).

### B. Mock Demo Seeds (`DataSeeder.java`)
- **Responsibility:** Populates sample records to help simulate actual operations for testing and development.
- **Content:** Seeds mock tenants, sample room allocations, fake transaction histories, and default page layouts.
- **Scope:** Runs only in active `dev` or `test` profiles. Bypassed in production.

---

## 4. Multi-Tenancy Routing & Security

The core system handles multi-tenancy dynamically through connection routing and JWT-based authentication.

### Subdomain Routing
1. The client sends a request to a tenant-specific address (e.g. `https://greenwood.pgcrm.com/api/...`).
2. A custom Spring Servlet filter extracts the subdomain prefix (`greenwood`).
3. This prefix is mapped to the target database tenant context (`tenant_greenwood_db`) using an abstract database routing configuration.
4. Connections are resolved dynamically on a per-thread basis using a thread-local context registry.

### JWT Security Context
- All API requests carry a Bearer JWT Token in the request headers.
- The filter chain validates the signature and parses claims to extract the user profile ID and role level (Super Admin, Manager, Tenant).
- Security attributes are saved into Spring's `SecurityContextHolder`, allowing controllers to fetch user identity securely.
