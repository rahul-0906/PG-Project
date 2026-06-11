# System Workflows & Visual Models

This document outlines the core business workflows, architectural data transitions, and entity lifecycle states of the Paying Guest CRM platform using visual sequence models.

---

## 1. Authentication & Token Refresh Flow
The frontend implements silent token refresh using an Axios interceptor to renew expired access tokens before failing subsequent requests.

```mermaid
sequenceDiagram
    autonumber
    actor User as Client Browser
    participant API as Axios / Client Interceptors
    participant Auth as Auth Controller
    participant JWT as JWT Service
    participant DB as PostgreSQL DB

    Note over User, DB: Authentication / Initial Login
    User->>Auth: POST /api/auth/login (email, password)
    Auth->>DB: Verify credentials
    DB->>Auth: User records matching
    Auth->>JWT: Generate Access Token (15m) & Refresh Token (7d)
    Auth->>User: Returns tokens & user meta
    Note over User: Tokens stored in SessionStorage

    Note over User, DB: Silent Token Refresh (API call 401 Interception)
    User->>API: GET /api/manager/guests (Access Token)
    API->>API: Request fails with 401 Unauthorized
    API->>Auth: POST /api/auth/refresh (Refresh Token)
    Auth->>JWT: Validate Refresh Token signature & expiry
    Auth->>DB: Check if user is active
    Auth->>User: Returns new Access Token
    API->>User: Retry original request with new Access Token
```

---

## 2. Guest Check-In & Provisioning Flow
Provisioning a new check-in allocates inventory, sets up a secure temp account, notifies the guest, and updates the building audit trail.

```mermaid
sequenceDiagram
    autonumber
    actor Manager
    participant App as ManagerGuests Component
    participant Controller as PgManagerController
    participant Service as GuestService
    participant DB as PostgreSQL DB
    participant Mail as EmailService

    Manager->>App: Submits check-in form (fullName, email, phone, bedId, advanceDeposit)
    App->>Controller: POST /api/manager/guests
    Controller->>Service: checkIn(...)
    Service->>DB: Verify Bed is VACANT
    Service->>DB: Create User account (Generate random temp password)
    Service->>DB: Create Guest profile linked to User and Bed
    Service->>DB: Update Bed status to OCCUPIED
    Service->>Mail: sendGuestWelcomeEmail(...) (asynchronous welcome)
    Service->>DB: Write GUEST_CHECKIN Audit Log
    Service->>Controller: Return Guest response
    Controller->>Manager: Check-In confirmed (displays in Guests table)
```

---

## 3. Checkout Notice & Financial Settlement Flow
Tracks the transition from notice registration (notice period) to final account settlement, pro-rated rent calculation, and bed release.

```mermaid
sequenceDiagram
    autonumber
    actor Manager
    participant App as ManagerGuests Component
    participant Controller as PgManagerController
    participant Service as SettlementService
    participant DB as PostgreSQL DB

    Note over Manager, DB: Phase 1: Notice Period Registration
    Manager->>App: Clicks "Notice" & Confirms
    App->>Controller: POST /api/manager/guests/{id}/initiate-checkout
    Controller->>Service: initiateCheckout(...)
    Service->>DB: Set noticeDate = today, exitDate = today + noticePeriodDays
    Service->>DB: Dispatch welcome check-out notification
    Service->>Manager: Return updated Guest (shows yellow notice indicator)

    Note over Manager, DB: Phase 2: Final Checkout & Account Settlement
    Manager->>App: Clicks "Checkout" & Confirms
    App->>Controller: POST /api/manager/guests/{id}/confirm-checkout
    Controller->>Service: confirmCheckout(...)
    Service->>DB: Calculate pro-rated rent for current month days
    Service->>DB: Calculate pending unbilled meal & laundry logs
    Service->>DB: Settle: advanceDeposit - (proratedRent + dues)
    Service->>DB: Release bed (Update Bed status to VACANT)
    Service->>DB: Set active = false, clear bed relation
    Service->>Manager: Return SettlementResult receipt (Displays final totals)
```

---

