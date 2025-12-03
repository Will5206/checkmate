# Verification Checklist

## Pre-Deployment Verification

### 1. Database Schema ✅
- [ ] Verify `complete` column exists in `receipts` table
  ```sql
  SHOW COLUMNS FROM receipts LIKE 'complete';
  ```
  Expected: `complete BOOLEAN DEFAULT FALSE NOT NULL`

- [ ] Verify `sender_name` column exists
  ```sql
  SHOW COLUMNS FROM receipts LIKE 'sender_name';
  ```

- [ ] Verify `number_of_items` column exists
  ```sql
  SHOW COLUMNS FROM receipts LIKE 'number_of_items';
  ```

- [ ] If `complete` column missing, run migration:
  ```bash
  ./scripts/database/run_complete_migration.sh
  ```

### 2. Code Compilation ✅
- [x] Backend compiles without errors
  ```bash
  mvn clean compile
  ```

### 3. Key Functionality Tests

#### Test 1: Item Claiming (Race Condition Fix)
- [ ] Create receipt with 2 items, quantity 1 each
- [ ] Have 2 users claim items simultaneously
- [ ] Verify: Only 1 user can claim each item (no over-claiming)
- [ ] Check logs: Should see transaction locks working

#### Test 2: Payment Atomicity
- [ ] Create receipt, claim items, attempt payment
- [ ] Simulate failure (e.g., disconnect during payment)
- [ ] Verify: Either payment fully recorded OR fully rolled back
- [ ] Check: No partial payments in database

#### Test 3: History Movement
- [ ] Create receipt, share with participant
- [ ] Uploader claims all items
- [ ] Participant claims items
- [ ] Verify: Still in Pending (items claimed but not paid)
- [ ] Participant pays
- [ ] Verify: Moves to History (both conditions met)

#### Test 4: Amount Calculation Precision
- [ ] Create receipt: $10.00 item, $1.00 tax, $1.50 tip
- [ ] Claim 50% of item
- [ ] Verify: Amount owed = $5.00 + $0.50 + $0.75 = $6.25 (exact)
- [ ] Check: No floating-point rounding errors

#### Test 5: Performance
- [ ] Create receipt with 20 items
- [ ] Claim items rapidly
- [ ] Verify: No lag or flickering
- [ ] Check: Single query for amount calculation

### 4. Error Scenarios

#### Test 6: Concurrent Claims
- [ ] Two users try to claim same item simultaneously
- [ ] Verify: One succeeds, one fails gracefully
- [ ] Check: No database deadlocks

#### Test 7: Payment Failure
- [ ] Attempt payment with insufficient balance
- [ ] Verify: Proper error message
- [ ] Verify: No partial state changes

#### Test 8: Network Interruption
- [ ] Start payment, interrupt network
- [ ] Verify: Transaction rolled back
- [ ] Verify: Balance unchanged

### 5. Log Verification

Check logs for:
- [ ] Transaction commits/rollbacks logged
- [ ] Async update executor working (no thread exhaustion)
- [ ] No SQL errors or deadlocks
- [ ] Proper error messages for failures

### 6. Database State Verification

After tests, verify:
- [ ] No orphaned records in `item_assignments`
- [ ] `receipts.complete` status correct
- [ ] `receipt_participants.paid_amount` matches actual payments
- [ ] No inconsistent states (e.g., items marked paid but payment not recorded)

---

## Quick Test Script

```bash
# 1. Verify database schema
mysql -u root -p -e "SHOW COLUMNS FROM receipts LIKE 'complete';"

# 2. Compile backend
cd backend && mvn clean compile

# 3. Start backend server
# (In separate terminal)

# 4. Run frontend
cd mobile && npm start

# 5. Perform manual tests as described above
```

---

## Success Criteria

✅ All tests pass
✅ No race conditions observed
✅ Payments are atomic
✅ History movement works correctly
✅ Amount calculations are precise
✅ Performance is acceptable (no lag)
✅ Error handling works correctly
✅ Database state is consistent

---

## Rollback Plan

If issues are found:
1. Revert code changes via git
2. Database migration is additive (safe to keep `complete` column)
3. No data loss expected

---

## Notes

- All fixes are backward compatible
- Database migration is safe (adds column, doesn't modify existing data)
- Code compiles successfully
- Ready for testing and deployment

