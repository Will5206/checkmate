package database;

import models.ReceiptItem;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Data Access Object for managing item assignments.
 * Handles all operations related to item_assignments table.
 */
public class ItemAssignmentDAO {
    
    private final DatabaseConnection dbConnection;

    public ItemAssignmentDAO() {
        this.dbConnection = DatabaseConnection.getInstance();
    }

    /**
     * Get total claimed quantity for an item (excluding paid items if paid_by column exists).
     */
    public int getTotalClaimedQuantity(int itemId) {
        String sql;
        boolean hasPaidByColumn = false;
        
        try (Connection conn = dbConnection.getConnection()) {
            java.sql.DatabaseMetaData metaData = conn.getMetaData();
            try (java.sql.ResultSet columns = metaData.getColumns(null, null, "item_assignments", "paid_by")) {
                hasPaidByColumn = columns.next();
            }
        } catch (SQLException e) {
            System.err.println("Error checking for paid_by column: " + e.getMessage());
        }
        
        if (hasPaidByColumn) {
            sql = "SELECT COALESCE(SUM(quantity), 0) as total_claimed " +
                  "FROM item_assignments " +
                  "WHERE item_id = ? AND paid_by IS NULL";
        } else {
            sql = "SELECT COALESCE(SUM(quantity), 0) as total_claimed " +
                  "FROM item_assignments " +
                  "WHERE item_id = ?";
        }
        
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

    /**
     * Assign an item to a user (claim an item) with proper transaction and locking.
     * Uses SELECT FOR UPDATE to prevent race conditions.
     * 
     * @param itemId The item ID
     * @param userId The user ID
     * @param quantity The quantity to claim
     * @param onSuccess Callback to execute after successful assignment (receiptId passed)
     * @return true if assignment was successful, false otherwise
     */
    public boolean assignItemToUser(int itemId, String userId, int quantity, Consumer<Integer> onSuccess) {
        long startTime = System.currentTimeMillis();
        System.out.println("[ItemAssignmentDAO] 🔵 STEP 1: assignItemToUser called - itemId=" + itemId + ", userId=" + userId + ", quantity=" + quantity);
        
        Connection conn = null;
        try {
            long connStart = System.currentTimeMillis();
            conn = dbConnection.getConnection();
            System.out.println("[ItemAssignmentDAO] 🔵 STEP 2: Connection obtained (took " + (System.currentTimeMillis() - connStart) + "ms)");
            
            conn.setAutoCommit(false);
            
            // Lock the item row to prevent race conditions
            long lockStart = System.currentTimeMillis();
            String getItemSql = "SELECT receipt_id, quantity as item_quantity " +
                               "FROM receipt_items " +
                               "WHERE item_id = ? FOR UPDATE";
            
            int receiptId = -1;
            int itemQuantity = 0;
            
            try (PreparedStatement pstmt = conn.prepareStatement(getItemSql)) {
                pstmt.setInt(1, itemId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        receiptId = rs.getInt("receipt_id");
                        itemQuantity = rs.getInt("item_quantity");
                    } else {
                        System.err.println("[ItemAssignmentDAO] 🔴 ERROR: Item not found: " + itemId);
                        conn.rollback();
                        return false;
                    }
                }
            }
            System.out.println("[ItemAssignmentDAO] 🔵 STEP 3: SELECT FOR UPDATE completed (took " + (System.currentTimeMillis() - lockStart) + "ms) - receiptId=" + receiptId + ", itemQuantity=" + itemQuantity);
            
            // Get current user's claimed quantity
            long qtyStart = System.currentTimeMillis();
            int userCurrentQty = getUserClaimedQuantityInTransaction(conn, itemId, userId);
            System.out.println("[ItemAssignmentDAO] 🔵 STEP 4: getUserClaimedQuantityInTransaction took " + (System.currentTimeMillis() - qtyStart) + "ms - userCurrentQty=" + userCurrentQty);
            
            // Get total claimed quantity by all users
            long totalStart = System.currentTimeMillis();
            int totalClaimed = getTotalClaimedQuantityInTransaction(conn, itemId);
            System.out.println("[ItemAssignmentDAO] 🔵 STEP 5: getTotalClaimedQuantityInTransaction took " + (System.currentTimeMillis() - totalStart) + "ms - totalClaimed=" + totalClaimed);
            
            // Calculate total claimed by others (excluding this user's current claim)
            int totalClaimedByOthers = totalClaimed - userCurrentQty;
            
            // Validate: new total claimed cannot exceed item quantity
            int newTotalClaimed = totalClaimedByOthers + quantity;
            if (newTotalClaimed > itemQuantity) {
                System.err.println("Cannot claim " + quantity + " of item " + itemId + 
                                 ". Item has quantity " + itemQuantity + 
                                 ", already claimed: " + totalClaimedByOthers + 
                                 " by others, user currently has: " + userCurrentQty);
                conn.rollback();
                return false;
            }
            
            // Insert or update assignment
            long insertStart = System.currentTimeMillis();
            String sql = "INSERT INTO item_assignments (receipt_id, item_id, user_id, quantity) " +
                         "VALUES (?, ?, ?, ?) " +
                         "ON DUPLICATE KEY UPDATE quantity = ?";
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, receiptId);
                pstmt.setInt(2, itemId);
                pstmt.setString(3, userId);
                pstmt.setInt(4, quantity);
                pstmt.setInt(5, quantity);
                
                int affectedRows = pstmt.executeUpdate();
                System.out.println("[ItemAssignmentDAO] 🔵 STEP 6: INSERT/UPDATE took " + (System.currentTimeMillis() - insertStart) + "ms - affectedRows=" + affectedRows);
                
                if (affectedRows > 0) {
                    long commitStart = System.currentTimeMillis();
                    conn.commit();
                    System.out.println("[ItemAssignmentDAO] 🔵 STEP 7: COMMIT took " + (System.currentTimeMillis() - commitStart) + "ms");
                    
                    // Execute callback if provided
                    if (onSuccess != null) {
                        onSuccess.accept(receiptId);
                    }
                    
                    long totalDuration = System.currentTimeMillis() - startTime;
                    System.out.println("[ItemAssignmentDAO] ✅ STEP 8: assignItemToUser completed successfully (total time: " + totalDuration + "ms)");
                    return true;
                } else {
                    conn.rollback();
                    System.err.println("[ItemAssignmentDAO] 🔴 ERROR: No rows affected by INSERT/UPDATE");
                    return false;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error assigning item to user: " + e.getMessage());
            e.printStackTrace();
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    System.err.println("Error rolling back transaction: " + rollbackEx.getMessage());
                }
            }
            return false;
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

