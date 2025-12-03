package database;

import models.Receipt;
import models.ReceiptItem;
import java.sql.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Data Access Object for managing receipts in the database.
 * Handles all CRUD operations for receipts, receipt_items, and receipt_participants tables.
 */
public class ReceiptDAO {

    private final DatabaseConnection dbConnection;

    /**
     * Constructor that gets the database connection instance
     */
    public ReceiptDAO() {
        this.dbConnection = DatabaseConnection.getInstance();
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
        String sql = "INSERT INTO receipts (uploaded_by, merchant_name, date, total_amount, " +
                     "tip_amount, tax_amount, image_url, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, 'pending')";

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
        String sql = "SELECT COALESCE(SUM(quantity), 0) as total_claimed " +
                     "FROM item_assignments " +
                     "WHERE item_id = ? AND paid_by IS NULL"; // Only count unpaid claims
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, itemId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("total_claimed");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting total claimed quantity: " + e.getMessage());
            e.printStackTrace();
        }
        
        return 0;
    }

    /**
     * Get the quantity currently claimed by a specific user for an item.
     * 
     * @param itemId The item ID
     * @param userId The user ID
     * @return Quantity claimed by this user, or 0 if none
     */
    public int getUserClaimedQuantity(int itemId, String userId) {
        String sql = "SELECT COALESCE(quantity, 0) as quantity " +
                     "FROM item_assignments " +
                     "WHERE item_id = ? AND user_id = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, itemId);
            pstmt.setString(2, userId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("quantity");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting user claimed quantity: " + e.getMessage());
            e.printStackTrace();
        }
        
        return 0;
    }

    public boolean assignItemToUser(int itemId, String userId, int quantity) {
        // First get receipt_id and item quantity from item
        String getItemSql = "SELECT receipt_id, quantity as item_quantity FROM receipt_items WHERE item_id = ?";
        int receiptId = -1;
        int itemQuantity = 0;
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(getItemSql)) {
            pstmt.setInt(1, itemId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    receiptId = rs.getInt("receipt_id");
                    itemQuantity = rs.getInt("item_quantity");
                } else {
                    System.err.println("Item not found: " + itemId);
                    return false;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting item info: " + e.getMessage());
            return false;
        }
        
        // Get current user's claimed quantity
        int userCurrentQty = getUserClaimedQuantity(itemId, userId);
        // Get total claimed quantity by all users (excluding this user's current claim)
        int totalClaimedByOthers = getTotalClaimedQuantity(itemId) - userCurrentQty;
        
        // Validate: new total claimed cannot exceed item quantity
        int newTotalClaimed = totalClaimedByOthers + quantity;
        if (newTotalClaimed > itemQuantity) {
            System.err.println("Cannot claim " + quantity + " of item " + itemId + 
                             ". Item has quantity " + itemQuantity + 
                             ", already claimed: " + totalClaimedByOthers + 
                             " by others, user currently has: " + userCurrentQty);
            return false; // Validation failed
        }
        
        // Insert or update assignment
        String sql = "INSERT INTO item_assignments (receipt_id, item_id, user_id, quantity) " +
                     "VALUES (?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE quantity = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, receiptId);
            pstmt.setInt(2, itemId);
            pstmt.setString(3, userId);
            pstmt.setInt(4, quantity);
            pstmt.setInt(5, quantity);
            
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            System.err.println("Error assigning item to user: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Unassign an item from a user (unclaim an item).
     * 
     * @param itemId The item ID
     * @param userId The user ID (VARCHAR(36))
     * @return true if unassignment was successful, false otherwise
     */
    public boolean unassignItemFromUser(int itemId, String userId) {
        String sql = "DELETE FROM item_assignments WHERE item_id = ? AND user_id = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, itemId);
            pstmt.setString(2, userId);
            
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            System.err.println("Error unassigning item from user: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Get all item assignments for a specific receipt and user.
     * 
     * @param receiptId The receipt ID
     * @param userId The user ID (VARCHAR(36))
     * @return Map of item_id -> quantity assigned to this user
     */
    public Map<Integer, Integer> getItemAssignmentsForUser(int receiptId, String userId) {
        String sql = "SELECT item_id, quantity FROM item_assignments " +
                     "WHERE receipt_id = ? AND user_id = ?";
        Map<Integer, Integer> assignments = new HashMap<>();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, receiptId);
            pstmt.setString(2, userId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int itemId = rs.getInt("item_id");
                    int quantity = rs.getInt("quantity");
                    assignments.put(itemId, quantity);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting item assignments: " + e.getMessage());
            e.printStackTrace();
        }
        
        return assignments;
    }

    /**
     * Get all item assignments for a receipt with payment information.
     * Returns assignments for all users, not just one.
     * 
     * @param receiptId The receipt ID
     * @return List of maps containing item_id, user_id, quantity, paid_by, paid_at
     */
    public List<Map<String, Object>> getAllItemAssignmentsForReceipt(int receiptId) {
        // First check if paid_by column exists, if not, use a simpler query
        String sql = "SELECT item_id, user_id, quantity " +
                     "FROM item_assignments " +
                     "WHERE receipt_id = ?";
        
        // Try to include paid_by and paid_at if they exist
        try (Connection conn = dbConnection.getConnection()) {
            // Check if paid_by column exists
            java.sql.DatabaseMetaData metaData = conn.getMetaData();
            try (java.sql.ResultSet columns = metaData.getColumns(null, null, "item_assignments", "paid_by")) {
                if (columns.next()) {
                    // Column exists, use full query
                    sql = "SELECT item_id, user_id, quantity, paid_by, paid_at " +
                          "FROM item_assignments " +
                          "WHERE receipt_id = ?";
                }
            } catch (SQLException e) {
                // Column doesn't exist, use simple query (will be handled below)
                System.out.println("Note: paid_by column doesn't exist yet. Run migration script: scripts/database/migrate_add_item_payment_tracking.sql");
            }
        } catch (SQLException e) {
            System.err.println("Error checking for paid_by column: " + e.getMessage());
        }
        
        List<Map<String, Object>> assignments = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, receiptId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> assignment = new HashMap<>();
                    assignment.put("itemId", rs.getInt("item_id"));
                    assignment.put("userId", rs.getString("user_id"));
                    assignment.put("quantity", rs.getInt("quantity"));
                    
                    // Try to get paid_by and paid_at if they exist
                    try {
                        String paidBy = rs.getString("paid_by");
                        assignment.put("paidBy", paidBy);
                        Timestamp paidAt = rs.getTimestamp("paid_at");
                        assignment.put("paidAt", paidAt != null ? paidAt.getTime() : null);
                        assignment.put("isPaid", paidBy != null);
                    } catch (SQLException e) {
                        // Columns don't exist, set defaults
                        assignment.put("paidBy", null);
                        assignment.put("paidAt", null);
                        assignment.put("isPaid", false);
                    }
                    
                    assignments.add(assignment);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting all item assignments: " + e.getMessage());
            e.printStackTrace();
        }
        
        return assignments;
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
        // Mark items in receipt_items table where the user has claimed them
        // Only mark items that aren't already paid
        String sql = "UPDATE receipt_items ri " +
                     "INNER JOIN item_assignments ia ON ri.item_id = ia.item_id " +
                     "SET ri.paid_by = ?, ri.paid_at = CURRENT_TIMESTAMP " +
                     "WHERE ri.receipt_id = ? AND ia.user_id = ? AND ri.paid_by IS NULL";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.setInt(2, receiptId);
            pstmt.setString(3, userId);
            
            int affectedRows = pstmt.executeUpdate();
            System.out.println("Marked " + affectedRows + " items as paid in receipt_items for user " + userId);
            return affectedRows;
        } catch (SQLException e) {
            System.err.println("Error marking items as paid in receipt_items: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }
    
    /**
     * Get payment info for all items in a receipt (from receipt_items table).
     * 
     * @param receiptId The receipt ID
     * @return Map of itemId -> {paidBy, payerName, paidAt}
     */
    public Map<Integer, Map<String, Object>> getItemPaymentInfoForReceipt(int receiptId) {
        String sql = "SELECT item_id, paid_by, paid_at " +
                     "FROM receipt_items " +
                     "WHERE receipt_id = ? AND paid_by IS NOT NULL";
        
        Map<Integer, Map<String, Object>> paymentInfo = new HashMap<>();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, receiptId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int itemId = rs.getInt("item_id");
                    String paidBy = rs.getString("paid_by");
                    Timestamp paidAt = rs.getTimestamp("paid_at");
                    
                    Map<String, Object> info = new HashMap<>();
                    info.put("paidBy", paidBy);
                    info.put("paidAt", paidAt != null ? paidAt.getTime() : null);
                    paymentInfo.put(itemId, info);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting item payment info from receipt_items: " + e.getMessage());
            e.printStackTrace();
        }
        
        return paymentInfo;
    }

    /**
     * Check if an item is already paid for by any user.
     * 
     * @param itemId The item ID
     * @return true if the item (or any quantity of it) is paid, false otherwise
     */
    public boolean isItemPaid(int itemId) {
        String sql = "SELECT COUNT(*) as count FROM item_assignments " +
                     "WHERE item_id = ? AND paid_by IS NOT NULL";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, itemId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("count") > 0;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error checking if item is paid: " + e.getMessage());
            e.printStackTrace();
        }
        
        return false;
    }

    /**
     * Get payment information for an item (who paid for it).
     * 
     * @param itemId The item ID
     * @return Map with paidBy (user_id) and paidAt (timestamp), or null if not paid
     */
    public Map<String, Object> getItemPaymentInfo(int itemId) {
        String sql = "SELECT paid_by, paid_at FROM item_assignments " +
                     "WHERE item_id = ? AND paid_by IS NOT NULL " +
                     "LIMIT 1";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, itemId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> paymentInfo = new HashMap<>();
                    paymentInfo.put("paidBy", rs.getString("paid_by"));
                    Timestamp paidAt = rs.getTimestamp("paid_at");
                    paymentInfo.put("paidAt", paidAt != null ? paidAt.getTime() : null);
                    return paymentInfo;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting item payment info: " + e.getMessage());
            e.printStackTrace();
        }
        
        return null;
    }

    /**
     * Calculate the total amount owed by a user for a receipt based on their item assignments.
     * 
     * @param receiptId The receipt ID
     * @param userId The user ID (VARCHAR(36))
     * @return The total amount owed (items + proportional tax/tip), or 0 if no items assigned
     */
    public float calculateUserOwedAmount(int receiptId, String userId) {
        // Get all items for this receipt
        List<ReceiptItem> allItems = getReceiptItems(receiptId);
        if (allItems.isEmpty()) {
            return 0.0f;
        }
        
        // Get user's item assignments
        Map<Integer, Integer> assignments = getItemAssignmentsForUser(receiptId, userId);
        if (assignments.isEmpty()) {
            return 0.0f;
        }
        
        // Calculate subtotal from assigned items
        float assignedSubtotal = 0.0f;
        float totalSubtotal = 0.0f;
        
        for (ReceiptItem item : allItems) {
            float itemTotal = item.getPrice() * item.getQuantity();
            totalSubtotal += itemTotal;
            
            Integer assignedQty = assignments.get(item.getItemId());
            if (assignedQty != null && assignedQty > 0) {
                // Calculate proportional cost for assigned quantity
                float itemPricePerUnit = item.getPrice();
                assignedSubtotal += itemPricePerUnit * assignedQty;
            }
        }
        
        if (totalSubtotal == 0) {
            return 0.0f;
        }
        
        // Get receipt for tax and tip
        Receipt receipt = getReceiptById(receiptId);
        if (receipt == null) {
            return assignedSubtotal;
        }
        
        // Calculate proportional tax and tip
        float taxAmount = receipt.getTaxAmount();
        float tipAmount = receipt.getTipAmount();
        float proportion = assignedSubtotal / totalSubtotal;
        
        float assignedTax = taxAmount * proportion;
        float assignedTip = tipAmount * proportion;
        
        return assignedSubtotal + assignedTax + assignedTip;
    }

    /**
     * Get all pending receipts for a specific user.
     * A receipt is pending for a user if they are a participant with status 'pending'.
     * Excludes receipts that were uploaded by the user (they shouldn't see their own receipts as pending).
     * 
     * @param userId The user's ID (VARCHAR(36))
     * @return List of Receipt objects
     */
    public List<Receipt> getPendingReceiptsForUser(String userId) {
        String sql = "SELECT r.* FROM receipts r " +
                     "INNER JOIN receipt_participants rp ON r.receipt_id = rp.receipt_id " +
                     "WHERE rp.user_id = ? AND rp.status = 'pending' AND r.uploaded_by != ? " +
                     "ORDER BY r.created_at DESC";
        
        List<Receipt> receipts = new ArrayList<>();

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, userId);
            pstmt.setString(2, userId); // Also exclude receipts uploaded by this user

            try (ResultSet rs = pstmt.executeQuery()) {
                System.out.println("[ReceiptDAO] Getting pending receipts for user " + userId);
                int count = 0;
                while (rs.next()) {
                    Receipt receipt = mapResultSetToReceipt(rs);
                    // Load items for this receipt using addItem() method
                    for (ReceiptItem item : getReceiptItems(receipt.getReceiptId())) {
                        receipt.addItem(item);
                    }
                    receipts.add(receipt);
                    count++;
                    System.out.println("[ReceiptDAO] Added pending receipt " + receipt.getReceiptId() + " (total so far: " + count + ")");
                }
                System.out.println("[ReceiptDAO] Found total of " + count + " pending receipts for user " + userId);
            }
        } catch (SQLException e) {
            System.err.println("Error getting pending receipts for user: " + e.getMessage());
            e.printStackTrace();
        }

        return receipts;
    }

    /**
     * Get all receipts for a specific user (accepted, declined, or uploaded by them).
     * Includes receipts where the user is a participant with status 'accepted' or 'declined',
     * or receipts they uploaded.
     * 
     * @param userId The user's ID (VARCHAR(36))
     * @return List of Receipt objects with participant status information
     */
    public List<Receipt> getAllReceiptsForUser(String userId) {
        // Get receipts where:
        // 1. User is a participant with status 'accepted' or 'completed'
        // 2. OR user uploaded the receipt (they should see all their receipts regardless of status)
        // This ensures uploaders see their newly created receipts immediately
        String sql = "SELECT DISTINCT r.* " +
                     "FROM receipts r " +
                     "LEFT JOIN receipt_participants rp ON r.receipt_id = rp.receipt_id AND rp.user_id = ? " +
                     "WHERE (rp.user_id = ? AND rp.status IN ('accepted', 'completed')) " +
                     "   OR r.uploaded_by = ? " +
                     "ORDER BY r.created_at DESC";
        
        List<Receipt> receipts = new ArrayList<>();

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, userId);
            pstmt.setString(2, userId);
            pstmt.setString(3, userId);

            try (ResultSet rs = pstmt.executeQuery()) {
                System.out.println("[ReceiptDAO] Getting all receipts for user " + userId);
                int count = 0;
                while (rs.next()) {
                    Receipt receipt = mapResultSetToReceipt(rs);
                    // Load items for this receipt using addItem() method
                    for (ReceiptItem item : getReceiptItems(receipt.getReceiptId())) {
                        receipt.addItem(item);
                    }
                    receipts.add(receipt);
                    count++;
                    System.out.println("[ReceiptDAO] Added receipt " + receipt.getReceiptId() + " (total so far: " + count + ")");
                }
                System.out.println("[ReceiptDAO] Found total of " + count + " receipts for user " + userId);
            }
        } catch (SQLException e) {
            System.err.println("Error getting all receipts for user: " + e.getMessage());
            e.printStackTrace();
        }

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
        String sql = "INSERT INTO receipt_items (receipt_id, name, price, quantity, category) " +
                     "VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, receiptId);
            pstmt.setString(2, name);
            pstmt.setBigDecimal(3, java.math.BigDecimal.valueOf(price));
            pstmt.setInt(4, quantity);
            pstmt.setString(5, category);

            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int itemId = generatedKeys.getInt(1);
                        return getReceiptItemById(itemId);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error adding receipt item: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Get all items for a receipt.
     * 
     * @param receiptId The receipt ID
     * @return List of ReceiptItem objects
     */
    public List<ReceiptItem> getReceiptItems(int receiptId) {
        String sql = "SELECT * FROM receipt_items WHERE receipt_id = ? ORDER BY item_id";
        List<ReceiptItem> items = new ArrayList<>();

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, receiptId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    items.add(mapResultSetToReceiptItem(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting receipt items: " + e.getMessage());
            e.printStackTrace();
        }

        return items;
    }

    /**
     * Get a receipt item by its ID.
     * 
     * @param itemId The item ID
     * @return ReceiptItem object or null if not found
     */
    public ReceiptItem getReceiptItemById(int itemId) {
        String sql = "SELECT * FROM receipt_items WHERE item_id = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, itemId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToReceiptItem(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting receipt item by ID: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Add a participant to a receipt (user who should receive/see the receipt).
     * 
     * @param receiptId The receipt ID
     * @param userId The user's ID (VARCHAR(36))
     * @return true if participant was added successfully
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
     * Update the status of a receipt participant (accept/decline).
     * 
     * @param receiptId The receipt ID
     * @param userId The user's ID (VARCHAR(36))
     * @param status New status ('pending', 'accepted', 'declined')
     * @return true if update was successful
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
     * Get the status of a receipt for a specific user.
     * 
     * @param receiptId The receipt ID
     * @param userId The user's ID (VARCHAR(36))
     * @return Status string ('pending', 'accepted', 'declined') or null if not found
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

        // Create Receipt - note: uploadedBy is int in model but String in DB
        // We'll use 0 as placeholder and handle conversion in service layer
        // The actual uploadedBy can be retrieved via getReceiptUploadedBy() when needed
        Receipt receipt = new Receipt(receiptId, 0, merchantName, date, totalAmount, tipAmount, taxAmount, imageUrl, status);
        
        return receipt;
    }

    /**
     * Helper method to map a ResultSet row to a ReceiptItem object.
     */
    private ReceiptItem mapResultSetToReceiptItem(ResultSet rs) throws SQLException {
        int itemId = rs.getInt("item_id");
        int receiptId = rs.getInt("receipt_id");
        String name = rs.getString("name");
        float price = rs.getBigDecimal("price").floatValue();
        int quantity = rs.getInt("quantity");
        String category = rs.getString("category");

        return new ReceiptItem(itemId, receiptId, name, price, quantity, category);
    }

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
     * 
     * @param receiptId The receipt ID
     * @param userId The user ID
     * @return The amount paid, or 0 if no payment recorded
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

    /**
     * Get all participants for a receipt with their payment status.
     * 
     * @param receiptId The receipt ID
     * @return List of maps containing user_id, status, paid_amount, and owed_amount
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
                    String userId = rs.getString("user_id");
                    participant.put("user_id", userId);
                    participant.put("status", rs.getString("status"));
                    participant.put("paid_amount", rs.getBigDecimal("paid_amount").floatValue());
                    participant.put("owed_amount", calculateUserOwedAmount(receiptId, userId));
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
     * Check if all participants have paid their full amount and mark receipt as completed if so.
     * 
     * @param receiptId The receipt ID
     * @return true if receipt was marked as completed, false otherwise
     */
    public boolean checkAndMarkReceiptCompleted(int receiptId) {
        List<Map<String, Object>> participants = getParticipantsWithPaymentStatus(receiptId);
        
        if (participants.isEmpty()) {
            return false; // No accepted participants
        }

        // Check if all participants have paid their full amount
        boolean allPaid = true;
        for (Map<String, Object> participant : participants) {
            float paidAmount = (Float) participant.get("paid_amount");
            float owedAmount = (Float) participant.get("owed_amount");
            
            // Allow small rounding differences (0.01)
            if (paidAmount < owedAmount - 0.01f) {
                allPaid = false;
                break;
            }
        }

        if (allPaid) {
            // Mark receipt as completed
            return updateReceiptStatus(receiptId, "completed");
        }

        return false;
    }
}
