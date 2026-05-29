# Codebase Registry & File Architecture
### Complete Mapping of the PG CRM Decoupled Directory Tree

This document acts as a granular registry of the entire codebase, detail by detail. It breaks down every file in the Spring Boot backend and React/Vite frontend, describing its structural role, internal logic, database relationships, and exact operational behaviors.

---

## 1. Backend Registry (`com.pgcrm`)

The backend codebase is written in Java 17 and built on Spring Boot 3.2.5. It follows a classic Controller-Service-Repository multi-layered architecture.

```
backend/src/main/java/com/pgcrm/
├── config/
├── controller/
├── entity/
│   └── enums/
├── repository/
├── scheduler/
├── security/
├── seeder/
└── service/
```

---

### 1.1 Config Package (`com.pgcrm.config`)

This package manages initialization parameters, JSON mapping overrides, and third-party credential injection.

#### `JacksonConfig.java`
* **Structural Purpose**: Overrides default JSON mapping parameters.
* **Internal Logic**: Configures Spring Boot's default `ObjectMapper` to serialize and deserialize Java 8 time APIs (e.g. `LocalDate`, `LocalDateTime`) using ISO-8601 formatting, avoiding runtime parsing failures on REST payloads.

#### `SystemConfigProperties.java`
* **Structural Purpose**: Direct mapping of the YAML-based whitelabel branding parameters.
* **Internal Logic**: Uses `@ConfigurationProperties(prefix = "pg.system")` to bind properties from `application.yml` and `tenant-config.yml` into a structured Java Bean. It handles configuration parameters for the system name, logo pathways, primary theme colors, default support contacts, and baseline prices.

#### `TwilioConfig.java`
* **Structural Purpose**: Inject credentials for Twilio SMS/WhatsApp services.
* **Internal Logic**: Standardizes credentials lookup from OS environmental parameters (Account SID, Auth Token, and Sender number) and initializes the Twilio client interface.

---

### 1.2 Controller Package (`com.pgcrm.controller`)

Controllers define HTTP endpoint routing and map payload structures. They are secured using Spring Security role-based filters.

```
HTTP Request ──► JwtAuthenticationFilter ──► Controller Endpoint ──► Service Layer
```

#### `AuthController.java`
* **Operational Scope**: User Authentication & Session Management.
* **Endpoints**:
  * `POST /api/auth/login`: Accepts credentials, validates via Spring Security, generates a JWT, and returns a JSON payload with user details and roles.
  * `POST /api/auth/change-password`: Modifies user password after validating the current password.
  * `GET /api/auth/me`: Authenticates active requests via JWT and yields full session details.
* **Access Control**: Public for login; authenticated for other endpoints.

#### `GlobalExceptionHandler.java`
* **Operational Scope**: Global Exception Interceptor.
* **Internal Logic**: Uses `@RestControllerAdvice` to intercept exceptions (e.g., `BadCredentialsException`, `EntityNotFoundException`, `IllegalArgumentException`) and return a standardized JSON error payload with specific HTTP status codes.

#### `GuestController.java`
* **Operational Scope**: Guest Portal REST Interface.
* **Endpoints**:
  * `GET /api/guest/dashboard`: Fetches check-in details, current bed assignments, and pending invoices.
  * `GET /api/guest/daily-log/month/{yearMonth}`: Fetches all meal and service logs for the guest within a target calendar month.
  * `PUT /api/guest/daily-log`: Updates future date meal preferences.
  * `GET /api/guest/addons`: Returns chronological add-on logs.
  * `POST /api/guest/maintenance`: Files a maintenance ticket.
* **Access Control**: Restricted to `GUEST` role.

#### `InventoryController.java`
* **Operational Scope**: Building Inventory Lookup.
* **Endpoints**:
  * `GET /api/inventory/buildings`: Returns the entire structural building tree (Floors, Blocks, Rooms, Beds).
* **Access Control**: Restricted to `PG_MANAGER` and `PG_OWNER` roles.

#### `PaymentController.java`
* **Operational Scope**: Transaction & Invoice Settlements.
* **Endpoints**:
  * `POST /api/payments/razorpay/order/{invoiceId}`: Generates a signed transaction order on Razorpay servers.
  * `POST /api/payments/razorpay/verify`: Validates HMAC signature hashes sent by Razorpay webhook callbacks.
  * `POST /api/payments/manual`: Submits cash/offline payment entries.
* **Access Control**: Razorpay orders are restricted to `GUEST`; manual record endpoints require `PG_MANAGER` or `PG_OWNER`.

