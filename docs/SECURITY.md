# PG CRM — Security Policy & Threat Model
### Mandatory Security Guidelines for the PG CRM Ecosystem

> [!CAUTION]
> This document defines the security posture of the PG CRM platform. ALL contributors and AI agents
> MUST read and adhere to every policy stated here. Security violations are critical failures.

---

## 1. Security Architecture Overview

The PG CRM platform enforces a **defense-in-depth** security model with multiple independent layers:

```
[Client Browser]
    ↓ HTTPS (TLS in production)
[Nginx Reverse Proxy] — Terminates TLS, sets upstream headers
    ↓
[React Frontend] — SessionStorage token management
    ↓ Authorization: Bearer <JWT>
[Spring Security Filter Chain] — JWT validation, CORS enforcement
    ↓
[Role-Based Access Control] — Per-endpoint permission matrix
    ↓
[Service Layer] — Owner Permission Guard, business rule validation
    ↓
[PostgreSQL] — Physical database isolation per tenant
```

---

## 2. Authentication & Token Security

### 2.1 JWT Configuration

| Parameter | Value | Notes |
| :--- | :--- | :--- |
| Algorithm | `HS256` | HMAC-SHA256 |
| Access Token Expiry | 15 minutes | Short-lived to minimize exposure |
| Refresh Token Expiry | 7 days | Stored in database for revocation |
| Secret Key Minimum Length | 256 bits | Enforced at startup |

### 2.2 Token Storage

- **Access tokens** are stored in `sessionStorage` — cleared on browser tab close.
- **Refresh tokens** are stored in `sessionStorage` — NOT `localStorage` (reduces XSS persistence risk).
- Tokens are **never stored in cookies** (avoids CSRF attack vector).

### 2.3 Silent Token Refresh

The Axios interceptor in `src/api/index.js` automatically handles 401 responses:
1. Calls `POST /api/auth/refresh` with the stored refresh token.
2. If refresh succeeds, retries the original request.
3. If refresh fails, clears `sessionStorage` and redirects to `/login`.

### 2.4 Password Security

- Passwords are hashed with **BCrypt** (`BCryptPasswordEncoder`).
- New accounts receive a random temporary password — users are forced to change on first login (`mustChangePassword` flag).
- Password reset uses OTP-based email verification.

---

## 3. API Security Rules

### 3.1 CORS Policy (SEC-02)

**Rule**: Never use `allowedOriginPatterns("*")` with `allowCredentials(true)`.

The correct configuration in `SecurityConfig.java`:
```java
// ✅ CORRECT: Explicit origins from config
List<String> origins = Arrays.asList(corsAllowedOriginsRaw.split(","));
config.setAllowedOrigins(origins);
config.setAllowCredentials(true);
```

Production setup: Set `APP_CORS_ALLOWED_ORIGINS=https://app.yourpg.com,https://admin.yourpg.com` in environment.

### 3.2 URL Pattern Security (SEC-03)

**Rule**: Never add a wildcard `permitAll()` on `/**`.

Every unmatched URL must require authentication. The endpoint security matrix is:

| Pattern | Policy |
| :--- | :--- |
| `/api/auth/**` (except change-password) | `permitAll()` |
| `/api/webhooks/**` | `permitAll()` (Razorpay HMAC-verified) |
| `/api/config/public` | `permitAll()` (public branding) |
| `/api/super-admin/**` | `hasRole("SUPER_ADMIN")` |
| `/api/owner/**` | `hasAnyRole("PG_OWNER", "SUPER_ADMIN")` |
| `/api/manager/**` | `hasAnyRole("PG_MANAGER", "PG_OWNER", "SUPER_ADMIN")` |
| `/api/guest/**` | `hasAnyRole("GUEST", "PG_MANAGER", "PG_OWNER", "SUPER_ADMIN")` |
| All others | `.anyRequest().authenticated()` |

### 3.3 Credential Management (SEC-01, SEC-10)

**Rules**:
- **Never** hardcode API keys, passwords, or secrets in source code.
- **Never** commit real credentials to Git — use `.env` files excluded by `.gitignore`.
- **Never** log sensitive values (passwords, JWT tokens, API keys, phone numbers).

Environment variable pattern (from `application.yml`):
```yaml
razorpay:
  key-id: ${RAZORPAY_KEY_ID:}      # Empty default — must be set in env
  key-secret: ${RAZORPAY_KEY_SECRET:}
```

---

## 4. Razorpay Webhook Security (PG-Core Payments)

### 4.1 HMAC-SHA256 Signature Verification

All incoming Razorpay webhooks to `/api/webhooks/razorpay` MUST be cryptographically verified before processing:

$$\text{Expected Signature} = \text{HMAC-SHA256}(\text{rawPayloadBody}, \text{webhookSecret})$$

Implementation in `WebhookController.java` / `PaymentService.java`:
```java
Mac mac = Mac.getInstance("HmacSHA256");
mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
String expectedSignature = HexFormat.of().formatHex(hash);
```

### 4.2 Sandbox Bypass

The `razorpay.bypass-signature-for-sandbox` flag allows skipping signature verification in development/testing:

> [!CAUTION]
> `RAZORPAY_BYPASS_SIGNATURE=false` is **mandatory in production**.
> Setting this to `true` in production allows unauthenticated tenant provisioning.

### 4.3 Razorpay Guest Invoice Payments (PG-CORE)

