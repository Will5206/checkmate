-- Migration: Add 'complete' boolean column to receipts table
-- This column tracks whether all items in a receipt have been claimed
-- When complete = true, receipt appears in History
-- When complete = false, receipt appears in Pending

-- Note: Database name is specified in the mysql command, no need for USE statement

-- Check if column exists before adding (idempotent migration)
SET @column_exists = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 'receipts' 
    AND COLUMN_NAME = 'complete'
);

-- Add the complete column only if it doesn't exist
SET @sql = IF(@column_exists = 0,
    'ALTER TABLE receipts ADD COLUMN complete BOOLEAN DEFAULT FALSE NOT NULL',
    'SELECT "Column complete already exists, skipping ALTER TABLE" AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Create index only if it doesn't exist
SET @index_exists = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 'receipts' 
    AND INDEX_NAME = 'idx_receipts_complete'
);

SET @sql = IF(@index_exists = 0,
    'CREATE INDEX idx_receipts_complete ON receipts(complete)',
    'SELECT "Index idx_receipts_complete already exists, skipping CREATE INDEX" AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Update existing receipts: set complete = false for all (will be updated by application logic)
-- The application will update this column when items are claimed
UPDATE receipts SET complete = FALSE WHERE complete IS NULL OR complete != FALSE;

SELECT 'Migration completed: Verified complete column exists in receipts table' AS status;

