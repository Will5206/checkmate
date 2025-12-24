package database;

import models.Receipt;
import models.ReceiptItem;
import java.sql.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Data Access Object for managing receipts in the database.
 * Handles all CRUD operations for receipts, receipt_items, and receipt_participants tables.
 */
public class ReceiptDAO {

    private final DatabaseConnection dbConnection;
    // Thread pool for async operations (prevents thread exhaustion)
    private static final ExecutorService asyncUpdateExecutor = Executors.newFixedThreadPool(5);

    // Specialized DAOs for better organization
    private final ReceiptItemDAO receiptItemDAO;
    private final ItemAssignmentDAO itemAssignmentDAO;
    private final ReceiptParticipantDAO receiptParticipantDAO;
    private final ReceiptPaymentDAO receiptPaymentDAO;

    /**
     * Constructor that gets the database connection instance and initializes specialized DAOs
     */
    public ReceiptDAO() {
        this.dbConnection = DatabaseConnection.getInstance();
        this.receiptItemDAO = new ReceiptItemDAO();
        this.itemAssignmentDAO = new ItemAssignmentDAO();
        this.receiptParticipantDAO = new ReceiptParticipantDAO();
        this.receiptPaymentDAO = new ReceiptPaymentDAO();
    }

    /**
     * Create a new receipt in the database.
     * 
     * @param uploadedBy User ID (VARCHAR(36)) of the user who uploaded the receipt
     * @param merchantName Name of the merchant
     * @param date Date of the receipt
     * @param totalAmount Total amount of the receipt
     * @param tipAmount Tip amount
     * @param taxAmount Tax amount
     * @param imageUrl URL/path to the receipt image
     * @return The created Receipt object with receipt_id, or null if creation failed
     */
    public Receipt createReceipt(String uploadedBy, String merchantName, Date date,
                                  float totalAmount, float tipAmount, float taxAmount,
                                  String imageUrl) {
        // Get sender name from users table
        String senderName = null;
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement nameStmt = conn.prepareStatement("SELECT name FROM users WHERE user_id = ?")) {
            nameStmt.setString(1, uploadedBy);
            try (ResultSet rs = nameStmt.executeQuery()) {
                if (rs.next()) {
                    senderName = rs.getString("name");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting sender name: " + e.getMessage());
        }
        
        String sql = "INSERT INTO receipts (uploaded_by, merchant_name, date, total_amount, " +
                     "tip_amount, tax_amount, image_url, status, complete, sender_name, number_of_items) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, 'pending', FALSE, ?, 0)";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, uploadedBy);
            pstmt.setString(2, merchantName);
            if (date != null) {
                pstmt.setTimestamp(3, new Timestamp(date.getTime()));
            } else {
                pstmt.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
            }
            pstmt.setBigDecimal(4, java.math.BigDecimal.valueOf(totalAmount));
            pstmt.setBigDecimal(5, java.math.BigDecimal.valueOf(tipAmount));
            pstmt.setBigDecimal(6, java.math.BigDecimal.valueOf(taxAmount));
            pstmt.setString(7, imageUrl);
            pstmt.setString(8, senderName);

            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int receiptId = generatedKeys.getInt(1);
                        return getReceiptById(receiptId);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error creating receipt: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Get a receipt by its ID.
     * 
     * @param receiptId The receipt ID
     * @return Receipt object with items loaded, or null if not found
     */
    public Receipt getReceiptById(int receiptId) {
        String sql = "SELECT * FROM receipts WHERE receipt_id = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, receiptId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Receipt receipt = mapResultSetToReceipt(rs);
                    // Load items for this receipt using addItem() method
                    for (ReceiptItem item : getReceiptItems(receiptId)) {
                        receipt.addItem(item);
                    }
                    return receipt;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting receipt by ID: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Assign an item to a user (claim an item).
     * 
     * @param itemId The item ID
     * @param userId The user ID (VARCHAR(36))
     * @param quantity Quantity of this item to assign (default 1)
     * @return true if assignment was successful, false otherwise
     */
    /**
     * Get the total quantity already claimed for an item across all users.
     * 
     * @param itemId The item ID
     * @return Total quantity claimed, or 0 if none
     */
    public int getTotalClaimedQuantity(int itemId) {
        // Delegate to ItemAssignmentDAO
        return itemAssignmentDAO.getTotalClaimedQuantity(itemId);
    }

    /**
     * Get the quantity currently claimed by a specific user for an item.
     * 
     * @param itemId The item ID
     * @param userId The user ID
     * @return Quantity claimed by this user, or 0 if none
     */
    public int getUserClaimedQuantity(int itemId, String userId) {
        // Delegate to ItemAssignmentDAO
        return itemAssignmentDAO.getUserClaimedQuantity(itemId, userId);
    }

    /**
     * Assign an item to a user (claim an item) with proper transaction and locking to prevent race conditions.
     * Uses SELECT FOR UPDATE to lock the item row and ensure atomic validation and update.
     * 
     * @param itemId The item ID
     * @param userId The user ID (VARCHAR(36))
     * @param quantity The quantity to claim
     * @return true if assignment was successful, false otherwise
     */
    public boolean assignItemToUser(int itemId, String userId, int quantity) {
        // Delegate to ItemAssignmentDAO with callback for async status update
        return itemAssignmentDAO.assignItemToUser(itemId, userId, quantity, (receiptId) -> {
            updateReceiptCompleteStatusAsync(receiptId);
        });
    }

    /**
     * Unassign an item from a user (unclaim an item) with proper transaction handling.
     * 
     * @param itemId The item ID
     * @param userId The user ID (VARCHAR(36))
     * @return true if unassignment was successful, false otherwise
     */
    public boolean unassignItemFromUser(int itemId, String userId) {
        // Delegate to ItemAssignmentDAO with callback for async status update
        return itemAssignmentDAO.unassignItemFromUser(itemId, userId, (receiptId) -> {
                        updateReceiptCompleteStatusAsync(receiptId);
        });
    }
    
    /**
     * Helper method to update receipt complete status asynchronously using thread pool.
     * Prevents thread exhaustion and provides better error handling.
     * 
     * @param receiptId The receipt ID to update
     */
    private void updateReceiptCompleteStatusAsync(int receiptId) {
        asyncUpdateExecutor.submit(() -> {
            try {
                updateReceiptCompleteStatus(receiptId);
            } catch (Exception e) {
                System.err.println("[ReceiptDAO] Error updating receipt complete status in background for receipt " + receiptId + ": " + e.getMessage());
                e.printStackTrace();
                // TODO: In production, add retry logic or alerting here
            }
        });
    }

    /**
     * Get all item assignments for a specific receipt and user.
     * 
     * @param receiptId The receipt ID
     * @param userId The user ID (VARCHAR(36))
     * @return Map of item_id -> quantity assigned to this user
     */
    public Map<Integer, Integer> getItemAssignmentsForUser(int receiptId, String userId) {
        // Delegate to ItemAssignmentDAO
        return itemAssignmentDAO.getItemAssignmentsForUser(receiptId, userId);
    }

    /**
     * Get all item assignments for a receipt with payment information.
     * Returns assignments for all users, not just one.
     * 
     * @param receiptId The receipt ID
     * @return List of maps containing item_id, user_id, quantity, paid_by, paid_at
     */
    public List<Map<String, Object>> getAllItemAssignmentsForReceipt(int receiptId) {
        // Delegate to ItemAssignmentDAO
        return itemAssignmentDAO.getAllItemAssignmentsForReceipt(receiptId);
    }

    /**
     * Mark all items assigned to a user as paid in receipt_items table.
     * Called when a user pays for their portion of a receipt.
     * 
     * @param receiptId The receipt ID
     * @param userId The user ID who is paying
     * @return Number of items marked as paid
     */
    public int markItemsAsPaid(int receiptId, String userId) {
        // Delegate to ItemAssignmentDAO
        return itemAssignmentDAO.markItemsAsPaid(receiptId, userId);
    }
    
    /**
     * Get payment info for all items in a receipt (from item_assignments table).
     * FIXED: Now queries item_assignments instead of receipt_items (which doesn't have payment columns).
     * 
     * @param receiptId The receipt ID
     * @return Map of itemId -> {paidBy, paidAt} for items that have been paid
     */
    public Map<Integer, Map<String, Object>> getItemPaymentInfoForReceipt(int receiptId) {
        // Delegate to ItemAssignmentDAO
        return itemAssignmentDAO.getItemPaymentInfoForReceipt(receiptId);
    }

    /**
     * Check if an item is already paid for by any user.
     * 
     * @param itemId The item ID
     * @return true if the item (or any quantity of it) is paid, false otherwise
     */
    public boolean isItemPaid(int itemId) {
        // Delegate to ItemAssignmentDAO
        return itemAssignmentDAO.isItemPaid(itemId);
    }

    /**
     * Get payment information for an item (who paid for it).
     * 
     * @param itemId The item ID
     * @return Map with paidBy (user_id) and paidAt (timestamp), or null if not paid
     */
    public Map<String, Object> getItemPaymentInfo(int itemId) {
        // Delegate to ItemAssignmentDAO
        return itemAssignmentDAO.getItemPaymentInfo(itemId);
    }

    /**
     * OPTIMIZED: Calculate the total amount owed by a user for a receipt using a single SQL query.
     * Uses BigDecimal internally for precision, then converts to float for compatibility.
     * 
     * @param receiptId The receipt ID
     * @param userId The user ID (VARCHAR(36))
     * @return The total amount owed (items + proportional tax/tip), or 0 if no items assigned
     */
    public float calculateUserOwedAmount(int receiptId, String userId) {
        // Delegate to ReceiptPaymentDAO
        return receiptPaymentDAO.calculateUserOwedAmount(receiptId, userId);
    }

    /**
     * Calculate the total amount owed by a user for a receipt, EXCLUDING items that have been paid for.
     * This is used for the "Amount Owed" section to show remaining balance after payments.
     * Items with paid_by IS NOT NULL in item_assignments are excluded from the calculation.
     * 
     * @param receiptId The receipt ID
     * @param userId The user ID (VARCHAR(36))
     * @return The remaining amount owed (excluding paid items), or 0 if no unpaid items assigned
     */
    public float calculateUserOwedAmountExcludingPaid(int receiptId, String userId) {
        // Delegate to ReceiptPaymentDAO
        return receiptPaymentDAO.calculateUserOwedAmountExcludingPaid(receiptId, userId);
    }

    /**
     * Get all pending receipts for a specific user.
     * 
     * CRITICAL: This method uses ONLY the 'complete' column to determine if a receipt is pending.
     * - Pending: complete = 0 (FALSE) - receipt is not yet complete (items not all paid for)
     * - History: complete = 1 (TRUE) - receipt is complete (all items paid for)
     * 
     * The 'receipts.status' column is NOT used for this determination.
     * 
     * A receipt is pending for a user if:
     * 1. User is the uploader AND complete = 0, OR
     * 2. User is a participant (receipt_participants.status = 'pending' or 'accepted', not 'declined') AND complete = 0
     * 
     * This includes receipts uploaded by the user - they should see their own receipts in Pending 
     * until all items are paid for (complete = 1) and the receipt moves to History.
     * 
     * @param userId The user's ID (VARCHAR(36))
     * @return List of Receipt objects where complete = 0
     */
    public List<Receipt> getPendingReceiptsForUser(String userId) {
        // Get receipts where:
        // 1. User is the uploader, OR
        // 2. User is a participant with receipt_participants.status 'pending' or 'accepted' (not declined)
        // AND complete = 0 (receipt not yet complete - items not all paid for)
        // Receipts with complete = 1 appear in History, not Pending
        // NOTE: We check receipt_participants.status (participant invitation status), NOT receipts.status
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
                System.out.println("[ReceiptDAO] Getting pending receipts for user " + userId);
                while (rs.next()) {
                    Receipt receipt = mapResultSetToReceipt(rs);
                    receipts.add(receipt);
                    System.out.println("[ReceiptDAO] Collected pending receipt " + receipt.getReceiptId() + " (complete = FALSE, total so far: " + receipts.size() + ")");
                }
                System.out.println("[ReceiptDAO] Found total of " + receipts.size() + " pending receipts for user " + userId);
            }
        } catch (SQLException e) {
            System.err.println("Error getting pending receipts for user: " + e.getMessage());
            e.printStackTrace();
        }

        // OPTIMIZATION: Don't load items here - only load basic receipt info for list view
        // Items will be loaded on-demand when user clicks to view receipt details
        System.out.println("[ReceiptDAO] Skipping item loading for pending receipts list (optimization)");
        return receipts;
    }

