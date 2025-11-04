package services;

import database.DatabaseConnection;
import models.Transaction;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for managing financial transactions between users.
 * Handles creation, retrieval, and status updates of transactions.
 */
public class TransactionService {
    
    private DatabaseConnection dbConnection;
    
    // Transaction type constants
    public static final String TYPE_RECEIPT_PAYMENT = "receipt_payment";
    public static final String TYPE_POT_CONTRIBUTION = "pot_contribution";
    public static final String TYPE_POT_WITHDRAWAL = "pot_withdrawal";
    public static final String TYPE_PEER_TO_PEER = "peer_to_peer";
    public static final String TYPE_REFUND = "refund";
    public static final String TYPE_OTHER = "other";
    
    // Status constants
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_CANCELLED = "cancelled";
    
    public TransactionService() {
        this.dbConnection = DatabaseConnection.getInstance();
    }
    
    /**
     * Create a new transaction.
     * 
     * @param fromUserId The user sending money (required)
     * @param toUserId The user receiving money (can be null for some transaction types)
     * @param amount The transaction amount (must be positive)
     * @param transactionType Type of transaction (use TYPE_ constants)
     * @param description Description of the transaction
     * @param status Initial status (defaults to "pending" if null)
     * @param relatedEntityId Optional ID of related entity (receipt, pot, etc.)
     * @return The created Transaction object, or null if creation failed
     * @throws IllegalArgumentException if validation fails
     */
    public Transaction createTransaction(String fromUserId, String toUserId, 
                                        double amount, String transactionType,
                                        String description, String status, 
                                        String relatedEntityId) {
        // Validation
        if (fromUserId == null || fromUserId.trim().isEmpty()) {
            throw new IllegalArgumentException("From user ID cannot be null or empty");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (transactionType == null || transactionType.trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction type cannot be null or empty");
        }
        
        // Set default status
        if (status == null || status.trim().isEmpty()) {
            status = STATUS_PENDING;
        }
        
        Connection conn = null;
        try {
            conn = dbConnection.getConnection();
            
            String sql = "INSERT INTO transactions " +
                        "(from_user_id, to_user_id, amount, transaction_type, description, status, related_entity_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)";
            
            PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            stmt.setString(1, fromUserId);
            if (toUserId != null && !toUserId.trim().isEmpty()) {
                stmt.setString(2, toUserId);
            } else {
                stmt.setNull(2, Types.VARCHAR);
            }
            stmt.setDouble(3, amount);
            stmt.setString(4, transactionType);
            stmt.setString(5, description);
            stmt.setString(6, status);
            if (relatedEntityId != null && !relatedEntityId.trim().isEmpty()) {
                stmt.setString(7, relatedEntityId);
            } else {
                stmt.setNull(7, Types.VARCHAR);
            }
            
            int rowsInserted = stmt.executeUpdate();
            
            if (rowsInserted == 0) {
                stmt.close();
                return null;
            }
            
            // Get generated transaction ID
            ResultSet rs = stmt.getGeneratedKeys();
            int transactionId = 0;
            if (rs.next()) {
                transactionId = rs.getInt(1);
            }
            rs.close();
            stmt.close();
            
            // Return the created transaction
            return getTransactionById(transactionId);
            
        } catch (SQLException e) {
            System.err.println("Error creating transaction: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Get transaction by ID.
     * 
     * @param transactionId The transaction ID
     * @return Transaction object, or null if not found
     */
    public Transaction getTransactionById(int transactionId) {
        try {
            Connection conn = dbConnection.getConnection();
            String sql = "SELECT transaction_id, from_user_id, to_user_id, amount, " +
                        "transaction_type, description, status, related_entity_id, " +
                        "created_at, updated_at FROM transactions WHERE transaction_id = ?";
            
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, transactionId);
            
            ResultSet rs = stmt.executeQuery();
            Transaction transaction = null;
            
            if (rs.next()) {
                transaction = mapResultSetToTransaction(rs);
            }
            
            rs.close();
            stmt.close();
            
            return transaction;
            
        } catch (SQLException e) {
            System.err.println("Error getting transaction by ID: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Get all transactions for a user (both sent and received).
     * 
     * @param userId The user ID
     * @return List of transactions, ordered by most recent first
     */
    public List<Transaction> getTransactionHistory(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        
        List<Transaction> transactions = new ArrayList<>();
        
        try {
            Connection conn = dbConnection.getConnection();
            String sql = "SELECT transaction_id, from_user_id, to_user_id, amount, " +
                        "transaction_type, description, status, related_entity_id, " +
                        "created_at, updated_at FROM transactions " +
                        "WHERE from_user_id = ? OR to_user_id = ? " +
                        "ORDER BY created_at DESC";
            
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, userId);
            stmt.setString(2, userId);
            
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                transactions.add(mapResultSetToTransaction(rs));
            }
            
            rs.close();
            stmt.close();
            
        } catch (SQLException e) {
            System.err.println("Error getting transaction history: " + e.getMessage());
            e.printStackTrace();
        }
        
        return transactions;
    }
    
    /**
     * Get recent transactions for a user with a limit.
     * 
     * @param userId The user ID
     * @param limit Maximum number of transactions to return
     * @return List of recent transactions, ordered by most recent first
     */
    public List<Transaction> getRecentTransactions(String userId, int limit) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be positive");
        }
        
        List<Transaction> transactions = new ArrayList<>();
        
        try {
            Connection conn = dbConnection.getConnection();
            String sql = "SELECT transaction_id, from_user_id, to_user_id, amount, " +
                        "transaction_type, description, status, related_entity_id, " +
                        "created_at, updated_at FROM transactions " +
                        "WHERE from_user_id = ? OR to_user_id = ? " +
                        "ORDER BY created_at DESC LIMIT ?";
            
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, userId);
            stmt.setString(2, userId);
            stmt.setInt(3, limit);
            
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                transactions.add(mapResultSetToTransaction(rs));
            }
            
            rs.close();
            stmt.close();
            
        } catch (SQLException e) {
            System.err.println("Error getting recent transactions: " + e.getMessage());
            e.printStackTrace();
        }
        
        return transactions;
    }
    
