# Implementation Plan: Dedicated Test Profile and Seeder Neutralization

This plan outlines the design and steps to configure a dedicated `test` profile. This profile will guarantee a 100% empty, freshly created database on startup by disabling Flyway migrations and using Hibernate's `ddl-auto: create` to drop and recreate the schema. All seeders will be neutralized under the `test` profile using Spring's `@Profile` annotations.

## User Review Required

> [!WARNING]
> - **Empty Test Database**: The `test` profile (defined in `application-test.yml`) will set `spring.jpa.hibernate.ddl-auto=create` and disable Flyway (`spring.flyway.enabled=false`). Running the application with this active profile drops all existing tables and leaves the database completely empty of any schema migrations and data.
> - **Spring Profile Expression Syntax**: For `DatabaseSeeder.java`, we will use `@Profile("!prod & !test")` (or the equivalent Spring 5.1+ profile expression) to guarantee it executes *only* when neither `prod` nor `test` is active. Using the array format `@Profile({"!prod", "!test"})` is resolved as an `OR` condition by Spring, which would incorrectly allow the seeder to run under `test` or `prod`.

---

## Proposed Changes

### Configuration

#### [NEW] [application-test.yml](file:///E:/Antigravity%20Project/PG%20Project/backend/src/main/resources/application-test.yml)
- Configure `spring.jpa.hibernate.ddl-auto` to `create` (or `create-drop`) to physically drop and recreate all tables empty on startup.
- Disable Flyway migrations (`spring.flyway.enabled: false`) to bypass all migration SQL scripts.

---

### Seeder Components

#### [MODIFY] [DataSeeder.java](file:///E:/Antigravity%20Project/PG%20Project/backend/src/main/java/com/pgcrm/seeder/DataSeeder.java)
- Add the `@Profile("!test")` annotation to ensure this legacy seeder never runs when the `test` profile is active.

#### [MODIFY] [DatabaseSeeder.java](file:///E:/Antigravity%20Project/PG%20Project/backend/src/main/java/com/pgcrm/seeder/DatabaseSeeder.java)
- Update the profile annotation from `@Profile("!prod")` to `@Profile("!prod & !test")` (or `@Profile("!prod and !test")`) to ensure this seeder never runs in either production or test environments.

---

## Verification Plan

### Automated Tests
- Run `mvn clean test` to verify the project compiles correctly and all existing tests pass.

### Manual Verification
- Start the application with `-Dspring.profiles.active=test` and verify that the database is completely empty (no migrations, no users, no seeded owner account).
