# Codebase Registry & File Architecture
### Complete Mapping of the PG CRM Decoupled Directory Tree

This document acts as a granular registry of the entire codebase, detail by detail. It breaks down every file in the Spring Boot backend, the React/Vite frontend, and the production infrastructure deployment directories, detailing their structural roles, why they were created, internal logic/dependencies, and database/UI interactions.

## Monorepo Directory Layout

The repository is structured to accurately map the files visible in our remote repository:

```
   ├── core-pg-crm/
   ├── docs/
   ├── master-control-plane/
   ├── scripts/
   ├── .env.example
   ├── .gitignore
   ├── MEMORY.md
   ├── README.md
   ├── control_plane_architecture.md
   ├── docker-compose.yml
   ├── sop.md
   └── start_project.bat
```

---

## 1. Role-Based Access Control (RBAC) & Mapping

The application enforces a strict four-tier role hierarchy that separates end-user resident portal access from administrative and system operations. The backend enums map directly to this hierarchy as follows:

| Hierarchy Tier | Platform Role | Database Role Enum | Access Scope & Responsibilities |
| :--- | :--- | :--- | :--- |
| **Tier 1** | **Guest** | `GUEST` | Resident Portal. Access is limited to their own check-in details, meal calendars, maintenance requests, and payment logs. |
| **Tier 2** | **Manager / Branch Admin** | `PG_MANAGER` | Property Administration. Operates assigned buildings, registers checked-in guests, logs meals and add-ons, generates monthly invoices, and resolves tickets. |
| **Tier 3** | **Owner / Super Admin** | `PG_OWNER` | Global Administration. Manages building setup, registers Manager / Branch Admin accounts, allocates building permissions, and views revenue reports. |
| **Tier 4** | **Super Super Admin (Software Provider)** | *(System Level)* | Infrastructure Operations. Configures host variables, deploys docker-compose services, updates whitelabel config parameters, and manages SSL certificates. |

---

## 2. Core PG CRM - Backend Registry (`core-pg-crm/backend`)

The backend codebase is written in Java 23 and built on Spring Boot 3.2.5. It follows a Controller-Service-Repository multi-layered architecture.

### 2.1 Config Package (`com.pgcrm.config`)

#### `JacksonConfig.java`
* **Structural Purpose**: Overrides default JSON mapping parameters.
* **Why Created**: Solves JSON serialization issues for Java 8 date/time types (`LocalDate`, `LocalDateTime`) when communicating with the React client.
* **Internal Logic & Dependencies**: Configures the Spring `ObjectMapper` with the `JavaTimeModule`.
* **Database/UI Interaction**: Formats entity date fields into standard ISO-8601 strings for React consumption.

#### `SystemConfigProperties.java`
* **Structural Purpose**: Externalized property binding model.
* **Why Created**: Enables white-label branding customization (`name`, `short-title`, `primary-color`) dynamically.
* **Internal Logic & Dependencies**: Uses `@ConfigurationProperties(prefix = "pg.system")` to parse values from host YAML configs.
* **Database/UI Interaction**: Injects branding attributes into the public configuration payload returned to the UI.

#### `TwilioConfig.java`
* **Structural Purpose**: Twilio integration settings mapping.
* **Why Created**: Allows SMS/WhatsApp notification delivery.
* **Internal Logic & Dependencies**: Binds environment account SIDs, auth tokens, and source numbers.
* **Database/UI Interaction**: Utilized by the notification engine to trigger external mobile logs.

---

### 2.2 DTO Package (`com.pgcrm.dto`)

