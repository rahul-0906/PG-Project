-- Add user_id to notifications table to support notifications for any user (managers, owners, guests)
ALTER TABLE notifications ADD COLUMN user_id VARCHAR(255) REFERENCES users(id);

-- Populate user_id for existing guest notifications
UPDATE notifications n
SET user_id = (SELECT g.user_id FROM guests g WHERE g.id = n.guest_id)
WHERE n.guest_id IS NOT NULL;
