package database;

import java.sql.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * DAO for managing receipt completion status.
 * Handles checking and updating whether receipts are complete (all items paid).
 */
public class ReceiptCompletionDAO {

    private final DatabaseConnection dbConnection;
    // Thread pool for async operations (prevents thread exhaustion)
    private static final ExecutorService asyncUpdateExecutor = Executors.newFixedThreadPool(5);
    
    // Dependencies on other DAOs for completion checks
    private final ItemAssignmentDAO itemAssignmentDAO;
    private final ReceiptParticipantDAO participantDAO;
    private final ReceiptPaymentDAO paymentDAO;
    private final ReceiptCoreDAO coreDAO;

    public ReceiptCompletionDAO() {
        this.dbConnection = DatabaseConnection.getInstance();
        this.itemAssignmentDAO = new ItemAssignmentDAO();
        this.participantDAO = new ReceiptParticipantDAO();
        this.paymentDAO = new ReceiptPaymentDAO();
        this.coreDAO = new ReceiptCoreDAO();
    }

    /**
     * Update receipt complete status based on payment status.
     * 
     * @param receiptId The receipt ID
     * @return true if receipt is complete (all items paid), false otherwise
     */
    public boolean updateReceiptCompleteStatus(int receiptId) {
        System.out.println("[ReceiptCompletionDAO] updateReceiptCompleteStatus called for receipt " + receiptId);
        
        // Check if all item assignments are paid (paid_by IS NOT NULL in item_assignments)
        boolean allItemsPaid = areAllItemsPaidFor(receiptId);
        System.out.println("[ReceiptCompletionDAO] areAllItemsPaidFor returned: " + allItemsPaid + " for receipt " + receiptId);
        
        // Receipt is complete only if ALL items are paid for
        updateCompleteStatusInDB(receiptId, allItemsPaid);
        
        return allItemsPaid;
    }

