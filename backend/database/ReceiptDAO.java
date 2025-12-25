package database;

import models.Receipt;
import models.ReceiptItem;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Facade DAO for managing receipts in the database.
 * Delegates to specialized DAOs for better organization while maintaining backward compatibility.
 * This class provides a unified interface for all receipt-related database operations.
 */
public class ReceiptDAO {

    // Specialized DAOs
    private final ReceiptCoreDAO coreDAO;
    private final ReceiptQueryDAO queryDAO;
    private final ReceiptCompletionDAO completionDAO;
    private final ReceiptItemDAO receiptItemDAO;
    private final ItemAssignmentDAO itemAssignmentDAO;
    private final ReceiptParticipantDAO receiptParticipantDAO;
    private final ReceiptPaymentDAO receiptPaymentDAO;

    /**
     * Constructor that initializes all specialized DAOs
     */
    public ReceiptDAO() {
        this.coreDAO = new ReceiptCoreDAO();
        this.queryDAO = new ReceiptQueryDAO();
        this.completionDAO = new ReceiptCompletionDAO();
        this.receiptItemDAO = new ReceiptItemDAO();
        this.itemAssignmentDAO = new ItemAssignmentDAO();
        this.receiptParticipantDAO = new ReceiptParticipantDAO();
        this.receiptPaymentDAO = new ReceiptPaymentDAO();
    }

    // ==================== Receipt Core Operations ====================

    /**
     * Create a new receipt in the database.
     */
    public Receipt createReceipt(String uploadedBy, String merchantName, java.util.Date date,
                                  float totalAmount, float tipAmount, float taxAmount,
                                  String imageUrl) {
        return coreDAO.createReceipt(uploadedBy, merchantName, date, totalAmount, tipAmount, taxAmount, imageUrl);
    }

    /**
     * Get a receipt by its ID.
     */
    public Receipt getReceiptById(int receiptId) {
        return coreDAO.getReceiptById(receiptId);
    }

    /**
     * Get the uploaded_by user ID as a String.
     */
    public String getReceiptUploadedBy(int receiptId) {
        return coreDAO.getReceiptUploadedBy(receiptId);
    }

    /**
     * Update the status of a receipt.
     */
    public boolean updateReceiptStatus(int receiptId, String status) {
        return coreDAO.updateReceiptStatus(receiptId, status);
    }

    // ==================== Receipt Query Operations ====================

    /**
     * Get all pending receipts for a user.
     */
    public List<Receipt> getPendingReceiptsForUser(String userId) {
        return queryDAO.getPendingReceiptsForUser(userId);
    }

    /**
     * Get all receipts for a user (History/Activity - completed receipts only).
     */
    public List<Receipt> getAllReceiptsForUser(String userId) {
        return queryDAO.getAllReceiptsForUser(userId);
    }

    /**
     * Batch fetch metadata for multiple receipts in a single query.
     */
    public Map<Integer, ReceiptMetadata> getReceiptsMetadataBatch(List<Integer> receiptIds, String userId) {
        return queryDAO.getReceiptsMetadataBatch(receiptIds, userId);
    }

    /**
     * Batch calculate owed amounts for multiple receipts in a single query.
     */
    public Map<Integer, Float> calculateUserOwedAmountsBatch(List<Integer> receiptIds, String userId) {
        return queryDAO.calculateUserOwedAmountsBatch(receiptIds, userId);
    }

    // ==================== Receipt Completion Operations ====================

    /**
     * Update receipt complete status based on payment status.
     */
    public boolean updateReceiptCompleteStatus(int receiptId) {
        return completionDAO.updateReceiptCompleteStatus(receiptId);
    }

    /**
     * Update receipt complete status asynchronously.
     */
    private void updateReceiptCompleteStatusAsync(int receiptId) {
        completionDAO.updateReceiptCompleteStatusAsync(receiptId);
    }

    /**
     * Check if a receipt is marked as complete in the database.
     */
    public boolean isReceiptComplete(int receiptId) {
        return completionDAO.isReceiptComplete(receiptId);
    }