## 4. Meal & Add-on Tracking Flow
Tracks the daily meal preferences and add-on roster logs recorded by managers for bulk operations.

```mermaid
sequenceDiagram
    autonumber
    actor Manager
    participant App as ManagerGuestAddons Component
    participant Controller as PgManagerController
    participant DB as PostgreSQL DB

    Note over Manager, DB: Daily Meal & Add-on Log Retrieval
    Manager->>App: Selects date
    App->>Controller: GET /api/manager/guests-with-log/{date}
    Controller->>DB: Find active guests in building
    Controller->>DB: Retrieve or build default DailyLog records
    Controller->>Manager: Returns daily roster payload
    
    Note over Manager, DB: Daily Log Updates & Inline Auto-Saving
    Manager->>App: Toggles meal state or increments omelette/washing machine count
    Note over App: App displays dynamic saving spinner next to guest's name
    App->>Controller: PUT /api/guest/daily-log/{date} (via guest ID)
    Controller->>DB: Save DailyLog (isVeg, breakfast, lunch, dinner, omelettes, laundry)
    Controller->>App: Returns saved DailyLog state (200 OK)
    Note over App: App replaces spinner with checkmark (active Auto-Save feedback)
```

---

## 5. EB (Electricity) Bill Split Calculations
The application supports multiple bill calculation models: Equal Split across all active guests, Fixed Rate Per Bed, and Meter-Based Readings.

```mermaid
flowchart TD
    Start([Manager Submits EB Bill]) --> Method{Split Method?}
    
    Method -- EQUAL_SPLIT --o EqualCalc[Divide total bill amount equally among all active guests in period]
    EqualCalc --> SaveEqual[Save EbBill details & link pro-rated amounts to guests]
    
    Method -- PER_BED --o FixedCalc[Automatically charge configured fixed rate per bed in monthly invoice]
    FixedCalc --> DisableManual[Disable manual entries; charge per invoice cycle]
    
    Method -- METER_BASED --o MeterCalc[Manager enters previous & current unit readings per guest]
    MeterCalc --> DiffCalc[Calculate consumed units: current - previous]
    DiffCalc --> CostCalc[Dues: units * ratePerUnit]
    CostCalc --> SaveMeter[Save EbBill & individual guest meter logs]
```

---

## 6. Invoice Generation & PDF Rendering
Billing runs either automatically via monthly cron schedules or on-demand via the manager portal.

```mermaid
sequenceDiagram
    autonumber
    actor Manager
    participant App as ManagerBilling Component
    participant Controller as PgManagerController
    participant Service as InvoiceService
    participant PDF as InvoicePdfService
    participant DB as PostgreSQL DB

    Manager->>App: Clicks "Generate All Invoices" (Month, Year)
    App->>Controller: POST /api/manager/invoices/generate-all
    Controller->>Service: Generate invoices for active building guests
    loop For Each Active Guest
        Service->>DB: Verify no invoice exists for target month/year
        Service->>DB: Calculate base rent of bed
        Service->>DB: Load split EB bill costs & unbilled meals/add-ons
        Service->>DB: Create Invoice line items
        Service->>DB: Save Invoice record (GENERATED state)
    end
    Service->>Manager: Success message (Total invoices created)
    
    Note over Manager, DB: Download PDF Invoice
    Manager->>App: Clicks Download Invoice PDF
    App->>Controller: GET /api/manager/invoices/{id}/pdf
    Controller->>PDF: generateInvoicePdf(...)
    PDF->>DB: Retrieve invoice details & guest info
    PDF->>PDF: Render layout using iText PDF Library
    PDF->>Manager: Return PDF byte stream for local download
```

---

## 7. Guest Maintenance Portal Lifecycle
Guests submit maintenance requests from their portal. Real-time updates and status changes flow directly to the manager.

