-- ============================================================
--  PG CRM — Add Allowed Payment Modes Config (V6)
-- ============================================================

ALTER TABLE building_configs ADD COLUMN allowed_payment_modes VARCHAR(255) NOT NULL DEFAULT 'BOTH';
