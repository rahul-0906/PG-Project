# PG CRM — Unified UI/UX Layout & Style Draft

**Status:** Draft for Review  
**Design Inspiration:** RonDesignLab *Tenancy* Property SaaS (adapted for PG/Hostel bed-level operations)  
**Target Stack:** React 18, Tailwind CSS, Lucide Icons, `Plus Jakarta Sans` + `Inter`

---

## 1. Design Philosophy & Goals

1. **Glanceable Hierarchy**: Answer the operator's 3 daily questions on the first screen without extra clicks:
   - *Which beds are vacant right now?*
   - *Who hasn't paid rent this month?*
   - *What maintenance or meal tasks need action today?*
2. **Modular Asymmetric Grid**: Replace stacked full-width blocks with balanced **2/3 primary workspace + 1/3 action/alert sidebar** layouts on dashboards.
3. **Humanized Data Presentation**: Pair circular user/resident avatars (initials) with clean status pills (`VACANT`, `OCCUPIED`, `NOTICE`, `PAID`, `OVERDUE`) and inline quick actions.
4. **Multi-Tenant Safe Branding**: Keep the canvas strictly neutral (`#f8fafc` app background, `#ffffff` elevated cards) so every tenant's custom `primaryColor` from `tenant_settings` looks crisp and cohesive.

---

## 2. Design Tokens & Color System

### 2.1 Surfaces & Borders (`core-pg-crm/frontend/src/index.css`)
| Token | Current | Proposed Refinement | Purpose |
| :--- | :--- | :--- | :--- |
| `--bg-app` | `#f8fafc` | `#f8fafc` (or `#f6f7f9` warm-neutral) | Soft canvas so white cards float cleanly |
| `--bg-surface` | `#ffffff` | `#ffffff` | Elevated cards, modals, and tables |
| `--border` | `#e2e8f0` | `#e2e8f0` (hover `#cbd5e1`) | Crisp 1px card & table dividers |
| Card Radius | `rounded-xl` (12px) | `rounded-2xl` (16px) | Modern SaaS card softness |
| Card Shadow | `0 4px 6px -1px rgb(0 0 0 / 0.05)` | `0 1px 3px rgba(15, 23, 42, 0.04), 0 6px 16px rgba(15, 23, 42, 0.04)` | Lighter, cleaner ambient elevation |

### 2.2 Accent & Semantic Status Palette
- **Primary Brand Accent**: Driven dynamically by `var(--brand-primary)` (defaults to `#2563eb`; tenants can customize via `TenantSettings`). Reserved strictly for primary CTAs, active sidebar indicator, and key focus states.
- **Semantic Status Pills** (soft tinted background + crisp border + bold uppercase text):
  - **Success / Vacant / Paid**: `bg-emerald-50 text-emerald-700 border-emerald-200`
  - **Warning / Notice / Pending**: `bg-amber-50 text-amber-700 border-amber-200`
  - **Danger / Overdue / Urgent**: `bg-rose-50 text-rose-700 border-rose-200`
  - **Neutral / Occupied / Info**: `bg-slate-100 text-slate-700 border-slate-200`

### 2.3 Typography
- **Headings (`font-heading`)**: `Plus Jakarta Sans` — weights `600` (cards/modals), `700` (page titles & KPIs).
- **Body & Tables (`font-sans`)**: `Inter` — tabular numerals (`tabular-nums`) for all currency (`₹`), room numbers, and meter readings so columns align cleanly.

---

## 3. Global Application Shell (`AppLayout`, `Sidebar`, `TopHeader`)

```
┌──────────────────┬───────────────────────────────────────────────────────────┐
│  [Logo] PG CRM   │  Page Title / Breadcrumb     [Branch ▼]  [🔔]  [Avatar ▼] │
├──────────────────┼───────────────────────────────────────────────────────────┤
│                  │                                                           │
│  ▪ Dashboard     │                                                           │
│  ▪ Guests        │                 MAIN CONTENT WORKSPACE                    │
│  ▪ Meals & Addons│                 (max-w-[1440px] mx-auto)                  │
│  ▪ EB Bill       │                                                           │
│  ▪ Invoices      │                                                           │
│  ▪ Pricing       │                                                           │
│  ▪ Maintenance   │                                                           │
│  ▪ Reports       │                                                           │
│  ▪ Audit Log     │                                                           │
│  ▪ Settings      │                                                           │
│                  │                                                           │
│  [→ Logout]      │                                                           │
└──────────────────┴───────────────────────────────────────────────────────────┘
```