```mermaid
sequenceDiagram
    autonumber
    actor Guest
    participant Portal as Guest Portal
    participant Controller as GuestController
    actor Manager
    participant MgrApp as ManagerMaintenance
    participant DB as PostgreSQL DB

    Guest->>Portal: Submits ticket (title, priority, description)
    Portal->>Controller: POST /api/guest/maintenance
    Controller->>DB: Fetch guest room location
    Controller->>DB: Create MaintenanceTicket (status = OPEN)
    Controller->>Portal: Ticket created
    
    Note over Manager, DB: Manager Ticket Resolution
    Manager->>MgrApp: Loads open tickets list
    MgrApp->>DB: Retrieve OPEN tickets
    Manager->>MgrApp: Clicks "Resolve Ticket"
    MgrApp->>DB: Update status to RESOLVED
    DB->>MgrApp: Saved
    MgrApp->>Guest: Update ticket status in Guest Portal view
```

---

## 8. Audit Trail Logging System
Ensures compliance and accountability by tracking all operational administrative modifications in a permanent ledger.

```mermaid
flowchart LR
    Admin([Manager / Owner Action]) --> API[API Endpoint Trigger]
    API --> Action[Perform Operation: check-in, update rules, pricing override]
    Action --> DB[(PostgreSQL Database)]
    Action --> Audit[AuditService.log]
    Audit --> DB_Log[Save AuditLog entity with action, metadata, timestamp]
```

---

## 9. Building Setup & Room Inventory Creation Flow
Owners programmatically provision physical buildings, layout structures, and room configurations using a step-by-step layout wizard.

```mermaid
sequenceDiagram
    autonumber
    actor Owner
    participant Creator as OwnerBuildingCreator Component
    participant Controller as PgOwnerController
    participant SetupService as BuildingSetupService
    participant DB as PostgreSQL DB

    Owner->>Creator: Defines Building metadata (Name, Address)
    Owner->>Creator: Configures layout schema (Floors, Blocks, Room counts, Sharing Type, Rents)
    Creator->>Creator: Computes default bed structures (e.g., Bed A, Bed B per room)
    Owner->>Creator: Clicks "Submit Property"
    Creator->>Controller: POST /api/owner/buildings (BuildingSetupRequest DTO)
    Controller->>SetupService: setupBuilding(...)
    Note over SetupService, DB: Database Transaction Context
    SetupService->>DB: Persist Building & BuildingConfig
    loop For Each Floor / Block / Room / Bed
        SetupService->>DB: Create & Link Floor
        SetupService->>DB: Create & Link Block (optional)
        SetupService->>DB: Create & Link Room (sharingType, baseRent)
        SetupService->>DB: Create & Link Bed (BedStatus = VACANT)
    end
    SetupService->>DB: Commit Transaction
    SetupService->>Controller: Return confirmation
    Controller->>Creator: Return success payload
    Creator->>Owner: Displays new building in property directory
```

---

## 10. Manager Assignment & Branch Access Scoping Flow
Owners delegate operations by assigning managers to specific buildings, enforcing multi-branch safety scopes via JWT attributes.

```mermaid
sequenceDiagram
    autonumber
    actor Owner
    participant App as OwnerDashboard
    participant Controller as PgOwnerController
    participant DB as PostgreSQL DB
    actor Manager
    participant MgrApp as ManagerApp
    participant Filter as JwtAuthenticationFilter

    Owner->>App: Selects building mappings & saves Manager
    App->>Controller: POST/PUT /api/owner/managers (assigns branchId array)
    Controller->>DB: Update User record (branchId: "1,2")
    
    Note over Manager, DB: Manager Login & Scoped Requests
    Manager->>MgrApp: Performs Login
    MgrApp->>Controller: POST /api/auth/login
    Controller->>DB: Lookup Manager User
    DB->>Controller: Return assignments (branchIds: [1,2])
    Controller->>Manager: Yields JWT Token (claims: branchIds)
    Manager->>MgrApp: Selects active branch (Building 1) from TopHeader
    Manager->>MgrApp: Selects active branch (Building 1) from TopHeader
    MgrApp->>Filter: GET /api/manager/guests (Headers: X-Selected-Branch-Id = 1, JWT Bearer)
    Filter->>Filter: Verify selected branch is in user JWT claims list
    Filter->>Filter: Set context branchId attribute
    Filter->>DB: Scoped Query: Get active guests where buildingId = 1
    DB->>Manager: Yields building-specific guest roster
```

