package services;

import models.Receipt;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing receipt viewing and acceptance/decline operations.
 * Provides foundational methods for users to view receipts sent to them and accept or decline them.
 * Database integration to be added by partner.
 */
public class ReceiptService {

    // Singleton instance
    private static ReceiptService instance;

    // In-memory storage for pending receipts sent to users
    // Key: userId, Value: Map of receiptId -> Receipt
    private final Map<Integer, Map<Integer, Receipt>> pendingReceiptsByUser = new ConcurrentHashMap<>();

    // Track receipt acceptance/decline status
    // Key: userId:receiptId, Value: "accepted" | "declined" | "pending"
    private final Map<String, String> receiptStatus = new ConcurrentHashMap<>();

    private ReceiptService() {
        // Private constructor for singleton
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
     * 
     * @param userId The user who should receive this receipt
     * @param receipt The receipt to add
     */
    public void addPendingReceipt(int userId, Receipt receipt) {
        pendingReceiptsByUser.computeIfAbsent(userId, k -> new ConcurrentHashMap<>()).put(receipt.getReceiptId(), receipt);
        String statusKey = userId + ":" + receipt.getReceiptId();
        receiptStatus.put(statusKey, "pending");
        System.out.println("Added pending receipt " + receipt.getReceiptId() + " for user " + userId);
    }

    /**
     * Get a receipt by ID for a specific user.
     * Returns null if the receipt doesn't exist or isn't accessible to the user.
     * 
     * @param userId The user requesting the receipt
     * @param receiptId The receipt ID
     * @return The receipt if found and accessible, null otherwise
     */
    public Receipt getReceipt(int userId, int receiptId) {
        Map<Integer, Receipt> userReceipts = pendingReceiptsByUser.get(userId);
        if (userReceipts == null) {
            return null;
        }
        return userReceipts.get(receiptId);
    }

    /**
     * Get all pending receipts for a user.
     * 
     * @param userId The user ID
     * @return List of pending receipts
     */
    public List<Receipt> getPendingReceipts(int userId) {
        Map<Integer, Receipt> userReceipts = pendingReceiptsByUser.get(userId);
        if (userReceipts == null) {
            return Collections.emptyList();
        }
        
        // Filter to only pending receipts
        List<Receipt> pending = new ArrayList<>();
        for (Receipt receipt : userReceipts.values()) {
            String statusKey = userId + ":" + receipt.getReceiptId();
            String status = receiptStatus.getOrDefault(statusKey, "pending");
            if ("pending".equals(status)) {
                pending.add(receipt);
            }
        }
        return pending;
    }

    /**
     * Accept a receipt. This will mark it as accepted and prepare it for database integration.
     * Database persistence to be implemented by partner.
     * 
     * @param userId The user accepting the receipt
     * @param receiptId The receipt ID to accept
     * @return true if the receipt was successfully accepted, false if not found or already processed
     */
    public boolean acceptReceipt(int userId, int receiptId) {
        Receipt receipt = getReceipt(userId, receiptId);
        if (receipt == null) {
            System.out.println("Receipt " + receiptId + " not found for user " + userId);
            return false;
        }

        String statusKey = userId + ":" + receiptId;
        String currentStatus = receiptStatus.get(statusKey);
        
        if (!"pending".equals(currentStatus)) {
            System.out.println("Receipt " + receiptId + " for user " + userId + " is already " + currentStatus);
            return false;
        }

        // Mark as accepted
        receiptStatus.put(statusKey, "accepted");
        receipt.setStatus("accepted");
        
        System.out.println("User " + userId + " accepted receipt " + receiptId);
        
        // TODO: Database integration - partner will implement this
        // This is where the receipt should be saved to the database
        // Example: databaseConnection.saveReceiptForUser(userId, receipt);
        
        return true;
    }

    /**
     * Decline a receipt. This will mark it as declined.
     * 
     * @param userId The user declining the receipt
     * @param receiptId The receipt ID to decline
     * @return true if the receipt was successfully declined, false if not found or already processed
     */
    public boolean declineReceipt(int userId, int receiptId) {
        Receipt receipt = getReceipt(userId, receiptId);
        if (receipt == null) {
            System.out.println("Receipt " + receiptId + " not found for user " + userId);
            return false;
        }

        String statusKey = userId + ":" + receiptId;
        String currentStatus = receiptStatus.get(statusKey);
        
        if (!"pending".equals(currentStatus)) {
            System.out.println("Receipt " + receiptId + " for user " + userId + " is already " + currentStatus);
            return false;
        }

        // Mark as declined
        receiptStatus.put(statusKey, "declined");
        receipt.setStatus("declined");
        
        System.out.println("User " + userId + " declined receipt " + receiptId);
        
        return true;
    }

    /**
     * Get the status of a receipt for a user.
     * 
     * @param userId The user ID
     * @param receiptId The receipt ID
     * @return "accepted", "declined", or "pending", or null if not found
     */
    public String getReceiptStatus(int userId, int receiptId) {
        String statusKey = userId + ":" + receiptId;
        return receiptStatus.get(statusKey);
    }

    /**
     * Remove a receipt from pending list (cleanup method).
     * 
     * @param userId The user ID
     * @param receiptId The receipt ID
     */
    public void removePendingReceipt(int userId, int receiptId) {
        Map<Integer, Receipt> userReceipts = pendingReceiptsByUser.get(userId);
        if (userReceipts != null) {
            userReceipts.remove(receiptId);
            String statusKey = userId + ":" + receiptId;
            receiptStatus.remove(statusKey);
        }
    }
}

