# PG CRM — Complete REST API Reference
### Authoritative Endpoint Documentation for `[PG-CORE]` and `[CONTROL-PLANE]`

> [!NOTE]
> All PG-CORE endpoints are served from `http://localhost:8080` in development.
> All CONTROL-PLANE endpoints are served from `http://localhost:8090` in development.
> All requests that require authentication must include the header: `Authorization: Bearer <accessToken>`

---

## Authentication Flow

### Token Lifecycle

| Token | Storage | Expiry | Purpose |
| :--- | :--- | :--- | :--- |
| Access Token | `sessionStorage.accessToken` | 15 minutes | Sent as `Authorization: Bearer` header on every API call |
| Refresh Token | `sessionStorage.refreshToken` | 7 days | Used to silently obtain a new access token on 401 |

The frontend Axios client (`src/api/index.js`) automatically handles 401 errors by calling `/api/auth/refresh` and retrying the original request.

---

## Part 1: PG-CORE REST API (Port 8080)

### 1. Authentication Endpoints (`/api/auth`)

#### `POST /api/auth/login`
- **Access**: Public
- **Request Body**:
  ```json
  { "email": "user@example.com", "password": "password" }
  ```
- **Response** `200 OK`:
  ```json
  {
    "accessToken": "eyJ...",
    "refreshToken": "eyJ...",
    "userId": "uuid",
    "email": "user@example.com",
    "role": "PG_MANAGER",
    "mustChangePassword": false
  }
  ```
- **Error**: `401 Unauthorized` if credentials are invalid or account is inactive.

---

#### `POST /api/auth/refresh`
- **Access**: Public
- **Request Body**:
  ```json
  { "refreshToken": "eyJ..." }
  ```
- **Response** `200 OK`:
  ```json
  { "accessToken": "eyJ..." }
  ```

---

#### `POST /api/auth/logout`
- **Access**: Authenticated
- **Response** `200 OK`: Session token invalidated server-side.

---

#### `POST /api/auth/forgot-password`
- **Access**: Public
- **Request Body**:
  ```json
  { "email": "user@example.com" }
  ```
- **Response** `200 OK`: OTP or reset link sent via email.

---

#### `POST /api/auth/change-password`
- **Access**: Authenticated (any role)
- **Request Body**:
  ```json
  { "currentPassword": "...", "newPassword": "..." }
  ```
- **Response** `200 OK`.

---

### 2. Public Configuration (`/api/config/public`)

#### `GET /api/config/public`
- **Access**: Public (used by frontend at startup)
- **Response** `200 OK`:
  ```json
  {
    "pgName": "My PG Name",
    "pgShortTitle": "MPG",
    "primaryColor": "#2563eb"
  }
  ```
- **Purpose**: Powers the dynamic branding system. Frontend applies `primaryColor` as a CSS custom property `--brand-primary`.

---

### 3. Manager Endpoints (`/api/manager`)
> **Access**: `PG_MANAGER`, `PG_OWNER`, `SUPER_ADMIN`

#### `GET /api/manager/assigned-buildings`
Returns the list of buildings assigned to the authenticated manager (or all buildings if PG_OWNER).

#### `GET /api/manager/dashboard`
Returns dashboard statistics: occupancy rate, pending invoices, active guests count.

#### `GET /api/manager/guests`
Returns all active guests, optionally filtered by `buildingId` query parameter.
- **Query Params**: `?buildingId={id}`

#### `POST /api/manager/guests`
Check in a new guest.
- **Request Body** (key fields):
  ```json
  {
    "fullName": "Ravi Kumar",
    "email": "ravi@example.com",
    "phone": "9876543210",
    "targetBedIds": ["bed-uuid-1"],
    "isBookEntireRoom": false,
    "monthlyRent": 9000,
    "depositAmount": 18000,
    "checkInDate": "2026-08-01",
    "mealPlanEnabled": true,
    "breakfastPreference": true,
    "lunchPreference": false,
    "dinnerPreference": true
  }
  ```

#### `PUT /api/manager/guests/{guestId}`
Update guest profile details (contact info, rent override, KYC status).

#### `POST /api/manager/guests/{guestId}/checkout`
Process guest checkout. Triggers settlement invoice and bed vacancy.
- **Request Body**: `{ "checkoutDate": "2026-08-12", "reason": "Shifting out" }`