* **`AuthRequest.java`**: Binds incoming user credentials (`email`, `password`) securely. Solves entities exposure during auth and triggers validation check constraints.
* **`AuthResponse.java`**: Formats login session results including JWT tokens, active roles (Guest/Admin/Super Admin), names, and the `mustChangePassword` security flag.
* **`GuestCheckInRequest.java`**: Transports checking-in data (deposit, room ID, check-in date, defaults, meal preferences, list of bed IDs, isBookEntireRoom) to the manager/admin layer.
* **`GuestResponse.java`**: Formats resident details, room numbers, list of bed labels, check-in status, and KYC records for admin dashboard presentation.
* **`InvoiceResponse.java`**: Combines tax invoicing calculations, statuses, due dates, building mappings, and structured lists of `InvoiceLineItem` objects.
* **`SystemConfigResponse.java`**: Delivers whitelabel properties (name, short title, primary CSS color) and active system rule settings to client context.
* **`UserResponse.java`**: Maps Super Admin profiles and Admin (PG Owner) account tables for administration lists.

---

### 2.3 Exception Package (`com.pgcrm.exception`)

* **`ResourceNotFoundException.java`**: Maps a missing resource (e.g. invalid Bed ID or Guest ID) to an HTTP **404 (NOT_FOUND)** response.
* **`BedUnavailableException.java`**: Triggered when check-in attempts are made on occupied/notice beds. Yields an HTTP **400 (BAD_REQUEST)**.
* **`InvalidLockoutException.java`**: Prevents guests from altering meal preferences after lockout cutoff times. Returns HTTP **400 (BAD_REQUEST)**.
* **`SignatureVerificationException.java`**: Raised on failed Webhook signature verification checks. Returns HTTP **400 (BAD_REQUEST)**.
* **`DuplicateEmailException.java`**: Prevents multiple active guest records from utilizing the same email. Returns HTTP **400 (BAD_REQUEST)**.

---

### 2.4 Controller Package (`com.pgcrm.controller`)

#### `AuthController.java`
* **Structural Purpose**: User session entry points.
* **Why Created**: Manages login authentication, token refresh flows, password modifications, and password recovery.
* **Internal Logic & Dependencies**: Leverages `AuthenticationManager`, `JwtUtil`, and `AuthService`.
* **Database/UI Interaction**: Matches credentials against `User` tables, generating short-lived JWT access tokens for the frontend.

#### `GlobalExceptionHandler.java`
* **Structural Purpose**: Global interceptor for application runtime errors.
* **Why Created**: Decouples business logic from REST response codes, returning clean JSON payloads rather than server stack traces.
* **Internal Logic & Dependencies**: Annotated with `@ControllerAdvice` and `@ExceptionHandler`.
* **Database/UI Interaction**: Formats error descriptions for UI toast/banner presentation.

#### `GuestController.java`
* **Structural Purpose**: Handles guest portal requests.
* **Why Created**: Provides self-service APIs for Tier 1 Guest dashboards, profile settings, meal planning, and maintenance logs.
* **Internal Logic & Dependencies**: Integrates `GuestService`, `DailyLogService`, and `NotificationService`.
* **Database/UI Interaction**: Reads and updates `Guest`, `DailyLog`, `MaintenanceTicket`, and `Notification` schemas.

#### `InventoryController.java`
* **Structural Purpose**: Exposes building layouts.
* **Why Created**: Serves structural occupancy trees to property admins (Tier 2 and Tier 3).
* **Internal Logic & Dependencies**: Queries `BuildingSetupService`.
* **Database/UI Interaction**: Maps `Building`, `Floor`, `Block`, `Room`, and `Bed` records into JSON representations.

#### `PaymentController.java`
* **Structural Purpose**: Directs transactions and billing.
* **Why Created**: Integrates online gateway payments (Razorpay) and manual cash collection records.
* **Internal Logic & Dependencies**: Leverages `PaymentService`.
* **Database/UI Interaction**: Generates Razorpay order IDs and updates invoice status indicators from `PENDING` to `PAID` or `PENDING_CASH_VERIFICATION`.

#### `PgManagerController.java`
* **Structural Purpose**: Property management operations.
* **Why Created**: Powers guest check-in, guest switch bed, guest checkout, daily logs, and meal headcount analytics for Tier 2 Admins.
* **Internal Logic & Dependencies**: Integrates `GuestService`, `DailyLogService`, and `SettlementService`.
* **Database/UI Interaction**: Exposes mutation routes for active resident registries.

