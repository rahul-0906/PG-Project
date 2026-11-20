-- ============================================================
--  PG CRM — Add check_in_time column to guests table
-- ============================================================

ALTER TABLE guests ADD COLUMN check_in_time TIME;
