# Scenario: Uploader Invites Someone But Pays for Everything

## Question
Does the code work if you invite someone but pay for the whole receipt by yourself? Should it move to complete because all items are paid for?

## Scenario Breakdown

1. **Uploader creates receipt** → Adds participant (participant in `receipt_participants` with `status='accepted'`)
2. **Uploader claims all items** → All items assigned to uploader in `item_assignments`
3. **Uploader pays for everything** → Payment recorded in `receipt_participants` for uploader
4. **Participant hasn't claimed anything** → No items assigned to participant
5. **Participant hasn't paid** → `paid_amount = 0` in `receipt_participants`

## Code Flow Analysis

### Step 1: Check If All Items Claimed
**File**: `backend/database/ReceiptDAO.java:1379`
- `areAllItemsClaimed(receiptId)` 
- ✅ Returns `true` (all items claimed by uploader)

### Step 2: Get Participants
**File**: `backend/database/ReceiptDAO.java:1334` - `getParticipantsWithPaymentStatus()`

**SQL Query**:
```sql
SELECT user_id, status, COALESCE(paid_amount, 0) as paid_amount 
FROM receipt_participants 
WHERE receipt_id = ? AND status = 'accepted'
```

**Result**: Returns participant (even though they haven't claimed/paid anything)

### Step 3: Calculate Owed Amount for Participant
**File**: `backend/database/ReceiptDAO.java:1352`
- Calls `calculateUserOwedAmount(receiptId, participantUserId)`

**File**: `backend/database/ReceiptDAO.java:658` - `calculateUserOwedAmount()`

**SQL Query**:
```sql
SELECT 
  COALESCE(SUM(ri.price * ia.quantity), 0) as assigned_subtotal,
  ...
FROM receipt_items ri
LEFT JOIN item_assignments ia ON ri.item_id = ia.item_id AND ia.user_id = ?
WHERE ri.receipt_id = ?
```

**Key**: `LEFT JOIN` with `ia.user_id = ?` (participant's ID)
- If participant has **no item assignments**, `assigned_subtotal = 0`

**Line 686-688**:
```java
if (assignedSubtotal == null || assignedSubtotal.compareTo(java.math.BigDecimal.ZERO) == 0) {
    return 0.0f;  // ← Returns 0 if no items assigned
}
```

**Result**: `owed_amount = 0` for participant (they have no items claimed)

### Step 4: Check If All Participants Paid
**File**: `backend/database/ReceiptDAO.java:1405` - `areAllParticipantsPaid()`

**For participant**:
- `paid_amount = 0` (from `receipt_participants` table)
- `owed_amount = 0` (calculated - no items claimed)

**Line 1430**: Comparison
```java
if (paidAmount.compareTo(owedAmount.subtract(roundingTolerance)) < 0) {
    return false;
}
```

**Calculation**:
- `paidAmount = 0`
- `owedAmount = 0`
- `owedAmount.subtract(roundingTolerance) = 0 - 0.01 = -0.01`
- `paidAmount.compareTo(-0.01) < 0` → `0 < -0.01` → **FALSE** ✅

**Result**: Check passes! `0 >= -0.01` is true, so participant is considered "paid"

### Step 5: Update Complete Status
**File**: `backend/database/ReceiptDAO.java:1393`
- `isComplete = allClaimed && allPaid`
- `allClaimed = true` ✅
- `allPaid = true` ✅ (participant owes 0, paid 0)
- `isComplete = true` ✅
- **Line 1394**: `updateCompleteStatusInDB(receiptId, true)` → Sets `complete = 1` ✅

---

## Answer: YES, It Works! ✅

**The code correctly handles this scenario:**

1. ✅ Participant with no items claimed has `owed_amount = 0`
2. ✅ Participant with `paid_amount = 0` passes the check (`0 >= 0 - 0.01`)
3. ✅ Receipt moves to complete when all items are claimed and all participants have paid their owed amount (which is 0 for participants with no items)

---

## Edge Case: What If Participant Claims Items But Uploader Pays?

**Scenario**:
1. Participant claims some items
2. Uploader pays for everything (including participant's items)

**What happens**:
- Participant's `owed_amount > 0` (they have items claimed)
- Participant's `paid_amount = 0` (they haven't paid)
- Check: `0 >= owedAmount - 0.01` → **FALSE** ❌
- `areAllParticipantsPaid()` returns `false`
- `complete` stays at `0` ❌

**This is CORRECT behavior** - the participant still owes money even though uploader paid, so receipt shouldn't be complete.

---

## Summary

**Scenario**: Uploader invites someone, claims all items, pays for everything
- ✅ **Works correctly** - Receipt moves to complete
- Participant's `owed_amount = 0` (no items claimed)
- Participant's `paid_amount = 0` passes check (`0 >= 0`)
- `complete = 1` ✅

**Edge Case**: Participant claims items, uploader pays
- ✅ **Works correctly** - Receipt stays incomplete
- Participant's `owed_amount > 0` (has items claimed)
- Participant's `paid_amount = 0` fails check
- `complete = 0` ✅ (correct - participant still owes)

