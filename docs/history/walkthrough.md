# Database Reset, Profile Safeguards, and Owner Account Seeding Walkthrough

This walkthrough details the changes made to configure a profile-specific database strategy, allowing development resets while guaranteeing production safety, and the addition of a guarded database seeder for the master "Owner" account.

## Changes Made

### 1. Spring Profiles & Safeguards Configuration

To ensure the destructive schema wipe (`ddl-auto: create`) is never executed in a production environment, we configured Spring Boot profile overrides:

- **Created** [application-dev.yml](file:///E:/Antigravity%20Project/PG%20Project/backend/src/main/resources/application-dev.yml):
  - Active when the `dev` profile is specified.
  - Configures `spring.jpa.hibernate.ddl-auto` to `create` to cleanly wipe and rebuild tables on startup.
  - Configures `spring.flyway.enabled` to `false` to disable Flyway migrations and prevent conflicts with Hibernate-driven schema generation.
- **Modified** [application-prod.yml](file:///E:/Antigravity%20Project/PG%20Project/backend/src/main/resources/application-prod.yml):
  - Explicitly set `spring.jpa.hibernate.ddl-auto` to `validate` to validate the schema upon startup in production, ensuring no schema drop or alter commands are executed automatically.

### 2. Database Seeder Component

- **Created** [DatabaseSeeder.java](file:///E:/Antigravity%20Project/PG%20Project/backend/src/main/java/com/pgcrm/seeder/DatabaseSeeder.java):
  - Created a new `@Component` named `DatabaseSeeder` implementing `CommandLineRunner`.
  - Annotated the class with `@Profile("!prod")` to ensure it only executes in environments outside of production (e.g., development, local testing).
  - Injected `UserRepository` and `PasswordEncoder`.
  - Implements a safety check inside the `run()` method (`if (userRepository.count() == 0)`) to instantiate the master Owner account when the users table is empty:
    - **Full Name**: `"System Owner"`
    - **Email**: `"owner@pgcrm.com"`
    - **Password**: Encoded version of `"Admin@123"`
    - **Role**: `Role.PG_OWNER`
    - **Active status**: `true`
    - **First Login / Must Change Password**: `false`
  - Saves the owner user record to the database and logs a clear banner in the server console:
    ```
    =========================================
    DATABASE INITIALIZED & OWNER SEEDED
    Email: owner@pgcrm.com
    Role: PG_OWNER
    =========================================
    ```

---

## Verification & Build Results

### 1. Backend Compilation & Tests
- Executed `mvn clean test` in the `backend` directory.
- **Result**: `BUILD SUCCESS`, all 14 tests completed successfully with no compilation errors.

### 2. Frontend Production Build
- Executed `npm run build` in the `frontend` directory.
- **Result**: Frontend compiled and bundled successfully in `12.88s` with no warnings/errors.
