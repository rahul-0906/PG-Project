# Implementation Plan: Database Profile Configuration and Guarded Database Seeder

This plan outlines the design and steps to configure a profile-specific database strategy. We will ensure that the destructive schema wipe (`ddl-auto: create`) runs only in the `dev` profile, while the `prod` profile explicitly uses the production-safe standard. Furthermore, a profile-guarded `DatabaseSeeder` will seed the master Owner account only outside of production.

## User Review Required

> [!WARNING]
> - **Destructive Wipe in Dev Profile**: The `dev` profile (defined in `application-dev.yml`) will set `spring.jpa.hibernate.ddl-auto=create` and disable Flyway (`spring.flyway.enabled=false`). This drops and recreates the database schema every time the application is started in `dev` mode.
> - **Production Safe Guard**: The `prod` profile (defined in `application-prod.yml`) will explicitly set `spring.jpa.hibernate.ddl-auto=validate` (or `update`) to prevent any accidental schema dropping.
> - **Seeder Execution Guard**: The new `DatabaseSeeder` will be annotated with `@Profile("!prod")` to prevent execution in production.

---

## Proposed Changes

### Configuration

#### [NEW] [application-dev.yml](file:///E:/Antigravity%20Project/PG%20Project/backend/src/main/resources/application-dev.yml)
- Configure `spring.jpa.hibernate.ddl-auto` to `create` to cleanly wipe and rebuild tables on startup in the `dev` environment.
- Disable Flyway migrations (`spring.flyway.enabled: false`) to avoid conflicts with Hibernate-driven schema generation.
- Add warning comments to remind developers that this profile is destructive.

#### [MODIFY] [application-prod.yml](file:///E:/Antigravity%20Project/PG%20Project/backend/src/main/resources/application-prod.yml)
- Explicitly set/confirm `spring.jpa.hibernate.ddl-auto` to `validate` (or `update`) to protect the database structure from destructive changes.

#### [MODIFY] [application.yml](file:///E:/Antigravity%20Project/PG%20Project/backend/src/main/resources/application.yml)
- Keep default `spring.jpa.hibernate.ddl-auto` to `validate` (or `update`) for out-of-the-box safe running without profiles.

---

### Seeder Component

#### [NEW] [DatabaseSeeder.java](file:///E:/Antigravity%20Project/PG%20Project/backend/src/main/java/com/pgcrm/seeder/DatabaseSeeder.java)
- Create a new `@Component` named `DatabaseSeeder` implementing `CommandLineRunner`.
- Annotate the class with `@Profile("!prod")` to prevent execution when the `prod` profile is active.
- Inject `UserRepository` and `PasswordEncoder`.
- Implement `run(String... args)` method:
  - Check if `userRepository.count() == 0`.
  - If `0`, instantiate and save a master `User` entity using Lombok `@Builder` or setters:
    - `fullName`: `"System Owner"`
    - `email`: `"owner@pgcrm.com"`
    - `password`: `passwordEncoder.encode("Admin@123")`
    - `role`: `Role.PG_OWNER`
    - `active`: `true`
    - `firstLogin`: `false`
    - `mustChangePassword`: `false`
  - Print clearly formatted `System.out.println` startup logs:
    - `"========================================="`
    - `"DATABASE INITIALIZED & OWNER SEEDED"`
    - `"Email: owner@pgcrm.com"`
    - `"Role: PG_OWNER"`
    - `"========================================="`

---

## Verification Plan

### Automated Tests
- Run `mvn clean test` to verify the codebase builds and compiles correctly with the new files.

### Manual Verification
- Run the application with `-Dspring.profiles.active=dev` and confirm the tables are recreated and the seeder initializes the Owner account.
- Run the application with `-Dspring.profiles.active=prod` and confirm the seeder is bypassed and `ddl-auto` is set to the safe standard (`validate`/`update`).
