# Receipt History Movement - Complete Flow Explanation

## Problem
Receipts are not moving to History after all items are claimed and paid for.

## Root Cause
The `checkAndMarkReceiptCompleted()` method updates the `status` column to "completed" but **does NOT update the `complete` column to `TRUE`**. However, the History query (`getAllReceiptsForUser()`) filters by `complete = TRUE`, not `status = 'completed'`.

---

## Complete Flow Trace

### 1. FRONTEND → User clicks "History" tab

**File**: `mobile/screens/ActivityScreen.js`
- **Line 25-58**: `loadReceipts()` function
- **Line 31**: Calls `getActivityReceipts()` from service
- **Line 44**: Sets receipts state with API response
- **Display**: Shows all receipts in History tab

### 2. FRONTEND SERVICE → API call

**File**: `mobile/services/receiptsService.js`
- **Line 166-207**: `getActivityReceipts()` function
- **Line 180**: Makes GET request: `/api/receipts/activity?userId=...`
- **Line 192**: Returns receipts array

### 3. BACKEND CONTROLLER → Receives request

**File**: `backend/controllers/ReceiptController.java`
- **Line 723**: `GetActivityReceiptsHandler` class
- **Line 751**: Calls `receiptService.getAllReceiptsForUser(userIdStr)`

### 4. BACKEND DAO → Database query

**File**: `backend/database/ReceiptDAO.java`
- **Line 789**: `getAllReceiptsForUser(String userId)` method
- **Line 797**: SQL Query filters by `r.complete = TRUE`
  ```sql
  SELECT r.* FROM receipts r 
  WHERE r.uploaded_by = ? AND r.complete = TRUE
  UNION
  SELECT r.* FROM receipts r 
  INNER JOIN receipt_participants rp ON r.receipt_id = rp.receipt_id 
  WHERE rp.user_id = ? AND rp.status != 'declined' AND r.complete = TRUE
  ```
- **CRITICAL**: Only returns receipts where `complete = TRUE`

### 5. DATABASE → receipts table

- **Column**: `complete` (BOOLEAN, DEFAULT FALSE)
- **When `complete = TRUE`**: Receipt appears in History
- **When `complete = FALSE`**: Receipt appears in Pending

---

## When Does `complete` Get Set to TRUE?

### Path A: After Item Claiming/Unclaiming (Async)

**File**: `backend/database/ReceiptDAO.java`
- **Line 299**: After `assignItemToUser()` → calls `updateReceiptCompleteStatusAsync()`
- **Line 372**: After `unassignItemFromUser()` → calls `updateReceiptCompleteStatusAsync()`
- **Line 408**: `updateReceiptCompleteStatusAsync()` → submits to thread pool (async)
- **Line 1375**: `updateReceiptCompleteStatus()` checks:
  1. **Line 1379**: `areAllItemsClaimed(receiptId)` - Are all items claimed?
  2. **Line 1389**: `areAllParticipantsPaid(receiptId)` - Have all participants paid?
- **Line 1393**: Sets `complete = TRUE` only if **BOTH** conditions are true
- **Line 1441**: `updateCompleteStatusInDB()` updates database

### Path B: After Payment (Synchronous) - **THE BUG WAS HERE**

**File**: `backend/controllers/ReceiptController.java`
- **Line 1259**: After payment → calls `receiptDAO.checkAndMarkReceiptCompleted(receiptId)`

**File**: `backend/database/ReceiptDAO.java`
- **Line 1549**: `checkAndMarkReceiptCompleted()` method
- **Line 1551**: Checks if all items claimed
- **Line 1554**: Gets participants with payment status
- **Line 1560-1577**: Checks if all participants paid
- **Line 1581**: Updates `receipts.status` to "completed" ✅
- **Line 1585**: Updates participant statuses to "completed" ✅
- **❌ BUG**: Does NOT update `receipts.complete` to `TRUE` ❌

---

## The Bug Explained

### What Happens Currently:

1. User pays for receipt → Payment succeeds
2. `checkAndMarkReceiptCompleted()` runs:
   - ✅ Sets `status = 'completed'`
   - ✅ Updates participant statuses
   - ❌ **Does NOT set `complete = TRUE`**
3. `getAllReceiptsForUser()` queries:
   - Looks for `complete = TRUE`
   - Receipt still has `complete = FALSE`
   - **Result**: Receipt does NOT appear in History

### Why This Happens:

- `checkAndMarkReceiptCompleted()` was written before the `complete` column existed
- It only updates the old `status` column
- The new History query uses `complete` column
- These two systems are not synchronized

---

## The Solution

### Fix Applied:

**File**: `backend/database/ReceiptDAO.java:1579`

**Before**:
```java
if (allPaid) {
    boolean receiptUpdated = updateReceiptStatus(receiptId, "completed");
    if (receiptUpdated) {
        updateAllParticipantsStatus(receiptId, "completed");
        System.out.println("Receipt " + receiptId + " is now fully paid and completed");
    }
    return receiptUpdated;
}
```

**After**:
```java
if (allPaid) {
    boolean receiptUpdated = updateReceiptStatus(receiptId, "completed");
    
    // CRITICAL FIX: Also update the 'complete' column to TRUE
    // This is what getAllReceiptsForUser() queries for to show receipts in History
    updateReceiptCompleteStatus(receiptId);
    
    if (receiptUpdated) {
        updateAllParticipantsStatus(receiptId, "completed");
        System.out.println("Receipt " + receiptId + " is now fully paid and completed (status='completed', complete=TRUE)");
    }
    return receiptUpdated;
}
```

### What This Does:

1. After payment, `checkAndMarkReceiptCompleted()` now calls `updateReceiptCompleteStatus()`
2. `updateReceiptCompleteStatus()` checks both:
   - All items claimed ✅
   - All participants paid ✅
3. If both true, sets `complete = TRUE` in database
4. `getAllReceiptsForUser()` now finds the receipt and shows it in History

---

## Complete Flow After Fix

1. **User pays** → Payment handler processes payment
2. **Payment recorded** → `recordPaymentAndMarkItems()` updates database
3. **Check completion** → `checkAndMarkReceiptCompleted()` called
4. **Update status** → Sets `status = 'completed'` ✅
5. **Update complete** → Calls `updateReceiptCompleteStatus()` ✅
6. **Set complete = TRUE** → Database updated ✅
7. **User views History** → `getAllReceiptsForUser()` queries `complete = TRUE`
8. **Receipt appears** → Shows in History tab ✅

---

## Verification

To verify the fix works:

1. **Create receipt** with items
2. **Claim all items** by all participants
3. **Pay for all items** by all participants
4. **Check database**:
   ```sql
   SELECT receipt_id, status, complete FROM receipts WHERE receipt_id = ?;
   ```
   Should show: `status = 'completed'` AND `complete = 1` (TRUE)
5. **View History tab** → Receipt should appear

---

## Summary

**Problem**: `checkAndMarkReceiptCompleted()` updated `status` but not `complete` column.

**Solution**: Added call to `updateReceiptCompleteStatus()` after payment to ensure `complete = TRUE`.

**Result**: Receipts now correctly move to History when all items are claimed and paid.

