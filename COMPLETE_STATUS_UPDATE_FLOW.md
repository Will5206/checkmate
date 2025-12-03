# Exact Code Path: How `complete` Changes from 0 to 1

## Problem
After paying for a full receipt, `complete` stayed at 0 in the database. Need to trace the exact code that should change it to 1.

---

## Complete Code Flow

### Step 1: Payment Handler Calls Check Function

**File**: `backend/controllers/ReceiptController.java`
**Line**: 1259

```java
// Check if receipt should be marked as completed
boolean isCompleted = receiptDAO.checkAndMarkReceiptCompleted(receiptId);
```

**This is called AFTER payment is successfully recorded.**

---

### Step 2: Check If All Items Claimed and All Paid

**File**: `backend/database/ReceiptDAO.java`
**Line**: 1549 - `checkAndMarkReceiptCompleted(int receiptId)`

```java
public boolean checkAndMarkReceiptCompleted(int receiptId) {
    // First check if all items are claimed
    if (!areAllItemsClaimed(receiptId)) {
        return false; // Not all items are claimed yet
    }
    
    // Get participants with their payment status
    List<Map<String, Object>> participants = getParticipantsWithPaymentStatus(receiptId);
    
    if (participants.isEmpty()) {
        return false; // No accepted participants
    }

    // Check if all participants have paid their full amount
    boolean allPaid = true;
    java.math.BigDecimal roundingTolerance = new java.math.BigDecimal("0.01");
    for (Map<String, Object> participant : participants) {
        float paidAmountFloat = (Float) participant.get("paid_amount");
        float owedAmountFloat = (Float) participant.get("owed_amount");
        
        // Convert to BigDecimal for precise comparison
        java.math.BigDecimal paidAmount = java.math.BigDecimal.valueOf(paidAmountFloat).setScale(2, java.math.RoundingMode.HALF_UP);
        java.math.BigDecimal owedAmount = java.math.BigDecimal.valueOf(owedAmountFloat).setScale(2, java.math.RoundingMode.HALF_UP);
        
        // Allow small rounding differences (0.01)
        if (paidAmount.compareTo(owedAmount.subtract(roundingTolerance)) < 0) {
            allPaid = false;
            break;
        }
    }

    if (allPaid) {
        // Mark receipt as completed
        boolean receiptUpdated = updateReceiptStatus(receiptId, "completed");
        
        // CRITICAL: Also update the 'complete' column to TRUE
        updateReceiptCompleteStatus(receiptId);  // ← THIS CALLS THE FUNCTION THAT UPDATES complete
        
        if (receiptUpdated) {
            updateAllParticipantsStatus(receiptId, "completed");
            System.out.println("Receipt " + receiptId + " is now fully paid and completed (status='completed', complete=TRUE)");
        }
        
        return receiptUpdated;
    }

    return false;
}
```

**Key Check**: Line 1573 - Compares `paidAmount` vs `owedAmount` for each participant.

---

### Step 3: Update Complete Status (Checks Conditions Again)

**File**: `backend/database/ReceiptDAO.java`
**Line**: 1375 - `updateReceiptCompleteStatus(int receiptId)`

```java
public boolean updateReceiptCompleteStatus(int receiptId) {
    System.out.println("[ReceiptDAO] updateReceiptCompleteStatus called for receipt " + receiptId);
    
    // Condition 1: Check if all items are claimed
    boolean allClaimed = areAllItemsClaimed(receiptId);
    System.out.println("[ReceiptDAO] areAllItemsClaimed returned: " + allClaimed + " for receipt " + receiptId);
    
    if (!allClaimed) {
        // Not all items claimed - receipt cannot be complete
        updateCompleteStatusInDB(receiptId, false);
        return false;
    }
    
    // Condition 2: Check if all participants have paid
    boolean allPaid = areAllParticipantsPaid(receiptId);
    System.out.println("[ReceiptDAO] areAllParticipantsPaid returned: " + allPaid + " for receipt " + receiptId);
    
    // Receipt is complete only if BOTH conditions are true
    boolean isComplete = allClaimed && allPaid;
    updateCompleteStatusInDB(receiptId, isComplete);  // ← THIS ACTUALLY UPDATES THE DATABASE
    
    return isComplete;
}
```