#### `PgOwnerController.java`
* **Structural Purpose**: Global administrative operations.
* **Why Created**: Allows Tier 3 Super Admins to manage property admins, configure buildings, and monitor global activity.
* **Internal Logic & Dependencies**: Interfaces `BuildingSetupService` and `AuthService`.
* **Database/UI Interaction**: Saves new manager profiles and building configuration entities.

#### `PricingController.java`
* **Structural Purpose**: Pricing overrides controller.
* **Why Created**: Allows dynamic overrides of defaults (rent, food, washing machine rates) per building for property admins.
* **Internal Logic & Dependencies**: Interfaces `PricingService`.
* **Database/UI Interaction**: Persists configuration updates to `PricingConfig` tables.

#### `PublicConfigController.java`
* **Structural Purpose**: Exposes system branding values.
* **Why Created**: Serves public whitelabel properties (name, short name, primary color) without requiring authentication.
* **Internal Logic & Dependencies**: Reads system property beans.
* **Database/UI Interaction**: Injected into frontend contexts before authenticating user sessions.

#### `ReportController.java`
* **Structural Purpose**: Exposes operational analytics.
* **Why Created**: Powers Tier 3 Super Admin reports (revenue trends, occupancy rates).
* **Internal Logic & Dependencies**: Interfaces `ReportService`.
* **Database/UI Interaction**: Aggregates datasets using specialized SQL queries.

#### `SystemConfigController.java`
* **Structural Purpose**: Standardized authenticated setup endpoint.
* **Why Created**: Exposes details like allowed payment modes and active rules.
* **Internal Logic & Dependencies**: Resolves configuration structures.
* **Database/UI Interaction**: Loaded by admins/guests to enforce client side options.

#### `WebhookController.java`
* **Structural Purpose**: Receives third-party service callbacks.
* **Why Created**: Validates transaction confirmations and incoming WhatsApp updates.
* **Internal Logic & Dependencies**: Parses JSON headers and verifies signature tokens.
* **Database/UI Interaction**: Integrates with invoice payment state reconciliations.

---

### 2.5 Entity Package (`com.pgcrm.entity`)

* **`User.java`**: Base user account mapping containing email credentials, BCrypt password hashes, and active roles.
* **`Guest.java`**: Models check-in status, expected checkouts, advanced deposits, default food preferences, and assigns a `@ManyToMany` mapping representing lists of allocated `Bed` records.
* **`Building.java`**: Represents properties with nested lists of `Floor` records.
* **`BuildingConfig.java`**: Configures building-specific values (lockout times, prices, allowed payment modes, split methods, and flags like food-in-rent).
* **`Floor.java`**, `Block.java`, `Room.java`, `Bed.java`: Models the structure of properties. Rooms map base rents and sharing configurations; beds trace occupancy status (`VACANT`, `OCCUPIED`, `NOTICE`).
* **`DailyLog.java`**: Ledger tracking daily food options (breakfast, lunch, dinner opt-ins) and add-on tallies.
* **`EbBill.java`** & `EbBillGuest.java`: Represents utilities split sessions, mapping start/end readings and individual guest allocations.
* **`Invoice.java`** & `InvoiceLineItem.java`: Relational invoices detailing rent calculations, utility shares, and daily log add-ons.
* **`MaintenanceTicket.java`**: Mapped guest-reported issues linked to target buildings.
* **`Notification.java`**: In-app push notification log referencing individual user accounts.
* **`PricingConfig.java`**: Table maps pricing overrides using property keys.

---

### 2.6 Security Package (`com.pgcrm.security`)

#### `JwtAuthenticationFilter.java`
* **Structural Purpose**: Stateless request filter.
* **Why Created**: Authorizes API requests using JWT headers and filters out invalid requests.
* **Internal Logic & Dependencies**: Decrypts bearer tokens, asserts claims, and loads `UserDetails`. Extracts the `X-Selected-Branch-Id` header to scope subsequent repository calls.
* **Database/UI Interaction**: Intercepts requests from the React client.

