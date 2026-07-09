-- ============================================================
--  PG CRM — Add Meal Plan and Meter Readings support
-- ============================================================

-- Add has_meal_plan to guests table
ALTER TABLE guests ADD COLUMN has_meal_plan BOOLEAN NOT NULL DEFAULT FALSE;

-- Create meter_readings table
CREATE TABLE meter_readings (
    id VARCHAR(255) PRIMARY KEY,
    room_id VARCHAR(255) NOT NULL REFERENCES rooms(id),
    reading_type VARCHAR(50) NOT NULL,
    reading_value NUMERIC(10, 2) NOT NULL,
    reading_date DATE NOT NULL
);
