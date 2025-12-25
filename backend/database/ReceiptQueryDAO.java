package database;

import models.Receipt;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DAO for receipt query operations.
 * Handles listing receipts (pending, activity) and batch metadata operations.
 */
public class ReceiptQueryDAO {

    private final DatabaseConnection dbConnection;
    private final ReceiptCoreDAO coreDAO;
    private final ReceiptPaymentDAO paymentDAO;

    public ReceiptQueryDAO() {
        this.dbConnection = DatabaseConnection.getInstance();
        this.coreDAO = new ReceiptCoreDAO();
        this.paymentDAO = new ReceiptPaymentDAO();
    }

    /**
     * Get all pending receipts for a user.
     * Pending receipts are those where complete = 0 (not yet fully paid).
     * 
     * @param userId The user ID
     * @return List of pending Receipt objects
     */
    public List<Receipt> getPendingReceiptsForUser(String userId) {
        // Get receipts where:
        // 1. User is the uploader, OR
        // 2. User is a participant with receipt_participants.status 'pending' or 'accepted' (not declined)
        // AND complete = 0 (receipt not yet complete - items not all paid for)
        String sql = "SELECT DISTINCT r.* FROM (" +
                     "  SELECT r.* FROM receipts r WHERE r.uploaded_by = ? AND r.complete = 0 " +
                     "  UNION " +
                     "  SELECT r.* FROM receipts r " +
                     "  INNER JOIN receipt_participants rp ON r.receipt_id = rp.receipt_id " +
                     "  WHERE rp.user_id = ? AND rp.status IN ('pending', 'accepted') AND r.complete = 0" +
                     ") AS r " +
                     "ORDER BY r.created_at DESC";
        
        List<Receipt> receipts = new ArrayList<>();

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, userId);
            pstmt.setString(2, userId);