#### `JwtUtil.java`
* **Structural Purpose**: JWT generator and parser helper.
* **Why Created**: Cryptographically generates and signs tokens.
* **Internal Logic & Dependencies**: Implements HMAC-SHA256 signature algorithms.
* **Database/UI Interaction**: Invoked during successful authentication flows.

#### `SecurityConfig.java`
* **Structural Purpose**: Spring Security configuration.
* **Why Created**: Configures endpoint access controls, CORS mappings, and session policy configurations.
* **Internal Logic & Dependencies**: Defines route rules, permits public APIs (`/api/auth/login`, `/api/config/public`), and enforces filters.
* **Database/UI Interaction**: Guards backend endpoints against unauthorized access.

#### `UserDetailsServiceImpl.java`
* **Structural Purpose**: Implements Spring's `UserDetailsService`.
* **Why Created**: Connects database user tables with security authorization checks.
* **Internal Logic & Dependencies**: Looks up users by email in `UserRepository`.
* **Database/UI Interaction**: Queries database user records on authentication.

---

### 2.7 Seeder Package (`com.pgcrm.seeder`)

#### `DataSeeder.java`
* **Structural Purpose**: Seeds layout infrastructure and test profiles.
* **Why Created**: Auto-populates sample data for demonstrations.
* **Internal Logic & Dependencies**: Muted under the `test` profile. Loads config files like `pg-layout.yml`.
* **Database/UI Interaction**: Initializes buildings, guest check-ins, invoices, and meal histories.

#### `DatabaseSeeder.java`
* **Structural Purpose**: Master owner account initiator.
* **Why Created**: Auto-seeds the initial super admin/owner login securely.
* **Internal Logic & Dependencies**: Active under `dev` and `test` profiles. Reads values dynamically from environment variables (`@Value` parameters) and injects default credentials if user tables are blank. Muted under `prod` profile.
* **Database/UI Interaction**: Inserts the root Super Admin / Owner account.

---

### 2.8 Service Package (`com.pgcrm.service`)

#### `AuditService.java`
* **Structural Purpose**: Logs backend activities.
* **Why Created**: Maintains audit trails of business operations.
* **Database/UI Interaction**: Saves data directly into the `audit_logs` table.

#### `AuthService.java`
* **Structural Purpose**: Handles authentication logic.
* **Why Created**: Manages logins, token validation, and password resets.
* **Database/UI Interaction**: Wipes/rewrites user credentials records.

#### `BuildingSetupService.java`
* **Structural Purpose**: Transactional inventory manager.
* **Why Created**: Builds building trees dynamically (floors, rooms, beds).
* **Database/UI Interaction**: Creates and updates property layouts.

#### `DailyLogService.java`
* **Structural Purpose**: Logs daily meal headcounts.
* **Why Created**: Manages preferences while enforcing time lockout restrictions.
* **Database/UI Interaction**: Saves daily log details and queries occupant choices.

#### `EbBillService.java`
* **Structural Purpose**: Computes utility billing.
* **Why Created**: Divides energy usage costs (Equal Split vs. Sub-meter).
* **Database/UI Interaction**: Creates invoices and saves usage details.

#### `EmailService.java`
* **Structural Purpose**: Handles SMTP notifications.
* **Why Created**: Dispatches verification OTPs, password recoveries, and invoices.
* **Internal Logic & Dependencies**: Compiles templates via Thymeleaf and sends mail.

#### `EmailVerificationService.java`
* **Structural Purpose**: Caches email change verification codes.
* **Why Created**: Prevents updating profile emails without verifying the new address.
* **Internal Logic & Dependencies**: Stores codes in-memory for 15 minutes.

#### `GuestService.java`
* **Structural Purpose**: Operations service for guests.
* **Why Created**: Handles checking-in, profiling, and bed switches.
* **Database/UI Interaction**: Updates `Guest`, `User`, and `Bed` tables.