    /**
     * Get all receipts for a specific user (History/Activity - completed receipts only).
     * 
     * CRITICAL: This method uses ONLY the 'complete' column to determine if a receipt is in history.
     * - History: complete = 1 (TRUE) - receipt is complete (all items paid for)
     * - Pending: complete = 0 (FALSE) - receipt is not yet complete (items not all paid for)
     * 
     * The 'receipts.status' column is NOT used for this determination.
     * 
     * Shows receipts where complete = 1 and the user is either the uploader or a participant.
     * Excludes receipts where the user has declined (receipt_participants.status = 'declined').
     * 
     * @param userId The user's ID (VARCHAR(36))
     * @return List of Receipt objects where complete = 1
     */
    public List<Receipt> getAllReceiptsForUser(String userId) {
        System.out.println("[ReceiptDAO] STEP C1: getAllReceiptsForUser called for userId: " + userId);
        // Show all receipts where:
        // 1. User is the uploader (r.uploaded_by = ?), OR
        // 2. User is a participant and hasn't declined (rp.user_id = ? AND rp.status != 'declined')
        // AND complete = 1 (receipt is completed - all items paid for)
        // NOTE: We check receipt_participants.status (participant invitation status), NOT receipts.status
        // Use UNION to avoid duplicate rows from JOIN
        String sql = "SELECT DISTINCT r.* FROM (" +
                     "  SELECT r.* FROM receipts r WHERE r.uploaded_by = ? AND r.complete = 1 " +
                     "  UNION " +
                     "  SELECT r.* FROM receipts r " +
                     "  INNER JOIN receipt_participants rp ON r.receipt_id = rp.receipt_id " +
                     "  WHERE rp.user_id = ? AND rp.status != 'declined' AND r.complete = 1" +
                     ") AS r " +
                     "ORDER BY r.created_at DESC";
        
        System.out.println("[ReceiptDAO] STEP C2: SQL query prepared");
        List<Receipt> receipts = new ArrayList<>();
        Set<Integer> receiptIds = new HashSet<>(); // Track unique receipt IDs

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            System.out.println("[ReceiptDAO] STEP C3: Database connection established, setting parameters");
            pstmt.setString(1, userId); // For uploaded_by check
            pstmt.setString(2, userId); // For participant check
            System.out.println("[ReceiptDAO] STEP C4: Parameters set, executing query");

            try (ResultSet rs = pstmt.executeQuery()) {
                System.out.println("[ReceiptDAO] STEP C5: Query executed, iterating ResultSet");
                int rowCount = 0;
                // First, collect all receipts from ResultSet
                while (rs.next()) {
                    rowCount++;
                    System.out.println("[ReceiptDAO] STEP C6: Processing row " + rowCount);
                    Receipt receipt = mapResultSetToReceipt(rs);
                    int receiptId = receipt.getReceiptId();
                    System.out.println("[ReceiptDAO] STEP C7: Mapped receipt ID: " + receiptId + ", merchant: " + receipt.getMerchantName());
                    
                    // Only add if we haven't seen this receipt ID before (UNION should prevent duplicates, but be safe)
                    if (!receiptIds.contains(receiptId)) {
                        receiptIds.add(receiptId);
                        receipts.add(receipt);
                        System.out.println("[ReceiptDAO] STEP C8: Added receipt " + receiptId + " to History (complete = TRUE, total: " + receipts.size() + ")");
                    } else {
                        System.out.println("[ReceiptDAO] STEP C8-SKIP: Receipt " + receiptId + " already in list, skipping duplicate");
                    }
                }
                System.out.println("[ReceiptDAO] STEP C9: Finished iterating ResultSet. Total rows: " + rowCount + ", Unique receipts: " + receipts.size());
            }
        } catch (SQLException e) {
            System.err.println("[ReceiptDAO] ERROR: Error getting all receipts for user: " + e.getMessage());
            e.printStackTrace();
        }

