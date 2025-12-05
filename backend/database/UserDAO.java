package database;

import models.User;
import java.sql.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object for User operations.
 * Centralizes all user-related database queries.
 */
public class UserDAO {
    private final DatabaseConnection dbConnection;

    public UserDAO() {
        this.dbConnection = DatabaseConnection.getInstance();
    }

    /**
     * Find user by email address
     * @param email User's email address
     * @return User object if found, null otherwise
     */
    public User findUserByEmail(String email) {
        String sql = "SELECT * FROM users WHERE email = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, email);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new User(
                        rs.getString("user_id"),
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getString("phone_number"),
                        rs.getString("password_hash"),
                        rs.getDouble("balance"),
                        rs.getTimestamp("created_at"),
                        rs.getTimestamp("updated_at")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("Error finding user by email: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Find user by user ID
     * @param userId User's UUID
     * @return User object if found, null otherwise
     */
    public User findUserById(String userId) {
        String sql = "SELECT * FROM users WHERE user_id = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, userId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new User(
                        rs.getString("user_id"),
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getString("phone_number"),
                        rs.getString("password_hash"),
                        rs.getDouble("balance"),
                        rs.getTimestamp("created_at"),
                        rs.getTimestamp("updated_at")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("Error finding user by ID: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }
    
    /**
     * OPTIMIZED: Batch find users by email addresses in a single database query.
     * This is much faster than looking up users one by one.
     * 
     * @param emails List of email addresses to lookup
     * @return Map of email (lowercase) -> User object for found users
     */
    public Map<String, User> findUsersByEmailsBatch(List<String> emails) {
        Map<String, User> usersMap = new HashMap<>();
        
        if (emails == null || emails.isEmpty()) {
            return usersMap;
        }
        
        // Build SQL with IN clause for batch lookup
        StringBuilder sqlBuilder = new StringBuilder("SELECT * FROM users WHERE email IN (");
        for (int i = 0; i < emails.size(); i++) {
            if (i > 0) sqlBuilder.append(", ");
            sqlBuilder.append("?");
        }
        sqlBuilder.append(")");
        
        String sql = sqlBuilder.toString();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            // Set all email parameters (normalize to lowercase)
            for (int i = 0; i < emails.size(); i++) {
                pstmt.setString(i + 1, emails.get(i).trim().toLowerCase());
            }
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    User user = new User(
                        rs.getString("user_id"),
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getString("phone_number"),
                        rs.getString("password_hash"),
                        rs.getDouble("balance"),
                        rs.getTimestamp("created_at"),
                        rs.getTimestamp("updated_at")
                    );
                    // Use lowercase email as key for easy lookup
                    usersMap.put(user.getEmail().toLowerCase(), user);
                }
            }
            
            System.out.println("[UserDAO] Batch found " + usersMap.size() + " users for " + emails.size() + " emails");
            
        } catch (SQLException e) {
            System.err.println("Error batch finding users by emails: " + e.getMessage());
            e.printStackTrace();
        }
        
        return usersMap;
    }
}
