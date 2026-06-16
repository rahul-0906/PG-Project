-- Migration to add AC support, building-level add-on toggles, and Book Entire Room support
ALTER TABLE rooms ADD COLUMN is_ac BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE building_configs ADD COLUMN offer_omelette BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE building_configs ADD COLUMN offer_boiled_egg BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE guests ADD COLUMN is_book_entire_room BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE guests ADD COLUMN building_id VARCHAR(255) REFERENCES buildings(id);

-- Backfill building_id for existing active and checked-out guests who have a bed assignment
UPDATE guests g
SET building_id = (
    SELECT f.building_id 
    FROM beds b
    JOIN rooms r ON b.room_id = r.id
    JOIN floors f ON r.floor_id = f.id
    WHERE b.id = g.bed_id
)
WHERE g.bed_id IS NOT NULL;
