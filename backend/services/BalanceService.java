package services;

import database.DatabaseConnection;
import models.BalanceHistory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for managing user account balances and balance history.
 * Handles all balance transactions and maintains a complete audit trail.
 */
public class BalanceService {
    
    private DatabaseConnection dbConnection;
    
    // Transaction type constants
    public static final String TYPE_PAYMENT_RECEIVED = "payment_received";
    public static final String TYPE_PAYMENT_SENT = "payment_sent";
    public static final String TYPE_POT_CONTRIBUTION = "pot_contribution";
    public static final String TYPE_POT_WITHDRAWAL = "pot_withdrawal";
    public static final String TYPE_RECEIPT_SPLIT = "receipt_split";
    public static final String TYPE_REFUND = "refund";
    public static final String TYPE_ADJUSTMENT = "adjustment";
    public static final String TYPE_OTHER = "other";
    
    public BalanceService() {
        this.dbConnection = DatabaseConnection.getInstance();
    }
    
    /**
     * Add amount to user's balance and record the transaction in history.
     * 
     * @param userId The user whose balance will be updated
     * @param amount The amount to add (must be positive)
     * @param transactionType Type of transaction (use TYPE_ constants)
     * @param description Description of the transaction
     * @param referenceId Optional reference ID (transaction ID, pot ID, etc.)
     * @param referenceType Optional reference type (transaction, pot, receipt, etc.)
     * @return true if successful, false otherwise
     * @throws IllegalArgumentException if amount is negative or zero, or userId is invalid
     */
    public boolean addToBalance(String userId, double amount, String transactionType, 
                                String description, String referenceId, String referenceType) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive for additions");
        }
        
        Connection conn = null;
        try {
            conn = dbConnection.getConnection();
            conn.setAutoCommit(false); // Start transaction
            
            // Get current balance
            double balanceBefore = getCurrentBalanceInternal(conn, userId);
            
            // Calculate new balance
            double balanceAfter = balanceBefore + amount;
            
            // Update user balance
            String updateSql = "UPDATE users SET balance = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?";
            PreparedStatement updateStmt = conn.prepareStatement(updateSql);
            updateStmt.setDouble(1, balanceAfter);
            updateStmt.setString(2, userId);
            
            int rowsUpdated = updateStmt.executeUpdate();
            updateStmt.close();
            
            if (rowsUpdated == 0) {
                conn.rollback();
                return false; // User not found
            }
            
            // Record in balance history
            String historySql = "INSERT INTO balance_history " +
                    "(user_id, amount, balance_before, balance_after, transaction_type, " +
                    "description, reference_id, reference_type) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            
            PreparedStatement historyStmt = conn.prepareStatement(historySql);
            historyStmt.setString(1, userId);
            historyStmt.setDouble(2, amount);
            historyStmt.setDouble(3, balanceBefore);
            historyStmt.setDouble(4, balanceAfter);
            historyStmt.setString(5, transactionType);
            historyStmt.setString(6, description);
            historyStmt.setString(7, referenceId);
            historyStmt.setString(8, referenceType);
            
            historyStmt.executeUpdate();
            historyStmt.close();
            
            conn.commit();
            return true;
            
        } catch (SQLException e) {
            System.err.println("Error adding to balance: " + e.getMessage());
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
     * Subtract amount from user's balance and record the transaction in history.
     * 
     * @param userId The user whose balance will be updated
     * @param amount The amount to subtract (must be positive, will be stored as negative in history)
     * @param transactionType Type of transaction (use TYPE_ constants)
     * @param description Description of the transaction
     * @param referenceId Optional reference ID (transaction ID, pot ID, etc.)
     * @param referenceType Optional reference type (transaction, pot, receipt, etc.)
     * @return true if successful, false otherwise
     * @throws IllegalArgumentException if amount is negative or zero, or userId is invalid
     */
    public boolean subtractFromBalance(String userId, double amount, String transactionType, 
                                      String description, String referenceId, String referenceType) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive for subtractions");
        }
        
        Connection conn = null;
        try {
            conn = dbConnection.getConnection();
            conn.setAutoCommit(false); // Start transaction
            
            // Get current balance
            double balanceBefore = getCurrentBalanceInternal(conn, userId);
            
            // Check if user has sufficient balance
            if (balanceBefore < amount) {
                conn.rollback();
                throw new IllegalArgumentException("Insufficient balance. Current: " + balanceBefore + ", Required: " + amount);
            }
            
            // Calculate new balance
            double balanceAfter = balanceBefore - amount;
            
            // Update user balance
            String updateSql = "UPDATE users SET balance = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?";
            PreparedStatement updateStmt = conn.prepareStatement(updateSql);
            updateStmt.setDouble(1, balanceAfter);
            updateStmt.setString(2, userId);
            
            int rowsUpdated = updateStmt.executeUpdate();
            updateStmt.close();
            
            if (rowsUpdated == 0) {
                conn.rollback();
                return false; // User not found
            }
            
            // Record in balance history (store as negative amount)
            String historySql = "INSERT INTO balance_history " +
                    "(user_id, amount, balance_before, balance_after, transaction_type, " +
                    "description, reference_id, reference_type) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            
            PreparedStatement historyStmt = conn.prepareStatement(historySql);
            historyStmt.setString(1, userId);
            historyStmt.setDouble(2, -amount); // Store as negative
            historyStmt.setDouble(3, balanceBefore);
            historyStmt.setDouble(4, balanceAfter);
            historyStmt.setString(5, transactionType);
            historyStmt.setString(6, description);
            historyStmt.setString(7, referenceId);
            historyStmt.setString(8, referenceType);
            
            historyStmt.executeUpdate();
            historyStmt.close();
            
            conn.commit();
            return true;
            
        } catch (SQLException e) {
            System.err.println("Error subtracting from balance: " + e.getMessage());
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
     * Get the current balance for a user.
     * 
     * @param userId The user ID
     * @return Current balance, or 0.0 if user not found
     */
    public double getCurrentBalance(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        
        try {
            Connection conn = dbConnection.getConnection();
            return getCurrentBalanceInternal(conn, userId);
        } catch (SQLException e) {
            System.err.println("Error getting current balance: " + e.getMessage());
            e.printStackTrace();
            return 0.0;
        }
    }
    
    /**
     * Internal method to get current balance using existing connection.
     */
    private double getCurrentBalanceInternal(Connection conn, String userId) throws SQLException {
        String sql = "SELECT balance FROM users WHERE user_id = ?";
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setString(1, userId);
        
        ResultSet rs = stmt.executeQuery();
        double balance = 0.0;
        if (rs.next()) {
            balance = rs.getDouble("balance");
        }
        rs.close();
        stmt.close();
        
        return balance;
    }
    
    /**
     * Get balance history for a user, ordered by most recent first.
     * 
     * @param userId The user ID
     * @param limit Optional limit on number of records (null for all)
     * @return List of balance history records
     */
    public List<BalanceHistory> getBalanceHistory(String userId, Integer limit) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        
        List<BalanceHistory> history = new ArrayList<>();
        
        try {
            Connection conn = dbConnection.getConnection();
            String sql = "SELECT history_id, user_id, amount, balance_before, balance_after, " +
                         "transaction_type, description, reference_id, reference_type, created_at " +
                         "FROM balance_history WHERE user_id = ? " +
                         "ORDER BY created_at DESC";
            
            if (limit != null && limit > 0) {
                sql += " LIMIT ?";
            }
            
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, userId);
            if (limit != null && limit > 0) {
                stmt.setInt(2, limit);
            }
            
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                BalanceHistory record = new BalanceHistory(
                    rs.getInt("history_id"),
                    rs.getString("user_id"),
                    rs.getDouble("amount"),
                    rs.getDouble("balance_before"),
                    rs.getDouble("balance_after"),
                    rs.getString("transaction_type"),
                    rs.getString("description"),
                    rs.getString("reference_id"),
                    rs.getString("reference_type"),
                    rs.getTimestamp("created_at")
                );
                history.add(record);
            }
            
            rs.close();
            stmt.close();
            
        } catch (SQLException e) {
            System.err.println("Error getting balance history: " + e.getMessage());
            e.printStackTrace();
        }
        
        return history;
    }
    
    /**
     * Get balance history for a user (all records).
     * 
     * @param userId The user ID
     * @return List of balance history records
     */
    public List<BalanceHistory> getBalanceHistory(String userId) {
        return getBalanceHistory(userId, null);
    }
    
    /**
     * Get balance history filtered by transaction type.
     * 
     * @param userId The user ID
     * @param transactionType The transaction type to filter by
     * @return List of balance history records
     */
    public List<BalanceHistory> getBalanceHistoryByType(String userId, String transactionType) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        if (transactionType == null || transactionType.trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction type cannot be null or empty");
        }
        
        List<BalanceHistory> history = new ArrayList<>();
        
        try {
            Connection conn = dbConnection.getConnection();
            String sql = "SELECT history_id, user_id, amount, balance_before, balance_after, " +
                         "transaction_type, description, reference_id, reference_type, created_at " +
                         "FROM balance_history WHERE user_id = ? AND transaction_type = ? " +
                         "ORDER BY created_at DESC";
            
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, userId);
            stmt.setString(2, transactionType);
            
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                BalanceHistory record = new BalanceHistory(
                    rs.getInt("history_id"),
                    rs.getString("user_id"),
                    rs.getDouble("amount"),
                    rs.getDouble("balance_before"),
                    rs.getDouble("balance_after"),
                    rs.getString("transaction_type"),
                    rs.getString("description"),
                    rs.getString("reference_id"),
                    rs.getString("reference_type"),
                    rs.getTimestamp("created_at")
                );
                history.add(record);
            }
            
            rs.close();
            stmt.close();
            
        } catch (SQLException e) {
            System.err.println("Error getting balance history by type: " + e.getMessage());
            e.printStackTrace();
        }
        
        return history;
    }
}

