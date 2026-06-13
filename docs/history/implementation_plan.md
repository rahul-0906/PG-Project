# Implementation Plan: Change Server Default Profile to Test

This plan outlines the steps to change the server startup script and environment configuration to use the `test` profile instead of `dev` by default.

## Proposed Changes

### Startup Script

#### [MODIFY] [start_project.bat](file:///E:/Antigravity%20Project/PG%20Project/start_project.bat)
- Update the backend startup maven command to pass `-Dspring-boot.run.profiles=test` (or set `SPRING_PROFILES_ACTIVE=test` environment variable) to ensure the server starts under the `test` profile by default.
  ```cmd
  start "PG CRM Backend" cmd /k "cd /d %ROOT_DIR%backend && ..\apache-maven-3.9.16\bin\mvn spring-boot:run -Dspring-boot.run.profiles=test"
  ```

### Environment Configuration

#### [MODIFY] [.env](file:///E:/Antigravity%20Project/PG%20Project/.env)
- Append `SPRING_PROFILES_ACTIVE=test` as the default active profile.

---

## Verification Plan

### Automated Tests
- Run `mvn clean test` to verify no compilation errors.

### Manual Verification
- Execute `start_project.bat` and verify that the Spring Boot console output logs that the `test` profile is active.