    /**
     * Unassign an item from a user (unclaim an item).
     * 
     * @param itemId The item ID
     * @param userId The user ID
     * @param onSuccess Callback to execute after successful unassignment (receiptId passed)
     * @return true if unassignment was successful, false otherwise
     */
    public boolean unassignItemFromUser(int itemId, String userId, Consumer<Integer> onSuccess) {
        Connection conn = null;
        int receiptId = -1;
        
        try {
            conn = dbConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Get receipt_id before deleting (with lock)
            String getReceiptSql = "SELECT receipt_id FROM item_assignments WHERE item_id = ? AND user_id = ? LIMIT 1 FOR UPDATE";
            try (PreparedStatement pstmt = conn.prepareStatement(getReceiptSql)) {
                pstmt.setInt(1, itemId);
                pstmt.setString(2, userId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        receiptId = rs.getInt("receipt_id");
                    } else {
                        conn.rollback();
                        return false;
                    }
                }
            }
            
            // Delete assignment
            String sql = "DELETE FROM item_assignments WHERE item_id = ? AND user_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, itemId);
                pstmt.setString(2, userId);
                
                int affectedRows = pstmt.executeUpdate();
                if (affectedRows > 0) {
                    conn.commit();
                    
                    // Execute callback if provided
                    if (onSuccess != null && receiptId > 0) {
                        onSuccess.accept(receiptId);
                    }
                    
                    return true;
                } else {
                    conn.rollback();
                    return false;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error unassigning item from user: " + e.getMessage());
            e.printStackTrace();
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    System.err.println("Error rolling back transaction: " + rollbackEx.getMessage());
                }
            }
            return false;
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

