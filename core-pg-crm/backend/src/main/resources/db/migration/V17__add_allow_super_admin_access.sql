-- ============================================================
--  PG CRM — Migration V17: Support PG Owner Super Admin Access Permission Flag
-- ============================================================

ALTER TABLE users ADD COLUMN IF NOT EXISTS allow_super_admin_access BOOLEAN NOT NULL DEFAULT FALSE;
