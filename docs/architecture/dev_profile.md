# Dev Profile: Database Reset & Persistence Strategy

This document details the strategy for managing local database state in PG CRM during development using the `dev` profile and the `DB_WIPE_ON_STARTUP` environment variable toggle.

---

## Overview

During development, it is often necessary to reset the local database to a clean, well-defined initial state with demo data. However, for subsequent development runs, you want database modifications and user data to persist across application restarts.

We use **Flyway Database Migrations** with a toggleable startup configuration to achieve this behavior.

---

## Local Database Strategy

The database clean-and-migration behavior is controlled by the `DB_WIPE_ON_STARTUP` environment variable, which is parsed by the `[PG-CORE]` Spring Boot backend when starting under the `dev` profile.

> [!CAUTION]
> **`DB_WIPE_ON_STARTUP=true` is ONLY effective in the `dev` and `test` profiles.**
> `FlywayConfig.java` is annotated `@Profile("dev | test")` — it cannot load in `prod`.
> `application-prod.yml` also enforces `spring.flyway.clean-disabled: true` as a double safeguard.

### Phase 1: The Wipe (Initial Boot)

To completely wipe your local `pgcrmdb` and populate it with fresh schema structures and seeded data:

1. Open your root `.env` file at `E:\PG-Project\.env`.
2. Add or update:
   ```env
   DB_WIPE_ON_STARTUP=true
   ```
3. Run `scripts/dev/start_core.bat`.
4. On startup, Flyway will:
   - Drop all existing tables, views, and schemas inside `pgcrmdb`.
   - Run all migration scripts from scratch.
   - Trigger `DatabaseSeeder` to load the default SUPER_ADMIN account.

### Phase 2: Persistence (Subsequent Runs)

Once databases are set up and seeded, disable the wipe to preserve your local data:

1. Open `.env`.
2. Set:
   ```env
   DB_WIPE_ON_STARTUP=false
   ```
3. Restart the application.
4. Flyway will skip the clean operation, only applying new migrations and preserving all records.

---

## Technical Details

The `dev` profile config enables Flyway clean:

```yaml
# application-dev.yml
spring:
  flyway:
    clean-disabled: false
```

The `prod` profile explicitly disables it:

```yaml
# application-prod.yml
spring:
  flyway:
    clean-disabled: true  # CRITICAL: Never allow clean in production
```

And `FlywayConfig.java` is guarded at the class level:

```java
@Configuration
@Profile("dev | test")   // Never loads in prod
public class FlywayConfig { ... }
```

---

## Verification Steps

### Step 1: Arm the Wipe Toggle
Set `DB_WIPE_ON_STARTUP=true` in `.env` and save.

### Step 2: Launch the Core Application
Run `scripts/dev/start_core.bat` and watch the backend terminal.

### Step 3: Verify the Logs
You should see:
```
INFO ... Flyway cleaned database "pgcrmdb"
INFO ... Migrating schema "public" to version "1 - init schema"
...
INFO ... Successfully applied N migrations
```

### Step 4: Test Persistence
1. Stop the servers.
2. Set `DB_WIPE_ON_STARTUP=false` in `.env`.
3. Restart.
4. Verify: `Schema "public" is up to date. No migration necessary.`
