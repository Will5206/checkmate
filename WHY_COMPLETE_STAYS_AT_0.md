# Why `complete` Stays at 0 After Payment

## Exact Code That Changes `complete` from 0 to 1

### The Database Update Code

**File**: `backend/database/ReceiptDAO.java`
**Line**: 1441 - `updateCompleteStatusInDB(int receiptId, boolean isComplete)`

```java
private void updateCompleteStatusInDB(int receiptId, boolean isComplete) {
    String sql = "UPDATE receipts SET complete = ? WHERE receipt_id = ?";
    
    try (Connection conn = dbConnection.getConnection();
         PreparedStatement pstmt = conn.prepareStatement(sql)) {
        
        pstmt.setBoolean(1, isComplete);  // ← THIS SETS complete TO 1 (TRUE) OR 0 (FALSE)
        pstmt.setInt(2, receiptId);
        
        int affectedRows = pstmt.executeUpdate();
        if (affectedRows > 0) {
            System.out.println("[ReceiptDAO] SUCCESS: Updated receipt " + receiptId + " complete status to " + isComplete);
            // ... verification code ...
        }
    }
}
```

**This is the ONLY code that updates `complete` in the database.**

---

## Code Flow After Payment

### Step 1: Payment Handler
**File**: `backend/controllers/ReceiptController.java:1259`
```java
boolean isCompleted = receiptDAO.checkAndMarkReceiptCompleted(receiptId);
```

### Step 2: Check Completion
**File**: `backend/database/ReceiptDAO.java:1549` - `checkAndMarkReceiptCompleted()`

**Checks**:
1. **Line 1551**: `areAllItemsClaimed(receiptId)` - Are all items claimed?
2. **Line 1554**: Gets participants from `receipt_participants` table
3. **Line 1556**: If no participants → **RETURNS FALSE** ❌ (This might be the bug!)
4. **Line 1573**: For each participant, checks `paidAmount >= owedAmount - 0.01`

**If all paid**:
- **Line 1586**: Calls `updateReceiptCompleteStatus(receiptId)`

### Step 3: Update Complete Status
**File**: `backend/database/ReceiptDAO.java:1375` - `updateReceiptCompleteStatus()`

**Checks AGAIN**:
1. **Line 1379**: `areAllItemsClaimed(receiptId)`
2. **Line 1389**: `areAllParticipantsPaid(receiptId)`
3. **Line 1393**: `isComplete = allClaimed && allPaid`
4. **Line 1394**: Calls `updateCompleteStatusInDB(receiptId, isComplete)`

### Step 4: Check All Participants Paid
**File**: `backend/database/ReceiptDAO.java:1405` - `areAllParticipantsPaid()`

**Gets participants**:
- **Line 1406**: Calls `getParticipantsWithPaymentStatus(receiptId)`
- **Line 1408**: If empty → **RETURNS FALSE** ❌ (This might be the bug!)

**For each participant**:
- **Line 1417**: Gets `paid_amount` from `receipt_participants` table
- **Line 1418**: Gets `owed_amount` from `calculateUserOwedAmount()`
- **Line 1425**: Checks `paidAmount >= owedAmount - 0.01`

### Step 5: Get Participants
**File**: `backend/database/ReceiptDAO.java:1334` - `getParticipantsWithPaymentStatus()`

**SQL Query**:
```sql
SELECT user_id, status, COALESCE(paid_amount, 0) as paid_amount 
FROM receipt_participants 
WHERE receipt_id = ? AND status = 'accepted'
```

**Key**: Only gets users from `receipt_participants` table with `status = 'accepted'`
- **Does NOT include the uploader** (uploader is in `receipts.uploaded_by`, not `receipt_participants`)

---

## Why `complete` Might Stay at 0

### Issue #1: No Participants Found
**Location**: `areAllParticipantsPaid()` line 1408

**Problem**: If `getParticipantsWithPaymentStatus()` returns empty list:
- Code returns `false`
- `complete` never gets set to `TRUE`

**When this happens**:
- Receipt has no participants (only uploader)
- All participants declined
- Participants not in `receipt_participants` table with `status = 'accepted'`