**Key Check**: Line 1389 - Calls `areAllParticipantsPaid(receiptId)` which checks payment status.

---

### Step 4: Check If All Participants Paid (Helper Method)

**File**: `backend/database/ReceiptDAO.java`
**Line**: 1405 - `areAllParticipantsPaid(int receiptId)`

```java
private boolean areAllParticipantsPaid(int receiptId) {
    List<Map<String, Object>> participants = getParticipantsWithPaymentStatus(receiptId);
    
    if (participants.isEmpty()) {
        System.out.println("[ReceiptDAO] No participants found for receipt " + receiptId);
        return false; // No participants means not all paid
    }
    
    // Check if all participants have paid their full amount
    // Use BigDecimal for precise comparison to avoid floating-point errors
    java.math.BigDecimal roundingTolerance = new java.math.BigDecimal("0.01");
    for (Map<String, Object> participant : participants) {
        float paidAmountFloat = (Float) participant.get("paid_amount");
        float owedAmountFloat = (Float) participant.get("owed_amount");
        
        // Convert to BigDecimal for precise comparison
        java.math.BigDecimal paidAmount = java.math.BigDecimal.valueOf(paidAmountFloat).setScale(2, java.math.RoundingMode.HALF_UP);
        java.math.BigDecimal owedAmount = java.math.BigDecimal.valueOf(owedAmountFloat).setScale(2, java.math.RoundingMode.HALF_UP);
        
        // Allow small rounding differences (0.01)
        if (paidAmount.compareTo(owedAmount.subtract(roundingTolerance)) < 0) {
            System.out.println("[ReceiptDAO] Participant " + participant.get("user_id") + " has not paid fully: paid=" + paidAmount + ", owed=" + owedAmount);
            return false;
        }
    }
    
    System.out.println("[ReceiptDAO] All " + participants.size() + " participants have paid their full amount");
    return true;
}
```

**Key Check**: Line 1425 - Compares `paidAmount >= owedAmount - 0.01` for each participant.

---

### Step 5: Get Participants With Payment Status

**File**: `backend/database/ReceiptDAO.java`
**Line**: 1230 - `getParticipantsWithPaymentStatus(int receiptId)`

```java
public List<Map<String, Object>> getParticipantsWithPaymentStatus(int receiptId) {
    String sql = "SELECT user_id, status, COALESCE(paid_amount, 0) as paid_amount " +
                 "FROM receipt_participants WHERE receipt_id = ? AND status = 'accepted'";

    List<Map<String, Object>> participants = new ArrayList<>();

    try (Connection conn = dbConnection.getConnection();
         PreparedStatement pstmt = conn.prepareStatement(sql)) {

        pstmt.setInt(1, receiptId);

        try (ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> participant = new HashMap<>();
                String userId = rs.getString("user_id");
                participant.put("user_id", userId);
                participant.put("status", rs.getString("status"));
                participant.put("paid_amount", rs.getBigDecimal("paid_amount").floatValue());
                participant.put("owed_amount", calculateUserOwedAmount(receiptId, userId));  // ← CALCULATES OWED AMOUNT
                participants.add(participant);
            }
        }
    } catch (SQLException e) {
        System.err.println("Error getting participants with payment status: " + e.getMessage());
        e.printStackTrace();
    }

    return participants;
}
```

**Key**: 
- Gets `paid_amount` from `receipt_participants` table
- Calculates `owed_amount` using `calculateUserOwedAmount(receiptId, userId)`

---

### Step 6: ACTUAL DATABASE UPDATE

**File**: `backend/database/ReceiptDAO.java`
**Line**: 1441 - `updateCompleteStatusInDB(int receiptId, boolean isComplete)`

