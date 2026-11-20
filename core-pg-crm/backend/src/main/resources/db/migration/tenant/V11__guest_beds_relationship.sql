-- Create guest_beds join table
CREATE TABLE guest_beds (
    guest_id VARCHAR(255) NOT NULL REFERENCES guests(id) ON DELETE CASCADE,
    bed_id VARCHAR(255) NOT NULL REFERENCES beds(id) ON DELETE CASCADE,
    PRIMARY KEY (guest_id, bed_id)
);

-- Copy existing single bed records from guests to guest_beds
INSERT INTO guest_beds (guest_id, bed_id)
SELECT id, bed_id FROM guests WHERE bed_id IS NOT NULL;

-- Drop bed_id column from guests, which automatically drops the foreign key constraint
ALTER TABLE guests DROP COLUMN bed_id;
