-- Clear all data from CheckMate database (KEEPING ONLY USER ACCOUNTS)
-- This script deletes all receipts, transactions, sessions, friendships, and related data
-- but keeps all user accounts

-- Disable foreign key checks temporarily for faster deletion
SET FOREIGN_KEY_CHECKS = 0;

-- Delete in order from most dependent to least dependent
-- NOTE: We are ONLY keeping the users table
DELETE FROM item_assignments;
DELETE FROM receipt_participants;
DELETE FROM receipt_items;
DELETE FROM receipts;
DELETE FROM transactions;
DELETE FROM balance_history;
DELETE FROM sessions;
DELETE FROM friendships;

-- Reset AUTO_INCREMENT counters
ALTER TABLE item_assignments AUTO_INCREMENT = 1;
ALTER TABLE receipt_participants AUTO_INCREMENT = 1;
ALTER TABLE receipt_items AUTO_INCREMENT = 1;
ALTER TABLE receipts AUTO_INCREMENT = 1;
ALTER TABLE transactions AUTO_INCREMENT = 1;
ALTER TABLE balance_history AUTO_INCREMENT = 1;

-- Reset user balances to 0.00
UPDATE users SET balance = 0.00;

-- Re-enable foreign key checks
SET FOREIGN_KEY_CHECKS = 1;

-- Verify deletion (only users should still have data)
SELECT 'item_assignments' AS table_name, COUNT(*) AS count FROM item_assignments
UNION ALL
SELECT 'receipt_participants', COUNT(*) FROM receipt_participants
UNION ALL
SELECT 'receipt_items', COUNT(*) FROM receipt_items
UNION ALL
SELECT 'receipts', COUNT(*) FROM receipts
UNION ALL
SELECT 'transactions', COUNT(*) FROM transactions
UNION ALL
SELECT 'balance_history', COUNT(*) FROM balance_history
UNION ALL
SELECT 'sessions', COUNT(*) FROM sessions
UNION ALL
SELECT 'friendships', COUNT(*) FROM friendships
UNION ALL
SELECT 'users', COUNT(*) FROM users;

