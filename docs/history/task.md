# Database Reset & Test Profile Configuration Task List

- [x] Configuration
  - [x] Create `application-test.yml` with `ddl-auto: create` and `flyway.enabled: false`
- [x] Database Seeder Overrides
  - [x] Update `DataSeeder.java` with `@Profile("!test")`
  - [x] Update `DatabaseSeeder.java` with `@Profile("!prod & !test")`
- [x] Verification and Build
  - [x] Run `mvn clean test` on backend
  - [x] Run `npm run build` on frontend to verify compilation
