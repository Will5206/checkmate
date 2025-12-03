-- Migration: Add 'complete' boolean column to receipts table
-- This column tracks whether all items in a receipt have been claimed
-- When complete = true, receipt appears in History
-- When complete = false, receipt appears in Pending

-- Note: Database name is specified in the mysql command, no need for USE statement

-- Add the complete column (default false for existing receipts)
ALTER TABLE receipts 
ADD COLUMN complete BOOLEAN DEFAULT FALSE NOT NULL;

-- Create index for faster queries
CREATE INDEX idx_receipts_complete ON receipts(complete);

-- Update existing receipts: set complete = false for all (will be updated by application logic)
-- The application will update this column when items are claimed
UPDATE receipts SET complete = FALSE;

SELECT 'Migration completed: Added complete column to receipts table' AS status;

