# PG CRM Master Control Plane

The **Master Control Plane** is the centralized orchestrator and administration portal for the PG CRM B2B SaaS platform. It manages the onboarding funnel, handles billing and subscriptions via Razorpay, coordinates tenant infrastructure provisioning, and provides platform-wide monitoring.

---

## 1. System Overview

The Control Plane consists of a unified React frontend and a Spring Boot backend connected to the `controlplane_db` PostgreSQL database. It isolates metadata and tenant management details from the individual tenant database systems.

```
┌────────────────────────────────────────────────────────┐
│               Master Control Plane                     │
├────────────────────────────────────────────────────────┤
│  [ React Frontend ] ──► [ Spring Boot Controller ]     │
│                                  │                     │
│                                  ▼                     │
│                       [ DB / Infra Provisioner ]       │
│                                  │                     │
│                                  ▼                     │
│                       ( provision_tenant.sh )          │
└──────────────────────────────────┬─────────────────────┘
                                   │
                                   ▼
                 ┌──────────────────────────────────┐
                 │        PG Core App Nodes         │
                 │ ──► [ tenant_a_db ]              │
                 │ ──► [ tenant_b_db ]              │
                 └──────────────────────────────────┘
```

---

## 2. Tenant Onboarding State Machine

The platform drives tenant onboarding through a strict state machine represented in `TenantStatus`:

| Status | Trigger Event | Description |
| :--- | :--- | :--- |
| `PENDING_SETUP` | Initial user signup | User profile is registered but company details and preferences are not configured. |
| `PENDING_PAYMENT` | Form submission completed | Onboarding data (branding, metadata) is stored. Awaiting Razorpay billing confirmation. |
| `PROVISIONING` | Webhook call or Admin override | Payment verified successfully. The background worker is executing the infrastructure script. |
| `LIVE` | Process exit status `0` | Database schema created, routes mapped, and welcome credentials emailed to the owner. |
| `SUSPENDED` | Process exit status `!= 0` / Admin override | Provisioning failed or tenant has outstanding billing or administrative suspensions. |

---

## 3. Infrastructure Automation

Provisioning is executed out-of-band by the `TenantProvisioningWorker` to prevent blocking the HTTP server threads.

### Provisioning Pipeline Flow:
1. `TenantProvisioningWorker.executeProvisioningPipeline(tenantId)` is called asynchronously using Spring `@Async`.
2. A `ProcessBuilder` spawns the bash automation script.
3. Output and error streams are captured, redirected, and logged using SLF4J.
4. If `process.waitFor()` returns `0`, status is updated to `LIVE` and the owner is notified. Otherwise, status is set to `SUSPENDED`.

> [!WARNING]  
> **Script Execute Permission Requirement:**
> On Unix-based production environments, ensure the shell execution script has appropriate execution permission:
> ```bash
> chmod +x src/main/resources/scripts/provision_tenant.sh
> ```

---

## 4. Razorpay Payment Gateway Integration

Security is managed via HMAC-SHA256 signatures to prevent verification bypass.

- **Endpoint:** `POST /api/webhooks/razorpay`
- **Validation:** Signature received in the headers is validated against the computed payload hash using the configured `webhookSecret`.
- **Identity Linkage:** The payment capture payload extracts `payload.payment.entity.notes.tenant_id`. This unique UUID links the transaction straight back to the corresponding `TenantProfile` and pushes it into the `PROVISIONING` pipeline.

---

## 5. Front-End Features

The frontend interface, located in the `frontend/` subdirectory, delivers the following modules:
1. **Multi-Step Onboarding Wizard:** Collects setup information, dynamic branding selections, and calculates AMC payment rates based on chosen terms (Monthly vs. Yearly).
2. **Super Admin Dashboard:** A premium, dark-mode analytics console displaying all registered tenants, billing status, and platform controls allowing manual status overrides (Provision, Suspend, Activate).
3. **Live Provisioning Tracker:** Relies on automated periodic polling (`setInterval` every 2.5 seconds) against `/api/tenant/me` to track setup execution. Displays custom status-bar animations and presents an **Enter Workspace** link once provisioning transitions to `LIVE`.
