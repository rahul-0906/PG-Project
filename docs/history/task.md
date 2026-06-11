# Task Checklist - Iconography Refactoring

- [x] Phase 1: Frontend Icon Standardization (React/Tailwind)
  - [x] Update `StatCard` in `GuestDashboard.jsx`, `ManagerDashboard.jsx`, and `OwnerDashboard.jsx` to remove colored `iconBg` wrappers.
  - [x] Update Lucide React icon imports in `Sidebar.jsx`, `ManagerPricing.jsx`, `OwnerBuildingCreator.jsx`, `ManagerEbBill.jsx` to replace `Building2` with `Building`.
  - [x] Standardize all Lucide icons to `strokeWidth={1.5}` across all frontend files.
- [x] Phase 2: Action Buttons & Badges Cleanup
  - [x] Audit list table buttons and verify they use `flex items-center gap-2` with `strokeWidth={1.5}` icons.
- [x] Phase 3: Email Template Iconography (Thymeleaf / HTML)
  - [x] Replace emojis in `email-verification.html` with Key and AlertTriangle inline SVGs.
  - [x] Replace emojis in `password-reset-email.html` with Lock, AlertTriangle, and Key inline SVGs.
  - [x] Replace emojis in `bed-switch-email.html` with RefreshCw inline SVG.
  - [x] Replace emojis in `welcome-back-email.html` with Home and Key inline SVGs.
  - [x] Replace emojis in `welcome-email.html` with Home, AlertTriangle, and Key inline SVGs.
  - [x] Replace emojis in `payment-reminder.html` with AlertTriangle, Calendar, and CreditCard inline SVGs.
- [x] Phase 4: Build, Verification & Documentation
  - [x] Run frontend compilation `npm run build` to verify correctness.
  - [x] Commit changes to Git.

- [x] Execution Run: Target Iconography Refactoring
  - [x] Refactor [ManagerPricing.jsx](file:///e:/Antigravity%20Project/PG%20Project/frontend/src/pages/manager/ManagerPricing.jsx) to remove emojis/bulky SVGs and use strict Lucide components (Utensils, Ban, Zap, CreditCard, Clock, CalendarClock, Bed, etc.) with strokeWidth={1.5}.
  - [x] Refactor [GuestDashboard.jsx](file:///e:/Antigravity%20Project/PG%20Project/frontend/src/pages/guest/GuestDashboard.jsx) to replace emojis/bulky SVGs (🍳, 🥚, 🧺, 💰) in metrics and service logs with strict Lucide components (Utensils, Egg, Shirt, Wallet, etc.) with strokeWidth={1.5}.

- [x] Execution Run: Meal & Add-on Tracker UI Refactor
  - [x] Remove row-level save button column and implement background Auto-Save pattern.
  - [x] Convert Diet Preference dropdown column into a simple colored visual indicator dot next to the guest's name.
  - [x] Redesign counters using ghost outline buttons and `Plus`/`Minus` Lucide icons with strokeWidth={1.5}.
  - [x] Ensure table rows have active hover states (`hover:bg-slate-50`).
  - [x] Place header-level bulk-action toggle placeholder checkboxes in Breakfast, Lunch, and Dinner.

- [x] Execution Run: Monthly Roster Table Header Fix
  - [x] Fix z-index and transparency bug on the sticky Monthly Roster table header in [ManagerGuestAddons.jsx](file:///e:/Antigravity%20Project/PG%20Project/frontend/src/pages/manager/ManagerGuestAddons.jsx).
  - [x] Apply solid background colors (`bg-white`) to header elements to prevent scroll bleed-through.
  - [x] Ensure structural border-bottom (`border-b border-slate-200`) and z-index (`z-10`) are preserved/applied.
  - [x] Run build verification to ensure no compilation errors exist.

- [x] Execution Run: Roster Table Color Stripping (Minimalism)
  - [x] Locate the Breakfast, Lunch, and Dinner columns in the Monthly Roster table in [ManagerGuestAddons.jsx](file:///e:/Antigravity%20Project/PG%20Project/frontend/src/pages/manager/ManagerGuestAddons.jsx).
  - [x] Remove colorful background classes (`bg-indigo-50`, `bg-emerald-50`, `bg-blue-50`, etc.) and text colors (`text-indigo-600`, `text-emerald-600`, `text-blue-600`, etc.) from both headers (`<th>`) and data cells (`<td>`).
  - [x] Set the `<th>` text to uniform `text-slate-500` and ensure header backgrounds rely solely on the sticky `bg-white` class.
  - [x] Set `<td>` text style to `font-medium text-slate-700` with no background color overlays.
  - [x] Replace row expansion highlight color (`bg-indigo-50/20`) with a neutral grey `bg-slate-50`.

- [x] Execution Run: Migrate Session Persistence to sessionStorage
  - [x] Scan and locate all occurrences of `localStorage` relating to tokens, profiles, or branch IDs.
  - [x] Refactor context [AuthContext.jsx](file:///e:/Antigravity%20Project/PG%20Project/frontend/src/context/AuthContext.jsx) to replace `localStorage` with `sessionStorage`.
  - [x] Refactor API client [index.js](file:///e:/Antigravity%20Project/PG%20Project/frontend/src/api/index.js) request/response interceptors to utilize `sessionStorage`.
  - [x] Refactor layouts and pages like [Sidebar.jsx](file:///e:/Antigravity%20Project/PG%20Project/frontend/src/components/Sidebar.jsx), [TopHeader.jsx](file:///e:/Antigravity%20Project/PG%20Project/frontend/src/components/TopHeader.jsx), [ChangePassword.jsx](file:///e:/Antigravity%20Project/PG%20Project/frontend/src/pages/ChangePassword.jsx), and [ManagerDashboard.jsx](file:///e:/Antigravity%20Project/PG%20Project/frontend/src/pages/manager/ManagerDashboard.jsx).
  - [x] Rebuild the application with `npm run build` and push the changes to git.

- [x] Execution Run: Docker Compose Fail-Fast DB Health Check
  - [x] Verify `postgres` database service implements a functional `healthcheck` utilizing `pg_isready`.
  - [x] Verify `app` service depends on `postgres` with `condition: service_healthy`.
  - [x] Save and verify the completed [docker-compose.yml](file:///e:/Antigravity%20Project/PG%20Project/docker-compose.yml) structure in the root directory.

- [x] Execution Run: Enforce Real Backend Authentication (Strip Demo Quick Login)
  - [x] Completely delete the "QUICK LOGIN (DEMO)" panel and demo credentials from the Login page layout [Login.jsx](file:///e:/Antigravity%20Project/PG%20Project/frontend/src/pages/Login.jsx).
  - [x] Enforce real authentication endpoints by ensuring the `login` function in [AuthContext.jsx](file:///e:/Antigravity%20Project/PG%20Project/frontend/src/context/AuthContext.jsx) strictly requires a successful 200 OK status code from the server.
  - [x] Catch unreachable host (connection failed) and 500 internal database failure status codes in the login controller.
  - [x] Display the custom error message: "Cannot connect to the server. Please ensure the backend and database are running." on the UI.
  - [x] Validate and compile the frontend resources, and push updates to the git repository.
