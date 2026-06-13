# Config-Driven Refactoring Task List

- [x] Backend Configuration
  - [x] Refactor [application.yml](file:///E:/Antigravity%20Project/PG%20Project/backend/src/main/resources/application.yml) environment variable mappings
  - [x] Refactor [application-prod.yml](file:///E:/Antigravity%20Project/PG%20Project/backend/src/main/resources/application-prod.yml) database credentials
  - [x] Refactor [tenant-config.yml](file:///E:/Antigravity%20Project/PG%20Project/backend/src/main/resources/tenant-config.yml) branding properties
  - [x] Update [SystemConfigProperties.java](file:///E:/Antigravity%20Project/PG%20Project/backend/src/main/java/com/pgcrm/config/SystemConfigProperties.java)
  - [x] Update [SystemConfigResponse.java](file:///E:/Antigravity%20Project/PG%20Project/backend/src/main/java/com/pgcrm/dto/SystemConfigResponse.java)
  - [x] Create [PublicConfigController.java](file:///E:/Antigravity%20Project/PG%20Project/backend/src/main/java/com/pgcrm/controller/PublicConfigController.java)
  - [x] Add exception mapping in [SecurityConfig.java](file:///E:/Antigravity%20Project/PG%20Project/backend/src/main/java/com/pgcrm/security/SecurityConfig.java)
- [x] Frontend Configuration
  - [x] Update [tailwind.config.js](file:///E:/Antigravity%20Project/PG%20Project/frontend/tailwind.config.js) colors theme mapping
  - [x] Refactor [index.css](file:///E:/Antigravity%20Project/PG%20Project/frontend/src/index.css) variables mapping
  - [x] Refactor [App.jsx](file:///E:/Antigravity%20Project/PG%20Project/frontend/src/App.jsx) mount hook theme injector
- [x] Verification and Testing
  - [x] Backend test execution (`mvn clean test`)
  - [x] Frontend production build validation (`npm run build`)
- [x] Dynamic Seeder Refactoring
  - [x] Refactor [DatabaseSeeder.java](file:///E:/Antigravity%20Project/PG%20Project/backend/src/main/java/com/pgcrm/seeder/DatabaseSeeder.java) to pull owner credentials dynamically