    /**
     * Update receipt complete status asynchronously.
     * 
     * @param receiptId The receipt ID
     */
    public void updateReceiptCompleteStatusAsync(int receiptId) {
        asyncUpdateExecutor.submit(() -> {
            try {
                updateReceiptCompleteStatus(receiptId);
            } catch (Exception e) {
                System.err.println("[ReceiptCompletionDAO] Error updating receipt complete status in background for receipt " + receiptId + ": " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Check if a receipt is marked as complete in the database.
     * 
     * @param receiptId The receipt ID
     * @return true if receipt.complete = 1, false otherwise
     */
    public boolean isReceiptComplete(int receiptId) {
        String sql = "SELECT complete FROM receipts WHERE receipt_id = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, receiptId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean("complete");
                }
            }
        } catch (SQLException e) {
            System.err.println("[ReceiptCompletionDAO] ERROR: Error checking receipt complete status: " + e.getMessage());
            e.printStackTrace();
        }
        
        return false;
    }

    /**
     * Check if all items in a receipt are claimed.
     * Delegates to ItemAssignmentDAO.
     * 
     * @param receiptId The receipt ID
     * @return true if all items are claimed, false otherwise
     */
    public boolean areAllItemsClaimed(int receiptId) {
        return itemAssignmentDAO.areAllItemsClaimed(receiptId);
    }

    /**
     * Check if all participants have paid their full amount and mark receipt as completed if so.
     * Also checks if all items are claimed before marking as completed.
     * 
     * @param receiptId The receipt ID
     * @return true if receipt was marked as completed, false otherwise
     */
    public boolean checkAndMarkReceiptCompleted(int receiptId) {
        // CRITICAL FIX: Check if all item assignments are paid
        boolean allItemsPaid = areAllItemsPaidFor(receiptId);
        
        if (allItemsPaid) {
            // Mark receipt as completed
            boolean receiptUpdated = coreDAO.updateReceiptStatus(receiptId, "completed");
            
            // CRITICAL FIX: Also update the 'complete' column to 1
            updateReceiptCompleteStatus(receiptId);
            
            if (receiptUpdated) {
                // Mark all participants as 'completed' so receipt moves to History for everyone
                participantDAO.updateAllParticipantsStatus(receiptId, "completed");
                System.out.println("Receipt " + receiptId + " is now fully paid and completed (all item assignments paid, status='completed', complete=1)");
            }
            
            return receiptUpdated;
        }

        return false;
    }

    /**
     * Check if all items in a receipt are paid for.
     * An item is paid for when all its assignments in item_assignments have paid_by IS NOT NULL.
     * 
     * @param receiptId The receipt ID
     * @return true if all items are paid for, false otherwise
     */
    private boolean areAllItemsPaidFor(int receiptId) {
        String sql = "SELECT " +
                     "  COUNT(DISTINCT ri.item_id) as total_items, " +
                     "  COUNT(DISTINCT CASE WHEN ia.paid_by IS NOT NULL THEN ri.item_id END) as paid_items " +
                     "FROM receipt_items ri " +
                     "LEFT JOIN item_assignments ia ON ri.item_id = ia.item_id " +
                     "WHERE ri.receipt_id = ? " +
                     "GROUP BY ri.receipt_id";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, receiptId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int totalItems = rs.getInt("total_items");
                    int paidItems = rs.getInt("paid_items");
                    
                    System.out.println("[ReceiptCompletionDAO] Receipt " + receiptId + ": " + paidItems + "/" + totalItems + " items have paid assignments");
                    
                    if (totalItems == 0) {
                        System.out.println("[ReceiptCompletionDAO] Receipt " + receiptId + " has no items - not complete");
                        return false;
                    }
                    
                    // Check if all items have at least one paid assignment
                    boolean allItemsHavePaidAssignments = (paidItems == totalItems);
                    
                    // Also check that ALL assignments are paid (not just some)
                    String checkAllAssignmentsSql = "SELECT " +
                                                   "  COUNT(*) as total_assignments, " +
                                                   "  SUM(CASE WHEN paid_by IS NOT NULL THEN 1 ELSE 0 END) as paid_assignments " +
                                                   "FROM item_assignments " +
                                                   "WHERE receipt_id = ?";
                    
                    try (PreparedStatement pstmt2 = conn.prepareStatement(checkAllAssignmentsSql)) {
                        pstmt2.setInt(1, receiptId);
                        try (ResultSet rs2 = pstmt2.executeQuery()) {
                            if (rs2.next()) {
                                int totalAssignments = rs2.getInt("total_assignments");
                                int paidAssignments = rs2.getInt("paid_assignments");
                                
                                if (totalAssignments == 0) {
                                    // No assignments yet - receipt not complete
                                    System.out.println("[ReceiptCompletionDAO] Receipt " + receiptId + " has no item assignments - not complete");
                                    return false;
                                }
                                
                                boolean allAssignmentsPaid = (paidAssignments == totalAssignments);
                                boolean allPaid = allItemsHavePaidAssignments && allAssignmentsPaid;
                                
                                if (allPaid) {
                                    System.out.println("[ReceiptCompletionDAO] Receipt " + receiptId + ": ALL " + totalItems + " items and ALL " + totalAssignments + " assignments are paid - can move to History");
                                } else {
                                    System.out.println("[ReceiptCompletionDAO] Receipt " + receiptId + ": " + (totalAssignments - paidAssignments) + " assignments still need to be paid for");
                                }
                                
                                return allPaid;
                            }
                        }
                    }
                } else {
                    // No items found
                    System.out.println("[ReceiptCompletionDAO] Receipt " + receiptId + " has no items - not complete");
                    return false;
                }
            }
        } catch (SQLException e) {
            System.err.println("[ReceiptCompletionDAO] ERROR: Error checking if all items paid for: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
        
        return false;
    }
    
    /**
     * Helper method to check if all participants have paid their full amount.
     * 
     * @param receiptId The receipt ID
     * @return true if all participants have paid, false otherwise
     */
    private boolean areAllParticipantsPaid(int receiptId) {
        List<Map<String, Object>> participants = participantDAO.getParticipantsWithPaymentStatus(receiptId);
        
        // CRITICAL FIX: If no participants exist, it means only the uploader is involved
        // The uploader doesn't need to pay, so if all items are claimed and no participants exist,
        // the receipt is complete (uploader already "paid" by uploading the receipt)
        if (participants.isEmpty()) {
            System.out.println("[ReceiptCompletionDAO] No participants found for receipt " + receiptId + " - checking if uploader has all items claimed");
            // If there are no participants, the receipt is complete if all items are claimed
            return areAllItemsClaimed(receiptId);
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
                System.out.println("[ReceiptCompletionDAO] Participant " + participant.get("user_id") + " has not paid fully: paid=" + paidAmount + ", owed=" + owedAmount);
                return false;
            }
        }
        
        System.out.println("[ReceiptCompletionDAO] All " + participants.size() + " participants have paid their full amount");
        return true;
    }
    
    /**
     * Helper method to update the complete status in the database.
     * 
     * @param receiptId The receipt ID
     * @param isComplete Whether the receipt is complete
     */
    private void updateCompleteStatusInDB(int receiptId, boolean isComplete) {
        String sql = "UPDATE receipts SET complete = ? WHERE receipt_id = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setBoolean(1, isComplete);
            pstmt.setInt(2, receiptId);
            
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                System.out.println("[ReceiptCompletionDAO] SUCCESS: Updated receipt " + receiptId + " complete status to " + isComplete + " (affected rows: " + affectedRows + ")");
                
                // Verify the update by querying the database
                String verifySql = "SELECT complete FROM receipts WHERE receipt_id = ?";
                try (PreparedStatement verifyPstmt = conn.prepareStatement(verifySql)) {
                    verifyPstmt.setInt(1, receiptId);
                    try (ResultSet verifyRs = verifyPstmt.executeQuery()) {
                        if (verifyRs.next()) {
                            boolean actualComplete = verifyRs.getBoolean("complete");
                            System.out.println("[ReceiptCompletionDAO] VERIFIED: Receipt " + receiptId + " complete status in DB is now: " + actualComplete);
                        }
                    }
                }
            } else {
                System.out.println("[ReceiptCompletionDAO] WARNING: No rows affected when updating receipt " + receiptId + " complete status");
            }
        } catch (SQLException e) {
            System.err.println("[ReceiptCompletionDAO] ERROR: Error updating receipt complete status: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

