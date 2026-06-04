# Iconography Refactoring & Minimalist Design Standard

Enforce a strict, uniform, minimalist aesthetic across the React frontend and Spring Boot Thymeleaf email templates by standardizing icons to thin line-art, removing background container fills, and replacing legacy emojis/images with inline SVGs.

## User Review Required

> [!IMPORTANT]
> - All Lucide icons across the 19 React frontend components will be standardized to a thin stroke width (`strokeWidth={1.5}`).
> - Emojis inside the HTML email templates (such as `🔑`, `🔐`, `🔄`, `🏠`, `⚠️`, `📅`, and `💳`) will be replaced with clean, raw inline SVGs that match the Lucide styling exactly.
> - Dashboard `StatCard` circular/square colored backgrounds (`iconBg`) will be removed, allowing the thin line-art icons to sit directly on the whitespace.

## Proposed Changes

---

### React Frontend Icon Standardization

All Lucide React icon instances will be updated to include `strokeWidth={1.5}`. Complex icons will be swapped with geometric equivalents (e.g. `Building2` to `Building`).

#### [MODIFY] [Sidebar.jsx](file:///e:/Antigravity%20Project/PG%20Project/frontend/src/components/Sidebar.jsx)
- Swap `Building2` import and usage with `Building`.
- Apply `strokeWidth={1.5}` to all sidebar navigation icons.

#### [MODIFY] [TopHeader.jsx](file:///e:/Antigravity%20Project/PG%20Project/frontend/src/components/TopHeader.jsx)
- Set `strokeWidth={1.5}` to top header icons.

#### [MODIFY] [Reports.jsx](file:///e:/Antigravity%20Project/PG%20Project/frontend/src/pages/Reports.jsx)
- Set `strokeWidth={1.5}` to all `StatCard` and tab icons.

#### [MODIFY] [Settings.jsx](file:///e:/Antigravity%20Project/PG%20Project/frontend/src/pages/Settings.jsx)
- Set `strokeWidth={1.5}` to settings icons.

#### [MODIFY] [GuestDashboard.jsx](file:///e:/Antigravity%20Project/PG%20Project/frontend/src/pages/guest/GuestDashboard.jsx)
- Refactor `StatCard` to remove `iconBg` wrapper class and style icon to be `w-5 h-5` and `strokeWidth={1.5}` directly on whitespace.
- Apply `strokeWidth={1.5}` to all other dashboard icons.

#### [MODIFY] [ManagerDashboard.jsx](file:///e:/Antigravity%20Project/PG%20Project/frontend/src/pages/manager/ManagerDashboard.jsx)
- Refactor `StatCard` to remove `iconBg` background wrapper, allowing icons to sit cleanly on whitespace.
- Set `strokeWidth={1.5}` to dashboard icons.

#### [MODIFY] [OwnerDashboard.jsx](file:///e:/Antigravity%20Project/PG%20Project/frontend/src/pages/owner/OwnerDashboard.jsx)
- Refactor `StatCard` to remove `iconBg` wrapper background.
- Set `strokeWidth={1.5}` to dashboard icons.

#### [MODIFY] [ManagerPricing.jsx](file:///e:/Antigravity%20Project/PG%20Project/frontend/src/pages/manager/ManagerPricing.jsx)
- Swap `Building2` with `Building`.
- Enforce `strokeWidth={1.5}`.

#### [MODIFY] [OwnerBuildingCreator.jsx](file:///e:/Antigravity%20Project/PG%20Project/frontend/src/pages/owner/OwnerBuildingCreator.jsx)
- Swap `Building2` with `Building`.
- Enforce `strokeWidth={1.5}`.

#### [MODIFY] [ManagerEbBill.jsx](file:///e:/Antigravity%20Project/PG%20Project/frontend/src/pages/manager/ManagerEbBill.jsx)
- Swap `Building2` with `Building`.
- Enforce `strokeWidth={1.5}`.

#### [MODIFY] [ManagerGuests.jsx](file:///e:/Antigravity%20Project/PG%20Project/frontend/src/pages/manager/ManagerGuests.jsx)
- Audit buttons (e.g. "Edit", "Checkout", "Switch Bed") to ensure they use `flex items-center gap-2` with `strokeWidth={1.5}` icons.

#### [MODIFY] [All Other Page Components](file:///e:/Antigravity%20Project/PG%20Project/frontend/src/pages/)
- Traverse remaining page components (`AuditLog.jsx`, `ForgotPassword.jsx`, `DailyLog.jsx`, `GuestInvoices.jsx`, `GuestMaintenance.jsx`, `ManagerGuestAddons.jsx`, `ManagerInvoiceGenerator.jsx`, `ManagerMaintenance.jsx`) and set `strokeWidth={1.5}` on Lucide icons.

---

### Spring Boot Thymeleaf Email Templates

Replace legacy emojis in headers and text with inline minimalist SVGs styled via inline CSS.

#### [MODIFY] [email-verification.html](file:///e:/Antigravity%20Project/PG%20Project/backend/src/main/resources/templates/email-verification.html)
- Replace `🔑` with a clean `Key` SVG (stroke-width 1.5, stroke currentColor, no fill).
- Replace `⚠️` with an `AlertTriangle` SVG.

#### [MODIFY] [password-reset-email.html](file:///e:/Antigravity%20Project/PG%20Project/backend/src/main/resources/templates/password-reset-email.html)
- Replace `🔐` with a `Lock` SVG.
- Replace `⚠️` with an `AlertTriangle` SVG.
- Replace `🔑` in the login button with a `Key` SVG.

#### [MODIFY] [bed-switch-email.html](file:///e:/Antigravity%20Project/PG%20Project/backend/src/main/resources/templates/bed-switch-email.html)
- Replace `🔄` in the header with a `RefreshCw` SVG.

#### [MODIFY] [welcome-back-email.html](file:///e:/Antigravity%20Project/PG%20Project/backend/src/main/resources/templates/welcome-back-email.html)
- Replace `🏠` with a `Home` SVG.
- Replace `🔑` in the button with a `Key` SVG.

#### [MODIFY] [welcome-email.html](file:///e:/Antigravity%20Project/PG%20Project/backend/src/main/resources/templates/welcome-email.html)
- Replace `🏠` with a `Home` SVG.
- Replace `⚠️` with an `AlertTriangle` SVG.
- Replace `🔑` in the button with a `Key` SVG.

#### [MODIFY] [payment-reminder.html](file:///e:/Antigravity%20Project/PG%20Project/backend/src/main/resources/templates/payment-reminder.html)
- Replace `⚠️` with an `AlertTriangle` SVG.
- Replace `📅` with a `Calendar` SVG.
- Replace `💳` in the button with a `CreditCard` SVG.

---

## Verification Plan

## Automated Tests
- Build the frontend project via `npm run build` to verify there are no compilation errors or missing/unused Lucide imports.

## Manual Verification
1. Run the frontend application and inspect all dashboards (`GuestDashboard`, `ManagerDashboard`, `OwnerDashboard`) to confirm that:
   - All icons are thin, minimalist (`strokeWidth={1.5}`).
   - Dashboard stat cards no longer have filled background containers and sit cleanly on the whitespace.
   - Button icons are centered and use a consistent `gap-2` space.
2. Render or check the HTML email templates to confirm that SVGs display correctly across headers and buttons, matching the branding aesthetic.