#### `POST /api/manager/guests/{guestId}/notice`
Issue checkout notice. Sets `noticeDate` and computes `exitDate`.
- **Request Body**: `{ "noticeDate": "2026-08-12" }`

#### `GET /api/manager/eb-bills`
List all electricity bills, optionally filtered by `buildingId`.

#### `POST /api/manager/eb-bills`
Record a new electricity bill for a block.
- **Request Body**:
  ```json
  {
    "blockId": "block-uuid",
    "billingPeriodStart": "2026-07-01",
    "billingPeriodEnd": "2026-07-31",
    "totalAmount": 12000,
    "splitMethod": "EQUAL_SPLIT"
  }
  ```

#### `GET /api/manager/invoices`
List all invoices, filterable by `guestId`, `status`, `month`, `year`.

#### `POST /api/manager/invoices/generate`
Manually trigger monthly invoice generation for a specific month/year.
- **Request Body**: `{ "month": 7, "year": 2026, "buildingId": "uuid" }`

#### `PUT /api/manager/invoices/{invoiceId}/mark-paid`
Mark an invoice as paid (cash or manager-verified).

#### `GET /api/manager/daily-logs`
Retrieve daily meal/addon logs for a date range and building.

#### `PUT /api/manager/daily-logs/{logId}`
Update a guest's daily log entry (meal preferences, laundry, etc.).

#### `GET /api/manager/maintenance`
List all maintenance tickets, filterable by status and building.

#### `PUT /api/manager/maintenance/{ticketId}`
Update a maintenance ticket status.

#### `GET /api/manager/notifications`
List unread in-app notifications for the authenticated user.

---

### 4. Owner Endpoints (`/api/owner`)
> **Access**: `PG_OWNER`, `SUPER_ADMIN`

#### `GET /api/owner/buildings`
List all buildings with their floors, rooms, and beds.

#### `POST /api/owner/buildings`
Create a new building and its complete spatial structure in one payload (used by `OwnerBuildingCreator.jsx` wizard).

#### `PUT /api/owner/buildings/{buildingId}`
Update building name/address.

#### `GET /api/owner/users`
List all user accounts in the system.

#### `POST /api/owner/users`
Create a new `PG_MANAGER` account.

#### `PUT /api/owner/users/{userId}/role`
Update user role assignment.

---

### 5. Guest Endpoints (`/api/guest`)
> **Access**: `GUEST` role (and above)

#### `GET /api/guest/dashboard`
Returns authenticated guest's profile, bed allocation, occupancy details, and current balance.

#### `GET /api/guest/invoices`
Returns all invoices for the authenticated guest.

#### `GET /api/guest/daily-log`
Returns daily log records for the authenticated guest.

#### `PUT /api/guest/daily-log/{logId}`
Guest updates their own daily meal preference (subject to cutoff times).

#### `GET /api/guest/maintenance`
Returns maintenance tickets submitted by the authenticated guest.

#### `POST /api/guest/maintenance`
Submit a new maintenance request.
- **Request Body**:
  ```json
  {
    "title": "Broken AC",
    "description": "AC not cooling properly",
    "priority": "HIGH"
  }
  ```

---

### 6. Super Admin Endpoints (`/api/super-admin`)
> **Access**: `SUPER_ADMIN` only

#### `GET /api/super-admin/summary`
Returns platform-wide statistics: total buildings, guests, active guests, revenue.

#### `POST /api/super-admin/users/provision-owner`
Provision a new `PG_OWNER` account with default credentials.

#### `GET /api/super-admin/audit-logs`
Returns full security audit log trail.

#### `POST /api/super-admin/financial-override`
Apply rent waiver or deposit adjustment (requires `allowSuperAdminAccess = true` on the target owner).

---

### 7. Reports Endpoints (`/api/reports`)
> **Access**: `PG_OWNER`, `PG_MANAGER`, `SUPER_ADMIN`

#### `GET /api/reports/revenue`
Revenue summary for a building over a date range.
- **Query Params**: `?buildingId={id}&from=YYYY-MM-DD&to=YYYY-MM-DD`

#### `GET /api/reports/occupancy`
Occupancy rate report by building and floor.

#### `GET /api/reports/arrears`
Pending rent arrears report across all active guests.

---

### 8. Payment Endpoints (`/api/payments`)
> **Access**: `PG_MANAGER`, `PG_OWNER`, `SUPER_ADMIN`

