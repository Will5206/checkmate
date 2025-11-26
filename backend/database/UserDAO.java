package database;

import models.User;
import java.sql.*;

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
}
