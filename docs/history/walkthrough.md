# Single Codebase, Config-Driven Architecture Walkthrough

This walkthrough details the changes made to adopt a strictly dynamic, config-driven architecture. Hardcoded database credentials, payment gateway keys, and client names/branding colors have been externalized and are injected via OS environment variables.

## Changes Made

### 1. Backend Configuration & Properties
- **Modified** [application.yml](file:///E:/Antigravity%20Project/PG%20Project/backend/src/main/resources/application.yml):
  - Datasource URL, username, and password configured to dynamically pull from `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`.
  - Added new `pg.system.branding` properties mapping `name`, `short-title`, and `primary-color` to `PG_NAME`, `PG_SHORT_NAME`, and `PG_PRIMARY_COLOR` environment variables.
- **Modified** [application-prod.yml](file:///E:/Antigravity%20Project/PG%20Project/backend/src/main/resources/application-prod.yml):
  - Standardized datasource credentials variables to use `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` consistently across dev/prod environments.
- **Modified** [tenant-config.yml](file:///E:/Antigravity%20Project/PG%20Project/backend/src/main/resources/tenant-config.yml):
  - Refactored config properties to delegate branding details to environment variables `PG_NAME`, `PG_SHORT_NAME`, and `PG_PRIMARY_COLOR`.
- **Modified** [SystemConfigProperties.java](file:///E:/Antigravity%20Project/PG%20Project/backend/src/main/java/com/pgcrm/config/SystemConfigProperties.java):
  - Added `primaryColor` field with default value `#2563eb` to the static nested configuration class `Branding`.
- **Modified** [SystemConfigResponse.java](file:///E:/Antigravity%20Project/PG%20Project/backend/src/main/java/com/pgcrm/dto/SystemConfigResponse.java):
  - Exposed `primaryColor` under the authenticated system configuration DTO `BrandingDto` and added its mapping builder logic.

### 2. Public Config API Endpoint
- **Created** [PublicConfigController.java](file:///E:/Antigravity%20Project/PG%20Project/backend/src/main/java/com/pgcrm/controller/PublicConfigController.java):
  - Created a public REST API endpoint at `/api/config/public` returning a JSON map containing `pgName`, `pgShortName`, and `primaryColor`.
- **Modified** [SecurityConfig.java](file:///E:/Antigravity%20Project/PG%20Project/backend/src/main/java/com/pgcrm/security/SecurityConfig.java):
  - Added `/api/config/public` to `requestMatchers().permitAll()` allowing the endpoint to bypass JWT authentication verification on initial application loading.

### 3. Frontend Theme Injection & Styling
- **Modified** [App.jsx](file:///E:/Antigravity%20Project/PG%20Project/frontend/src/App.jsx):
  - Implemented a `useEffect` hook on mount to fetch `/api/config/public`.
  - Sets browser tab title (`document.title`) dynamically using `pgName`.
  - Dynamically computes a hover color (10% darker) and injects both `--brand-primary` and `--brand-primary-hover` variables into `document.documentElement.style`.
- **Modified** [tailwind.config.js](file:///E:/Antigravity%20Project/PG%20Project/frontend/tailwind.config.js):
  - Refactored `primary` color theme mappings to route through `var(--brand-primary)` (default) and `var(--brand-primary-hover)` (hover).
- **Modified** [index.css](file:///E:/Antigravity%20Project/PG%20Project/frontend/src/index.css):
  - Declared the default fallback values for CSS root variables `--brand-primary` and `--brand-primary-hover`.
- **Modified** [DatabaseSeeder.java](file:///E:/Antigravity%20Project/PG%20Project/backend/src/main/java/com/pgcrm/seeder/DatabaseSeeder.java):
  - Configured `@Profile("!prod")` per explicit user request. This allows the seeder to run under the `test` profile.
  - Refactored the seeder to dynamically load the default owner's name, email, and password using Spring's `@Value` annotation with safe fallback defaults. It prints the dynamic credentials in the startup console banner.

---

## Verification & Build Results

### 1. Backend Compilation & Tests
- Executed `mvn clean test` in the `backend` directory.
- **Result**: `BUILD SUCCESS`, all 14 tests completed successfully with no compilation errors.

### 2. Frontend Production Build
- Executed `npm run build` in the `frontend` directory.
- **Result**: Frontend compiled and bundled successfully in `10.91s` with no warnings/errors.
