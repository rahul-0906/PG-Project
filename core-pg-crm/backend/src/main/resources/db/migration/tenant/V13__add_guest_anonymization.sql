-- ============================================================
--  PG CRM — Add Guest Anonymization Support
-- ============================================================

-- Add columns for guest anonymization and new fields
ALTER TABLE guests ADD COLUMN is_anonymized BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE guests ADD COLUMN first_name VARCHAR(255);
ALTER TABLE guests ADD COLUMN last_name VARCHAR(255);
ALTER TABLE guests ADD COLUMN phone_number VARCHAR(255);
ALTER TABLE guests ADD COLUMN emergency_contact VARCHAR(255);
ALTER TABLE guests ADD COLUMN id_proof_url VARCHAR(255);

-- Modify existing PII columns to drop any NOT NULL constraints
ALTER TABLE guests ALTER COLUMN email DROP NOT NULL;
ALTER TABLE guests ALTER COLUMN phone DROP NOT NULL;