#### `PgManagerController.java`
* **Operational Scope**: Operations & Guest Onboarding.
* **Endpoints**:
  * `GET /api/manager/dashboard`: Aggregates occupancy statistics, pending tickets, and income trends.
  * `GET /api/manager/assigned-buildings`: Returns buildings associated with the active manager.
  * `POST /api/manager/guests`: Onboards and checks in a new guest, assigning them a bed and seeding default preferences.
  * `PUT /api/manager/guests/{id}/checkout`: Handles exit dates and releases assigned beds back to `VACANT`.
  * `GET /api/manager/monthly-meals`: Returns the 31-day meal tracker matrix.
* **Access Control**: Restricted to `PG_MANAGER` and `PG_OWNER` roles.

#### `PgOwnerController.java`
* **Operational Scope**: Owner Control & Manager Registries.
* **Endpoints**:
  * `GET /api/owner/managers`: Lists active property managers.
  * `POST /api/owner/managers`: Registers a new manager.
  * `PUT /api/owner/managers/{id}`: Modifies manager profiles and building scope assignments.
  * `POST /api/owner/buildings`: Initiates building setups.
* **Access Control**: Restricted to `PG_OWNER` role.

#### `PricingController.java`
* **Operational Scope**: Pricing overrides.
* **Endpoints**:
  * `GET /api/manager/pricing`: Retreives default and override prices.
  * `PUT /api/manager/pricing/{key}`: Stores a pricing override.
  * `PUT /api/manager/pricing/sharing/{sharingType}/rent`: Overwrites rents for all rooms of a given sharing configuration (Single, Double, Triple, Quad).
* **Access Control**: Restricted to `PG_MANAGER` and `PG_OWNER` roles.

#### `ReportController.java`
* **Operational Scope**: Financial Analytics & Audits.
* **Endpoints**:
  * `GET /api/owner/reports/revenue`: Provides historical collections data.
  * `GET /api/owner/reports/occupancy`: Yields occupancy rates.
  * `GET /api/owner/reports/audit-logs`: Retrieves database transaction audits.
* **Access Control**: Restricted to `PG_OWNER` role.

#### `SystemConfigController.java`
* **Operational Scope**: Public system-branding metadata.
* **Endpoints**:
  * `GET /api/system/config`: Resolves name, logo path, and CSS primary theme colors.
* **Access Control**: Public.

---

### 1.3 Entity Package (`com.pgcrm.entity`)

Defines the relational mappings using JPA annotations, detailing table structures, relationships, and cascade operations.

```
[Building] ──(1:N, cascade)──► [Floor] ──(1:N, cascade)──► [Room] ──(1:N, cascade)──► [Bed]
                                  │
                              (optional)
                                  ▼
                               [Block] ──(1:N)──► [Room]
```

#### `User.java`
* **Purpose**: Base authentication entity.
* **Fields**: `id`, `email` (unique index), `password` (BCrypt hash), `fullName`, `phone`, `role` (Enum), `active` (boolean), `branchId` (comma-separated IDs), `mustChangePassword` (boolean).
* **Relationships**: Relates 1:1 with Guest entity (optional).

#### `Guest.java`
* **Purpose**: Tracks active residency and preferences.
* **Fields**: `id`, `checkInDate`, `expectedCheckOutDate`, `actualCheckOutDate`, `advanceDeposit`, `isVegPreference`, `breakfastPreference`, `lunchPreference`, `dinnerPreference`.
* **Relationships**:
  * `@OneToOne` with `User` (cascade = CascadeType.ALL).
  * `@ManyToOne` with `Bed` (keeps track of room assignments).

#### `Building.java`
* **Purpose**: Root inventory node representing properties.
* **Fields**: `id`, `name`, `address`, `createdAt`.
* **Relationships**: `@OneToMany` with `Floor` (cascade = CascadeType.ALL, orphanRemoval = true).

#### `Floor.java`
* **Purpose**: Property floors.
* **Fields**: `id`, `name` (e.g. "1st Floor"), `floorOrder`.
* **Relationships**:
  * `@ManyToOne` with `Building` (JoinColumn = `building_id`).
  * `@OneToMany` with `Block` (cascade = CascadeType.ALL, orphanRemoval = true).
  * `@OneToMany` with `Room` (cascade = CascadeType.ALL, orphanRemoval = true).

#### `Block.java`
* **Purpose**: Structural wings (e.g. "A-Wing").
* **Fields**: `id`, `name`.
* **Relationships**:
  * `@ManyToOne` with `Floor`.
  * `@OneToMany` with `Room` (cascade = CascadeType.ALL).