    /**
     * Check if all items in a receipt are claimed.
     */
    public boolean areAllItemsClaimed(int receiptId) {
        return completionDAO.areAllItemsClaimed(receiptId);
    }

    /**
     * Check if all participants have paid their full amount and mark receipt as completed if so.
     */
    public boolean checkAndMarkReceiptCompleted(int receiptId) {
        return completionDAO.checkAndMarkReceiptCompleted(receiptId);
    }

    // ==================== Receipt Item Operations ====================

    /**
     * Add an item to a receipt.
     */
    public ReceiptItem addReceiptItem(int receiptId, String name, float price, int quantity, String category) {
        return receiptItemDAO.addReceiptItem(receiptId, name, price, quantity, category);
    }

    /**
     * Add multiple items to a receipt in a batch.
     */
    public List<ReceiptItem> addReceiptItemsBatch(int receiptId, List<Map<String, Object>> items) {
        return receiptItemDAO.addReceiptItemsBatch(receiptId, items);
    }

    /**
     * Update the item count for a receipt.
     */
    public void updateReceiptItemCount(int receiptId) {
        receiptItemDAO.updateReceiptItemCount(receiptId);
    }

    /**
     * Get all items for a receipt.
     */
    public List<ReceiptItem> getReceiptItems(int receiptId) {
        return receiptItemDAO.getReceiptItems(receiptId);
    }

    /**
     * Get a receipt item by its ID.
     */
    public ReceiptItem getReceiptItemById(int itemId) {
        return receiptItemDAO.getReceiptItemById(itemId);
    }

    // ==================== Item Assignment Operations ====================

    /**
     * Get the total quantity already claimed for an item across all users.
     */
    public int getTotalClaimedQuantity(int itemId) {
        return itemAssignmentDAO.getTotalClaimedQuantity(itemId);
    }

    /**
     * Get the quantity of an item claimed by a specific user.
     */
    public int getUserClaimedQuantity(int itemId, String userId) {
        return itemAssignmentDAO.getUserClaimedQuantity(itemId, userId);
    }

    /**
     * Assign an item to a user (claim an item).
     */
    public boolean assignItemToUser(int itemId, String userId, int quantity) {
        return itemAssignmentDAO.assignItemToUser(itemId, userId, quantity, receiptId -> {});
    }

    /**
     * Unassign an item from a user (unclaim an item).
     */
    public boolean unassignItemFromUser(int itemId, String userId) {
        return itemAssignmentDAO.unassignItemFromUser(itemId, userId, receiptId -> {});
    }

    /**
     * Get all item assignments for a specific receipt and user.
     */
    public Map<Integer, Integer> getItemAssignmentsForUser(int receiptId, String userId) {
        return itemAssignmentDAO.getItemAssignmentsForUser(receiptId, userId);
    }

    /**
     * Get all item assignments for a receipt.
     */
    public List<Map<String, Object>> getAllItemAssignmentsForReceipt(int receiptId) {
        return itemAssignmentDAO.getAllItemAssignmentsForReceipt(receiptId);
    }

    /**
     * Mark items as paid for a user.
     */
    public int markItemsAsPaid(int receiptId, String userId) {
        return itemAssignmentDAO.markItemsAsPaid(receiptId, userId);
    }

    /**
     * Get payment info for all items in a receipt.
     */
    public Map<Integer, Map<String, Object>> getItemPaymentInfoForReceipt(int receiptId) {
        return itemAssignmentDAO.getItemPaymentInfoForReceipt(receiptId);
    }

    /**
     * Check if an item is already paid for by any user.
     */
    public boolean isItemPaid(int itemId) {
        return itemAssignmentDAO.isItemPaid(itemId);
    }

    /**
     * Get payment information for an item.
     */
    public Map<String, Object> getItemPaymentInfo(int itemId) {
        return itemAssignmentDAO.getItemPaymentInfo(itemId);
    }

    /**
     * Get item claim info (item details + claimed quantities).
     */
    public ItemClaimInfo getItemClaimInfo(int itemId, String userId) {
        return itemAssignmentDAO.getItemClaimInfo(itemId, userId);
    }

    // ==================== Receipt Participant Operations ====================