    /**
     * Get all item assignments for a specific receipt and user.
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
     */
    public List<Map<String, Object>> getAllItemAssignmentsForReceipt(int receiptId) {
        String sql = "SELECT item_id, user_id, quantity " +
                     "FROM item_assignments " +
                     "WHERE receipt_id = ?";
        
        // Check if paid_by column exists
        try (Connection conn = dbConnection.getConnection()) {
            java.sql.DatabaseMetaData metaData = conn.getMetaData();
            try (java.sql.ResultSet columns = metaData.getColumns(null, null, "item_assignments", "paid_by")) {
                if (columns.next()) {
                    sql = "SELECT item_id, user_id, quantity, paid_by, paid_at " +
                          "FROM item_assignments " +
                          "WHERE receipt_id = ?";
                }
            }
        } catch (SQLException e) {
            System.out.println("Note: paid_by column doesn't exist yet. Run migration script: scripts/database/migrate_add_item_payment_tracking.sql");
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
     * Mark all items assigned to a user as paid.
     */
    public int markItemsAsPaid(int receiptId, String userId) {
        String sql = "UPDATE item_assignments " +
                     "SET paid_by = ?, paid_at = CURRENT_TIMESTAMP " +
                     "WHERE receipt_id = ? AND user_id = ? AND paid_by IS NULL";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.setInt(2, receiptId);
            pstmt.setString(3, userId);
            
            int affectedRows = pstmt.executeUpdate();
            System.out.println("Marked " + affectedRows + " item assignments as paid for user " + userId);
            return affectedRows;
        } catch (SQLException e) {
            System.err.println("Error marking item assignments as paid: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Get payment info for all items in a receipt.
     */
    public Map<Integer, Map<String, Object>> getItemPaymentInfoForReceipt(int receiptId) {
        String sql = "SELECT item_id, paid_by, paid_at " +
                     "FROM item_assignments " +
                     "WHERE receipt_id = ? AND paid_by IS NOT NULL " +
                     "GROUP BY item_id, paid_by, paid_at";
        
        Map<Integer, Map<String, Object>> paymentInfo = new HashMap<>();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, receiptId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int itemId = rs.getInt("item_id");
                    if (!paymentInfo.containsKey(itemId)) {
                        String paidBy = rs.getString("paid_by");
                        Timestamp paidAt = rs.getTimestamp("paid_at");
                        
                        Map<String, Object> info = new HashMap<>();
                        info.put("paidBy", paidBy);
                        info.put("paidAt", paidAt != null ? paidAt.getTime() : null);
                        paymentInfo.put(itemId, info);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting item payment info from item_assignments: " + e.getMessage());
            e.printStackTrace();
        }
        
        return paymentInfo;
    }

    /**
     * Check if an item is already paid for by any user.
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
     * Get payment information for an item.
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
     * Check if all items in a receipt are fully claimed.
     */
    public boolean areAllItemsClaimed(int receiptId) {
        String sql = "SELECT " +
                     "  ri.item_id, " +
                     "  ri.name, " +
                     "  ri.quantity as item_quantity, " +
                     "  COALESCE(SUM(ia.quantity), 0) as total_claimed " +
                     "FROM receipt_items ri " +
                     "LEFT JOIN item_assignments ia ON ri.item_id = ia.item_id " +
                     "WHERE ri.receipt_id = ? " +
                     "GROUP BY ri.item_id, ri.name, ri.quantity";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, receiptId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                int itemCount = 0;
                while (rs.next()) {
                    itemCount++;
                    int itemQuantity = rs.getInt("item_quantity");
                    int totalClaimed = rs.getInt("total_claimed");
                    
                    if (totalClaimed < itemQuantity) {
                        return false;
                    }
                }
                
                if (itemCount == 0) {
                    return false;
                }
                
                return true;
            }
        } catch (SQLException e) {
            System.err.println("Error checking if all items claimed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Helper methods for transaction context
    
    private int getUserClaimedQuantityInTransaction(Connection conn, int itemId, String userId) throws SQLException {
        String sql = "SELECT COALESCE(SUM(quantity), 0) as user_qty " +
                     "FROM item_assignments " +
                     "WHERE item_id = ? AND user_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, itemId);
            pstmt.setString(2, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("user_qty");
                }
            }
        }
        return 0;
    }

    private int getTotalClaimedQuantityInTransaction(Connection conn, int itemId) throws SQLException {
        String sql = "SELECT COALESCE(SUM(quantity), 0) as total_qty " +
                     "FROM item_assignments " +
                     "WHERE item_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, itemId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("total_qty");
                }
            }
        }
        return 0;
    }

