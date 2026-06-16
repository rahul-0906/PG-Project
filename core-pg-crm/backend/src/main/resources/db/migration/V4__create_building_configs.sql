-- ============================================================
--  PG CRM — Create Building Configs and Add Maintenance Title (V4)
-- ============================================================

CREATE TABLE building_configs (
    building_id VARCHAR(255) PRIMARY KEY REFERENCES buildings(id) ON DELETE CASCADE,
    food_included_in_rent BOOLEAN NOT NULL DEFAULT FALSE,
    allow_meal_cancellations BOOLEAN NOT NULL DEFAULT TRUE,
    breakfast_price NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    lunch_price NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    dinner_price NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    omelette_price NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    boiled_egg_price NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    washing_machine_price NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    eb_split_method VARCHAR(255)
);

ALTER TABLE maintenance_tickets ADD COLUMN title VARCHAR(255);
UPDATE maintenance_tickets SET title = COALESCE(location, 'Maintenance Issue') WHERE title IS NULL;
ALTER TABLE maintenance_tickets ALTER COLUMN title SET NOT NULL;
