# Change Default Profile to Test Task List

- [x] Configuration
  - [x] Database Seeder Overrides
  - [x] Update `DataSeeder.java` with `@Profile("!test")`
  - [x] Update `DatabaseSeeder.java` with `@Profile("!prod")` (Restored to !prod per user request)
- [x] Verification and Build
  - [x] Run `mvn clean test` on backend
  - [x] Run `npm run build` on frontend to verify compilation
