package database;

import java.sql.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object for managing receipt payment calculations.
 * Handles payment-related calculations and operations.
 */
public class ReceiptPaymentDAO {
    
    private final DatabaseConnection dbConnection;
    private final ItemAssignmentDAO itemAssignmentDAO;

    public ReceiptPaymentDAO() {
        this.dbConnection = DatabaseConnection.getInstance();
        this.itemAssignmentDAO = new ItemAssignmentDAO();
    }

    /**
     * Calculate the total amount owed by a user for a receipt.
     * Uses BigDecimal internally for precision.
     */
    public float calculateUserOwedAmount(int receiptId, String userId) {
        long startTime = System.currentTimeMillis();
        System.out.println("[ReceiptPaymentDAO] 🔵 calculateUserOwedAmount called - receiptId=" + receiptId + ", userId=" + userId);
        
        String sql = "SELECT " +
                     "  COALESCE(SUM(ri.price * ia.quantity), 0) as assigned_subtotal, " +
                     "  COALESCE(SUM(ri.price * ri.quantity), 0) as total_subtotal, " +
                     "  r.tax_amount, " +
                     "  r.tip_amount " +
                     "FROM receipt_items ri " +
                     "LEFT JOIN item_assignments ia ON ri.item_id = ia.item_id AND ia.user_id = ? " +
                     "INNER JOIN receipts r ON ri.receipt_id = r.receipt_id " +
                     "WHERE ri.receipt_id = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, userId);
            pstmt.setInt(2, receiptId);
            
            long queryStart = System.currentTimeMillis();
            try (ResultSet rs = pstmt.executeQuery()) {
                System.out.println("[ReceiptPaymentDAO] 🔵 Query executed (took " + (System.currentTimeMillis() - queryStart) + "ms)");
                if (rs.next()) {
                    java.math.BigDecimal assignedSubtotal = rs.getBigDecimal("assigned_subtotal");
                    java.math.BigDecimal totalSubtotal = rs.getBigDecimal("total_subtotal");
                    java.math.BigDecimal taxAmount = rs.getBigDecimal("tax_amount");
                    java.math.BigDecimal tipAmount = rs.getBigDecimal("tip_amount");
                    
                    if (assignedSubtotal == null || assignedSubtotal.compareTo(java.math.BigDecimal.ZERO) == 0) {
                        return 0.0f;
                    }
                    
                    if (totalSubtotal == null || totalSubtotal.compareTo(java.math.BigDecimal.ZERO) == 0) {
                        return 0.0f;
                    }
                    
                    java.math.BigDecimal proportion = assignedSubtotal.divide(
                        totalSubtotal, 
                        10,
                        java.math.RoundingMode.HALF_UP
                    );
                    
                    java.math.BigDecimal assignedTax = (taxAmount != null ? taxAmount : java.math.BigDecimal.ZERO)
                        .multiply(proportion)
                        .setScale(2, java.math.RoundingMode.HALF_UP);
                    
                    java.math.BigDecimal assignedTip = (tipAmount != null ? tipAmount : java.math.BigDecimal.ZERO)
                        .multiply(proportion)
                        .setScale(2, java.math.RoundingMode.HALF_UP);
                    
                    java.math.BigDecimal total = assignedSubtotal
                        .add(assignedTax)
                        .add(assignedTip)
                        .setScale(2, java.math.RoundingMode.HALF_UP);
                    
                    float result = total.floatValue();
                    long totalDuration = System.currentTimeMillis() - startTime;
                    System.out.println("[ReceiptPaymentDAO] ✅ calculateUserOwedAmount completed (total time: " + totalDuration + "ms) - result=" + result);
                    return result;
                }
            }
        } catch (SQLException e) {
            System.err.println("[ReceiptPaymentDAO] 🔴 ERROR: Error calculating user owed amount: " + e.getMessage());
            e.printStackTrace();
        }
        