#### `Room.java`
* **Purpose**: Room configurations.
* **Fields**: `id`, `roomNumber`, `sharingType` (Single, Double, Triple, Quad), `baseRent`.
* **Relationships**:
  * `@ManyToOne` with `Floor`.
  * `@ManyToOne` with `Block` (optional).
  * `@OneToMany` with `Bed` (cascade = CascadeType.ALL, orphanRemoval = true).

#### `Bed.java`
* **Purpose**: Individual beds.
* **Fields**: `id`, `bedCode`, `status` (BedStatus Enum).
* **Relationships**: `@ManyToOne` with `Room`.

#### `DailyLog.java`
* **Purpose**: Tracks meal preferences and add-on consumptions day by day.
* **Fields**: `id`, `logDate`, `breakfast`, `lunch`, `dinner`, `omeletteCount`, `boiledEggCount`, `washingMachineCount`.
* **Relationships**: `@ManyToOne` with `Guest` (cascade = CascadeType.ALL).

#### `EbBill.java`
* **Purpose**: Master sub-meter utilities tracker.
* **Fields**: `id`, `billingMonth`, `startReading`, `endReading`, `ratePerUnit`, `splitMethod`.

#### `EbBillGuest.java`
* **Purpose**: Mapped electricity bill splits per guest.
* **Fields**: `id`, `personalReadingStart`, `personalReadingEnd`, `allocatedAmount`.
* **Relationships**: `@ManyToOne` with `Guest`, `@ManyToOne` with `EbBill`.

#### `Invoice.java`
* **Purpose**: Invoice document.
* **Fields**: `id`, `billingMonth` (e.g. "May 2026"), `totalAmount`, `status` (InvoiceStatus Enum), `dueDate`, `razorpayOrderId`, `razorpayPaymentId`.
* **Relationships**:
  * `@ManyToOne` with `Guest`.
  * `@OneToMany` with `InvoiceLineItem` (cascade = CascadeType.ALL, orphanRemoval = true).

#### `InvoiceLineItem.java`
* **Purpose**: Individual invoice line item charges.
* **Fields**: `id`, `description`, `amount`, `lineType` (LineType Enum).
* **Relationships**: `@ManyToOne` with `Invoice`.

#### `MaintenanceTicket.java`
* **Purpose**: Tracks issues submitted by guests.
* **Fields**: `id`, `title`, `description`, `priority` (Enum), `status` (Enum), `createdAt`.
* **Relationships**: `@ManyToOne` with `Guest`, `@ManyToOne` with `Building`.

#### `Notification.java`
* **Purpose**: System-wide notifications log.
* **Fields**: `id`, `title`, `message`, `channel` (WhatsApp/Mail), `sentAt`, `isRead`.
* **Relationships**: `@ManyToOne` with `User`.

#### `PricingConfig.java`
* **Purpose**: Property-specific price adjustments.
* **Fields**: `id`, `buildingId`, `priceKey` (e.g., "breakfast"), `value` (mapped to column `price_value` to bypass reserved SQL word conflicts).

---

### 1.4 Supporting Enums (`com.pgcrm.entity.enums`)

* `Role`: `PG_OWNER`, `PG_MANAGER`, `GUEST` (defines security scope).
* `BedStatus`: `VACANT`, `OCCUPIED`, `NOTICE` (drives interactive check-in flow).
* `InvoiceStatus`: `PENDING`, `PAID`, `OVERDUE` (manages settlement workflows).
* `InvoiceLineType`: `RENT`, `ELECTRICITY`, `ADDON_FOOD`, `ADDON_LAUNDRY` (categorizes charges).
* `EbSplitMethod`: `EQUAL_SPLIT`, `SUB_METER` (drives billing automation math).
* `MaintenanceStatus`: `OPEN`, `IN_PROGRESS`, `RESOLVED` (manages ticket progression).
* `MaintenancePriority`: `LOW`, `MEDIUM`, `HIGH` (flags priority level).
* `AuditAction`: `USER_LOGIN`, `GUEST_CHECKIN`, `INVOICE_GENERATED`, `BILL_PAID` (audits operations).
* `NotificationChannel`: `EMAIL`, `WHATSAPP` (delivery channel).
* `KycStatus`: `PENDING`, `VERIFIED`, `REJECTED` (compliance state).

---

### 1.5 Security Package (`com.pgcrm.security`)

Secures backend execution using stateless token filtering.

```
Client Request 
  │ (Header: Authorization: Bearer <JWT>)
  ▼
JwtAuthenticationFilter ──► Verify Signature & Claims ──► Set SecurityContextHolder ──► Controller
```

#### `JwtAuthenticationFilter.java`
* **Logic**: Intercepts requests, extracts the JWT bearer token, decrypts it, validates signatures against expiration limits, and resolves user details to the security context. Additionally reads the optional `X-Selected-Branch-Id` header, checks it against the manager's claims, and injects it into the request attributes context for scoped querying.

