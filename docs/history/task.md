# Database Reset & Owner Seeding Task List

- [x] Schema Wipe & Profile Configuration
  - [x] Create `application-dev.yml` with `ddl-auto: create` and `flyway.enabled: false`
  - [x] Modify `application-prod.yml` to explicitly set `ddl-auto: validate` or `update`
- [x] Database Seeder Component
  - [x] Create `DatabaseSeeder.java` class under `com.pgcrm.seeder`
  - [x] Annotate `DatabaseSeeder` with `@Profile("!prod")`
  - [x] Inject `UserRepository` and `PasswordEncoder`
  - [x] Implement `CommandLineRunner` with `userRepository.count() == 0` check
  - [x] Create and save Owner user with specified attributes
  - [x] Add `System.out.println` statements for initialization logs
- [x] Verification and Build
  - [x] Run `mvn clean test` on backend
  - [x] Run `npm run build` on frontend to verify compilation
