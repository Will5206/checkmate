package services;

import models.Receipt;
import database.ReceiptDAO;
import java.util.*;

/**
 * Service for managing receipt viewing and acceptance/decline operations.
 * Now uses database persistence via ReceiptDAO.
 */
public class ReceiptService {

    // Singleton instance
    private static ReceiptService instance;

    // Data Access Object for database operations
    private final ReceiptDAO receiptDAO;

    private ReceiptService() {
        // Private constructor for singleton
        this.receiptDAO = new ReceiptDAO();
    }

    /**
     * Get the singleton instance of ReceiptService.
     */
    public static synchronized ReceiptService getInstance() {
        if (instance == null) {
            instance = new ReceiptService();
        }
        return instance;
    }

    /**
     * Add a receipt to a user's pending receipts list.
     * Called when a receipt is sent to a friend.
     * This now uses the database via ReceiptDAO.
     * 
     * @param userId The user who should receive this receipt (as int for backward compatibility)
     * @param receipt The receipt to add
     */
    public void addPendingReceipt(int userId, Receipt receipt) {
        // Convert int userId to String for database
        String userIdStr = String.valueOf(userId);
        
        // Add participant to the receipt in database
        boolean added = receiptDAO.addReceiptParticipant(receipt.getReceiptId(), userIdStr);
        
        if (added) {
            System.out.println("Added pending receipt " + receipt.getReceiptId() + " for user " + userId);
        } else {
            System.err.println("Failed to add pending receipt " + receipt.getReceiptId() + " for user " + userId);
        }
    }

    /**
     * Add a receipt to a user's pending receipts list (String userId version).
     * 
     * @param userId The user who should receive this receipt (as String)
     * @param receipt The receipt to add
     */
    public void addPendingReceipt(String userId, Receipt receipt) {
        boolean added = receiptDAO.addReceiptParticipant(receipt.getReceiptId(), userId);
        
        if (added) {
            System.out.println("Added pending receipt " + receipt.getReceiptId() + " for user " + userId);
        } else {
            System.err.println("Failed to add pending receipt " + receipt.getReceiptId() + " for user " + userId);
        }
    }

    /**
     * Get a receipt by ID for a specific user.
     * Returns null if the receipt doesn't exist or isn't accessible to the user.
     * 
     * @param userId The user requesting the receipt (as int for backward compatibility)
     * @param receiptId The receipt ID
     * @return The receipt if found and accessible, null otherwise
     */
    public Receipt getReceipt(int userId, int receiptId) {
        // Convert int userId to String for database
        String userIdStr = String.valueOf(userId);
        
        // Get receipt from database
        Receipt receipt = receiptDAO.getReceiptById(receiptId);
        
        if (receipt == null) {
            return null;
        }
        
        // Check if user is a participant (either uploaded it or was sent it)
        String uploadedBy = receiptDAO.getReceiptUploadedBy(receiptId);
        String participantStatus = receiptDAO.getParticipantStatus(receiptId, userIdStr);
        
        // User can access if they uploaded it or are a participant
        if (uploadedBy != null && uploadedBy.equals(userIdStr)) {
            return receipt;
        }
        
        if (participantStatus != null) {
            return receipt;
        }
        
        return null;
    }

    /**
     * Get all pending receipts for a user.
     * 
     * @param userId The user ID (as int for backward compatibility)
     * @return List of pending receipts
     */
    public List<Receipt> getPendingReceipts(int userId) {
        // Convert int userId to String for database
        String userIdStr = String.valueOf(userId);
        
        // Get pending receipts from database
        return receiptDAO.getPendingReceiptsForUser(userIdStr);
    }

    /**
     * Get all pending receipts for a user (String userId version).
     * 
     * @param userId The user ID (as String)
     * @return List of pending receipts
     */
    public List<Receipt> getPendingReceipts(String userId) {
        return receiptDAO.getPendingReceiptsForUser(userId);
    }

