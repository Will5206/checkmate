package database;

import models.Receipt;
import java.sql.*;
import java.util.Date;

/**
 * Core DAO for receipt CRUD operations.
 * Handles basic receipt creation, retrieval, and status updates.
 */
public class ReceiptCoreDAO {

    private final DatabaseConnection dbConnection;

    public ReceiptCoreDAO() {
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
            System.err.println("[ReceiptCoreDAO] Error getting sender name: " + e.getMessage());
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
            System.err.println("[ReceiptCoreDAO] Error creating receipt: " + e.getMessage());
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
                    
                    // Load items using ReceiptItemDAO
                    // NOTE: We must use addItem() instead of getItems().addAll() because
                    // Receipt.getItems() returns a copy of the list, not the original
                    ReceiptItemDAO receiptItemDAO = new ReceiptItemDAO();
                    for (models.ReceiptItem item : receiptItemDAO.getReceiptItems(receiptId)) {
                        receipt.addItem(item);
                    }
                    
                    return receipt;
                }
            }
        } catch (SQLException e) {
            System.err.println("[ReceiptCoreDAO] Error getting receipt by ID: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
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
            System.err.println("[ReceiptCoreDAO] Error getting receipt uploaded_by: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Update the status of a receipt.
     * 
     * @param receiptId The receipt ID
     * @param status The new status
     * @return true if successful, false otherwise
     */
    public boolean updateReceiptStatus(int receiptId, String status) {
        String sql = "UPDATE receipts SET status = ? WHERE receipt_id = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, status);
            pstmt.setInt(2, receiptId);

            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            System.err.println("[ReceiptCoreDAO] Error updating receipt status: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Map a ResultSet row to a Receipt object.
     * 
     * @param rs The ResultSet
     * @return Receipt object
     * @throws SQLException
     */
    public Receipt mapResultSetToReceipt(ResultSet rs) throws SQLException {
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
        Receipt receipt = new Receipt(receiptId, 0, merchantName, date, totalAmount, tipAmount, taxAmount, imageUrl, status);
        receipt.setSenderName(senderName);
        receipt.setNumberOfItems(numberOfItems);
        
        return receipt;
    }
}

