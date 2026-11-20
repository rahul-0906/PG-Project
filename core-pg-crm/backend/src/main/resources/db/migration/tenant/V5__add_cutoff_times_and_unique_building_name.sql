-- ============================================================
--  PG CRM — Add Dynamic Cutoff Times and Unique Building Name (V5)
-- ============================================================

ALTER TABLE buildings ADD CONSTRAINT uq_building_name UNIQUE (name);

ALTER TABLE building_configs ADD COLUMN breakfast_cutoff_time TIME NOT NULL DEFAULT '22:00:00';
ALTER TABLE building_configs ADD COLUMN dinner_cutoff_time TIME NOT NULL DEFAULT '14:00:00';
ALTER TABLE building_configs ADD COLUMN is_previous_day BOOLEAN NOT NULL DEFAULT TRUE;