#### `InvoicePdfService.java`
* **Structural Purpose**: Generates PDF documents.
* **Why Created**: Programmatically compiles standard tax invoice PDFs.
* **Internal Logic & Dependencies**: Standardizes layout columns using the **OpenPDF** library.

#### `InvoiceService.java`
* **Structural Purpose**: Generates monthly invoices.
* **Why Created**: Automates rent pro-ration, add-on tallies, and utility split invoicing.
* **Database/UI Interaction**: Inserts new `Invoice` records.

#### `NotificationService.java`
* **Structural Purpose**: Dispatches multi-channel notifications.
* **Why Created**: Routes alerts to in-app databases, emails, or WhatsApp.
* **Database/UI Interaction**: Persists notification records.

#### `PaymentService.java`
* **Structural Purpose**: Manages online transactions.
* **Why Created**: Performs Razorpay payment validations and cash handovers.
* **Database/UI Interaction**: Updates payment states in invoice tables.

#### `PricingService.java`
* **Structural Purpose**: Resolves active prices.
* **Why Created**: Merges default base values with dynamic database overrides.
* **Database/UI Interaction**: Reads building configs and pricing records.

#### `ReportService.java`
* **Structural Purpose**: Computes metrics.
* **Why Created**: Aggregates occupancy, collection trends, and cash logs.
* **Database/UI Interaction**: Generates reports via database aggregates.

#### `SettlementService.java`
* **Structural Purpose**: Computes checkout settlements.
* **Why Created**: Manages exits, subtracts dues from deposits, and frees beds.
* **Database/UI Interaction**: Updates check-out states and releases bed records.

---

### 2.9 Scheduler Package (`com.pgcrm.scheduler`)

* **`MonthlyBillingScheduler.java`**: Runs at midnight on the 1st of the month (`0 0 0 1 * *`) to generate arrears invoices.
* **`PaymentReminderScheduler.java`**: Runs daily at 9:00 AM (`0 0 9 * * *`) to notify users of pending invoices.

---

## 3. Core PG CRM - Infrastructure Registry (`core-pg-crm/deploy/`)

#### `docker-compose.prod.yml`
* **Structural Purpose**: Defines the production service stack.
* **Why Created**: Automates multi-container orchestration.
* **Internal Logic**: Provisions a `postgres:18-alpine` database service and the Spring Boot `backend` service. Configured with a health check to wait for the database daemon before starting the backend.
* **Database/UI Interaction**: Binds host storage volume `pgdata` for data persistence.

#### `nginx-site.conf`
* **Structural Purpose**: Nginx reverse proxy configuration.
* **Why Created**: Configures domain routing, SSL encryption, and API path proxying.
* **Internal Logic**: Routes root requests to the React static container (Port 80) and `/api` requests to Spring Boot (Port 8080).
* **Database/UI Interaction**: Enables HTTPS and handles network performance limits.

#### `.env.example`
* **Structural Purpose**: Production environment variables template.
* **Why Created**: Documents required secrets and variables without exposing live credentials.
* **Internal Logic**: Outlines DB keys, JWT secrets, mail credentials, Razorpay keys, and whitelabel color settings.

---

## 4. Core PG CRM - Frontend Registry (`core-pg-crm/frontend/src`)

### 4.1 Contexts & Client Infrastructure

* **`App.jsx`**: Coordinates routing paths and checks access privileges (`PrivateRoute` route guard based on tiers).
* **`main.jsx`**: Bootstraps the application, mounting components to the root DOM node.
* **`api/index.js`**: Configures Axios, automatically attaching authorization tokens and target building headers from `sessionStorage`.
* **`context/AuthContext.jsx`**: Manages auth states, credentials, and handles sign-out operations. Uses `sessionStorage` for token security.
* **`context/SystemConfigContext.jsx`**: Stores branding data (whitelabel values) and system rules globally.

---

### 4.2 Layout Components (`src/components`)

