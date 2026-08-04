# PG CRM Ecosystem — Comprehensive Code Audit Report

**Audit Date:** 2026-08-03
**Audited By:** AI Code Review (Antigravity)
**Branch:** `feature-claude-audit`
**Scope:** Full-stack — `core-pg-crm` + `master-control-plane`
**Stack:** Java 23 / Spring Boot 3.2.5 · React 18/19 · PostgreSQL · Docker

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Architecture Review](#2-architecture-review)
3. [Security Audit](#3-security-audit)
4. [Business Logic & Calculation Engine](#4-business-logic--calculation-engine)
5. [Performance & Scalability](#5-performance--scalability)
6. [Data Integrity & Compliance (GDPR / PCI-DSS)](#6-data-integrity--compliance-gdpr--pci-dss)
7. [Infrastructure & DevOps](#7-infrastructure--devops)
8. [Frontend Quality](#8-frontend-quality)
9. [Code Quality & Maintainability](#9-code-quality--maintainability)
10. [Testing Coverage](#10-testing-coverage)
11. [Prioritized Findings Summary](#11-prioritized-findings-summary)
12. [Recommended Improvement Plan](#12-recommended-improvement-plan)

---

## 1. Executive Summary

The PG CRM Ecosystem is a **well-structured, production-capable full-stack application** with thoughtful domain modeling, clear separation of concerns, and solid documentation standards. The code demonstrates engineering maturity in several areas — particularly the billing engine, the audit trail, and the data anonymization scheduler.

However, there are **critical security vulnerabilities** and **medium-risk architectural gaps** that must be resolved before production deployment at scale. The most serious issues are concentrated in three areas: the **webhook sandbox bypass left in production paths**, the **overly permissive CORS and authorization policies**, and the **complete absence of automated tests**.

### Overall Risk Score

| Category | Rating |
|---|---|
| Security | HIGH RISK |
| Business Logic | MEDIUM RISK |
| Performance | MEDIUM RISK |
| Compliance (GDPR/PCI-DSS) | MEDIUM RISK |
| Code Quality | LOW RISK |
| Infrastructure | MEDIUM RISK |
| Testing | HIGH RISK (0% coverage) |

---

## 2. Architecture Review

### 2.1 Strengths

- **Clean dual-module separation.** The `core-pg-crm` (single-tenant property ops) and `master-control-plane` (B2B billing & provisioning) are correctly isolated with separate databases, ports, and deployment lifecycles.
- **Physical multi-tenancy via Docker provisioning.** Each B2B client gets a dedicated containerized stack, making cross-tenant data leaks physically impossible at the database layer. This is the right model for a B2B SaaS PG product.
- **Flyway-managed schema.** Controlled migration history ensures database drift cannot occur silently.
- **Two-tier pricing resolution.** The `PricingService` correctly cascades `BuildingConfig` -> YAML defaults, which prevents hardcoded values from leaking into billing.
- **Soft-delete everywhere.** The `active` flag pattern on `Guest` and `User` entities preserves audit references correctly.

### 2.2 Architectural Issues

#### ARCH-01 — `PricingController` violates Single Responsibility Principle (Medium)

The `getPricing` endpoint (lines 42-165) fetches building hierarchy data (floors, blocks, rooms) directly via **5 different repositories injected into the controller**. This is business logic that belongs in a dedicated `BuildingHierarchyService`. Controllers should only orchestrate responses, not execute multi-repository queries.

```java
// Bad: 5 repositories injected into a controller
private final BuildingRepository buildingRepository;
private final FloorRepository floorRepository;
private final BlockRepository blockRepository;
private final RoomRepository roomRepository;
private final BuildingConfigRepository buildingConfigRepository;
```

#### ARCH-02 — N+1 Query Risk in `PricingController.getPricing` (Medium-High)

The `getPricing` endpoint iterates `buildingRepository.findAll()` -> per-building `floorRepository.findByBuildingId()` -> per-floor `blockRepository.findByFloorId()` -> per-block `roomRepository.findByBlockId()`. This is a **4-level nested loop with individual SQL queries at each level**, causing severe N+1 query explosions for buildings with many floors/blocks/rooms.

#### ARCH-03 — Dual Provisioning Path Confusion (Medium)

Two separate provisioning pipelines exist for what should be the same operation:
- `ProvisioningService.executeProvisioningPipeline(TenantProfile)` — used in one webhook flow
- `TenantProvisioningWorker.executeProvisioningPipeline(UUID)` — used in the Razorpay webhook flow

Both call a `provision_tenant.sh` script but with different parameter orderings and script paths. This duplicated logic risks provisioning failures if one path diverges. **Pick one canonical path and delete the other.**

#### ARCH-04 — `branchId` Stored as Comma-Separated String (Low-Medium)

```java
@Column(name = "branch_id")
private String branchId; // e.g., "uuid1,uuid2,uuid3"
```

Storing multi-valued foreign key references as a delimited string is an anti-pattern. It prevents proper JPA joins, makes validation difficult, and could cause a security gap if branch IDs contain commas. A proper `@ManyToMany` join table is the correct solution.

---

## 3. Security Audit

### CRITICAL Findings

#### SEC-01 — Webhook Sandbox Bypass is a Production Security Hole

**Files:**
- `RazorpayWebhookServiceImpl.java` (line 94)
- `BillingServiceImpl.java`
- `CheckoutServiceImpl.java`

All three webhook service implementations contain a bypass that **accepts any request claiming to be from a sandbox** without any real cryptographic validation:

```java
if ("mockWebhookSecret".equals(webhookSecret) || "sandbox_mock_signature".equals(signature)) {
    log.warn("Bypassing webhook signature validation (Sandbox/Mock environment)");
    return true;
}
```

**Impact:** Any attacker who knows the magic string `sandbox_mock_signature` can send a fake `payment.captured` webhook, triggering automated tenant provisioning without paying. This is a **revenue bypass vulnerability**. The bypass must be controlled by an env var that is provably `false` in production.

**Fix:**
```java
// Add to application.yml
// app.razorpay.bypass-signature-for-sandbox: ${RAZORPAY_BYPASS_SIGNATURE:false}

@Value("${app.razorpay.bypass-signature-for-sandbox:false}")
private boolean bypassSignatureForSandbox;

private boolean verifySignature(String payload, String signature) {
    if (bypassSignatureForSandbox) {
        log.warn("Bypassing webhook signature validation — SANDBOX MODE");
        return true;
    }
    // ... real HMAC verification
}
```

---

#### SEC-02 — Wildcard CORS with `allowCredentials = true`

```java
// SecurityConfig.java
config.setAllowedOriginPatterns(List.of("*"));
config.setAllowCredentials(true);
```

This combination is a **CORS misconfiguration**. Allowing all origins with credentials means any website can make authenticated requests to your API using a logged-in user's session.

**Fix:** Define explicit allowed origins from environment variables:
```java
@Value("${app.cors.allowed-origins}")
private List<String> allowedOrigins;
config.setAllowedOrigins(allowedOrigins); // e.g., ["https://app.yourpg.com"]
```

---

#### SEC-03 — Static Files Bypass Security (`/**` Permit All)

```java
// SecurityConfig.java
.requestMatchers("/**").permitAll() // React static files
.anyRequest().authenticated()       // UNREACHABLE dead code
```

The `/**` permit-all rule catches everything, making `.anyRequest().authenticated()` unreachable. Any API endpoint not explicitly listed above is effectively public.

**Fix:** Serve static files via Nginx exclusively. Remove the `/**` wildcard rule from the security chain.

---

#### SEC-04 — Refresh Token Not Rotated (Medium-High)

The code itself acknowledges: *"refresh token rotation is not implemented."* Refresh tokens with a 7-day TTL that are never rotated are stolen-token-friendly.

**Fix:** On every `/auth/refresh` call, invalidate the old refresh token (via a `revoked_tokens` table or Redis) and issue a new one.

---

#### SEC-05 — No Rate Limiting on Auth Endpoints (Medium-High)

The `/api/auth/login`, `/api/auth/refresh`, and `/api/auth/forgot-password` endpoints have no rate limiting, enabling brute force and password spray attacks.

**Fix:** Implement IP-based rate limiting (5 attempts/minute) using Bucket4j or Resilience4j.

---

#### SEC-06 — `GlobalExceptionHandler` Leaks Internal Details in 500s (Medium)

```java
return ResponseEntity.internalServerError()
        .body(error("Internal server error: " + ex.getMessage(), 500));
```

`ex.getMessage()` is serialized directly into the HTTP response. This can leak SQL error details, class names, file paths, or internal business data. The handler's own Javadoc even flags this for a production hardening pass.

**Fix:**
```java
log.error("Unhandled exception", ex);  // Full detail server-side only
return ResponseEntity.internalServerError()
        .body(error("An unexpected error occurred. Please contact support.", 500));
```

---

#### SEC-07 — `SuperAdminController` Returns Raw `User` Entity (Medium)

```java
public ResponseEntity<List<User>> getAllUsers() {
    return ResponseEntity.ok(superAdminService.getAllUsers()); // raw entity, unbounded
}
```

No DTO projection, no pagination — returns an unbounded full-table scan as JSON. Use a `UserSummaryDto` and paginate.

---

#### SEC-08 — Docker Socket Mounted in Control Plane (High)

```yaml
# docker-compose.yml
volumes:
  - /var/run/docker.sock:/var/run/docker.sock
```

Full Docker socket access is equivalent to root on the host machine. This is a known container escape vector.

**Fix:** Use `Tecnativa/docker-socket-proxy` to restrict to only the specific Docker API calls needed.

---

### MEDIUM Findings

#### SEC-09 — No `@PreAuthorize` on Service Methods (Defense-in-Depth Gap)

URL-level authorization in `SecurityConfig` is the only enforcement layer. Service-level `@PreAuthorize` annotations provide defense-in-depth.

#### SEC-10 — Real-Looking API Keys Embedded as YAML Defaults

```yaml
razorpay:
  key-id: ${RAZORPAY_KEY_ID:rzp_test_SuLwO7L565iIkE}
  key-secret: ${RAZORPAY_KEY_SECRET:crxqdRsIIaL2gbti1Hg9cZgG}
```

Real-looking test credentials in committed YAML files are a leak risk. Use empty string or `CHANGEME` as defaults.

---

## 4. Business Logic & Calculation Engine

### 4.1 Strengths

- **Flat-rate rent correctly enforced.** `calculateProRatedRent` applies the flat monthly rate, consistent with the AGENTS.md business rule.
- **Invoice idempotency is correctly implemented.** Same `(guestId, month, year)` triple returns the existing invoice.
- **Meal lockout logic is correctly gated** by cutoff times from `BuildingConfig`, with sensible defaults.
- **Data anonymization is correctly scoped.** `DataRetentionScheduler` anonymizes PII only for guests checked out >365 days ago, preserving financial audit data.

### 4.2 Issues

#### BIZ-01 — `calculateProRatedRent` Name Contradicts the Flat-Rate Rule (Medium)

The method is named `calculateProRatedRent` and its Javadoc says rent is pro-rated for mid-month check-ins. But the implementation returns the full flat `baseRent` with a log message saying "Flat-Rate Rent Applied". The name and Javadoc are **contradictory and dangerous** — a future developer could introduce a regression by "fixing" the method to match the Javadoc.

**Fix:** Rename to `calculateFlatRateRent()` and update the Javadoc.

---

#### BIZ-02 — `MonthlyBillingScheduler` Has No Distributed Lock (Medium-High)

```java
@Scheduled(cron = "0 0 0 1 * *")
@Transactional
public void generateMonthlyInvoices() { ... }
```

If the application is horizontally scaled, this cron fires on every instance simultaneously, causing lock contention and wasted DB connections (even though the idempotency guard prevents duplicate invoice rows).

**Fix:** Use ShedLock to ensure only one instance executes the cron at a time.

---

#### BIZ-03 — Bulk Room Rent Update Uses Individual `save()` Per Room (Medium)

```java
for (Room r : rooms) {
    if (r.getSharingType() == sharingType) {
        r.setBaseRent(rent);
        updated.add(roomRepository.save(r)); // N individual UPDATE statements
    }
}
```

**Fix:** Use `roomRepository.saveAll(updated)` with `hibernate.jdbc.batch_size` configured, or a native bulk UPDATE query.

---

#### BIZ-04 — `PaymentReminderScheduler` Hardcodes `localhost` in WhatsApp Message (Low-Medium)

```java
"Please pay to avoid any issues. Login: http://localhost:5173"
```

This will send `localhost:5173` to guests' WhatsApp in production.

**Fix:** Read the URL from `${app.frontend.url}` config property.

---

#### BIZ-05 — `EmailVerificationService` is Single-Node In-Memory Cache (Low-Medium)

The code correctly documents: *"not safe for clustered / multi-pod deployments."* Lazy eviction also means expired entries accumulate in memory indefinitely. Track as a pre-scaling prerequisite (migrate to Redis TTL).

---

## 5. Performance & Scalability

#### PERF-01 — `PricingController.getPricing` — 4-Level N+1 Query (High)

For a building with 5 floors x 4 blocks x 10 rooms = 200 rooms, this endpoint generates ~210 SQL queries per request.

**Fix:**
```java
@Query("SELECT r FROM Room r JOIN FETCH r.block b JOIN FETCH b.floor f JOIN FETCH f.building WHERE f.building.id = :buildingId")
List<Room> findRoomsByBuildingIdWithHierarchy(@Param("buildingId") String buildingId);
```

#### PERF-02 — `MonthlyBillingScheduler` Loads All Active Guests In Memory (Medium)

For 500+ guests, `guestRepository.findByActiveTrue()` loads all records into JVM heap simultaneously. Migrate to paginated/chunked processing (page size of 50).

#### PERF-03 — `open-in-view: true` in Production Config (Medium)

```yaml
# application.yml (core-pg-crm)
jpa:
  open-in-view: true
```

Keeps the JPA EntityManager open for the full HTTP lifecycle, exhausting the connection pool under load.

**Fix:** Set `open-in-view: false`.

#### PERF-04 — `show-sql: true` in Control Plane Production Config (Low-Medium)

```yaml
# master-control-plane/application.yml
jpa:
  show-sql: true
```

SQL logging in production adds overhead to every DB operation. No prod profile override disables this.

#### PERF-05 — `ddl-auto: update` in Control Plane (Critical for Production Safety)

```yaml
# master-control-plane/application.yml
jpa:
  hibernate:
    ddl-auto: update
```

Allows Hibernate to auto-modify the production database schema. Hibernate's `update` mode does not handle renames, type changes, or constraint drops safely and can silently corrupt data. The core CRM correctly uses `validate`.

**Fix:** Migrate control plane to Flyway migrations + `ddl-auto: validate`.

---

## 6. Data Integrity & Compliance (GDPR / PCI-DSS)

### 6.1 Strengths

- **GDPR anonymization implemented.** `DataRetentionScheduler` correctly scrubs PII after 365 days for checked-out guests.
- **Audit trail is comprehensive.** `AuditService` logs all significant operations with actor, entity, and context data.
- **Soft-delete preserves financial references.** No hard deletes on guests/users.
- **HMAC-SHA256 webhook verification** is correctly implemented in the actual (non-bypassed) verification path.
- **Password reset uses `SecureRandom`** with ambiguity-reduced character set.

### 6.2 Issues

#### COMP-01 — No GDPR Right-to-Erasure Endpoint (Medium)

Under GDPR Article 17, users have the right to erasure on demand. The `anonymizeGuest` endpoint in `SuperAdminController` partially addresses this but it's not surfaced through a proper subject rights management workflow.

#### COMP-02 — PCI-DSS: Razorpay Key Secrets Stored as Plain Text in DB (Medium)

The `TenantInstance` entity stores `razorpayKeyId` and `razorpayKeySecret` directly as plain text columns. These should be encrypted at-rest using column-level encryption (Jasypt or application-layer AES).

#### COMP-03 — `User.password` Lacks `@JsonIgnore` at Entity Level (Medium)

The Javadoc says the password is "suppressed via `@JsonIgnoreProperties` wherever a `User` reference is serialised," but there is no `@JsonIgnore` or `@JsonProperty(access = WRITE_ONLY)` on the field itself. This relies on every developer remembering to add exclusion annotations everywhere `User` is referenced.

**Fix:** Add `@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)` directly to the `password` field in `User.java`.

#### COMP-04 — WhatsApp Messages Contain Invoice Totals in Plaintext (Low)

Invoice amounts sent via Twilio WhatsApp Business API are not end-to-end encrypted on the server-to-server path. Note this in the privacy policy.

#### COMP-05 — Audit Log Descriptions Retain Guest Email After Anonymization (Low)

```java
auditService.log(..., "Payment reminder sent to " + guest.getEmail());
```

When GDPR anonymization scrubs the `Guest` entity, the email remains in `AuditLog.description`. The anonymization job should also scrub audit log descriptions for affected guests.

---

## 7. Infrastructure & DevOps

### 7.1 Strengths

- **Multi-stage Docker builds** correctly implemented, minimizing final image sizes.
- **nginx.conf** correctly configured for SPA routing, API proxying, and gzip compression.
- **Health checks** defined for the database service in `docker-compose.yml`.
- **PWA support** with correct `NetworkOnly` strategy for API routes.
- **`maven dependency:go-offline`** used in core CRM Dockerfile for layer caching.

### 7.2 Issues

#### INFRA-01 — Control Plane Backend Uses Full JDK Runtime Image (Medium)

```dockerfile
FROM eclipse-temurin:23-jdk-alpine  # ~200MB larger than JRE
```

The core CRM correctly uses `eclipse-temurin:23-jre-alpine`. The control plane needs the `docker-cli` (which is already installed via `apk`) but does not need the JDK. Use `eclipse-temurin:23-jre-alpine` for the runtime stage.

#### INFRA-02 — No Local Dev `docker-compose.dev.yml` for Standalone CRM (Documentation Gap)

The `docker-compose.yml` only defines control plane services. There is no way to bring up a standalone CRM instance for local development without the control plane provisioning one. A `docker-compose.dev.yml` is needed.

#### INFRA-03 — Control Plane Dockerfile Missing Dependency Cache Layer (Low)

```dockerfile
# Control plane — inefficient
COPY pom.xml .
COPY src ./src          # src copied before deps downloaded
RUN mvn clean package -DskipTests
```

Every code change triggers a full dependency re-download. Fix by adding `RUN mvn dependency:go-offline` before `COPY src ./src`.

#### INFRA-04 — Provisioning Script Timeout Hardcoded at 5 Minutes (Low)

```java
boolean completed = process.waitFor(5, TimeUnit.MINUTES);
```

May be insufficient for first-time Docker image pulls. Should be configurable via env var.

#### INFRA-05 — No HTTPS/TLS Configuration for Production (High)

`nginx.conf` only listens on port 80. Production requires HTTPS. Configure Let's Encrypt via Certbot or an external load balancer with TLS termination.

---

## 8. Frontend Quality

### 8.1 Strengths

- **React Query** with 5-minute stale time and disabled window-focus refetch — well-tuned for a dashboard.
- **ErrorBoundary** implemented at the app root — prevents full app crashes.
- **PrivateRoute** enforces role-based access and forces `/change-password` when `mustChangePassword` is true.
- **`esbuild` drops `console` and `debugger`** in production builds.
- **PWA correctly configured** with `NetworkOnly` for API routes.

### 8.2 Issues

#### FE-01 — React Version Mismatch Between Modules (Medium)

- `core-pg-crm/frontend`: React 18.3.1, React Router v6, Vite 5
- `master-control-plane/frontend`: React 19.2.6, React Router v7, Vite 8

React 19 introduces breaking changes from React 18. Both modules should target the same major version.

#### FE-02 — Control Plane Admin UI Has Zero Authentication (Critical)

```jsx
// master-control-plane/frontend/src/App.jsx
<Route path="/admin" element={<AdminLayout />}>
  <Route index element={<AdminDashboard />} />  // No PrivateRoute wrapper
  <Route path="clients" element={<ClientRoster />} />
  <Route path="tenants" element={<SuperAdminDashboard />} />
</Route>
```

Any user who navigates to `/admin` sees the full admin dashboard. The backend admin API endpoints also need authentication. Implement a login flow and `PrivateRoute` guards.

#### FE-03 — `branchId` Exposed in JWT Payload and Local Storage (Medium)

Branch UUIDs are stored in localStorage and sent as a custom header (`X-Selected-Branch-Id`). While the backend re-validates on every request, this leaks building identifiers to the client.

#### FE-04 — Error Boundary Shows Raw Stack Traces in Production (Low-Medium)

```jsx
{this.state.errorInfo && this.state.errorInfo.componentStack}
```

Leaks internal component structure and file paths to end users. Show a friendly message in production and log to a monitoring service (Sentry).

#### FE-05 — Build Output Configured Into Backend Static Resources (Low)

```js
// vite.config.js
build: {
    outDir: '../backend/src/main/resources/static',
```

This tightly couples frontend and backend build pipelines and conflicts with the Nginx-based Docker deployment model. Use Nginx exclusively for production.

---

## 9. Code Quality & Maintainability

### 9.1 Strengths

- **Exceptionally high documentation quality.** Javadoc coverage across all entity classes, services, and controllers is well above industry average.
- **Consistent logging patterns.** Entry/exit logging and failure warnings at every significant operation.
- **Domain-specific custom exceptions.** `BedUnavailableException`, `DuplicateEmailException`, `SignatureVerificationException`.
- **Builder pattern** used consistently across all entities and DTOs.
- **`@Transactional` correctly applied** on all multi-step service operations.

### 9.2 Issues

#### QUAL-01 — `AuthController` Contains Change-Password Business Logic (Medium)

The `changePassword` endpoint directly injects `UserRepository` and `PasswordEncoder` into the controller and performs business logic inline, bypassing `AuthService`. This creates two divergent persistence paths and makes service-layer testing incomplete.

**Fix:** Move `changePassword` logic into `AuthService.changePassword(userId, currentPassword, newPassword)`.

#### QUAL-02 — `PricingController.updateBuildingConfig` Uses Raw Map Input (Low-Medium)

```java
public ResponseEntity<?> updateBuildingConfig(
        @RequestBody Map<String, Object> body) { ... }
```

No input validation, no type safety, no OpenAPI documentation. An invalid `ebSplitMethod` value throws an unhandled `IllegalArgumentException` from `EbSplitMethod.valueOf()`.

**Fix:** Replace with a typed DTO annotated with `@Valid` constraints.

#### QUAL-03 — Duplicate `TEMP_PASS_CHARS` Constant Defined in Two Services (Low)

Both `AuthService` and `GuestService` define the same static constant. Extract to a shared `PasswordUtils` utility class.

---

## 10. Testing Coverage

### 10.1 Current State: 0% Automated Test Coverage

The `src/test/java/com/` directory exists but contains **no test classes**. There are no unit tests, integration tests, or contract tests anywhere in the backend. The frontend has Vitest configured but no test files.

This is the **highest-risk gap in the entire codebase**. The billing engine, security filter chain, and webhook signature verification are all completely untested.

### 10.2 Minimum Test Coverage Required for Production

| Area | Test Type | Priority |
|---|---|---|
| `InvoiceService.generateInvoiceForGuest` | Unit | Critical |
| `InvoiceService` flat-rate rent calculation | Unit | Critical |
| `AuthService.login` — valid/invalid/deactivated | Unit | Critical |
| `RazorpayWebhookServiceImpl.verifySignature` | Unit | Critical |
| `GuestService.checkIn` — all 3 scenarios | Unit | Critical |
| `SettlementService` checkout lifecycle | Unit | Critical |
| `/api/auth/login` rate limiting | Integration | High |
| `SecurityConfig` — verify unauthorized access is blocked | Integration | High |
| Monthly billing scheduler — duplicate invoice guard | Integration | High |

---

## 11. Prioritized Findings Summary

### Critical (Fix Before Production)

| ID | Finding | Location |
|---|---|---|
| SEC-01 | Webhook sandbox bypass allows fraudulent provisioning | `RazorpayWebhookServiceImpl`, `BillingServiceImpl`, `CheckoutServiceImpl` |
| SEC-02 | Wildcard CORS with `allowCredentials` | `SecurityConfig.java` |
| SEC-03 | `/**` permit-all makes `anyRequest().authenticated()` dead code | `SecurityConfig.java` |
| PERF-05 | `ddl-auto: update` in control plane | `master-control-plane/application.yml` |
| FE-02 | Control plane admin UI has zero authentication | `master-control-plane/frontend/App.jsx` |
| TEST | Zero automated test coverage | Entire codebase |

### High (Fix Within 2 Sprints)

| ID | Finding | Location |
|---|---|---|
| SEC-04 | Refresh token never rotated | `AuthService.java` |
| SEC-05 | No rate limiting on auth endpoints | `SecurityConfig.java` |
| SEC-06 | 500 errors leak internal exception messages | `GlobalExceptionHandler.java` |
| SEC-07 | `SuperAdminController` returns raw User entity | `SuperAdminController.java` |
| SEC-08 | Docker socket mounted in control plane | `docker-compose.yml` |
| BIZ-01 | `calculateProRatedRent` name/docs contradict flat-rate rule | `InvoiceService.java` |
| BIZ-02 | Monthly billing cron has no distributed lock | `MonthlyBillingScheduler.java` |
| PERF-01 | 4-level N+1 query in pricing endpoint | `PricingController.java` |
| PERF-03 | `open-in-view: true` in production | `application.yml` |
| PERF-04 | `show-sql: true` in control plane | `master-control-plane/application.yml` |
| INFRA-05 | No HTTPS/TLS configuration | `nginx.conf` |

### Low (Backlog / Tech Debt)

| ID | Finding |
|---|---|
| ARCH-01 | `PricingController` violates SRP (5 repos injected) |
| ARCH-03 | Duplicate provisioning code paths |
| ARCH-04 | `branchId` stored as comma-separated string |
| SEC-09 | No `@PreAuthorize` on service methods |
| SEC-10 | Real-looking API keys as YAML defaults |
| BIZ-03 | Bulk room rent uses N individual saves |
| BIZ-04 | `localhost` URL hardcoded in WhatsApp message |
| BIZ-05 | `EmailVerificationService` is single-node in-memory |
| COMP-01 | No GDPR right-to-erasure endpoint |
| COMP-02 | Razorpay secrets stored as plain text in DB |
| COMP-03 | `User.password` lacks `@JsonProperty(WRITE_ONLY)` |
| COMP-05 | Audit log descriptions retain guest email after anonymization |
| FE-01 | React version mismatch between modules (18 vs 19) |
| FE-04 | Error boundary shows raw stack traces in production |
| FE-05 | Frontend build output into backend static resources |
| QUAL-01 | `changePassword` business logic in controller layer |
| QUAL-02 | Raw `Map<String, Object>` for config update request body |
| QUAL-03 | Duplicate `TEMP_PASS_CHARS` constant in two services |
| INFRA-01 | Full JDK used in control plane runtime image |
| INFRA-02 | No local dev `docker-compose.dev.yml` for standalone CRM |
| INFRA-03 | Control plane Dockerfile missing dependency caching layer |
| INFRA-04 | Provisioning timeout hardcoded at 5 minutes |

---

## 12. Recommended Improvement Plan

### Phase 1 — Security Hardening (Week 1)

1. **Fix SEC-01:** Gate webhook bypass behind `${RAZORPAY_BYPASS_SIGNATURE:false}` env var
2. **Fix SEC-02:** Replace `allowedOriginPatterns("*")` with explicit origins from config
3. **Fix SEC-03:** Remove `/**` permit-all rule; serve static files exclusively via Nginx
4. **Fix SEC-06:** Sanitize 500 error responses — log full detail server-side only
5. **Fix SEC-10:** Replace real-looking default API keys with empty string defaults
6. **Fix COMP-03:** Add `@JsonProperty(access = WRITE_ONLY)` to `User.password`

### Phase 2 — Business Logic & Config Fixes (Week 2)

1. **Fix BIZ-01:** Rename `calculateProRatedRent` to `calculateFlatRateRent`, fix Javadoc
2. **Fix BIZ-04:** Replace hardcoded `localhost` URL with `${app.frontend.url}` config property
3. **Fix PERF-03:** Set `open-in-view: false`
4. **Fix PERF-04:** Remove `show-sql: true` from control plane production config
5. **Fix PERF-05:** Migrate control plane to Flyway + `ddl-auto: validate`
6. **Fix QUAL-01:** Move `changePassword` logic from controller into `AuthService`
7. **Fix INFRA-01:** Switch control plane runtime to `eclipse-temurin:23-jre-alpine`
8. **Fix INFRA-03:** Add dependency-caching layer to control plane Dockerfile

### Phase 3 — Test Coverage (Week 3-4)

1. Write unit tests for `InvoiceService` billing calculations
2. Write unit tests for `AuthService` (login, refresh, forgot-password flows)
3. Write unit tests for `RazorpayWebhookServiceImpl.verifySignature`
4. Write unit tests for `GuestService.checkIn` (all 3 scenarios)
5. Write integration test for `SecurityConfig` authorization rules
6. Write integration test for invoice idempotency guard

### Phase 4 — Architecture Improvements (Future Sprint)

1. **Fix ARCH-01 + PERF-01:** Extract building hierarchy query into service with join fetch
2. **Fix ARCH-03:** Consolidate to single provisioning code path
3. **Fix ARCH-04:** Replace comma-delimited `branchId` with a proper `@ManyToMany` join table
4. **Fix BIZ-02:** Add ShedLock distributed lock to `MonthlyBillingScheduler`
5. **Fix FE-01:** Align both frontends to the same major React version
6. **Fix FE-02:** Implement authentication for the control plane admin UI

---

*Report generated from static analysis of all source files. No production systems were accessed.*
