-- Migration: Add sender_name and number_of_items columns to receipts table
-- sender_name: Stores the name of the user who uploaded the receipt
-- number_of_items: Stores the count of items on the receipt

USE railway;

-- Add sender_name column to track who sent the receipt
ALTER TABLE receipts ADD COLUMN sender_name VARCHAR(100) NULL;

-- Add number_of_items column to track item count
ALTER TABLE receipts ADD COLUMN number_of_items INT DEFAULT 0 NOT NULL;

-- Create indexes for faster lookups
CREATE INDEX idx_receipts_sender_name ON receipts(sender_name);
CREATE INDEX idx_receipts_number_of_items ON receipts(number_of_items);

-- Update existing receipts with sender_name from users table
UPDATE receipts r
INNER JOIN users u ON r.uploaded_by = u.user_id
SET r.sender_name = u.name
WHERE r.sender_name IS NULL;

-- Update existing receipts with number_of_items from receipt_items count
UPDATE receipts r
SET r.number_of_items = (
    SELECT COUNT(*) 
    FROM receipt_items ri 
    WHERE ri.receipt_id = r.receipt_id
)
WHERE r.number_of_items = 0;