    /**
     * Add a participant to a receipt.
     */
    public boolean addReceiptParticipant(int receiptId, String userId) {
        return receiptParticipantDAO.addReceiptParticipant(receiptId, userId);
    }

    /**
     * Add multiple participants to a receipt in a batch.
     */
    public int addReceiptParticipantsBatch(int receiptId, List<String> userIds) {
        return receiptParticipantDAO.addReceiptParticipantsBatch(receiptId, userIds);
    }

    /**
     * Update the status of a participant for a receipt.
     */
    public boolean updateParticipantStatus(int receiptId, String userId, String status) {
        return receiptParticipantDAO.updateParticipantStatus(receiptId, userId, status);
    }

    /**
     * Update the status of all participants for a receipt.
     */
    public boolean updateAllParticipantsStatus(int receiptId, String status) {
        return receiptParticipantDAO.updateAllParticipantsStatus(receiptId, status);
    }

    /**
     * Get the status of a participant for a receipt.
     */
    public String getParticipantStatus(int receiptId, String userId) {
        return receiptParticipantDAO.getParticipantStatus(receiptId, userId);
    }

    /**
     * Get all participants for a receipt with their payment status.
     */
    public List<Map<String, Object>> getParticipantsWithPaymentStatus(int receiptId) {
        List<Map<String, Object>> participants = receiptParticipantDAO.getParticipantsWithPaymentStatus(receiptId);
        
        // Add owed_amount for each participant
        for (Map<String, Object> participant : participants) {
            String userId = (String) participant.get("user_id");
            participant.put("owed_amount", calculateUserOwedAmount(receiptId, userId));
        }

        return participants;
    }

    // ==================== Receipt Payment Operations ====================

    /**
     * Calculate the total amount owed by a user for a receipt.
     */
    public float calculateUserOwedAmount(int receiptId, String userId) {
        return receiptPaymentDAO.calculateUserOwedAmount(receiptId, userId);
    }

    /**
     * Calculate the amount owed by a user excluding items already paid.
     */
    public float calculateUserOwedAmountExcludingPaid(int receiptId, String userId) {
        return receiptPaymentDAO.calculateUserOwedAmountExcludingPaid(receiptId, userId);
    }

    /**
     * Calculate both owed amounts in a single optimized query.
     */
    public float[] calculateBothOwedAmounts(int receiptId, String userId) {
        return receiptPaymentDAO.calculateBothOwedAmounts(receiptId, userId);
    }

    /**
     * Record a payment for a receipt.
     */
    public boolean recordPayment(int receiptId, String userId, float amount) {
        return receiptParticipantDAO.recordPayment(receiptId, userId, amount);
    }

    /**
     * Record payment and mark items as paid in a single transaction.
     */
    public int recordPaymentAndMarkItems(int receiptId, String userId, double amount) {
        int itemsMarked = receiptPaymentDAO.recordPaymentAndMarkItems(receiptId, userId, amount, receiptParticipantDAO, itemAssignmentDAO);
        if (itemsMarked >= 0) {
            updateReceiptCompleteStatusAsync(receiptId);
        }
        return itemsMarked;
    }

    /**
     * Get the amount paid by a user for a receipt.
     */
    public float getPaidAmount(int receiptId, String userId) {
        return receiptParticipantDAO.getPaidAmount(receiptId, userId);
    }

    // ==================== Inner Classes ====================

    /**
     * Inner class to hold item claim information.
     */
    public static class ItemClaimInfo {
        public ReceiptItem item;
        public int userClaimedQuantity;
        public int totalClaimedQuantity;
        
        public ItemClaimInfo(ReceiptItem item, int userClaimedQuantity, int totalClaimedQuantity) {
            this.item = item;
            this.userClaimedQuantity = userClaimedQuantity;
            this.totalClaimedQuantity = totalClaimedQuantity;
        }
    }

    /**
     * Inner class to hold receipt metadata for batch operations.
     */
    public static class ReceiptMetadata {
        public String uploadedBy;
        public boolean isComplete;
        public String participantStatus;
        public float paidAmount;
    }
}