    /**
     * Update transaction status.
     * 
     * @param transactionId The transaction ID
     * @param status New status (use STATUS_ constants)
     * @return true if successful, false otherwise
     */
    public boolean updateTransactionStatus(int transactionId, String status) {
        if (status == null || status.trim().isEmpty()) {
            throw new IllegalArgumentException("Status cannot be null or empty");
        }
        
        try {
            Connection conn = dbConnection.getConnection();
            String sql = "UPDATE transactions SET status = ?, updated_at = CURRENT_TIMESTAMP " +
                        "WHERE transaction_id = ?";
            
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, status);
            stmt.setInt(2, transactionId);
            
            int rowsUpdated = stmt.executeUpdate();
            stmt.close();
            
            return rowsUpdated > 0;
            
        } catch (SQLException e) {
            System.err.println("Error updating transaction status: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Get transactions by status for a user.
     * 
     * @param userId The user ID
     * @param status The status to filter by
     * @return List of transactions with the specified status
     */
    public List<Transaction> getTransactionsByStatus(String userId, String status) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        if (status == null || status.trim().isEmpty()) {
            throw new IllegalArgumentException("Status cannot be null or empty");
        }
        
        List<Transaction> transactions = new ArrayList<>();
        
        try {
            Connection conn = dbConnection.getConnection();
            String sql = "SELECT transaction_id, from_user_id, to_user_id, amount, " +
                        "transaction_type, description, status, related_entity_id, " +
                        "created_at, updated_at FROM transactions " +
                        "WHERE (from_user_id = ? OR to_user_id = ?) AND status = ? " +
                        "ORDER BY created_at DESC";
            
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, userId);
            stmt.setString(2, userId);
            stmt.setString(3, status);
            
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                transactions.add(mapResultSetToTransaction(rs));
            }
            
            rs.close();
            stmt.close();
            
        } catch (SQLException e) {
            System.err.println("Error getting transactions by status: " + e.getMessage());
            e.printStackTrace();
        }
        
        return transactions;
    }
    
    /**
     * Get transactions by type for a user.
     * 
     * @param userId The user ID
     * @param transactionType The transaction type to filter by
     * @return List of transactions with the specified type
     */
    public List<Transaction> getTransactionsByType(String userId, String transactionType) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        if (transactionType == null || transactionType.trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction type cannot be null or empty");
        }
        
        List<Transaction> transactions = new ArrayList<>();
        
        try {
            Connection conn = dbConnection.getConnection();
            String sql = "SELECT transaction_id, from_user_id, to_user_id, amount, " +
                        "transaction_type, description, status, related_entity_id, " +
                        "created_at, updated_at FROM transactions " +
                        "WHERE (from_user_id = ? OR to_user_id = ?) AND transaction_type = ? " +
                        "ORDER BY created_at DESC";
            
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, userId);
            stmt.setString(2, userId);
            stmt.setString(3, transactionType);
            
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                transactions.add(mapResultSetToTransaction(rs));
            }
            
            rs.close();
            stmt.close();
            
        } catch (SQLException e) {
            System.err.println("Error getting transactions by type: " + e.getMessage());
            e.printStackTrace();
        }
        
        return transactions;
    }
    
    /**
     * Get transactions within a date range for a user.
     * 
     * @param userId The user ID
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return List of transactions within the date range
     */
    public List<Transaction> getTransactionsByDateRange(String userId, Timestamp startDate, Timestamp endDate) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start date and end date cannot be null");
        }
        if (startDate.after(endDate)) {
            throw new IllegalArgumentException("Start date must be before end date");
        }
        
        List<Transaction> transactions = new ArrayList<>();
        
        try {
            Connection conn = dbConnection.getConnection();
            String sql = "SELECT transaction_id, from_user_id, to_user_id, amount, " +
                        "transaction_type, description, status, related_entity_id, " +
                        "created_at, updated_at FROM transactions " +
                        "WHERE (from_user_id = ? OR to_user_id = ?) " +
                        "AND created_at >= ? AND created_at <= ? " +
                        "ORDER BY created_at DESC";
            
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, userId);
            stmt.setString(2, userId);
            stmt.setTimestamp(3, startDate);
            stmt.setTimestamp(4, endDate);
            
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                transactions.add(mapResultSetToTransaction(rs));
            }
            
            rs.close();
            stmt.close();
            
        } catch (SQLException e) {
            System.err.println("Error getting transactions by date range: " + e.getMessage());
            e.printStackTrace();
        }
        
        return transactions;
    }
    
    /**
     * Helper method to map ResultSet to Transaction object.
     */
    private Transaction mapResultSetToTransaction(ResultSet rs) throws SQLException {
        return new Transaction(
            rs.getInt("transaction_id"),
            rs.getString("from_user_id"),
            rs.getString("to_user_id"),
            rs.getDouble("amount"),
            rs.getString("transaction_type"),
            rs.getString("description"),
            rs.getString("status"),
            rs.getString("related_entity_id"),
            rs.getTimestamp("created_at"),
            rs.getTimestamp("updated_at")
        );
    }
}