---

## 11. Dynamic Pricing Overrides & Rent Settings Flow
Enables property administrators to adjust general prices or perform bulk sharing-type rent overrides across an entire building.

```mermaid
sequenceDiagram
    autonumber
    actor Manager
    participant App as ManagerPricing Component
    participant Controller as PricingController
    participant Service as PricingService
    participant DB as PostgreSQL DB

    Note over Manager, DB: Modifying Item Specific Prices
    Manager->>App: Enters new Omelette Price (e.g. 18.00)
    App->>Controller: PUT /api/manager/pricing/omelette (value = 18.00)
    Controller->>Service: savePricingOverride("omelette", 18.00)
    Service->>DB: Save or Update PricingConfig (key = "omelette", value = 18.00)
    Service->>Manager: Confirmation receipt
    
    Note over Manager, DB: Bulk Sharing-Type Rent Updates
    Manager->>App: Enters new Double-Sharing rent (e.g. 7500)
    App->>Controller: PUT /api/manager/pricing/sharing/DOUBLE/rent (value = 7500)
    Controller->>Service: updateSharingRent("DOUBLE", 7500)
    Service->>DB: Select all Rooms in building where sharingType = DOUBLE
    Service->>DB: Bulk Update Room set baseRent = 7500
    Service->>Manager: Update summary (X rooms updated)
```

---

## 12. Calendar-Based Guest Meal Booking & Lockout Validation Flow
Guests manage future meal schedules directly from their portal, checked against strict time-based locks.

```mermaid
sequenceDiagram
    autonumber
    actor Guest
    participant Portal as Guest DailyLog Page
    participant Controller as GuestController
    participant Service as DailyLogService
    participant DB as PostgreSQL DB

    Guest->>Portal: Opens Meal Planner page
    Portal->>Controller: GET /api/guest/daily-log/month/{yearMonth}
    Controller->>Service: getMonthlyLogsForGuest(...)
    Service->>DB: Query DailyLog records for month
    DB->>Service: Logs lists
    Note over Service: For missing dates, dynamic defaults from Guest profile are seeded
    Service->>Portal: Returns 31-day meal preference array
    
    Note over Guest, DB: Save preference with Lockout check
    Guest->>Portal: Toggles breakfast status for target date
    Portal->>Controller: PUT /api/guest/daily-log (date, breakfastStatus)
    Controller->>Service: updateMealPreference(date, breakfastStatus)
    alt Lockout Exceeded (Time is past 10:00 PM previous day)
        Service->>Portal: Throw InvalidLockoutException (400 Bad Request)
        Portal->>Guest: Displays error: "Modification cut-off has passed"
    else Lockout Valid
        Service->>DB: Insert or Update DailyLog (breakfast = breakfastStatus)
        Service->>Portal: Return updated Log state
        Portal->>Guest: Show green save success indicator
    end
```

---

## 13. Razorpay Payment Processing & Webhook Verification Flow
Facilitates secure guest billing collections via payment gateway triggers, completing the invoice lifecycle.

```mermaid
sequenceDiagram
    autonumber
    actor Guest
    participant Portal as GuestInvoices Page
    participant Controller as PaymentController
    participant Service as PaymentService
    participant Razorpay as Razorpay API
    participant DB as PostgreSQL DB

    Guest->>Portal: Clicks "Pay Now" on Invoice card
    Portal->>Controller: POST /api/payments/razorpay/order/{invoiceId}
    Controller->>Service: createPaymentOrder(invoiceId)
    Service->>Razorpay: POST /orders (amount, currency, receiptId)
    Razorpay->>Service: Returns order details (razorpayOrderId)
    Service->>Portal: Yields payment configuration details (orderId, keyId, amount)
    Portal->>Guest: Launches Razorpay Checkout Modal
    Guest->>Razorpay: Completes authorization (Card / UPI)
    Razorpay->>Portal: Returns payment tokens (razorpay_payment_id, razorpay_signature)
    Portal->>Controller: POST /api/payments/razorpay/verify (payload matching signatures)
    Controller->>Service: verifyPaymentSignature(payload)
    Service->>Service: Compute HMAC-SHA256 (orderId + "|" + paymentId, secret)
    alt Signatures Match
        Service->>DB: Update Invoice status = PAID, save paymentId
        Service->>DB: Log payment Audit Event
        Service->>Portal: Return success payment response
        Portal->>Guest: Display payment confirmation & updated ledger
    else Signatures Mismatch
        Service->>Portal: Throw SignatureVerificationException (400 Bad Request)
    end
```