Guest invoice payments also use Razorpay. The payment verification flow:
1. Frontend creates order via `POST /api/payments/initiate`.
2. Razorpay Checkout collects payment.
3. Frontend sends payment details to `POST /api/payments/verify`.
4. Backend verifies: `HMAC-SHA256(orderId + "|" + paymentId, keySecret)` must match `razorpaySignature`.

---

## 5. Data Privacy & PII Compliance

### 5.1 PII Fields in Guest Records

The following fields are considered Personally Identifiable Information (PII):
- `Guest.fullName`
- `Guest.phone`
- `User.email`
- `User.password` (BCrypt hash)

### 5.2 Automated PII Anonymization

`DataRetentionScheduler.java` runs daily at 3:00 AM to anonymize checked-out guests:
- **Trigger Condition**: Guest checked out >365 days ago.
- **Anonymization**: `fullName` → `ANON_<uuid>`, `phone` → `0000000000`.
- **Preserved**: All financial records, invoices, audit logs, and transactional history.

**Rule**: NEVER delete guest financial records. NEVER prevent the anonymization scheduler from running.

### 5.3 Owner Permission Guard

Super Admin (`SUPER_ADMIN`) operations on `PG_OWNER` data require explicit authorization:
- `User.allowSuperAdminAccess = true` must be set by the PG_OWNER via their dashboard.
- Financial overrides (rent waivers, deposit adjustments) throw `403` if this flag is `false`.
- Role modifications on PG_OWNER accounts require this flag.

---

## 6. Database Security

### 6.1 Physical Tenant Isolation

Each client PG-CORE instance connects to its own isolated PostgreSQL database:
- Database names follow pattern: `pgcrm_<sanitized_subdomain>`.
- No shared tables, schemas, or sequences between tenants.
- Database credentials per tenant are unique and stored only in their isolated `.env` file.

### 6.2 Soft Delete — No Physical Data Removal

Guest and Invoice records are **soft-deleted** only:
```sql
UPDATE guests SET is_deleted = true WHERE id = ?;
```
This preserves:
- Billing history for financial audits.
- Audit trail for security investigations.
- Statistical data for occupancy reports.

**NEVER run `DELETE FROM guests` or `DELETE FROM invoices`.**

### 6.3 Flyway Migration Security

- Existing migration files are checksummed. Tampering causes startup failure.
- Production databases should have the Flyway user with limited permissions (only DDL for migrations, not DROP DATABASE).

---

## 7. Infrastructure Security (Production)

### 7.1 Nginx Configuration

- Nginx terminates TLS (SSL certificates via Let's Encrypt).
- Host header routing directs client subdomains to the correct backend port.
- Direct backend port access should be firewall-blocked; all traffic goes through Nginx.

### 7.2 Docker Security

- The PG-Core stack runs in Docker using `docker compose` with three services: `pgcore-db`, `pgcore-backend`, `pgcore-frontend`.
- Do **not** mount `/var/run/docker.sock` into application containers — it is not required.
- Use `docker compose` restart policies (`unless-stopped`) for resilience.

### 7.3 Environment Files

- The production `.env` file lives alongside `docker-compose.yml` at the project root.
- It contains database credentials, JWT secrets, and Razorpay API keys — **never commit it**.
- Set appropriate file permissions: `chmod 600 .env` (Linux) or restrict via NTFS ACLs on Windows.

---

## 8. Security Incident Response Checklist

### 8.1 Compromised JWT Secret

1. Generate a new JWT secret (minimum 256-bit random string).
2. Update `JWT_SECRET` environment variable in the affected tenant's `.env`.
3. Restart the tenant's Spring Boot container.
4. All existing sessions are immediately invalidated — users must re-login.

### 8.2 Compromised Razorpay Keys

1. Revoke the compromised keys in the Razorpay Dashboard immediately.
2. Generate new API keys.
3. Update `RAZORPAY_KEY_ID` and `RAZORPAY_KEY_SECRET` in the affected environment.
4. Update `RAZORPAY_WEBHOOK_SECRET` if the webhook secret was also compromised.
5. Restart the affected service.

### 8.3 Unauthorized Data Access Detected

1. Check `audit_logs` table for the affected `userId` and time range.
2. Disable the compromised user account: `UPDATE users SET active = false WHERE id = ?`.
3. Revoke all active sessions by updating the user's password (forces re-login).
4. Review all `AuditLog` entries with actions related to financial overrides and role changes.

---

## 9. Security Anti-Patterns (Never Do)

| # | Anti-Pattern | Risk |
| :--- | :--- | :--- |
| SEC-01 | Hardcoding secrets/passwords in code | Credential leak via source control |
| SEC-02 | CORS wildcard + `allowCredentials(true)` | Cross-origin credential theft |
| SEC-03 | `permitAll()` wildcard on `/**` | Unauthenticated access to protected APIs |
| SEC-04 | Logging PII or credentials | Data breach via log aggregators |
| SEC-05 | Disabling Razorpay HMAC verification in prod | Fraudulent tenant provisioning |
| SEC-06 | `localStorage` for JWT tokens | XSS token theft persistence |
| SEC-07 | `ddl-auto: create-drop` in prod | Catastrophic data loss |
| SEC-08 | Docker socket exposed externally | Host system takeover |
| SEC-09 | Running DB migrations with DROP privileges | Data destruction risk |
| SEC-10 | Real credentials as YAML defaults | Secrets committed to Git |
