# Default Server Profile Set to Test Walkthrough

This walkthrough details the changes made to switch the default server execution profile from `dev` to `test` in the local launcher script and environment files.

## Changes Made

### 1. Launcher Script Update
- **Modified** [start_project.bat](file:///E:/Antigravity%20Project/PG%20Project/start_project.bat):
  - Updated the backend Maven run command to set `-Dspring-boot.run.profiles=test`, forcing the Spring Boot application to run under the `test` profile when launched via this script.

### 2. Environment Variables Configuration
- **Modified** [.env](file:///E:/Antigravity%20Project/PG%20Project/.env):
  - Appended `SPRING_PROFILES_ACTIVE=test` as the active environment profile.
- **Modified** [.env.example](file:///E:/Antigravity%20Project/PG%20Project/.env.example):
  - Appended `SPRING_PROFILES_ACTIVE=test` to the variables template file.

---

## Verification & Build Results

### 1. Backend Compilation & Tests
- Executed `mvn clean test` in the `backend` directory.
- **Result**: `BUILD SUCCESS`, all 14 tests completed successfully with no compilation errors.

### 2. Frontend Production Build
- Executed `npm run build` in the `frontend` directory.
- **Result**: Frontend compiled and bundled successfully in `13.41s` with no warnings/errors.