```java
private void updateCompleteStatusInDB(int receiptId, boolean isComplete) {
    String sql = "UPDATE receipts SET complete = ? WHERE receipt_id = ?";
    
    try (Connection conn = dbConnection.getConnection();
         PreparedStatement pstmt = conn.prepareStatement(sql)) {
        
        pstmt.setBoolean(1, isComplete);  // ← SETS complete TO TRUE (1) OR FALSE (0)
        pstmt.setInt(2, receiptId);
        
        int affectedRows = pstmt.executeUpdate();
        if (affectedRows > 0) {
            System.out.println("[ReceiptDAO] SUCCESS: Updated receipt " + receiptId + " complete status to " + isComplete + " (affected rows: " + affectedRows + ")");
            
            // Verify the update by querying the database
            String verifySql = "SELECT complete FROM receipts WHERE receipt_id = ?";
            try (PreparedStatement verifyPstmt = conn.prepareStatement(verifySql)) {
                verifyPstmt.setInt(1, receiptId);
                try (ResultSet verifyRs = verifyPstmt.executeQuery()) {
                    if (verifyRs.next()) {
                        boolean actualComplete = verifyRs.getBoolean("complete");
                        System.out.println("[ReceiptDAO] VERIFIED: Receipt " + receiptId + " complete status in DB is now: " + actualComplete);
                    }
                }
            }
        } else {
            System.out.println("[ReceiptDAO] WARNING: No rows affected when updating receipt " + receiptId + " complete status");
        }
    } catch (SQLException e) {
        System.err.println("[ReceiptDAO] ERROR: Error updating receipt complete status: " + e.getMessage());
        e.printStackTrace();
    }
}
```

**THIS IS THE EXACT CODE THAT UPDATES `complete` TO 1:**
- **Line 1442**: SQL: `UPDATE receipts SET complete = ? WHERE receipt_id = ?`
- **Line 1452**: `pstmt.setBoolean(1, isComplete)` - JDBC converts Java boolean to MySQL TINYINT(1)
  - `true` → **1** in database
  - `false` → **0** in database
- **Note**: MySQL stores BOOLEAN as `TINYINT(1)`, so it's actually an integer (0 or 1) in the database

---

## Why `complete` Might Stay at 0

### Possible Issues:

1. **`areAllItemsClaimed()` returns false**
   - Not all items are claimed by users
   - Check: `SELECT * FROM item_assignments WHERE receipt_id = ?` should have all items

2. **`areAllParticipantsPaid()` returns false**
   - `paid_amount < owed_amount` for at least one participant
   - Check: `SELECT user_id, paid_amount FROM receipt_participants WHERE receipt_id = ?`
   - Check: `calculateUserOwedAmount()` might be calculating wrong amount

3. **Payment not recorded correctly**
   - `paid_amount` in `receipt_participants` might not be updated
   - Check: `SELECT * FROM receipt_participants WHERE receipt_id = ? AND user_id = ?`

4. **Owed amount calculation is wrong**
   - `calculateUserOwedAmount()` might return incorrect value
   - Check: Compare `paid_amount` vs calculated `owed_amount`

---

## Debugging Steps

1. **Check backend logs** for:
   - `[ReceiptDAO] areAllItemsClaimed returned: ...`
   - `[ReceiptDAO] areAllParticipantsPaid returned: ...`
   - `[ReceiptDAO] SUCCESS: Updated receipt ... complete status to ...`

2. **Check database directly**:
   ```sql
   -- Check if all items claimed
   SELECT ri.item_id, ri.quantity, COALESCE(SUM(ia.quantity), 0) as claimed
   FROM receipt_items ri
   LEFT JOIN item_assignments ia ON ri.item_id = ia.item_id
   WHERE ri.receipt_id = ?
   GROUP BY ri.item_id, ri.quantity;
   
   -- Check payment status
   SELECT user_id, paid_amount, status 
   FROM receipt_participants 
   WHERE receipt_id = ?;
   
   -- Check complete status
   SELECT receipt_id, status, complete 
   FROM receipts 
   WHERE receipt_id = ?;
   ```

3. **Check if `updateReceiptCompleteStatus()` is actually called**:
   - Look for log: `[ReceiptDAO] updateReceiptCompleteStatus called for receipt ...`

---

## Summary

**Exact code that changes `complete` to 1:**
- **File**: `backend/database/ReceiptDAO.java`
- **Line**: 1441 - `updateCompleteStatusInDB()`
- **SQL**: `UPDATE receipts SET complete = ? WHERE receipt_id = ?`
- **Called from**: Line 1586 in `checkAndMarkReceiptCompleted()`
- **Only updates if**: Both `areAllItemsClaimed()` AND `areAllParticipantsPaid()` return true

