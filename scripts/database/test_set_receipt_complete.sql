-- Test script: Set receipt 22 to complete = 1
-- This allows testing if the History query correctly shows receipts with complete = 1

-- First, check current status
SELECT receipt_id, status, complete, merchant_name, created_at 
FROM receipts 
WHERE receipt_id = 22;

-- Update complete to 1 (TRUE)
UPDATE receipts 
SET complete = 1 
WHERE receipt_id = 22;

-- Verify the update
SELECT receipt_id, status, complete, merchant_name, created_at 
FROM receipts 
WHERE receipt_id = 22;

SELECT 'Receipt 22 complete status set to 1. Check History tab to see if it appears.' AS status;