* **`AppLayout.jsx`**: Wraps dashboard panels inside standard layout templates.
* **`Sidebar.jsx`**: Renders navigation options based on active user roles.
* **`TopHeader.jsx`**: Manages building branch dropdown selection for Tier 2 admins.

---

### 4.3 Dashboard & Settings Pages (`src/pages`)

* **`Login.jsx`**: Login portal. Strips mock login panels and displays system-offline banners if the server is unreachable.
* **`ForgotPassword.jsx`**: Initiates password recovery requests.
* **`Settings.jsx`**: Allows updating personal profiles and security keys.
* **`ChangePassword.jsx`**: Modifies passwords, forcing sign-outs on completion.
* **`AuditLog.jsx`**: Renders administrative audit history tables.
* **`Reports.jsx`**: Renders occupancy graphs and collection details.

---

### 4.4 Resident Pages (`src/pages/guest`)

* **`guest/DailyLog.jsx`**: Displays the calendar-based meal planner, enforcing opt-out lockouts.
* **`guest/GuestDashboard.jsx`**: Displays checked-in info, invoice counts, and active logs.
* **`guest/GuestInvoices.jsx`**: Renders invoices and triggers the Razorpay modal.
* **`guest/GuestMaintenance.jsx`**: Renders issue-reporting forms.

---

### 4.5 Manager Pages (`src/pages/manager`)

* **`manager/ManagerDashboard.jsx`**: Renders occupancy rates and task alerts for Tier 2 Admins.
* **`manager/ManagerEbBill.jsx`**: Handles recording energy readings and splits.
* **`manager/ManagerGuestAddons.jsx`**: Ledger for add-on orders (omelettes, laundry) with background Auto-Save status indicators.
* **`manager/ManagerGuests.jsx`**: Manages check-ins, bed lists, KYC, and check-outs.
* **`manager/ManagerInvoiceGenerator.jsx`**: Generates monthly invoices.
* **`manager/ManagerMaintenance.jsx`**: Renders forms to resolve guest maintenance tickets.
* **`manager/ManagerPricing.jsx`**: Portal to override room rents and food prices.

---

### 4.6 Owner Pages (`src/pages/owner`)

* **`owner/OwnerBuildingCreator.jsx`**: Multi-step wizard to register or edit properties for Tier 3 Super Admins.
* **`owner/OwnerDashboard.jsx`**: Registers and configures Tier 2 Admin (PG Owner) accounts.

---

## 5. Master Control Plane Registry (`master-control-plane/`)

The master control plane is a separate B2B SaaS application managed by the platform provider to register tenants, check payments, and automate VM instance setups.

### 5.1 Backend Service (`master-control-plane/backend`)
- **`com.controlplane.scheduler`**: Houses scheduled jobs such as the `AmcReminderScheduler` that evaluates AMC expiry dates (30, 7, 1 days prior) and triggers email notifications.
- **`com.controlplane.controller`**: Exposes REST endpoints to signup tenants, verify setup fee signatures, list tenant configurations, and suspend expired client accounts.
- **`com.controlplane.entity`**: Database mapping models including `Tenant`, `Subscription` (AMC), `RazorpayTransaction`, and `OnboardingTicket` schemas.
- **`com.controlplane.repository`**: Standard Spring Data JPA interfaces for managing master client profiles and billing ledgers.
- **`com.controlplane.service`**: Holds central provisioning business logic (SSH triggers, Ansible provisioning events, email reminders).

### 5.2 Frontend Admin Portal (`master-control-plane/frontend`)
- **Admin Dashboard**: Portal for system administrators to view list of B2B tenants, check setup ticket states, manually trigger provisioning, and verify payments history.

---

## 6. Root-Level Files Registry

The root-level directory contains critical launch scripts, SaaS architecture configurations, compliance documentations, and general guidelines:

* **`start_project.bat`**: The local development startup launcher.
* **`control_plane_architecture.md`**: The B2B SaaS architecture design document.
* **`MEMORY.md`**: The Antigravity context and file creation rules.
* **`sop.md`**: The unified client onboarding standard operating procedure.