    /**
     * Accept a receipt. This will mark it as accepted in the database.
     * 
     * @param userId The user accepting the receipt (as int for backward compatibility)
     * @param receiptId The receipt ID to accept
     * @return true if the receipt was successfully accepted, false if not found or already processed
     */
    public boolean acceptReceipt(int userId, int receiptId) {
        // Convert int userId to String for database
        String userIdStr = String.valueOf(userId);
        
        // Check current status
        String currentStatus = receiptDAO.getParticipantStatus(receiptId, userIdStr);
        
        if (currentStatus == null) {
            System.out.println("Receipt " + receiptId + " not found for user " + userId);
            return false;
        }
        
        if (!"pending".equals(currentStatus)) {
            System.out.println("Receipt " + receiptId + " for user " + userId + " is already " + currentStatus);
            return false;
        }

        // Update status in database
        boolean updated = receiptDAO.updateParticipantStatus(receiptId, userIdStr, "accepted");
        
        if (updated) {
            System.out.println("User " + userId + " accepted receipt " + receiptId);
        }
        
        return updated;
    }

    /**
     * Decline a receipt. This will mark it as declined in the database.
     * 
     * @param userId The user declining the receipt (as int for backward compatibility)
     * @param receiptId The receipt ID to decline
     * @return true if the receipt was successfully declined, false if not found or already processed
     */
    public boolean declineReceipt(int userId, int receiptId) {
        // Convert int userId to String for database
        String userIdStr = String.valueOf(userId);
        
        // Check current status
        String currentStatus = receiptDAO.getParticipantStatus(receiptId, userIdStr);
        
        if (currentStatus == null) {
            System.out.println("Receipt " + receiptId + " not found for user " + userId);
            return false;
        }
        
        if (!"pending".equals(currentStatus)) {
            System.out.println("Receipt " + receiptId + " for user " + userId + " is already " + currentStatus);
            return false;
        }

        // Update status in database
        boolean updated = receiptDAO.updateParticipantStatus(receiptId, userIdStr, "declined");
        
        if (updated) {
            System.out.println("User " + userId + " declined receipt " + receiptId);
        }
        
        return updated;
    }

    /**
     * Get the status of a receipt for a user.
     * 
     * @param userId The user ID (as int for backward compatibility)
     * @param receiptId The receipt ID
     * @return "accepted", "declined", or "pending", or null if not found
     */
    public String getReceiptStatus(int userId, int receiptId) {
        // Convert int userId to String for database
        String userIdStr = String.valueOf(userId);
        
        return receiptDAO.getParticipantStatus(receiptId, userIdStr);
    }

    /**
     * Get the status of a receipt for a user (String userId version).
     * 
     * @param userId The user ID (as String)
     * @param receiptId The receipt ID
     * @return "accepted", "declined", or "pending", or null if not found
     */
    public String getReceiptStatus(String userId, int receiptId) {
        return receiptDAO.getParticipantStatus(receiptId, userId);
    }

    /**
     * Remove a receipt from pending list (cleanup method).
     * Note: This doesn't delete the receipt, just removes the participant relationship.
     * 
     * @param userId The user ID (as int for backward compatibility)
     * @param receiptId The receipt ID
     */
    public void removePendingReceipt(int userId, int receiptId) {
        // Convert int userId to String for database
        String userIdStr = String.valueOf(userId);
        
        // Update status to declined (soft delete) or we could add a delete method to DAO
        receiptDAO.updateParticipantStatus(receiptId, userIdStr, "declined");
    }

    /**
     * Get all receipts for a user (accepted, declined, or uploaded by them).
     * 
     * @param userId The user ID (as String)
     * @return List of receipts
     */
    public List<Receipt> getAllReceiptsForUser(String userId) {
        return receiptDAO.getAllReceiptsForUser(userId);
    }

    /**
     * Get the ReceiptDAO instance (for use by other services that need to create receipts).
     * 
     * @return ReceiptDAO instance
     */
    public ReceiptDAO getReceiptDAO() {
        return receiptDAO;
    }
}