---

## 14. System Configuration & White-Label Customization Engine Flow
Dynamically loads custom system colors, logos, names, and structural preferences dynamically on load.

```mermaid
sequenceDiagram
    autonumber
    actor Client as User Browser
    participant Context as SystemConfigContext
    participant Controller as SystemConfigController
    participant Properties as SystemConfigProperties
    participant File as Filesystem (tenant-config.yml)

    Note over Properties, File: Server Startup Init
    Properties->>File: Loads tenant-config.yml configs
    Note over Properties: Falls back to Java defaults if keys are missing
    
    Note over Client, Properties: Dynamic Whitelabel Rendering
    Client->>Context: Mounts Context Provider
    Context->>Controller: GET /api/system/config
    Controller->>Properties: Retrieves active branding & rules
    Properties->>Controller: Returns Name, ShortTitle, Theme colors
    Controller->>Client: Yields SystemConfigResponse DTO
    Context->>Context: Injects custom variables into CSS Root Variables
    Note over Client: UI updates brand headers, titles, and color themes dynamically
```

---

## 15. Cron-Based Billing & Payment Reminders Automation Flow
Executes server-side batch billing pipelines on schedule, notifying residents automatically of pending balances.

```mermaid
sequenceDiagram
    autonumber
    participant Cron as Spring Task Scheduler
    participant Billing as MonthlyBillingScheduler
    participant Reminder as PaymentReminderScheduler
    participant Invoice as InvoiceService
    participant Notif as NotificationService
    participant DB as PostgreSQL DB
    participant WhatsApp as Twilio WhatsApp API

    Note over Cron, DB: Pipeline 1: Monthly Auto-Billing (1st @ Midnight)
    Cron->>Billing: Cron trigger ("0 0 0 1 * *")
    Billing->>DB: Query active Buildings with auto-billing enabled
    loop For Each Building
        Billing->>Invoice: generateMonthlyInvoices(buildingId)
        Invoice->>DB: Find active checked-in guests
        loop For Each Active Guest
            Invoice->>DB: Compute rent, EB utilities, and daily log add-ons
            Invoice->>DB: Persist Invoice (status = PENDING)
            Invoice->>Notif: sendNewInvoiceAlert(guest)
            Notif->>WhatsApp: Trigger WhatsApp invoice text notification
        end
    end
    
    Note over Cron, DB: Daily Payment Reminders (9:00 AM)
    Cron->>Reminder: Cron trigger ("0 0 9 * * *")
    Reminder->>DB: Query all PENDING invoices past or near due date
    loop For Each Pending Invoice
        Reminder->>Notif: sendPaymentReminder(invoice)
        Notif->>WhatsApp: Send WhatsApp reminder text (amount, link, due date)
        Notif->>DB: Log Notification entity
    end
```

---

## 16. Guest Email Profile Change OTP Verification Flow
Secures the guest profile update pipeline, preventing unauthorized email modifications by using a 6-digit verification code.