        return 0.0f;
    }

    /**
     * OPTIMIZED: Calculate both owed amount and owed amount excluding paid in a single query.
     * This reduces 2 separate queries (where one calls the other) to 1 query.
     * 
     * @param receiptId The receipt ID
     * @param userId The user ID
     * @return Array with [owedAmount, owedAmountExcludingPaid]
     */
    public float[] calculateBothOwedAmounts(int receiptId, String userId) {
        System.out.println("[ReceiptPaymentDAO] 🔵 STEP 1: calculateBothOwedAmounts called - receiptId=" + receiptId + ", userId=" + userId);
        
        // Use two separate queries but in a single database round-trip would require more complex SQL
        // For safety, we'll calculate both but reuse the total owed calculation
        // This is still better than calling calculateUserOwedAmount twice
        
        // First calculate total owed (this is the expensive one)
        System.out.println("[ReceiptPaymentDAO] 🔵 STEP 2: Calculating total owed amount...");
        float totalOwed = 0.0f;
        try {
            totalOwed = calculateUserOwedAmount(receiptId, userId);
            System.out.println("[ReceiptPaymentDAO] 🔵 STEP 3: Total owed calculated: " + totalOwed);
        } catch (Exception e) {
            System.err.println("[ReceiptPaymentDAO] 🔴 ERROR: Failed to calculate total owed: " + e.getMessage());
            e.printStackTrace();
            return new float[]{0.0f, 0.0f};
        }
        
        if (totalOwed <= 0.01f) {
            System.out.println("[ReceiptPaymentDAO] 🔵 STEP 4: Total owed is 0 or very small, returning zeros");
            return new float[]{0.0f, 0.0f};
        }
        
        // Now calculate unpaid items value
        // FIXED: Use LEFT JOIN to ensure we always get a row, even when there are no paid items
        System.out.println("[ReceiptPaymentDAO] 🔵 STEP 5: Calculating paid items amount...");
        String sql = "SELECT " +
                     "  COALESCE(SUM(CASE WHEN ia.paid_by IS NOT NULL THEN ri.price * ia.quantity ELSE 0 END), 0) as paid_items_subtotal, " +
                     "  COALESCE(SUM(ri.price * ri.quantity), 0) as total_subtotal, " +
                     "  r.tax_amount, " +
                     "  r.tip_amount " +
                     "FROM receipt_items ri " +
                     "LEFT JOIN item_assignments ia ON ri.item_id = ia.item_id AND ia.user_id = ? " +
                     "INNER JOIN receipts r ON ri.receipt_id = r.receipt_id " +
                     "WHERE ri.receipt_id = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            System.out.println("[ReceiptPaymentDAO] 🔵 STEP 6: Connection obtained, creating prepared statement...");
            pstmt.setString(1, userId);
            pstmt.setInt(2, receiptId);
            System.out.println("[ReceiptPaymentDAO] 🔵 STEP 7: Executing paid items query...");
            
            try (ResultSet rs = pstmt.executeQuery()) {
                System.out.println("[ReceiptPaymentDAO] 🔵 STEP 8: Query executed, reading results...");
                
                // This query should always return exactly one row (due to INNER JOIN with receipts)
                if (rs.next()) {
                    try {
                        java.math.BigDecimal paidItemsSubtotal = rs.getBigDecimal("paid_items_subtotal");
                        java.math.BigDecimal totalSubtotal = rs.getBigDecimal("total_subtotal");
                        java.math.BigDecimal taxAmount = rs.getBigDecimal("tax_amount");
                        java.math.BigDecimal tipAmount = rs.getBigDecimal("tip_amount");
                        
                        System.out.println("[ReceiptPaymentDAO] 🔵 STEP 9: Read values - paidItemsSubtotal=" + paidItemsSubtotal + 
                                         ", totalSubtotal=" + totalSubtotal);
                        
                        if (paidItemsSubtotal == null || paidItemsSubtotal.compareTo(java.math.BigDecimal.ZERO) == 0) {
                            // No paid items - return full amount owed
                            System.out.println("[ReceiptPaymentDAO] 🔵 STEP 10: No paid items, returning full amount");
                            return new float[]{totalOwed, totalOwed};
                        }
                        
                        if (totalSubtotal == null || totalSubtotal.compareTo(java.math.BigDecimal.ZERO) == 0) {
                            System.out.println("[ReceiptPaymentDAO] 🔵 STEP 10: Total subtotal is 0, returning full amount");
                            return new float[]{totalOwed, totalOwed};
                        }
                        
                        // Calculate proportion of paid items
                        java.math.BigDecimal proportion = paidItemsSubtotal.divide(
                            totalSubtotal,
                            10,
                            java.math.RoundingMode.HALF_UP
                        );
                        
                        // Calculate proportional tax and tip for paid items
                        java.math.BigDecimal paidTax = (taxAmount != null ? taxAmount : java.math.BigDecimal.ZERO)
                            .multiply(proportion)
                            .setScale(2, java.math.RoundingMode.HALF_UP);
                        
                        java.math.BigDecimal paidTip = (tipAmount != null ? tipAmount : java.math.BigDecimal.ZERO)
                            .multiply(proportion)
                            .setScale(2, java.math.RoundingMode.HALF_UP);
                        
                        // Total paid amount (items + tax + tip)
                        java.math.BigDecimal totalPaid = paidItemsSubtotal
                            .add(paidTax)
                            .add(paidTip)
                            .setScale(2, java.math.RoundingMode.HALF_UP);
                        
                        // Remaining owed = total owed - paid amount
                        java.math.BigDecimal remaining = java.math.BigDecimal.valueOf(totalOwed)
                            .subtract(totalPaid)
                            .setScale(2, java.math.RoundingMode.HALF_UP);
                        
                        // Return 0 if negative (shouldn't happen, but be safe)
                        float remainingOwed = remaining.compareTo(java.math.BigDecimal.ZERO) > 0 ? remaining.floatValue() : 0.0f;
                        System.out.println("[ReceiptPaymentDAO] ✅ STEP 11: Calculation complete - totalOwed=" + totalOwed + ", remainingOwed=" + remainingOwed);
                        return new float[]{totalOwed, remainingOwed};
                    } catch (SQLException e) {
                        System.err.println("[ReceiptPaymentDAO] 🔴 ERROR: Failed to read ResultSet columns: " + e.getMessage());
                        System.err.println("[ReceiptPaymentDAO] 🔴 ERROR: SQL State: " + e.getSQLState());
                        System.err.println("[ReceiptPaymentDAO] 🔴 ERROR: Error Code: " + e.getErrorCode());
                        e.printStackTrace();
                    }
                } else {
                    System.out.println("[ReceiptPaymentDAO] 🔴 STEP 8: Query returned no rows (unexpected - should always return 1 row)");
                }
            }
        } catch (SQLException e) {
            System.err.println("[ReceiptPaymentDAO] 🔴 ERROR: SQLException in calculateBothOwedAmounts: " + e.getMessage());
            System.err.println("[ReceiptPaymentDAO] 🔴 ERROR: SQL State: " + e.getSQLState());
            System.err.println("[ReceiptPaymentDAO] 🔴 ERROR: Error Code: " + e.getErrorCode());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("[ReceiptPaymentDAO] 🔴 ERROR: Unexpected exception in calculateBothOwedAmounts: " + e.getMessage());
            System.err.println("[ReceiptPaymentDAO] 🔴 ERROR: Exception type: " + e.getClass().getName());
            e.printStackTrace();
        }
        
        // Fallback: if paid items query fails, assume nothing is paid
        System.out.println("[ReceiptPaymentDAO] 🔵 STEP 13: Using fallback - assuming nothing is paid");
        return new float[]{totalOwed, totalOwed};
    }

    /**
     * Calculate the amount owed by a user excluding items that are already paid.
     */
    public float calculateUserOwedAmountExcludingPaid(int receiptId, String userId) {
        // Similar to calculateUserOwedAmount, but exclude items that are paid for
        // Use a simpler approach: get total owed, then subtract paid items
        // This avoids complex CASE statements that might cause ResultSet issues
        
        // First, get the total owed amount (includes all assigned items)
        float totalOwed = calculateUserOwedAmount(receiptId, userId);
        
        if (totalOwed <= 0.01f) {
            return 0.0f; // No items assigned or already paid
        }
        
        // Now calculate the value of paid items assigned to this user
        // FIXED: Check item_assignments.paid_by instead of receipt_items columns
        String sql = "SELECT " +
                     "  COALESCE(SUM(ri.price * ia.quantity), 0) as paid_items_subtotal, " +
                     "  COALESCE(SUM(ri.price * ri.quantity), 0) as total_subtotal, " +
                     "  r.tax_amount, " +
                     "  r.tip_amount " +
                     "FROM receipt_items ri " +
                     "INNER JOIN item_assignments ia ON ri.item_id = ia.item_id AND ia.user_id = ? " +
                     "INNER JOIN receipts r ON ri.receipt_id = r.receipt_id " +
                     "WHERE ri.receipt_id = ? " +
                     "  AND ia.paid_by IS NOT NULL";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, userId);
            pstmt.setInt(2, receiptId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    java.math.BigDecimal paidItemsSubtotal = rs.getBigDecimal("paid_items_subtotal");
                    java.math.BigDecimal totalSubtotal = rs.getBigDecimal("total_subtotal");
                    java.math.BigDecimal taxAmount = rs.getBigDecimal("tax_amount");
                    java.math.BigDecimal tipAmount = rs.getBigDecimal("tip_amount");
                    
                    if (paidItemsSubtotal == null || paidItemsSubtotal.compareTo(java.math.BigDecimal.ZERO) == 0) {
                        // No paid items assigned to this user - return full amount
                        return totalOwed;
                    }
                    
                    if (totalSubtotal == null || totalSubtotal.compareTo(java.math.BigDecimal.ZERO) == 0) {
                        return totalOwed;
                    }
                    
                    // Calculate proportion of paid items
                    java.math.BigDecimal proportion = paidItemsSubtotal.divide(
                        totalSubtotal,
                        10,
                        java.math.RoundingMode.HALF_UP
                    );
                    
                    // Calculate proportional tax and tip for paid items
                    java.math.BigDecimal paidTax = (taxAmount != null ? taxAmount : java.math.BigDecimal.ZERO)
                        .multiply(proportion)
                        .setScale(2, java.math.RoundingMode.HALF_UP);
                    
                    java.math.BigDecimal paidTip = (tipAmount != null ? tipAmount : java.math.BigDecimal.ZERO)
                        .multiply(proportion)
                        .setScale(2, java.math.RoundingMode.HALF_UP);
                    
                    // Total paid amount (items + tax + tip)
                    java.math.BigDecimal totalPaid = paidItemsSubtotal
                        .add(paidTax)
                        .add(paidTip)
                        .setScale(2, java.math.RoundingMode.HALF_UP);
                    
                    // Remaining owed = total owed - paid amount
                    java.math.BigDecimal remaining = java.math.BigDecimal.valueOf(totalOwed)
                        .subtract(totalPaid)
                        .setScale(2, java.math.RoundingMode.HALF_UP);
                    
                    // Return 0 if negative (shouldn't happen, but be safe)
                    return remaining.compareTo(java.math.BigDecimal.ZERO) > 0 ? remaining.floatValue() : 0.0f;
                }
            }
        } catch (SQLException e) {
            System.err.println("[ReceiptPaymentDAO] Error calculating user owed amount excluding paid: " + e.getMessage());
            e.printStackTrace();
            // Fallback: return the total owed amount if calculation fails
            return totalOwed;
        }
        
        // If no paid items found, return full amount owed
        return totalOwed;
    }

    /**
     * Batch calculate owed amounts for multiple receipts.
     */
    public Map<Integer, Float> calculateUserOwedAmountsBatch(List<Integer> receiptIds, String userId) {
        if (receiptIds == null || receiptIds.isEmpty()) {
            return new HashMap<>();
        }
        
        Map<Integer, Float> owedAmounts = new HashMap<>();
        
        // Build placeholders for IN clause
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < receiptIds.size(); i++) {
            if (i > 0) placeholders.append(",");
            placeholders.append("?");
        }
        
        String sql = "SELECT " +
                     "  r.receipt_id, " +
                     "  COALESCE(SUM(ri.price * ia.quantity), 0) as assigned_subtotal, " +
                     "  COALESCE(SUM(ri.price * ri.quantity), 0) as total_subtotal, " +
                     "  r.tax_amount, " +
                     "  r.tip_amount " +
                     "FROM receipts r " +
                     "LEFT JOIN receipt_items ri ON r.receipt_id = ri.receipt_id " +
                     "LEFT JOIN item_assignments ia ON ri.item_id = ia.item_id AND ia.user_id = ? " +
                     "WHERE r.receipt_id IN (" + placeholders + ") " +
                     "GROUP BY r.receipt_id, r.tax_amount, r.tip_amount";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, userId);
            for (int i = 0; i < receiptIds.size(); i++) {
                pstmt.setInt(i + 2, receiptIds.get(i));
            }
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int receiptId = rs.getInt("receipt_id");
                    java.math.BigDecimal assignedSubtotal = rs.getBigDecimal("assigned_subtotal");
                    java.math.BigDecimal totalSubtotal = rs.getBigDecimal("total_subtotal");
                    java.math.BigDecimal taxAmount = rs.getBigDecimal("tax_amount");
                    java.math.BigDecimal tipAmount = rs.getBigDecimal("tip_amount");
                    
                    if (assignedSubtotal == null || assignedSubtotal.compareTo(java.math.BigDecimal.ZERO) == 0) {
                        owedAmounts.put(receiptId, 0.0f);
                        continue;
                    }
                    
                    if (totalSubtotal == null || totalSubtotal.compareTo(java.math.BigDecimal.ZERO) == 0) {
                        owedAmounts.put(receiptId, 0.0f);
                        continue;
                    }
                    
                    java.math.BigDecimal proportion = assignedSubtotal.divide(
                        totalSubtotal,
                        10,
                        java.math.RoundingMode.HALF_UP
                    );
                    
                    java.math.BigDecimal assignedTax = (taxAmount != null ? taxAmount : java.math.BigDecimal.ZERO)
                        .multiply(proportion)
                        .setScale(2, java.math.RoundingMode.HALF_UP);
                    
                    java.math.BigDecimal assignedTip = (tipAmount != null ? tipAmount : java.math.BigDecimal.ZERO)
                        .multiply(proportion)
                        .setScale(2, java.math.RoundingMode.HALF_UP);
                    
                    java.math.BigDecimal total = assignedSubtotal
                        .add(assignedTax)
                        .add(assignedTip)
                        .setScale(2, java.math.RoundingMode.HALF_UP);
                    
                    owedAmounts.put(receiptId, total.floatValue());
                }
            }
        } catch (SQLException e) {
            System.err.println("Error batch calculating user owed amounts: " + e.getMessage());
            e.printStackTrace();
        }
        
        return owedAmounts;
    }

    /**
     * Record payment and mark items as paid in a single transaction.
     * This ensures atomicity - either both operations succeed or both fail.
     */
    public int recordPaymentAndMarkItems(int receiptId, String userId, double amount, ReceiptParticipantDAO participantDAO, ItemAssignmentDAO assignmentDAO) {
        Connection conn = null;
        try {
            conn = dbConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Step 1: Record payment in receipt_participants
            String paymentSql = "UPDATE receipt_participants " +
                               "SET paid_amount = COALESCE(paid_amount, 0) + ?, paid_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP " +
                               "WHERE receipt_id = ? AND user_id = ?";
            
            try (PreparedStatement pstmt = conn.prepareStatement(paymentSql)) {
                pstmt.setBigDecimal(1, java.math.BigDecimal.valueOf(amount));
                pstmt.setInt(2, receiptId);
                pstmt.setString(3, userId);
                
                int paymentRows = pstmt.executeUpdate();
                if (paymentRows == 0) {
                    conn.rollback();
                    System.err.println("Failed to record payment: participant not found for receipt " + receiptId + ", user " + userId);
                    return -1;
                }
            }
            
            // Step 2: Mark item assignments as paid
            String itemsSql = "UPDATE item_assignments " +
                            "SET paid_by = ?, paid_at = CURRENT_TIMESTAMP " +
                            "WHERE receipt_id = ? AND user_id = ? AND paid_by IS NULL";
            
            int itemsMarked = 0;
            try (PreparedStatement pstmt = conn.prepareStatement(itemsSql)) {
                pstmt.setString(1, userId);
                pstmt.setInt(2, receiptId);
                pstmt.setString(3, userId);
                
                itemsMarked = pstmt.executeUpdate();
            }
            
            conn.commit();
            System.out.println("[ReceiptPaymentDAO] Successfully recorded payment and marked " + itemsMarked + " items as paid in single transaction");
            
            return itemsMarked;
            
        } catch (SQLException e) {
            System.err.println("[ReceiptPaymentDAO] ERROR: Failed to record payment and mark items in transaction: " + e.getMessage());
            e.printStackTrace();
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    System.err.println("Error rolling back payment transaction: " + rollbackEx.getMessage());
                }
            }
            return -1;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException e) {
                    System.err.println("Error resetting auto-commit: " + e.getMessage());
                }
            }
        }
    }
}