#### `JwtUtil.java`
* **Logic**: Employs the `HS256` signature algorithm to sign and generate JWT string tokens containing claims for active user IDs, email handles, role lists, and associated building IDs.

#### `SecurityConfig.java`
* **Logic**: Sets up HTTP endpoint authorization mapping, configures CORS origins to allow client connections, disables CSRF protection (since JWT sessions are stateless), and inserts `JwtAuthenticationFilter` before the default Spring username/password filter.

#### `UserDetailsServiceImpl.java`
* **Logic**: Bridges database user configurations with Spring Security, loading profiles using query indexes on emails.

---

### 1.6 Seeder Package (`com.pgcrm.seeder`)

#### `DataSeeder.java`
* **Purpose**: Auto-populates sample data on clean startups.
* **Internal Logic**: Looks up `./pg-layout.yml` from classpath structures to build buildings, floors, and rooms. Adds users, initializes logs, generates invoices, and handles pre-seeding logic.

---

### 1.7 Service Package (`com.pgcrm.service`)

Handles business computations, integrations, and reports.

#### `AuditService.java`
* **Logic**: Persists operational events to the `audit_logs` table.

#### `AuthService.java`
* **Logic**: Handles password changes and credentials lookups.

#### `BuildingSetupService.java`
* **Logic**: Transactionally processes structural setups.
* **Database Operations**: Automatically generates the corresponding layout hierarchy and registers rooms and beds in the database.

#### `DailyLogService.java`
* **Logic**: Records guest meal options.
* **Lockout Rules**: Restricts Breakfast/Lunch modifications after 10 PM on the previous day and Dinner changes after 2 PM on the same day.
* **Check-in Seeding Fallback**:
  * If a guest has no log record for a selected date, the system dynamically checks the guest's default check-in preferences (`breakfastPreference`, `lunchPreference`, `dinnerPreference`, `isVegPreference`) and yields those values rather than defaulting to opt-out.

#### `EbBillService.java`
* **Logic**: Handles utility split calculations.
* **Mathematical Split Equations**:
  * **Equal Split**: Matches all guests checked-in during the billing month to a building and calculates:
    $$\text{Per Guest Bill} = \frac{(\text{End Reading} - \text{Start Reading}) \times \text{Rate Per Unit}}{\text{Count of Active Guests}}$$
  * **Sub-meter Split**: Calculates usage using individual sub-meters:
    $$\text{Per Guest Bill} = (\text{Guest End Reading} - \text{Guest Start Reading}) \times \text{Rate Per Unit}$$

#### `EmailService.java`
* **Logic**: Resolves properties like `fromName` from system config configuration details and compiles HTML notification templates using Thymeleaf.

#### `GuestService.java`
* **Logic**: Updates details and queries profile listings.

#### `InvoicePdfService.java`
* **Logic**: Utilizes PDF generator engines (such as OpenPDF) to compile official tax invoices containing logo headers, guest metadata, transaction summaries, and payment status stamps.

#### `InvoiceService.java`
* **Logic**: Computes and previews invoices for selected months.
* **Rent Pro-ration Mathematical Logic**:
  * Calculates proration when a guest stays for a partial month (e.g., checks in on the 10th):
    $$\text{Daily Rent} = \frac{\text{Monthly Base Rent}}{\text{Days in Month}}$$
    $$\text{Active Days} = \text{Days in Month} - \text{Check-in Day} + 1$$
    $$\text{Prorated Rent} = \text{Daily Rent} \times \text{Active Days}$$
  * Similar mathematical splits apply if check-outs happen mid-month.

#### `NotificationService.java`
* **Logic**: Integrates communication channels (SMTP, Twilio WhatsApp) to dispatch alerts.

#### `PaymentService.java`
* **Logic**: Manages online transactions.
* **HMAC Verification Pipeline**: Uses the SHA-256 HMAC algorithm to verify payment signatures:
  $$\text{Expected Signature} = \text{HMAC-SHA256}(\text{razorpay\_order\_id} + "|" + \text{razorpay\_payment\_id}, \text{razorpay\_key\_secret})$$

#### `PricingService.java`
* **Logic**: Merges system configurations with database overrides.

#### `ReportService.java`
* **Logic**: Computes database metrics to build historical trends.

#### `SettlementService.java`
* **Logic**: Handles guest exits, computes deposit deductions, and releases beds back to `VACANT`.

---

### 1.8 Scheduler Package (`com.pgcrm.scheduler`)

Executes background operations at configured times.