```mermaid
sequenceDiagram
    autonumber
    actor Guest
    participant Portal as Guest Settings Page
    participant Controller as GuestController
    participant EVService as EmailVerificationService
    participant Mail as EmailService
    participant DB as PostgreSQL DB

    Note over Guest, DB: Phase 1: Request Email Change
    Guest->>Portal: Enters new email & clicks "Send Code"
    Portal->>Controller: POST /api/guest/profile/request-email-change (newEmail)
    Controller->>DB: Check if email already exists in User table
    alt Email is taken
        DB->>Controller: Return duplicate record
        Controller->>Portal: 400 Bad Request (Email in use)
    else Email is free
        Controller->>EVService: storeCode(userId, newEmail, 6-digit-code)
        Note over EVService: Stores code & expiry (15 min) in ConcurrentHashMap cache
        Controller->>Mail: sendEmailVerificationCode(newEmail, code, fullName)
        Controller->>Portal: 200 OK (Verification code sent)
        Portal->>Guest: Prompts for 6-digit OTP verification code
    end

    Note over Guest, DB: Phase 2: Verify & Commit Email Change
    Guest->>Portal: Enters OTP & submits
    Portal->>Controller: POST /api/guest/profile/verify-email-change (newEmail, code)
    Controller->>EVService: verifyCode(userId, newEmail, code)
    alt Invalid/Expired OTP
        EVService->>Controller: Return false
        Controller->>Portal: 400 Bad Request (Invalid/Expired code)
    else Successful Verification
        EVService->>Controller: Return true (Clears cached OTP)
        Controller->>DB: Update Guest & User email fields
        Controller->>DB: Save changes & write Audit Log
        Controller->>Portal: Return updated GuestResponse
        Portal->>Guest: Displays success toast & shows new email
    end
```

---

## 17. Forgot/Reset Password Temporary Credentials Flow
Permits self-service password recovery, issuing a random temporary password and forcing users to change it on their subsequent login.

```mermaid
sequenceDiagram
    autonumber
    actor User as Guest / Manager
    participant App as ForgotPassword Page
    participant Controller as AuthController
    participant Service as AuthService
    participant DB as PostgreSQL DB
    participant Mail as EmailService

    User->>App: Enters email and submits
    App->>Controller: POST /api/auth/forgot-password (email)
    Controller->>Service: processForgotPassword(email)
    Service->>DB: Search for active User by email
    alt User Not Found / Inactive
        Service->>Controller: Complete silently (No action logged)
        Controller->>App: Return 200 OK (Generic success message)
    else Active User Found
        Service->>Service: Generate 10-char high-entropy tempPassword
        Service->>DB: Update user: password = encode(tempPassword), mustChangePassword = true, firstLogin = true
        Service->>Mail: sendPasswordResetEmail(user, tempPassword)
        Service->>Controller: Done
        Controller->>App: Return 200 OK
    end
    App->>User: Renders success checkmark & redirects to Login

    Note over User, DB: Subsequent Login & Forced Password Change
    User->>App: Logs in with temp password
    App->>Controller: POST /api/auth/login
    Controller->>DB: Verify credentials (matches encrypted tempPassword)
    Controller->>User: Return AuthResponse (mustChangePassword = true)
    Note over User: App routes user directly to ChangePassword view, locking navigation
    User->>App: Enters new password & submits
    App->>Controller: POST /api/auth/change-password (currentPassword, newPassword)
    Controller->>DB: Validate currentPassword & update user: password = encode(newPassword), mustChangePassword = false
    Controller->>DB: Log PASSWORD_CHANGED Audit Event
    Controller->>User: Password change confirmed, grants dashboard access
```

---

## 18. Room/Bed Switch & Multi-Channel Notification Flow
Handles transferring checked-in guests between rooms/beds via an interactive grid, recording audit logs, sending confirmation emails, and publishing in-app alerts.