**Fix Applied**: Now checks if all items claimed when no participants exist (uploader-only scenario)

### Issue #2: Paid Amount < Owed Amount
**Location**: `areAllParticipantsPaid()` line 1425

**Problem**: If `paidAmount < owedAmount - 0.01` for any participant:
- Code returns `false`
- `complete` never gets set to `TRUE`

**Check**:
```sql
SELECT user_id, paid_amount 
FROM receipt_participants 
WHERE receipt_id = ?;
```

Compare with calculated owed amount.

### Issue #3: Not All Items Claimed
**Location**: `areAllItemsClaimed()` 

**Problem**: If any item has `claimed_quantity < item_quantity`:
- Code returns `false`
- `complete` never gets set to `TRUE`

**Check**:
```sql
SELECT ri.item_id, ri.quantity, COALESCE(SUM(ia.quantity), 0) as claimed
FROM receipt_items ri
LEFT JOIN item_assignments ia ON ri.item_id = ia.item_id
WHERE ri.receipt_id = ?
GROUP BY ri.item_id, ri.quantity;
```

All items should have `claimed >= quantity`.

### Issue #4: Owed Amount Calculation Wrong
**Location**: `calculateUserOwedAmount()` line 658

**Problem**: If `calculateUserOwedAmount()` returns wrong value:
- Comparison `paidAmount >= owedAmount` fails
- `complete` never gets set to `TRUE`

**Check**: Verify the calculation includes:
- Assigned items subtotal
- Proportional tax
- Proportional tip

---

## Debugging Steps

### 1. Check Backend Logs

Look for these log messages:
```
[ReceiptDAO] areAllItemsClaimed returned: true/false
[ReceiptDAO] areAllParticipantsPaid returned: true/false
[ReceiptDAO] SUCCESS: Updated receipt X complete status to true
[ReceiptDAO] VERIFIED: Receipt X complete status in DB is now: true
```

### 2. Check Database Directly

```sql
-- Check complete status
SELECT receipt_id, status, complete 
FROM receipts 
WHERE receipt_id = ?;

-- Check if all items claimed
SELECT ri.item_id, ri.name, ri.quantity, 
       COALESCE(SUM(ia.quantity), 0) as claimed
FROM receipt_items ri
LEFT JOIN item_assignments ia ON ri.item_id = ia.item_id
WHERE ri.receipt_id = ?
GROUP BY ri.item_id, ri.name, ri.quantity;

-- Check payment status
SELECT user_id, status, paid_amount 
FROM receipt_participants 
WHERE receipt_id = ?;
```

### 3. Verify Payment Was Recorded

```sql
-- Check if payment was recorded
SELECT user_id, paid_amount, paid_at 
FROM receipt_participants 
WHERE receipt_id = ? AND user_id = ?;
```

Should show `paid_amount > 0` and `paid_at` is not NULL.

---

## Summary

**Exact code that updates `complete` to 1**:
- **File**: `backend/database/ReceiptDAO.java:1441`
- **Method**: `updateCompleteStatusInDB()`
- **SQL**: `UPDATE receipts SET complete = ? WHERE receipt_id = ?`
- **Line 1452**: `pstmt.setBoolean(1, isComplete)` - Sets Java boolean, JDBC converts to MySQL TINYINT(1)
  - `true` → **1** (complete = 1 in database)
  - `false` → **0** (complete = 0 in database)
- **Note**: MySQL stores BOOLEAN as `TINYINT(1)`, so it's actually an integer (0 or 1) in the database

**Called from**:
- `updateReceiptCompleteStatus()` → Line 1394
- Which is called from `checkAndMarkReceiptCompleted()` → Line 1586

**Only updates to 1 if**:
1. ✅ All items are claimed (`areAllItemsClaimed()` returns true)
2. ✅ All participants have paid (`areAllParticipantsPaid()` returns true)

**Most likely issues**:
1. No participants found (empty list) - **FIXED**
2. Paid amount < owed amount (payment not recorded correctly)
3. Not all items claimed
4. Owed amount calculation is wrong

