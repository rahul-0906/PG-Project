# Change Default Profile to Test Task List

- [x] Configuration
  - [x] Modify `start_project.bat` to run with `-Dspring-boot.run.profiles=test`
  - [x] Modify `.env` to include `SPRING_PROFILES_ACTIVE=test`
- [x] Verification and Build
  - [x] Run `mvn clean test` on backend
  - [x] Run `npm run build` on frontend to verify compilation
