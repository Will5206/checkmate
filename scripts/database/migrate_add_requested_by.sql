-- Migration: Add requested_by column to friendships table
-- This column tracks who sent the friend request (the requester)
-- Only the recipient (not the requester) should see pending friend requests

USE railway;

-- Add requested_by column to track who sent the request
ALTER TABLE friendships ADD COLUMN requested_by VARCHAR(36) NULL;

-- Create index for faster lookups
CREATE INDEX idx_friendships_requested_by ON friendships(requested_by);

-- Update existing pending friendships to set requested_by based on user_id_1
-- (This is a best-effort migration - for new requests, we'll set it correctly)
UPDATE friendships 
SET requested_by = user_id_1 
WHERE status = 'pending' AND requested_by IS NULL;

-- Add foreign key constraint
ALTER TABLE friendships 
ADD CONSTRAINT fk_friendships_requested_by 
FOREIGN KEY (requested_by) REFERENCES users(user_id) ON DELETE CASCADE;