            try (ResultSet rs = pstmt.executeQuery()) {
                System.out.println("[ReceiptQueryDAO] Getting pending receipts for user " + userId);
                while (rs.next()) {
                    Receipt receipt = coreDAO.mapResultSetToReceipt(rs);
                    receipts.add(receipt);
                    System.out.println("[ReceiptQueryDAO] Collected pending receipt " + receipt.getReceiptId() + " (complete = FALSE, total so far: " + receipts.size() + ")");
                }
                System.out.println("[ReceiptQueryDAO] Found total of " + receipts.size() + " pending receipts for user " + userId);
            }
        } catch (SQLException e) {
            System.err.println("[ReceiptQueryDAO] Error getting pending receipts for user: " + e.getMessage());
            e.printStackTrace();
        }

        // OPTIMIZATION: Don't load items here - only load basic receipt info for list view
        System.out.println("[ReceiptQueryDAO] Skipping item loading for pending receipts list (optimization)");
        return receipts;
    }

    /**
     * Get all receipts for a specific user (History/Activity - completed receipts only).
     * Shows receipts where complete = 1 and the user is either the uploader or a participant.
     * 
     * @param userId The user's ID (VARCHAR(36))
     * @return List of Receipt objects where complete = 1
     */
    public List<Receipt> getAllReceiptsForUser(String userId) {
        System.out.println("[ReceiptQueryDAO] STEP C1: getAllReceiptsForUser called for userId: " + userId);
        String sql = "SELECT DISTINCT r.* FROM (" +
                     "  SELECT r.* FROM receipts r WHERE r.uploaded_by = ? AND r.complete = 1 " +
                     "  UNION " +
                     "  SELECT r.* FROM receipts r " +
                     "  INNER JOIN receipt_participants rp ON r.receipt_id = rp.receipt_id " +
                     "  WHERE rp.user_id = ? AND rp.status != 'declined' AND r.complete = 1" +
                     ") AS r " +
                     "ORDER BY r.created_at DESC";
        
        System.out.println("[ReceiptQueryDAO] STEP C2: SQL query prepared");
        List<Receipt> receipts = new ArrayList<>();
        Set<Integer> receiptIds = new HashSet<>(); // Track unique receipt IDs

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            System.out.println("[ReceiptQueryDAO] STEP C3: Database connection established, setting parameters");
            pstmt.setString(1, userId); // For uploaded_by check
            pstmt.setString(2, userId); // For participant check
            System.out.println("[ReceiptQueryDAO] STEP C4: Parameters set, executing query");

            try (ResultSet rs = pstmt.executeQuery()) {
                System.out.println("[ReceiptQueryDAO] STEP C5: Query executed, iterating ResultSet");
                int rowCount = 0;
                while (rs.next()) {
                    rowCount++;
                    System.out.println("[ReceiptQueryDAO] STEP C6: Processing row " + rowCount);
                    Receipt receipt = coreDAO.mapResultSetToReceipt(rs);
                    int receiptId = receipt.getReceiptId();
                    System.out.println("[ReceiptQueryDAO] STEP C7: Mapped receipt ID: " + receiptId + ", merchant: " + receipt.getMerchantName());
                    
                    if (!receiptIds.contains(receiptId)) {
                        receiptIds.add(receiptId);
                        receipts.add(receipt);
                        System.out.println("[ReceiptQueryDAO] STEP C8: Added receipt " + receiptId + " to History (complete = TRUE, total: " + receipts.size() + ")");
                    } else {
                        System.out.println("[ReceiptQueryDAO] STEP C8-SKIP: Receipt " + receiptId + " already in list, skipping duplicate");
                    }
                }
                System.out.println("[ReceiptQueryDAO] STEP C9: Finished iterating ResultSet. Total rows: " + rowCount + ", Unique receipts: " + receipts.size());
            }
        } catch (SQLException e) {
            System.err.println("[ReceiptQueryDAO] ERROR: Error getting all receipts for user: " + e.getMessage());
            e.printStackTrace();
        }

        // OPTIMIZATION: Don't load items here - only load basic receipt info for list view
        System.out.println("[ReceiptQueryDAO] STEP C10: Skipping item loading for list view (optimization)");
        System.out.println("[ReceiptQueryDAO] STEP C11: Returning " + receipts.size() + " completed receipts (complete = TRUE) without items");
        return receipts;
    }

    /**
     * Batch fetch metadata for multiple receipts in a single query.
     * Gets uploaded_by, participant status, paid amount, and complete status.
     * 
     * @param receiptIds List of receipt IDs
     * @param userId The user ID to get participant status for
     * @return Map of receiptId -> ReceiptMetadata
     */
    public Map<Integer, ReceiptDAO.ReceiptMetadata> getReceiptsMetadataBatch(List<Integer> receiptIds, String userId) {
        if (receiptIds == null || receiptIds.isEmpty()) {
            return new HashMap<>();
        }
        
        // Build IN clause with placeholders
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < receiptIds.size(); i++) {
            if (i > 0) placeholders.append(",");
            placeholders.append("?");
        }
        
        // Single query to get all metadata: uploaded_by, participant status, paid amount, complete status
        String sql = "SELECT " +
                     "  r.receipt_id, " +
                     "  r.uploaded_by, " +
                     "  r.complete as is_complete, " +
                     "  COALESCE(rp.status, NULL) as participant_status, " +
                     "  COALESCE(rp.paid_amount, 0) as paid_amount " +
                     "FROM receipts r " +
                     "LEFT JOIN receipt_participants rp ON r.receipt_id = rp.receipt_id AND rp.user_id = ? " +
                     "WHERE r.receipt_id IN (" + placeholders.toString() + ")";
        
        Map<Integer, ReceiptDAO.ReceiptMetadata> metadataMap = new HashMap<>();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            // Set userId parameter (for LEFT JOIN)
            pstmt.setString(1, userId);
            
            // Set receipt ID parameters
            for (int i = 0; i < receiptIds.size(); i++) {
                pstmt.setInt(i + 2, receiptIds.get(i));
            }
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int receiptId = rs.getInt("receipt_id");
                    String uploadedBy = rs.getString("uploaded_by");
                    boolean isComplete = rs.getBoolean("is_complete");
                    String participantStatus = rs.getString("participant_status");
                    float paidAmount = rs.getBigDecimal("paid_amount").floatValue();
                    
                    ReceiptDAO.ReceiptMetadata metadata = new ReceiptDAO.ReceiptMetadata();
                    metadata.uploadedBy = uploadedBy;
                    metadata.isComplete = isComplete;
                    metadata.participantStatus = participantStatus;
                    metadata.paidAmount = paidAmount;
                    
                    metadataMap.put(receiptId, metadata);
                }
            }
        } catch (SQLException e) {
            System.err.println("[ReceiptQueryDAO] Error batch fetching receipts metadata: " + e.getMessage());
            e.printStackTrace();
        }
        
        return metadataMap;
    }
    
    /**
     * Batch calculate owed amounts for multiple receipts in a single query.
     * Delegates to ReceiptPaymentDAO.
     * 
     * @param receiptIds List of receipt IDs to calculate owed amounts for
     * @param userId The user ID
     * @return Map of receiptId -> owedAmount
     */
    public Map<Integer, Float> calculateUserOwedAmountsBatch(List<Integer> receiptIds, String userId) {
        return paymentDAO.calculateUserOwedAmountsBatch(receiptIds, userId);
    }
}