### Refinements:
- **Sidebar**: Soft border-right (`border-slate-200/80`), grouped navigation sections (*Operations*, *Billing & Finance*, *Administration*), subtle pill highlight for the active route (`bg-primary/10 text-primary font-semibold`).
- **TopHeader**: Sticky translucent header (`bg-white/90 backdrop-blur-md border-b border-slate-200/80`), quick `+ Check-In` shortcut button for managers, branch selector pill, notification bell, and user profile menu.

---

## 4. Page Layout Blueprints

### 4.1 Owner & Manager Operations Dashboard (`/owner/dashboard`, `/manager/dashboard`)
*Note: Unlike apartment-rental apps that show a city street map, PG CRM replaces the map with a **Live Occupancy & Collections Summary**.*

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  KPI Strip (4 Equal Cards)                                                  │
│  [ Occupancy % ]   [ Vacant Beds ]   [ Month Collected ]   [ Pending Dues ] │
├──────────────────────────────────────────────┬──────────────────────────────┤
│  LEFT (2/3 Width - Primary Analytics)        │  RIGHT (1/3 - Action Feed)   │
│                                              │                              │
│  1. Revenue & Collection Chart (Recharts)    │  Needs Attention Today:      │
│     (Collected vs. Pending by Month)         │  • 4 Overdue Invoices [View] │
│                                              │  • 2 Open Tickets    [Fix]   │
│  2. Floor-by-Floor Bed Occupancy Bar         │  • Today's Meal Headcount    │
│     (Ground: 18/20, 1st: 15/20, 2nd: 12/20)  │    (B: 42 | L: 38 | D: 45)   │
└──────────────────────────────────────────────┴──────────────────────────────┘
```

### 4.2 Guest Management & Bed Matrix (`/manager/guests`)
- **Top Section**: Interactive Floor Tabs + Room/Bed Availability Grid (`VACANT` green pills, `OCCUPIED` slate pills, `NOTICE` amber pills). Clicking a vacant bed opens the Check-In modal.
- **Bottom Section**: Unified Search & Filter Bar (`Search name/phone/room...` + `Floor Filter ▼` + `Excel Import` + `Export`) above a clean avatar-enabled Guest Table.
- **Modals**: Strictly bounded to `max-h-[90vh]` flex columns with `overflow-y-auto` body and pinned `Cancel` / `Confirm` footer bar.

### 4.3 Resident Portal (`/guest/dashboard`)
- **Top Hero Card**: Welcome banner + Assigned Room/Bed card + Monthly Rent summary.
- **Asymmetric 2-Column Body**:
  - **Left**: Latest Invoice card with instant **Pay Online (Razorpay)** primary CTA + Payment History list.
  - **Right**: Today's Meal Opt-in/Out quick toggle + Active Maintenance Tickets status.

### 4.4 High-Density Operational Pages (`Meals & Add-ons`, `EB Bill`, `Invoices`)
- Retain compact, keyboard-friendly table density (`table-compact`) for fast bulk data entry.
- Wrap tables inside `.card` / `.table-wrap` with sticky headers, `tabular-nums` alignment on all numbers, and clean inline action buttons.

---

## 5. Implementation Rollout Plan

| Phase | Scope | Files Affected |
| :--- | :--- | :--- |
| **Phase 1: Global Tokens & Shell** | Update card radius/shadows, status badges, table typography (`tabular-nums`), and shell spacing | `index.css`, `tailwind.config.js`, `AppLayout.jsx`, `Sidebar.jsx`, `TopHeader.jsx` |
| **Phase 2: Dashboards** | Apply 4-card KPI strip + 2/3 & 1/3 asymmetric grid to Owner, Manager, and Guest dashboards | `OwnerDashboard.jsx`, `ManagerDashboard.jsx`, `GuestDashboard.jsx` |
| **Phase 3: Operational Views** | Align filter bars, avatar cells, status pills, and modal bounds across operational pages | `ManagerGuests.jsx`, `ManagerInvoices`, `ManagerEbBill.jsx`, `ManagerMaintenance.jsx`, `Reports.jsx` |

---

## 6. Next Step

Review this draft and let me know:
1. Should we keep the current brand blue (`#2563eb`) as the default primary color, or switch the default theme to a warmer accent (like the *Tenancy* terracotta/orange)?
2. Once approved, we can start implementing **Phase 1 (Global Tokens & Shell)** and **Phase 2 (Dashboards)**.