```mermaid
sequenceDiagram
    autonumber
    actor Manager
    participant App as ManagerGuests Component
    participant Controller as PgManagerController
    participant Service as GuestService
    participant DB as PostgreSQL DB
    participant Mail as EmailService
    participant Notif as NotificationService

    Manager->>App: Selects Guest and clicks "Switch Bed"
    App->>Controller: GET /api/inventory/buildings (fetches all layout structures)
    Controller->>App: Return Building layout (all floors, blocks, rooms, beds)
    Note over App: App groups beds by Floor/Block/Room and opens visual selector modal (2-column layout)
    Manager->>App: Selects a VACANT bed (displays dynamic price impact preview) & clicks "Confirm"
    App->>Controller: PUT /api/manager/guests/{id}/switch-bed?bedId={bedId}
    Controller->>Service: switchBed(guestId, bedId)
    
    Note over Service, DB: Database Transaction
    Service->>DB: Update old bed status to VACANT
    Service->>DB: Update guest's assigned bed reference to new bed
    Service->>DB: Update new bed status to OCCUPIED
    Service->>DB: Write BED_SWITCH Audit Log entry
    
    Note over Service, Notif: Dispatch Notifications
    Service->>Mail: sendBedSwitchEmail(guest, oldBedCode, newBedCode, newRent)
    Service->>Notif: sendInAppNotification(guest.user, message)
    Notif->>DB: Persist Notification entity (isRead = false)
    
    Service->>Controller: Return updated GuestResponse
    Controller->>App: Return success status
    App->>Manager: Displays success toast and refreshes guests list
    
    Note over Guest, DB: Guest receives In-App Alert
    actor Guest
    Guest->>App: Logs into Guest Portal
    App->>Controller: GET /api/guest/dashboard / GET /api/guest/notifications
    Controller->>DB: Count unread notifications & fetch feed
    DB->>App: Returns notifications list & unread count
    Note over App: Bell icon in header displays red count badge. Guest opens notifications menu and clicks "Mark Read"
    Guest->>App: Clicks notification item / "Mark all read"
    App->>Controller: PUT /api/guest/notifications/{id}/read
    Controller->>DB: Set Notification isRead = true
```

---

## 19. Guest Cash Handover & Manager Verification Flow
Enables guests to request rent verification for offline cash handovers, placing verification cards at high priority on the manager's dashboard workspace.

```mermaid
sequenceDiagram
    autonumber
    actor Guest
    participant Portal as GuestInvoices Page
    participant Controller as PgManagerController
    participant Service as InvoiceService
    participant DB as PostgreSQL DB
    actor Manager
    participant Dashboard as ManagerDashboard

    Guest->>Portal: Clicks "Cash Handover" on Invoice
    Portal->>Controller: POST /api/guest/invoices/{id}/pay-cash
    Controller->>Service: initiateCashHandover(invoiceId)
    Service->>DB: Set Invoice status = PENDING_CASH_VERIFICATION
    Service->>Portal: Success payload
    Note over Portal: Renders a static Clock icon indicating pending approval status

    Note over Manager, DB: Manager Verification Dashboard Notification
    Manager->>Dashboard: Navigates to Dashboard / Logs In
    Dashboard->>Controller: GET /api/manager/invoices/pending-cash
    Controller->>DB: Query invoices with status = PENDING_CASH_VERIFICATION for manager's authorized branches
    DB->>Dashboard: Yields pending cash handover list
    Note over Dashboard: Renders high-priority "Pending Cash Verifications" card directly under stats grid
    
    Manager->>Dashboard: Clicks "Verify Cash" button
    Dashboard->>Controller: POST /api/manager/invoices/{id}/verify-cash
    Controller->>Service: verifyCashHandover(invoiceId)
    Service->>DB: Set Invoice status = PAID, paymentMode = CASH, paymentDate = today
    Service->>DB: Write CASH_VERIFICATION Audit Log entry
    Service->>Dashboard: Returns success confirmation
    Dashboard->>Manager: Triggers green success toast & refreshes pending queue
```

---

## 20. Guest Profile Modification & Constraint Validation Flow
Secures the guest profile update pipeline in the manager view, preventing duplicate email assignments using an in-modal validation error alert.

```mermaid
sequenceDiagram
    autonumber
    actor Manager
    participant App as ManagerGuests Component
    participant Controller as PgManagerController
    participant DB as PostgreSQL DB

    Manager->>App: Clicks "Edit" on a Guest row
    App->>Manager: Opens "Edit Guest Details" Modal
    Manager->>App: Modifies Email ID field to an existing email & clicks "Save"
    App->>Controller: PUT /api/manager/guests/{id} (editForm payload)
    Controller->>DB: Check if email is in use by another user account
    DB->>Controller: Email matches an existing active account
    Controller->>App: Return 400 Bad Request ("Email is already in use by another account")
    Note over App: App catches error, sets editError state, and renders red warning banner inside the modal
    App->>Manager: Displays warning banner: "Email is already in use by another account"
```

