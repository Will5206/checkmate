package database;

import models.ReceiptItem;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object for managing receipt items.
 * Handles all operations related to receipt_items table.
 */
public class ReceiptItemDAO {
    
    private final DatabaseConnection dbConnection;

    public ReceiptItemDAO() {
        this.dbConnection = DatabaseConnection.getInstance();
    }

    /**
     * Add a single receipt item to the database.
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
     * Add multiple receipt items in a batch operation for better performance.
     */
    public List<ReceiptItem> addReceiptItemsBatch(int receiptId, List<Map<String, Object>> items) {
        List<ReceiptItem> addedItems = new ArrayList<>();
        String sql = "INSERT INTO receipt_items (receipt_id, name, price, quantity, category) " +
                     "VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            for (Map<String, Object> item : items) {
                String name = (String) item.get("name");
                Object priceObj = item.get("price");
                float price = priceObj instanceof Number ? ((Number) priceObj).floatValue() : 0.0f;
                Object qtyObj = item.get("qty");
                int quantity = qtyObj instanceof Number ? ((Number) qtyObj).intValue() : 1;
                String category = (String) item.getOrDefault("category", "uncategorized");

                pstmt.setInt(1, receiptId);
                pstmt.setString(2, name);
                pstmt.setBigDecimal(3, java.math.BigDecimal.valueOf(price));
                pstmt.setInt(4, quantity);
                pstmt.setString(5, category);
                pstmt.addBatch();
            }

            pstmt.executeBatch();

            // Get generated keys
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                while (generatedKeys.next()) {
                    int itemId = generatedKeys.getInt(1);
                    ReceiptItem item = getReceiptItemById(itemId);
                    if (item != null) {
                        addedItems.add(item);
                    }
                }
            }

            // Update receipt item count
            updateReceiptItemCount(receiptId);

        } catch (SQLException e) {
            System.err.println("Error adding receipt items batch: " + e.getMessage());
            e.printStackTrace();
        }

        return addedItems;
    }

    /**
     * Update the number_of_items count in the receipts table.
     */
    public void updateReceiptItemCount(int receiptId) {
        String sql = "UPDATE receipts SET number_of_items = " +
                     "(SELECT COUNT(*) FROM receipt_items WHERE receipt_id = ?) " +
                     "WHERE receipt_id = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, receiptId);
            pstmt.setInt(2, receiptId);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("Error updating receipt item count: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get all receipt items for a receipt.
     */
    public List<ReceiptItem> getReceiptItems(int receiptId) {
        List<ReceiptItem> items = new ArrayList<>();
        String sql = "SELECT * FROM receipt_items WHERE receipt_id = ? ORDER BY item_id";

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
     * Map a ResultSet row to a ReceiptItem object.
     */
    ReceiptItem mapResultSetToReceiptItem(ResultSet rs) throws SQLException {
        int itemId = rs.getInt("item_id");
        int receiptId = rs.getInt("receipt_id");
        String name = rs.getString("name");
        float price = rs.getBigDecimal("price").floatValue();
        int quantity = rs.getInt("quantity");
        String category = rs.getString("category");
        return new ReceiptItem(itemId, receiptId, name, price, quantity, category);
    }
}