        // OPTIMIZATION: Don't load items here - only load basic receipt info for list view
        // Items will be loaded on-demand when user clicks to view receipt details
        System.out.println("[ReceiptDAO] STEP C10: Skipping item loading for list view (optimization)");
        System.out.println("[ReceiptDAO] STEP C11: Returning " + receipts.size() + " completed receipts (complete = TRUE) without items");
        return receipts;
    }

    /**
     * Add an item to a receipt.
     * 
     * @param receiptId The receipt ID
     * @param name Item name
     * @param price Item price
     * @param quantity Item quantity
     * @param category Item category (optional)
     * @return The created ReceiptItem, or null if creation failed
     */
    public ReceiptItem addReceiptItem(int receiptId, String name, float price, int quantity, String category) {
        // Delegate to ReceiptItemDAO
        return receiptItemDAO.addReceiptItem(receiptId, name, price, quantity, category);
    }
    
    /**
     * OPTIMIZED: Batch insert multiple receipt items in a single database operation.
     * This is much faster than inserting items one by one.
     * 
     * @param receiptId The receipt ID
     * @param items List of item data: {name, price, quantity, category}
     * @return List of created ReceiptItem objects with their generated IDs
     */
    public List<ReceiptItem> addReceiptItemsBatch(int receiptId, List<Map<String, Object>> items) {
        // Delegate to ReceiptItemDAO
        return receiptItemDAO.addReceiptItemsBatch(receiptId, items);
    }
    
    /**
     * Update the number_of_items count for a receipt.
     * 
     * @param receiptId The receipt ID
     */
    public void updateReceiptItemCount(int receiptId) {
        // Delegate to ReceiptItemDAO
        receiptItemDAO.updateReceiptItemCount(receiptId);
    }

    /**
     * Get all items for a receipt.
     * 
     * @param receiptId The receipt ID
     * @return List of ReceiptItem objects
     */
    public List<ReceiptItem> getReceiptItems(int receiptId) {
        // Delegate to ReceiptItemDAO
        return receiptItemDAO.getReceiptItems(receiptId);
    }

    /**
     * Get a receipt item by its ID.
     * 
     * @param itemId The item ID
     * @return ReceiptItem object or null if not found
     */
    public ReceiptItem getReceiptItemById(int itemId) {
        // Delegate to ReceiptItemDAO
        return receiptItemDAO.getReceiptItemById(itemId);
    }

    /**
     * Add a participant to a receipt (user who should receive/see the receipt).
     * 
     * @param receiptId The receipt ID
     * @param userId The user's ID (VARCHAR(36))
     * @return true if participant was added successfully
     */
    public boolean addReceiptParticipant(int receiptId, String userId) {
        // Delegate to ReceiptParticipantDAO
        return receiptParticipantDAO.addReceiptParticipant(receiptId, userId);
    }

    /**
     * OPTIMIZED: Batch insert multiple receipt participants in a single database operation.
     * This is much faster than adding participants one by one.
     * 
     * @param receiptId The receipt ID
     * @param userIds List of user IDs to add as participants
     * @return Number of participants successfully added
     */
    public int addReceiptParticipantsBatch(int receiptId, List<String> userIds) {
        // Delegate to ReceiptParticipantDAO
        return receiptParticipantDAO.addReceiptParticipantsBatch(receiptId, userIds);
    }

    /**
     * Update the status of a receipt participant (accept/decline).
     * 
     * @param receiptId The receipt ID
     * @param userId The user's ID (VARCHAR(36))
     * @param status New status ('pending', 'accepted', 'declined')
     * @return true if update was successful
     */
    public boolean updateParticipantStatus(int receiptId, String userId, String status) {
        // Delegate to ReceiptParticipantDAO
        return receiptParticipantDAO.updateParticipantStatus(receiptId, userId, status);
    }

    /**
     * Update status for all participants of a receipt.
     * Used when receipt is completed to move it to History for everyone.
     * 
     * FIX 3C: New method to update all participants at once
     * 
     * @param receiptId The receipt ID
     * @param status New status (typically 'completed')
     * @return true if update was successful
     */
    public boolean updateAllParticipantsStatus(int receiptId, String status) {
        // Delegate to ReceiptParticipantDAO
        return receiptParticipantDAO.updateAllParticipantsStatus(receiptId, status);
    }

    /**
     * Get the status of a receipt for a specific user.
     * 
     * @param receiptId The receipt ID
     * @param userId The user's ID (VARCHAR(36))
     * @return Status string ('pending', 'accepted', 'declined') or null if not found
     */
    public String getParticipantStatus(int receiptId, String userId) {
        // Delegate to ReceiptParticipantDAO
        return receiptParticipantDAO.getParticipantStatus(receiptId, userId);
    }

    /**
     * Update the status of a receipt.
     * 
     * @param receiptId The receipt ID
     * @param status New status ('pending', 'accepted', 'declined', 'completed')
     * @return true if update was successful
     */
    public boolean updateReceiptStatus(int receiptId, String status) {
        String sql = "UPDATE receipts SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE receipt_id = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, status);
            pstmt.setInt(2, receiptId);

            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;

        } catch (SQLException e) {
            System.err.println("Error updating receipt status: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Helper method to map a ResultSet row to a Receipt object.
     * Note: This creates a Receipt with uploadedBy as int (0) - the actual user ID is stored as String in DB.
     * The actual uploadedBy can be retrieved via getReceiptUploadedBy() when needed.
     */
    private Receipt mapResultSetToReceipt(ResultSet rs) throws SQLException {
        int receiptId = rs.getInt("receipt_id");
        String merchantName = rs.getString("merchant_name");
        Timestamp dateTs = rs.getTimestamp("date");
        Date date = dateTs != null ? new Date(dateTs.getTime()) : new Date();
        float totalAmount = rs.getBigDecimal("total_amount").floatValue();
        float tipAmount = rs.getBigDecimal("tip_amount").floatValue();
        float taxAmount = rs.getBigDecimal("tax_amount").floatValue();
        String imageUrl = rs.getString("image_url");
        String status = rs.getString("status");
        String senderName = rs.getString("sender_name");
        int numberOfItems = rs.getInt("number_of_items");

        // Create Receipt - note: uploadedBy is int in model but String in DB
        // We'll use 0 as placeholder and handle conversion in service layer
        // The actual uploadedBy can be retrieved via getReceiptUploadedBy() when needed
        Receipt receipt = new Receipt(receiptId, 0, merchantName, date, totalAmount, tipAmount, taxAmount, imageUrl, status);
        receipt.setSenderName(senderName);
        receipt.setNumberOfItems(numberOfItems);
        
        return receipt;
    }

    // mapResultSetToReceiptItem moved to ReceiptItemDAO

    /**
     * Get the uploaded_by user ID as a String (since DB stores it as VARCHAR(36)).
     * 
     * @param receiptId The receipt ID
     * @return User ID string or null if not found
     */
    public String getReceiptUploadedBy(int receiptId) {
        String sql = "SELECT uploaded_by FROM receipts WHERE receipt_id = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, receiptId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("uploaded_by");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting receipt uploaded_by: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Record a payment for a receipt participant.
     * 
     * @param receiptId The receipt ID
     * @param userId The user ID who is paying
     * @param amount The amount being paid
     * @return true if payment was recorded successfully
     */
    /**
     * Record payment in receipt_participants table.
     * 
     * @param receiptId The receipt ID
     * @param userId The user ID who paid
     * @param amount The amount paid
     * @return true if payment was recorded successfully
     */
    public boolean recordPayment(int receiptId, String userId, float amount) {
        // Delegate to ReceiptParticipantDAO
        return receiptParticipantDAO.recordPayment(receiptId, userId, amount);
    }
    
    /**
     * CRITICAL FIX: Record payment and mark items as paid in a single transaction.
     * This ensures atomicity - either both operations succeed or both fail.
     * 
     * @param receiptId The receipt ID
     * @param userId The user ID who paid
     * @param amount The amount paid
     * @return Number of items marked as paid, or -1 if transaction failed
     */
    public int recordPaymentAndMarkItems(int receiptId, String userId, double amount) {
        // Delegate to ReceiptPaymentDAO with callback for async status update
        int itemsMarked = receiptPaymentDAO.recordPaymentAndMarkItems(receiptId, userId, amount, receiptParticipantDAO, itemAssignmentDAO);
        if (itemsMarked >= 0) {
            updateReceiptCompleteStatusAsync(receiptId);
        }
            return itemsMarked;
    }

    /**
     * Get the amount paid by a user for a receipt.
     * 
     * @param receiptId The receipt ID
     * @param userId The user ID
     * @return The amount paid, or 0 if no payment recorded
     */
    public float getPaidAmount(int receiptId, String userId) {
        // Delegate to ReceiptParticipantDAO
        return receiptParticipantDAO.getPaidAmount(receiptId, userId);
    }

    /**
     * Get all participants for a receipt with their payment status.
     * 
     * @param receiptId The receipt ID
     * @return List of maps containing user_id, status, paid_amount, and owed_amount
     */
    public List<Map<String, Object>> getParticipantsWithPaymentStatus(int receiptId) {
        // Get base participant data from ReceiptParticipantDAO
        List<Map<String, Object>> participants = receiptParticipantDAO.getParticipantsWithPaymentStatus(receiptId);
        
        // Add owed_amount for each participant
        for (Map<String, Object> participant : participants) {
            String userId = (String) participant.get("user_id");
                    participant.put("owed_amount", calculateUserOwedAmount(receiptId, userId));
        }

        return participants;
    }

    /**
     * CRITICAL FIX: Update the complete status of a receipt based on ALL items being paid for.
     * A receipt is complete when ALL item assignments have paid_by IS NOT NULL.
     * 
     * @param receiptId The receipt ID
     * @return true if receipt is now complete (all items paid for), false otherwise
     */
    public boolean updateReceiptCompleteStatus(int receiptId) {
        System.out.println("[ReceiptDAO] updateReceiptCompleteStatus called for receipt " + receiptId);
        
        // Check if all item assignments are paid (paid_by IS NOT NULL in item_assignments)
        boolean allItemsPaid = areAllItemsPaidFor(receiptId);
        System.out.println("[ReceiptDAO] areAllItemsPaidFor returned: " + allItemsPaid + " for receipt " + receiptId);
        
        // Receipt is complete only if ALL items are paid for
        updateCompleteStatusInDB(receiptId, allItemsPaid);
        
        return allItemsPaid;
    }
    
    /**
     * Check if all items in a receipt are paid for.
     * An item is paid for when all its assignments in item_assignments have paid_by IS NOT NULL.
     * 
     * @param receiptId The receipt ID
     * @return true if all items are paid for, false otherwise
     */
    private boolean areAllItemsPaidFor(int receiptId) {
        // FIXED: Check item_assignments instead of receipt_items
        // A receipt is complete when ALL item assignments are paid
        // This means every quantity of every item has been paid for
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
                    
                    System.out.println("[ReceiptDAO] Receipt " + receiptId + ": " + paidItems + "/" + totalItems + " items have paid assignments");
                    
                    if (totalItems == 0) {
                        System.out.println("[ReceiptDAO] Receipt " + receiptId + " has no items - not complete");
                        return false;
                    }
                    
                    // Check if all items have at least one paid assignment
                    // AND all assignments for each item are paid
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
                                    System.out.println("[ReceiptDAO] Receipt " + receiptId + " has no item assignments - not complete");
                                    return false;
                                }
                                
                                boolean allAssignmentsPaid = (paidAssignments == totalAssignments);
                                boolean allPaid = allItemsHavePaidAssignments && allAssignmentsPaid;
                                
                    if (allPaid) {
                                    System.out.println("[ReceiptDAO] Receipt " + receiptId + ": ALL " + totalItems + " items and ALL " + totalAssignments + " assignments are paid - can move to History");
                    } else {
                                    System.out.println("[ReceiptDAO] Receipt " + receiptId + ": " + (totalAssignments - paidAssignments) + " assignments still need to be paid for");
                    }
                    
                    return allPaid;
                            }
                        }
                    }
                } else {
                    // No items found
                    System.out.println("[ReceiptDAO] Receipt " + receiptId + " has no items - not complete");
                    return false;
                }
            }
        } catch (SQLException e) {
            System.err.println("[ReceiptDAO] ERROR: Error checking if all items paid for: " + e.getMessage());
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
        List<Map<String, Object>> participants = getParticipantsWithPaymentStatus(receiptId);
        
        // CRITICAL FIX: If no participants exist, it means only the uploader is involved
        // The uploader doesn't need to pay, so if all items are claimed and no participants exist,
        // the receipt is complete (uploader already "paid" by uploading the receipt)
        if (participants.isEmpty()) {
            System.out.println("[ReceiptDAO] No participants found for receipt " + receiptId + " - checking if uploader has all items claimed");
            // If there are no participants, the receipt is complete if all items are claimed
            // (uploader doesn't need to pay, they already "paid" by uploading)
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
                System.out.println("[ReceiptDAO] Participant " + participant.get("user_id") + " has not paid fully: paid=" + paidAmount + ", owed=" + owedAmount);
                return false;
            }
        }
        
        System.out.println("[ReceiptDAO] All " + participants.size() + " participants have paid their full amount");
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
            System.err.println("[ReceiptDAO] ERROR: Error checking receipt complete status: " + e.getMessage());
            e.printStackTrace();
        }
        
        return false; // Default to false if error or not found
    }
    
    /**
     * Check if all items in a receipt are claimed by users.
     * Counts all item assignments (including paid items) to determine if all items are claimed.
     * 
     * @param receiptId The receipt ID
     * @return true if all items are fully claimed, false otherwise
     */
    /**
     * Check if all items in a receipt are claimed by users.
     * OPTIMIZED: Uses single JOIN query instead of N+1 queries for better performance.
     * 
     * @param receiptId The receipt ID
     * @return true if all items are fully claimed, false otherwise
     */
    public boolean areAllItemsClaimed(int receiptId) {
        // Delegate to ItemAssignmentDAO
        return itemAssignmentDAO.areAllItemsClaimed(receiptId);
    }

    /**
     * Check if all participants have paid their full amount and mark receipt as completed if so.
     * Also checks if all items are claimed before marking as completed.
     * 
     * FIX 3D: When receipt is completed, update all participants status to 'completed'
     * so receipt moves to History for everyone.
     * 
     * @param receiptId The receipt ID
     * @return true if receipt was marked as completed, false otherwise
     */
    public boolean checkAndMarkReceiptCompleted(int receiptId) {
        // CRITICAL FIX: Check if all item assignments are paid (paid_by IS NOT NULL in item_assignments)
        // A receipt is complete when ALL items are paid for, regardless of participants
        boolean allItemsPaid = areAllItemsPaidFor(receiptId);
        
        if (allItemsPaid) {
            // Mark receipt as completed
            boolean receiptUpdated = updateReceiptStatus(receiptId, "completed");
            
            // CRITICAL FIX: Also update the 'complete' column to 1
            // This is what getAllReceiptsForUser() queries for to show receipts in History
            updateReceiptCompleteStatus(receiptId);
            
            if (receiptUpdated) {
                // Mark all participants as 'completed' so receipt moves to History for everyone
                updateAllParticipantsStatus(receiptId, "completed");
                System.out.println("Receipt " + receiptId + " is now fully paid and completed (all item assignments paid, status='completed', complete=1)");
            }
            
            return receiptUpdated;
        }

        return false;
    }
    
    /**
     * OPTIMIZATION: Batch fetch metadata for multiple receipts in a single query.
     * This replaces N separate queries (getParticipantStatus, getPaidAmount, isReceiptComplete, etc.)
     * with a single JOIN query.
     * 
     * @param receiptIds List of receipt IDs to fetch metadata for
     * @param userId The user ID to get participant-specific data
     * @return Map of receiptId -> ReceiptMetadata containing all metadata
     */
    public Map<Integer, ReceiptMetadata> getReceiptsMetadataBatch(List<Integer> receiptIds, String userId) {
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
        
        Map<Integer, ReceiptMetadata> metadataMap = new HashMap<>();
        
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
                    
                    ReceiptMetadata metadata = new ReceiptMetadata();
                    metadata.uploadedBy = uploadedBy;
                    metadata.isComplete = isComplete;
                    metadata.participantStatus = participantStatus;
                    metadata.paidAmount = paidAmount;
                    
                    metadataMap.put(receiptId, metadata);
                }
            }
        } catch (SQLException e) {
            System.err.println("[ReceiptDAO] Error batch fetching receipts metadata: " + e.getMessage());
            e.printStackTrace();
        }
        
        return metadataMap;
    }
    
    /**
     * OPTIMIZATION: Batch calculate owed amounts for multiple receipts in a single query.
     * This replaces N separate calculateUserOwedAmount() calls with a single query.
     * 
     * @param receiptIds List of receipt IDs to calculate owed amounts for
     * @param userId The user ID
     * @return Map of receiptId -> owedAmount
     */
    public Map<Integer, Float> calculateUserOwedAmountsBatch(List<Integer> receiptIds, String userId) {
        // Delegate to ReceiptPaymentDAO
        return receiptPaymentDAO.calculateUserOwedAmountsBatch(receiptIds, userId);
    }
    
    /**
     * Inner class to hold receipt metadata for batch operations
     */
    public static class ReceiptMetadata {
        public String uploadedBy;
        public boolean isComplete;
        public String participantStatus;
        public float paidAmount;
    }
}