#### `POST /api/payments/initiate`
Create a Razorpay order for a guest invoice payment.
- **Request Body**: `{ "invoiceId": "uuid" }`
- **Response**: Razorpay order details for frontend checkout.

#### `POST /api/payments/verify`
Verify Razorpay payment signature and mark invoice as PAID.
- **Request Body**: `{ "invoiceId": "uuid", "razorpayOrderId": "...", "razorpayPaymentId": "...", "razorpaySignature": "..." }`

---

### 9. Webhook Endpoints (`/api/webhooks`)
> **Access**: Public (Razorpay)

#### `POST /api/webhooks/razorpay`
Razorpay webhook receiver. Validates `X-Razorpay-Signature` header and updates invoice status.

---

### 10. Import Endpoints (`/api/import`)
> **Access**: `PG_OWNER`, `SUPER_ADMIN`

#### `POST /api/import/guests`
Bulk import guests from Excel/CSV file.
- **Content-Type**: `multipart/form-data`
- **Field**: `file` (Excel or CSV)
- **Response**: `ImportSummaryDTO` with success/failure counts.

---

## Part 2: CONTROL-PLANE REST API (Port 8090)

### 1. Public Checkout (`/api/public/checkout`)

#### `POST /api/public/checkout/initiate-order`
Initiate a new client onboarding order (Razorpay Setup Fee).
- **Access**: Public
- **Request Body**:
  ```json
  {
    "pgBrandName": "Stanza PG",
    "domainName": "stanza",
    "ownerName": "Ramesh Iyer",
    "ownerEmail": "ramesh@stanza.in",
    "ownerPhone": "9876543210"
  }
  ```
- **Response**: Razorpay order details + `registrationId`.

#### `POST /api/public/checkout/webhook/reconcile`
Razorpay payment webhook for setup fee reconciliation. Triggers automated tenant provisioning.
- **Access**: Public
- **Headers**: `X-Razorpay-Signature: <hmac-sha256-hex>`
- **Body**: Raw Razorpay webhook JSON payload.

---

### 2. Tenant Onboarding (`/api/onboarding`)

#### `POST /api/onboarding/signup`
Legacy client registration endpoint.

#### `GET /api/onboarding/check-domain`
Check if a subdomain is available before checkout.
- **Query Params**: `?domain=stanza`

---

### 3. Admin Dashboard (`/api/admin`)
> **Access**: Platform Admin (internal use)

#### `GET /api/admin/dashboard`
Returns total clients, active tenants, suspended tenants, monthly revenue.

#### `GET /api/admin/tenants`
List all tenant instances with their provisioning status and subscription info.

#### `PUT /api/admin/tenants/{tenantId}/suspend`
Manually suspend a tenant instance.

#### `PUT /api/admin/tenants/{tenantId}/activate`
Re-activate a suspended tenant instance.

---

### 4. Billing (`/api/billing`)
> **Access**: Clients (authenticated via Control Plane credentials)

#### `POST /api/billing/renew-amc`
Initiate AMC renewal Razorpay order.
- **Request Body**: `{ "clientId": "uuid" }`
- **Response**: Razorpay order for ₹35,000 (AMC fee).

#### `POST /api/billing/verify-amc`
Verify AMC renewal payment and extend subscription by 1 year.
- **Request Body**: `{ "clientId": "uuid", "razorpayOrderId": "...", "razorpayPaymentId": "...", "razorpaySignature": "..." }`

---

## Common Response Codes

| HTTP Code | Meaning |
| :--- | :--- |
| `200 OK` | Success |
| `201 Created` | Resource created successfully |
| `400 Bad Request` | Validation error or bad input |
| `401 Unauthorized` | Missing/expired JWT token |
| `403 Forbidden` | Authenticated but insufficient role |
| `404 Not Found` | Resource does not exist (`ResourceNotFoundException`) |
| `409 Conflict` | Duplicate resource (e.g., bed already occupied) |
| `500 Internal Server Error` | Unexpected server-side error |

---

## Header Reference

| Header | Where Used | Value |
| :--- | :--- | :--- |
| `Authorization` | All authenticated requests | `Bearer <accessToken>` |
| `X-Selected-Branch-Id` | Manager requests with branch context | `<building-uuid>` |
| `Content-Type` | POST/PUT requests | `application/json` or `multipart/form-data` |
| `X-Razorpay-Signature` | Razorpay webhook endpoints | HMAC-SHA256 hex digest |
