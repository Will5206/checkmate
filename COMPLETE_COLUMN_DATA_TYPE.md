# Complete Column Data Type Explanation

## Database Storage

**Column Type**: `TINYINT(1)` (MySQL stores BOOLEAN as TINYINT(1))
- **0** = FALSE (receipt not complete, appears in Pending)
- **1** = TRUE (receipt complete, appears in History)

**Verification**:
```sql
SHOW COLUMNS FROM receipts WHERE Field='complete';
-- Result: Type = tinyint(1), Default = 0
```

## Java Code

**File**: `backend/database/ReceiptDAO.java:1446`

```java
private void updateCompleteStatusInDB(int receiptId, boolean isComplete) {
    String sql = "UPDATE receipts SET complete = ? WHERE receipt_id = ?";
    
    try (Connection conn = dbConnection.getConnection();
         PreparedStatement pstmt = conn.prepareStatement(sql)) {
        
        pstmt.setBoolean(1, isComplete);  // ← Java boolean
        pstmt.setInt(2, receiptId);
        
        int affectedRows = pstmt.executeUpdate();
        // ...
    }
}
```

**How it works**:
- Java `boolean` → JDBC `setBoolean()` → MySQL `TINYINT(1)`
- `true` → **1** in database
- `false` → **0** in database

## SQL Queries

**Pending Query** (complete = FALSE):
```sql
WHERE r.complete = FALSE  -- MySQL converts FALSE → 0
-- OR
WHERE r.complete = 0      -- Direct integer comparison
```

**History Query** (complete = TRUE):
```sql
WHERE r.complete = TRUE   -- MySQL converts TRUE → 1
-- OR
WHERE r.complete = 1      -- Direct integer comparison
```

## Summary

- **Database**: `TINYINT(1)` storing **0** or **1**
- **Java**: Uses `boolean` type, JDBC converts automatically
- **When we say "set complete = TRUE"**: It's actually setting it to **1** in the database
- **When we say "set complete = FALSE"**: It's actually setting it to **0** in the database

**So yes, you're correct** - it's stored as an integer (0 or 1), but we use boolean semantics in Java code, and MySQL's BOOLEAN type (which is TINYINT(1) under the hood) handles the conversion.

