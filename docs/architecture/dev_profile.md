# Dev Profile: Database Reset & Persistence Strategy

This document details the strategy for managing local database state in the PG CRM B2B SaaS Monorepo using the development environment profile (`dev`) and the `DB_WIPE_ON_STARTUP` environment variable toggle.

---

## Overview

During development, it is often necessary to reset the local database to a clean, well-defined initial state with demo data. However, for subsequent development runs, you want database modifications and user data to persist across application restarts.

We use **Flyway Database Migrations** with a toggleable startup configuration to achieve this behavior.

---

## Local Database Strategy

The database clean-and-migration behavior is controlled by the `DB_WIPE_ON_STARTUP` environment variable, which is parsed by both the `[PG-CORE]` and `[CONTROL-PLANE]` Spring Boot backend modules when starting under the `dev` profile.

### Phase 1: The Wipe (Initial Boot)

To completely wipe your local database instances (`pgcrmdb` and `controlplane_db`) and populate them with fresh schema structures and seeded data:

1. Open your root `.env` file located at the root of the monorepo workspace.
2. Add or update the following environment variable to `true`:
   ```env
   DB_WIPE_ON_STARTUP=true
   ```
3. Run the application launcher (`start_core.bat` or `start_control.bat`).
4. On startup, Flyway will:
   - Check if clean operations are allowed (`spring.flyway.clean-disabled=false`).
   - Drop all existing tables, views, and schemas inside the targeted database.
   - Run the baseline and migration scripts from scratch.
   - Trigger the system `DatabaseSeeder` to load default data.

### Phase 2: Persistence (Subsequent Runs)

Once the initial databases are set up and seeded, you want to preserve your local data across restarts. To disable the automatic wipe:

1. Open the root `.env` file.
2. Set the toggle value to `false`:
   ```env
   DB_WIPE_ON_STARTUP=false
   ```
3. Start the applications.
4. Flyway will skip the clean operation on boot, validating existing tables and only applying any new migration scripts, preserving all persisted records.

---

## Technical Details

The Dev profile configurations map the environment variable to Flyway's `clean-at-start` property:

```yaml
spring:
  flyway:
    clean-disabled: false
    clean-at-start: ${DB_WIPE_ON_STARTUP:false}
```

> [!WARNING]
> **Production Safety:**
> Under the production configuration (`application-prod.yml`), Flyway's clean feature is strictly disabled (`spring.flyway.clean-disabled=true`) and `DB_WIPE_ON_STARTUP` is ignored to prevent accidental data loss in live environments.


Step 1: Arm the Wipe Toggle
Open your root .env file (E:\Antigravity Project\PG Project\.env).

Ensure this exact line is present:
DB_WIPE_ON_STARTUP=true

Save the file.

Step 2: Launch the Core Application
Double-click your updated start_core.bat file.

Watch the initial black terminal window. Make sure it prints:  - Loaded: DB_WIPE_ON_STARTUP=true.

Step 3: Verify the Logs (The Proof)
Keep your eyes on the "PG CORE Backend" terminal window as Spring Boot starts up. Because we added the Java Bean, you are looking for this exact sequence of events in the logs:

The Clean: You should see a log explicitly stating Flyway is dropping the schema:
INFO ... Flyway cleaned database "pgcrmdb" (or similar wording indicating the drop).

The Rebuild: Immediately after the clean, you should see Flyway migrating all 12 of your existing scripts from scratch:
INFO ... Migrating schema "public" to version "1 - init schema"
...
INFO ... Successfully applied 12 migrations

The Seeder: Finally, because the database was wiped, your DataSeeder should run an INSERT command for your Super Admin, rather than an UPDATE.

Step 4: Test Persistence (Crucial)
Once the server has fully booted and you confirmed the wipe:

Stop the servers completely.

Open your .env file and change it to: DB_WIPE_ON_STARTUP=false.

Run start_core.bat one more time.

Verify the logs say: Schema "public" is up to date. No migration necessary.

If you see the database cleanly drop and rebuild on true, and safely persist on false, your automated local environment is 100% verified and bulletproof.

Run this test now and let me know if you see the successful wipe in the terminal!
