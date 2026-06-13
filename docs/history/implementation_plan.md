# Implementation Plan: Null-Safe Welcome Email Bed Formatting

This plan outlines the steps to resolve potential `NullPointerException` issues and data truncation during welcome email dispatch. We will refactor the bed label stream mapping to handle cases where the bed list, individual bed objects, or bed labels are null.

## User Review Required

> [!NOTE]
> This is a refactoring of existing logic in `EmailService.java` to introduce robust null-safety checks when formatting guest bed assignments for Thymeleaf email templates.

---

## Proposed Changes

### Service Layer

#### [MODIFY] [EmailService.java](file:///E:/Antigravity%20Project/PG%20Project/backend/src/main/java/com/pgcrm/service/EmailService.java)
- In `sendGuestWelcomeEmail(final Guest guest, final String tempPassword)` and `sendReturningGuestWelcomeEmail(final Guest guest, final String tempPassword)`, refactor the stream mapping logic for beds:
  ```java
  String assignedBeds = guest.getBeds() != null
          ? guest.getBeds().stream()
                  .filter(java.util.Objects::nonNull)
                  .map(b -> b.getBedLabel() != null ? b.getBedLabel() : "Unnamed Bed")
                  .collect(java.util.stream.Collectors.joining(", "))
          : "Unassigned";
  ```
- Ensure both `bedLabel` and `assignedBeds` context variables are populated using this clean, safe string.

---

## Verification Plan

### Automated Tests
- Run `mvn clean test` to ensure compilation and tests pass successfully.