    /**
     * Get item claim info (item details + claimed quantities).
     * Returns an ItemClaimInfo object containing the item, user's claimed quantity, and total claimed quantity.
     * 
     * @param itemId The item ID
     * @param userId The user ID
     * @return ItemClaimInfo object, or null if item not found
     */
    public ReceiptDAO.ItemClaimInfo getItemClaimInfo(int itemId, String userId) {
        System.out.println("[ItemAssignmentDAO] 🔵 STEP 1: getItemClaimInfo called for itemId=" + itemId + ", userId=" + userId);
        
        // Get item info and claimed quantities in a single query
        String sql = "SELECT " +
                     "  ri.item_id, ri.receipt_id, ri.name, ri.price, ri.quantity as item_quantity, ri.category, " +
                     "  (SELECT COALESCE(SUM(quantity), 0) FROM item_assignments WHERE item_id = ? AND user_id = ?) as user_claimed_qty, " +
                     "  (SELECT COALESCE(SUM(quantity), 0) FROM item_assignments WHERE item_id = ?) as total_claimed_qty " +
                     "FROM receipt_items ri " +
                     "WHERE ri.item_id = ?";
        
        System.out.println("[ItemAssignmentDAO] 🔵 STEP 2: SQL query prepared");
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            System.out.println("[ItemAssignmentDAO] 🔵 STEP 3: Connection obtained, setting parameters...");
            // Set parameters: itemId for user query, userId, itemId for total query, itemId for WHERE
            pstmt.setInt(1, itemId);  // user_claimed_qty subquery item_id
            pstmt.setString(2, userId); // user_claimed_qty subquery user_id
            pstmt.setInt(3, itemId);  // total_claimed_qty subquery item_id
            pstmt.setInt(4, itemId);  // main WHERE clause item_id
            
            System.out.println("[ItemAssignmentDAO] 🔵 STEP 4: Executing query...");
            
            try (ResultSet rs = pstmt.executeQuery()) {
                System.out.println("[ItemAssignmentDAO] 🔵 STEP 5: Query executed, checking results...");
                
                if (rs.next()) {
                    System.out.println("[ItemAssignmentDAO] 🔵 STEP 6: ResultSet has data, reading columns...");
                    
                    // Build ReceiptItem from result
                    int itemIdFromDb = rs.getInt("item_id");
                    int receiptId = rs.getInt("receipt_id");
                    String name = rs.getString("name");
                    java.math.BigDecimal price = rs.getBigDecimal("price");
                    int itemQuantity = rs.getInt("item_quantity");
                    String category = rs.getString("category");
                    
                    System.out.println("[ItemAssignmentDAO] 🔵 STEP 7: Read item columns - itemId=" + itemIdFromDb + ", name=" + name + ", price=" + price);
                    
                    // Read quantity columns
                    int userClaimedQty = rs.getInt("user_claimed_qty");
                    int totalClaimedQty = rs.getInt("total_claimed_qty");
                    
                    System.out.println("[ItemAssignmentDAO] 🔵 STEP 8: Read quantity columns - userClaimedQty=" + userClaimedQty + ", totalClaimedQty=" + totalClaimedQty);
                    
                    // Validate we got valid data
                    if (price == null) {
                        System.err.println("[ItemAssignmentDAO] 🔴 ERROR: price is NULL for itemId=" + itemId);
                        return null;
                    }
                    
                    ReceiptItem item = new ReceiptItem(
                        itemIdFromDb,
                        receiptId,
                        name,
                        price.floatValue(),
                        itemQuantity,
                        category
                    );
                    
                    System.out.println("[ItemAssignmentDAO] ✅ STEP 9: ItemClaimInfo created successfully");
                    return new ReceiptDAO.ItemClaimInfo(item, userClaimedQty, totalClaimedQty);
                    
                } else {
                    System.out.println("[ItemAssignmentDAO] 🔴 STEP 6: ResultSet is empty - item not found");
                    return null;
                }
            }
        } catch (SQLException e) {
            System.err.println("[ItemAssignmentDAO] 🔴 ERROR: SQLException in getItemClaimInfo: " + e.getMessage());
            System.err.println("[ItemAssignmentDAO] 🔴 ERROR: SQL State: " + e.getSQLState());
            System.err.println("[ItemAssignmentDAO] 🔴 ERROR: Error Code: " + e.getErrorCode());
            e.printStackTrace();
            return null;
        } catch (Exception e) {
            System.err.println("[ItemAssignmentDAO] 🔴 ERROR: Unexpected exception in getItemClaimInfo: " + e.getMessage());
            System.err.println("[ItemAssignmentDAO] 🔴 ERROR: Exception type: " + e.getClass().getName());
            e.printStackTrace();
            return null;
        }
    }
}






