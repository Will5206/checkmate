-- Migration: Add payment tracking to receipt_participants table
-- Run this script to add payment tracking columns
-- Note: This script will fail if columns already exist - that's okay, just means migration already ran

-- Add paid_amount column (ignore error if it already exists)
SET @dbname = DATABASE();
SET @tablename = "receipt_participants";
SET @columnname = "paid_amount";
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = @tablename)
      AND (table_schema = @dbname)
      AND (column_name = @columnname)
  ) > 0,
  "SELECT 'Column paid_amount already exists.'",
  CONCAT("ALTER TABLE ", @tablename, " ADD COLUMN ", @columnname, " DECIMAL(10, 2) DEFAULT 0.00")
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- Add paid_at column (ignore error if it already exists)
SET @columnname = "paid_at";
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = @tablename)
      AND (table_schema = @dbname)
      AND (column_name = @columnname)
  ) > 0,
  "SELECT 'Column paid_at already exists.'",
  CONCAT("ALTER TABLE ", @tablename, " ADD COLUMN ", @columnname, " TIMESTAMP NULL")
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- Update existing records to have 0.00 paid_amount (only if column exists)
UPDATE receipt_participants 
SET paid_amount = 0.00 
WHERE paid_amount IS NULL;

