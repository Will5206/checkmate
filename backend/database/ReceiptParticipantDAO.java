package database;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object for managing receipt participants.
 * Handles all operations related to receipt_participants table.
 */
public class ReceiptParticipantDAO {
    
    private final DatabaseConnection dbConnection;

    public ReceiptParticipantDAO() {
        this.dbConnection = DatabaseConnection.getInstance();
    }

    /**
     * Add a single receipt participant.
     */
    public boolean addReceiptParticipant(int receiptId, String userId) {
        String sql = "INSERT INTO receipt_participants (receipt_id, user_id, status) " +
                     "VALUES (?, ?, 'pending') " +
                     "ON DUPLICATE KEY UPDATE status = 'pending'";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, receiptId);
            pstmt.setString(2, userId);

            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;

        } catch (SQLException e) {
            System.err.println("Error adding receipt participant: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Batch insert multiple receipt participants for better performance.
     */
    public int addReceiptParticipantsBatch(int receiptId, List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }
        
        String sql = "INSERT INTO receipt_participants (receipt_id, user_id, status) " +
                     "VALUES (?, ?, 'pending') " +
                     "ON DUPLICATE KEY UPDATE status = 'pending'";
        
        int addedCount = 0;
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            for (String userId : userIds) {
                pstmt.setInt(1, receiptId);
                pstmt.setString(2, userId);
                pstmt.addBatch();
            }
            
            int[] affectedRows = pstmt.executeBatch();
            
            for (int rows : affectedRows) {
                if (rows > 0) {
                    addedCount++;
                }
            }
            
            System.out.println("[ReceiptParticipantDAO] Batch added " + addedCount + " participants for receipt " + receiptId);
            
        } catch (SQLException e) {
            System.err.println("Error batch adding receipt participants: " + e.getMessage());
            e.printStackTrace();
        }
        
        return addedCount;
    }

    /**
     * Update the status of a receipt participant.
     */
    public boolean updateParticipantStatus(int receiptId, String userId, String status) {
        String sql = "UPDATE receipt_participants SET status = ?, updated_at = CURRENT_TIMESTAMP " +
                     "WHERE receipt_id = ? AND user_id = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, status);
            pstmt.setInt(2, receiptId);
            pstmt.setString(3, userId);

            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;

        } catch (SQLException e) {
            System.err.println("Error updating participant status: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Update status for all participants of a receipt.
     */
    public boolean updateAllParticipantsStatus(int receiptId, String status) {
        String sql = "UPDATE receipt_participants SET status = ?, updated_at = CURRENT_TIMESTAMP " +
                     "WHERE receipt_id = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, status);
            pstmt.setInt(2, receiptId);
            
            int affectedRows = pstmt.executeUpdate();
            System.out.println("Updated status to '" + status + "' for " + affectedRows + " participants of receipt " + receiptId);
            return affectedRows > 0;
            
        } catch (SQLException e) {
            System.err.println("Error updating all participants status: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Get the status of a receipt for a specific user.
     */
    public String getParticipantStatus(int receiptId, String userId) {
        String sql = "SELECT status FROM receipt_participants WHERE receipt_id = ? AND user_id = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, receiptId);
            pstmt.setString(2, userId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("status");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting participant status: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Get all participants for a receipt with their payment status.
     */
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
                    participant.put("user_id", rs.getString("user_id"));
                    participant.put("status", rs.getString("status"));
                    participant.put("paid_amount", rs.getBigDecimal("paid_amount").floatValue());
                    participants.add(participant);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting participants with payment status: " + e.getMessage());
            e.printStackTrace();
        }

        return participants;
    }

    /**
     * Record a payment for a participant.
     */
    public boolean recordPayment(int receiptId, String userId, float amount) {
        String sql = "UPDATE receipt_participants " +
                     "SET paid_amount = COALESCE(paid_amount, 0) + ?, paid_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP " +
                     "WHERE receipt_id = ? AND user_id = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setBigDecimal(1, java.math.BigDecimal.valueOf(amount));
            pstmt.setInt(2, receiptId);
            pstmt.setString(3, userId);

            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;

        } catch (SQLException e) {
            System.err.println("Error recording payment: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Get the amount paid by a user for a receipt.
     */
    public float getPaidAmount(int receiptId, String userId) {
        String sql = "SELECT COALESCE(paid_amount, 0) as paid_amount FROM receipt_participants " +
                     "WHERE receipt_id = ? AND user_id = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, receiptId);
            pstmt.setString(2, userId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("paid_amount").floatValue();
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting paid amount: " + e.getMessage());
            e.printStackTrace();
        }

        return 0.0f;
    }
}






