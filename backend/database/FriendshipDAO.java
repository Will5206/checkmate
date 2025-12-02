package database;

import models.Friend;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for managing friendship relationships in the database.
 * Handles all CRUD operations for the friendships table.
 */
public class FriendshipDAO {

    private final DatabaseConnection dbConnection;

    /**
     * Constructor that gets the database connection instance
     */
    public FriendshipDAO() {
        this.dbConnection = DatabaseConnection.getInstance();
    }

    /**
     * Add a new friendship between two users.
     * Auto-accepts the friendship immediately (no approval needed).
     *
     * @param userId1 First user's ID
     * @param userId2 Second user's ID
     * @return The created Friend object, or null if creation failed
     */
    public Friend addFriendship(String userId1, String userId2) {
        String sql = "INSERT INTO friendships (user_id_1, user_id_2, status) VALUES (?, ?, 'accepted')";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            // Always store with smaller ID first for consistency
            String smallerId = userId1.compareTo(userId2) < 0 ? userId1 : userId2;
            String largerId = userId1.compareTo(userId2) < 0 ? userId2 : userId1;

            pstmt.setString(1, smallerId);
            pstmt.setString(2, largerId);

            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int friendshipId = generatedKeys.getInt(1);
                        return getFriendshipById(friendshipId);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error adding friendship: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Get a specific friendship by its ID.
     *
     * @param friendshipId The friendship ID
     * @return Friend object or null if not found
     */
    public Friend getFriendshipById(int friendshipId) {
        String sql = "SELECT * FROM friendships WHERE friendship_id = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, friendshipId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToFriend(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting friendship by ID: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Get friendship between two specific users.
     *
     * @param userId1 First user's ID
     * @param userId2 Second user's ID
     * @return Friend object or null if not found
     */
    public Friend getFriendship(String userId1, String userId2) {
        String sql = "SELECT * FROM friendships WHERE " +
                     "(user_id_1 = ? AND user_id_2 = ?) OR " +
                     "(user_id_1 = ? AND user_id_2 = ?)";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, userId1);
            pstmt.setString(2, userId2);
            pstmt.setString(3, userId2);
            pstmt.setString(4, userId1);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToFriend(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting friendship: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Get all friendships for a specific user.
     *
     * @param userId The user's ID
     * @return List of Friend objects
     */
    public List<Friend> listFriendships(String userId) {
        String sql = "SELECT * FROM friendships WHERE user_id_1 = ? OR user_id_2 = ?";
        List<Friend> friendships = new ArrayList<>();

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, userId);
            pstmt.setString(2, userId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    friendships.add(mapResultSetToFriend(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error listing friendships: " + e.getMessage());
            e.printStackTrace();
        }

        return friendships;
    }

    /**
     * Get all accepted friends for a specific user.
     *
     * @param userId The user's ID
     * @return List of friend user IDs
     */
    public List<String> listFriendIds(String userId) {
        String sql = "SELECT user_id_1, user_id_2 FROM friendships " +
                     "WHERE (user_id_1 = ? OR user_id_2 = ?) AND status = 'accepted'";
        List<String> friendIds = new ArrayList<>();

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, userId);
            pstmt.setString(2, userId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String userId1 = rs.getString("user_id_1");
                    String userId2 = rs.getString("user_id_2");

                    // Add the friend's ID (the one that's not the current user)
                    if (userId1.equals(userId)) {
                        friendIds.add(userId2);
                    } else {
                        friendIds.add(userId1);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error listing friend IDs: " + e.getMessage());
            e.printStackTrace();
        }

        return friendIds;
    }

    /**
     * Update the status of a friendship.
     *
     * @param userId1 First user's ID
     * @param userId2 Second user's ID
     * @param status New status ('pending', 'accepted', 'declined')
     * @return true if update was successful
     */
    public boolean updateFriendshipStatus(String userId1, String userId2, String status) {
        String sql = "UPDATE friendships SET status = ? WHERE " +
                     "(user_id_1 = ? AND user_id_2 = ?) OR " +
                     "(user_id_1 = ? AND user_id_2 = ?)";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, status);
            pstmt.setString(2, userId1);
            pstmt.setString(3, userId2);
            pstmt.setString(4, userId2);
            pstmt.setString(5, userId1);

            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;

        } catch (SQLException e) {
            System.err.println("Error updating friendship status: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Remove a friendship between two users.
     *
     * @param userId1 First user's ID
     * @param userId2 Second user's ID
     * @return true if deletion was successful
     */
    public boolean removeFriendship(String userId1, String userId2) {
        System.out.println("🔴 [5.5/8] FriendshipDAO.removeFriendship: userId1=" + userId1 + ", userId2=" + userId2);
        String sql = "DELETE FROM friendships WHERE " +
                     "(user_id_1 = ? AND user_id_2 = ?) OR " +
                     "(user_id_1 = ? AND user_id_2 = ?)";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, userId1);
            pstmt.setString(2, userId2);
            pstmt.setString(3, userId2);
            pstmt.setString(4, userId1);

            System.out.println("🔴 [5.7/8] FriendshipDAO: Executing SQL DELETE query");
            int affectedRows = pstmt.executeUpdate();
            System.out.println("🔴 [5.9/8] FriendshipDAO: affectedRows=" + affectedRows);
            return affectedRows > 0;

        } catch (SQLException e) {
            System.err.println("Error removing friendship: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Check if a friendship exists between two users.
     *
     * @param userId1 First user's ID
     * @param userId2 Second user's ID
     * @return true if friendship exists
     */
    public boolean friendshipExists(String userId1, String userId2) {
        return getFriendship(userId1, userId2) != null;
    }

    /**
     * Helper method to map a ResultSet row to a Friend object.
     *
     * @param rs ResultSet positioned at a friendship row
     * @return Friend object
     * @throws SQLException if database access error occurs
     */
    private Friend mapResultSetToFriend(ResultSet rs) throws SQLException {
        int friendshipId = rs.getInt("friendship_id");
        String userId1 = rs.getString("user_id_1");
        String userId2 = rs.getString("user_id_2");
        String status = rs.getString("status");
        Timestamp createdAt = rs.getTimestamp("created_at");

        return new Friend(friendshipId, userId1, userId2,
                         new java.util.Date(createdAt.getTime()), status);
    }
}
