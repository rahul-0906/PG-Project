-- ============================================================
--  PG CRM — Add Custom Add-on Labels to Building Configs (V15)
-- ============================================================

ALTER TABLE building_configs ADD COLUMN omelette_label VARCHAR(255) NOT NULL DEFAULT 'Omelette';
ALTER TABLE building_configs ADD COLUMN boiled_egg_label VARCHAR(255) NOT NULL DEFAULT 'Boiled Egg';
ALTER TABLE building_configs ADD COLUMN washing_machine_label VARCHAR(255) NOT NULL DEFAULT 'Washing Machine';