#### `MonthlyBillingScheduler.java`
* **Cron Expression**: `0 0 0 1 * *` (1st of the month at midnight).
* **Behavior**: Runs invoice generation for all active guests. Skips buildings where the auto-billing scheduler toggle is turned off.

#### `PaymentReminderScheduler.java`
* **Cron Expression**: `0 0 9 * * *` (Daily at 9:00 AM).
* **Behavior**: Scans invoices in `PENDING` state and sends out payment reminders.

---

## 2. Frontend Registry (`frontend/src`)

The client interface is built as a single-page React application powered by Vite and Tailwind CSS.

---

### 2.1 Contexts & Client Infrastructure

#### `App.jsx`
* **Purpose**: Directs client routing and role authorization blocks.
* **Details**: Wraps paths (e.g., `/settings`, `/owner/dashboard`, `/manager/pricing`) inside custom Route Guards (`PrivateRoute`) which validate JWT auth states and direct users to correct layout templates based on their roles.

#### `main.jsx`
* **Purpose**: Entry point. Binds and mounts React components to the root DOM node.

#### `api/index.js`
* **Purpose**: Axios REST API configuration.
* **Details**: Intercepts requests to append `Authorization: Bearer <Token>` and `X-Selected-Branch-Id` headers.

#### `context/AuthContext.jsx`
* **Purpose**: Manages auth status.
* **Details**: Stores active login details, saves JWTs, and handles sign-out operations.

#### `context/SystemConfigContext.jsx`
* **Purpose**: Dynamically stores whitelabel configurations.
* **Details**: Fetches branding styles and exposes them to the app.

---

### 2.2 Layout Engine (`src/components`)

#### `AppLayout.jsx`
* **Purpose**: Standard dashboard template. Implements a responsive 3-pane viewport containing a sticky navigation sidebar, header banner, and content view.

#### `Sidebar.jsx`
* **Purpose**: Dynamically renders links and icons based on roles.

#### `TopHeader.jsx`
* **Purpose**: Sticky header. Extracts initials for user avatars, displays property indicators, and provides branch dropdown switchers for multi-building managers.

---

### 2.3 Pages Directory (`src/pages`)

#### `Login.jsx`
* **Purpose**: Login portal. Features a glassmorphic login card and dynamically displays brand names and logos.

#### `Settings.jsx`
* **Purpose**: User preferences and security configuration page.

#### `ChangePassword.jsx`
* **Purpose**: Handles password change requests.

#### `AuditLog.jsx`
* **Purpose**: Tracks system operations with pagination, filters, and CSV exports.

#### `Reports.jsx`
* **Purpose**: Financial dashboard displaying occupancy indicators and collections data.

#### `guest/DailyLog.jsx`
* **Purpose**: Guest Meal Planner view.
* **Details**: Displays a compact monthly calendar view with color-coded dots showing Breakfast (amber), Lunch (emerald), and Dinner (blue) opt-ins. Features lockout verification to prevent changes after cutoff times.

#### `guest/GuestDashboard.jsx`
* **Purpose**: Resident home view. Includes check-in detail badges, service summary cards, a compact active logs feed, and spending charts.

#### `guest/GuestInvoices.jsx`
* **Purpose**: Lists invoices and provides a Razorpay payment modal integration.

#### `manager/ManagerDashboard.jsx`
* **Purpose**: Operational dashboard displaying occupancy graphs, pending tasks, and collections data.

#### `manager/ManagerEbBill.jsx`
* **Purpose**: Sub-meter input page for managers. Handles equal split and direct sub-meter billing.

#### `manager/ManagerGuestAddons.jsx`
* **Purpose**: Tracks guest daily add-on orders (omelettes, laundry, eggs) via inline tables and includes a monthly scrollable daily roster matrix.

#### `manager/ManagerGuests.jsx`
* **Purpose**: CRUD interface for guest check-ins, KYC tracking, and checkout settlements.

#### `manager/ManagerInvoiceGenerator.jsx`
* **Purpose**: Monthly billing dashboard. Allows manual invoice generation, previews, and dispatch controls.

#### `manager/ManagerMaintenance.jsx`
* **Purpose**: Lists and tracks tickets submitted by guests.

#### `manager/ManagerPricing.jsx`
* **Purpose**: Pricing override portal. Allows editing rents by sharing type building-wide.

#### `owner/OwnerBuildingCreator.jsx`
* **Purpose**: Step-by-step wizard for configuring properties. Prompts for floor, block, and room configuration details.

#### `owner/OwnerDashboard.jsx`
* **Purpose**: Manager registration page. Allows assigning managers to multiple buildings via checkbox toggles.
