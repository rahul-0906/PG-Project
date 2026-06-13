# Refactor for Single Codebase, Config-Driven Architecture

This plan details the steps required to eliminate hardcoded database credentials, Razorpay API credentials, and tenant branding configurations. They will be dynamically pulled from the OS environment variables at runtime and served to the React frontend before a user logs in.

## User Review Required

> [!IMPORTANT]
> The application will expect the following OS environment variables to be set at runtime:
> - `DB_URL`: JDBC database URL (e.g., `jdbc:postgresql://localhost:5432/pgcrmdb`)
> - `DB_USERNAME`: Database username (e.g., `postgres`)
> - `DB_PASSWORD`: Database password (e.g., `admin`)
> - `RAZORPAY_KEY_ID`: Third-party payment gateway client ID
> - `RAZORPAY_KEY_SECRET`: Third-party payment gateway secret
> - `PG_NAME`: Complete name of the PG Accommodation (default: `PG CRM`)
> - `PG_SHORT_NAME`: Abbreviated name/prefix of the PG (default: `PG`)
> - `PG_PRIMARY_COLOR`: Branding hex code (default: `#2563eb`)

---

## Proposed Changes

### Backend Components

#### [MODIFY] [application.yml](file:///E:/Antigravity%20Project/PG%20Project/backend/src/main/resources/application.yml)
- Update `spring.datasource` variables (`url`, `username`, `password`) to bind to `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` respectively, with sensible fallbacks.
- Update `razorpay` credential keys.
- Map the branding config to `PG_NAME`, `PG_SHORT_NAME`, and `PG_PRIMARY_COLOR`.

#### [MODIFY] [application-prod.yml](file:///E:/Antigravity%20Project/PG%20Project/backend/src/main/resources/application-prod.yml)
- Update production datasource url, username, and password properties to map to `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` to be consistent with the main profile.

#### [MODIFY] [tenant-config.yml](file:///E:/Antigravity%20Project/PG%20Project/backend/src/main/resources/tenant-config.yml)
- Map `name`, `short-title`, and `primary-color` under `pg.system.branding` to use environment variables.

#### [MODIFY] [SystemConfigProperties.java](file:///E:/Antigravity%20Project/PG%20Project/backend/src/main/java/com/pgcrm/config/SystemConfigProperties.java)
- Add the `primaryColor` field to the nested static class `Branding` (with default `#2563eb`) so Spring Boot's properties binder binds it.

#### [MODIFY] [SystemConfigResponse.java](file:///E:/Antigravity%20Project/PG%20Project/backend/src/main/java/com/pgcrm/dto/SystemConfigResponse.java)
- Add `primaryColor` to `BrandingDto` and update the `fromProperties` factory builder method.

#### [NEW] [PublicConfigController.java](file:///E:/Antigravity%20Project/PG%20Project/backend/src/main/java/com/pgcrm/controller/PublicConfigController.java)
- Expose a GET endpoint at `/api/config/public` returning a JSON map with keys `pgName`, `pgShortName`, and `primaryColor`.

#### [MODIFY] [SecurityConfig.java](file:///E:/Antigravity%20Project/PG%20Project/backend/src/main/java/com/pgcrm/security/SecurityConfig.java)
- Add `/api/config/public` to the public route exception request matchers to bypass JWT security verification filter.

---

### Frontend Components

#### [MODIFY] [tailwind.config.js](file:///E:/Antigravity%20Project/PG%20Project/frontend/tailwind.config.js)
- Map the `colors.primary.DEFAULT` utility class to `var(--brand-primary)` and `colors.primary.hover` to `var(--brand-primary-hover)`.

#### [MODIFY] [index.css](file:///E:/Antigravity%20Project/PG%20Project/frontend/src/index.css)
- Define `--brand-primary` and `--brand-primary-hover` in the `:root` pseudo-class with default values.

#### [MODIFY] [App.jsx](file:///E:/Antigravity%20Project/PG%20Project/frontend/src/App.jsx)
- Fetch the public config `/api/config/public` inside a `useEffect` on initial mount.
- Set `document.title` and inject color CSS variables `--brand-primary` and `--brand-primary-hover` dynamically into `document.documentElement.style`.

---

## Verification Plan

### Automated Tests
- Run `mvn clean test` on the backend to confirm compile success and that test profiles pass successfully.

### Manual Verification
- Build the frontend (`npm run build`) and start the project.
- Verify that accessing `/api/config/public` returns the correct JSON config without authentication headers.
- Verify that changing environment variables `PG_NAME` and `PG_PRIMARY_COLOR` reflects dynamically in the browser tab title and button styling colors.
