-- ============================================================
--  PG CRM — Add is_deleted column for soft delete support
-- ============================================================

ALTER TABLE guests ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE invoices ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE daily_logs ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
