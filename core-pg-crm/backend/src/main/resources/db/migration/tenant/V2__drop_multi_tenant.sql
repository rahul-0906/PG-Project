-- ============================================================
--  PG CRM — Single-Tenant Migration Script
--  Run this ONCE against your PostgreSQL database to:
--    1. Drop the obsolete multi-tenant tables
--    2. Remove tenant_id columns from all entity tables
-- ============================================================

-- STEP 1: Drop multi-tenant management tables (no longer needed)
DROP TABLE IF EXISTS tenant_config CASCADE;
DROP TABLE IF EXISTS tenant CASCADE;

-- STEP 2: Remove tenant_id column from every entity table
--         (If you are using H2 for dev, run these as needed)

ALTER TABLE users         DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE guests        DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE buildings     DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE floors        DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE blocks        DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE rooms         DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE beds          DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE invoices      DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE invoice_line_items DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE eb_bills      DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE eb_bill_guests DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE daily_logs    DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE audit_logs    DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE maintenance_tickets DROP COLUMN IF EXISTS tenant_id;
ALTER TABLE notifications DROP COLUMN IF EXISTS tenant_id;

-- STEP 3: Drop any remaining Hibernate tenant filter indexes
DROP INDEX IF EXISTS idx_users_tenant;
DROP INDEX IF EXISTS idx_guests_tenant;
DROP INDEX IF EXISTS idx_buildings_tenant;

-- ============================================================
-- NOTE: If using spring.jpa.hibernate.ddl-auto=update (H2/dev),
-- Hibernate will ignore unmapped columns automatically.
-- This script is intended for production PostgreSQL upgrades.
-- ============================================================
