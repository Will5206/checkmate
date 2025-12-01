import services.FriendService;
import models.Friend;
import database.DatabaseConnection;
import java.sql.*;
import java.util.List;
import java.util.UUID;

/**
 * Demo script to create test users, make them friends, and verify the database.
 */
public class TestFriendshipDemo {

    public static void main(String[] args) {
        System.out.println("=== Friendship Database Demo ===\n");

        // Initialize database connection
        DatabaseConnection dbConnection = DatabaseConnection.getInstance();

        if (!dbConnection.testConnection()) {
            System.err.println("ERROR: Could not connect to database!");
            System.err.println("Make sure MySQL is running.");
            return;
        }

        System.out.println("✓ Database connection successful\n");

        try (Connection conn = dbConnection.getConnection()) {

            // Create test users
            System.out.println("--- Creating Test Users ---");

            // User 1
            String user1Id = createOrGetUser(conn, "testuser1@checkmate.com", "Test User 1", "+15555555551", "password");
            System.out.println("User 1 ID: " + user1Id);
            System.out.println("  Email: testuser1@checkmate.com");
            System.out.println("  Password: password\n");

            // User 2 (new user)
            String user2Id = createOrGetUser(conn, "testuser2@checkmate.com", "Test User 2", "+15555555552", "password");
            System.out.println("User 2 ID: " + user2Id);
            System.out.println("  Email: testuser2@checkmate.com");
            System.out.println("  Password: password\n");

            // Make them friends
            System.out.println("--- Creating Friendship ---");
            FriendService friendService = new FriendService();
            boolean added = friendService.addFriend(user1Id, user2Id);

            if (added) {
                System.out.println("✓ Friendship created successfully!\n");
            } else {
                System.out.println("⚠ Friendship already existed\n");
            }

            // Verify friendship
            System.out.println("--- Verifying Friendship ---");
            Friend friendship = friendService.getFriendship(user1Id, user2Id);

            if (friendship != null) {
                System.out.println("✓ Friendship found in database:");
                System.out.println("  Friendship ID: " + friendship.getFriendshipId());
                System.out.println("  User 1: " + friendship.getUserId1());
                System.out.println("  User 2: " + friendship.getUserId2());
                System.out.println("  Status: " + friendship.getStatus());
                System.out.println("  Created: " + friendship.getCreatedAt());
                System.out.println();
            }

            // List friends for user 1
            System.out.println("--- Friends for testuser1@checkmate.com ---");
            List<String> user1Friends = friendService.listFriends(user1Id);
            System.out.println("Total friends: " + user1Friends.size());
            for (String friendId : user1Friends) {
                String friendEmail = getUserEmail(conn, friendId);
                System.out.println("  - " + friendEmail + " (" + friendId + ")");
            }
            System.out.println();

            // List friends for user 2
            System.out.println("--- Friends for testuser2@checkmate.com ---");
            List<String> user2Friends = friendService.listFriends(user2Id);
            System.out.println("Total friends: " + user2Friends.size());
            for (String friendId : user2Friends) {
                String friendEmail = getUserEmail(conn, friendId);
                System.out.println("  - " + friendEmail + " (" + friendId + ")");
            }
            System.out.println();

            // Print entire friendships table
            System.out.println("--- Complete Friendships Table ---");
            printFriendshipsTable(conn);

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n=== Demo Complete ===");
    }

    /**
     * Create a user or get existing user ID
     */
    private static String createOrGetUser(Connection conn, String email, String name, String phone, String password) throws SQLException {
        // Check if user already exists
        String checkSql = "SELECT user_id FROM users WHERE email = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(checkSql)) {
            pstmt.setString(1, email);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    System.out.println("  (User already exists)");
                    return rs.getString("user_id");
                }
            }
        }

        // Create new user
        String userId = UUID.randomUUID().toString();
        String insertSql = "INSERT INTO users (user_id, name, email, phone_number, password_hash) VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
            pstmt.setString(1, userId);
            pstmt.setString(2, name);
            pstmt.setString(3, email);
            pstmt.setString(4, phone);
            pstmt.setString(5, hashPassword(password)); // Simple hash for demo
            pstmt.executeUpdate();
            System.out.println("  ✓ New user created");
        }

        return userId;
    }

    /**
     * Simple password hashing (in production, use proper hashing like BCrypt)
     */
    private static String hashPassword(String password) {
        return "hashed_" + password; // Placeholder - use proper hashing in production
    }

    /**
     * Get user email by ID
     */
    private static String getUserEmail(Connection conn, String userId) throws SQLException {
        String sql = "SELECT email FROM users WHERE user_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("email");
                }
            }
        }
        return "Unknown";
    }

    /**
     * Print the entire friendships table
     */
    private static void printFriendshipsTable(Connection conn) throws SQLException {
        String sql = "SELECT f.friendship_id, f.user_id_1, f.user_id_2, f.status, f.created_at, " +
                     "u1.email as email1, u2.email as email2 " +
                     "FROM friendships f " +
                     "JOIN users u1 ON f.user_id_1 = u1.user_id " +
                     "JOIN users u2 ON f.user_id_2 = u2.user_id " +
                     "ORDER BY f.created_at DESC";

        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            System.out.println("┌─────────────┬───────────────────────────┬───────────────────────────┬──────────┬─────────────────────┐");
            System.out.println("│ Friendship  │ User 1 Email              │ User 2 Email              │ Status   │ Created At          │");
            System.out.println("├─────────────┼───────────────────────────┼───────────────────────────┼──────────┼─────────────────────┤");

            int count = 0;
            while (rs.next()) {
                count++;
                int friendshipId = rs.getInt("friendship_id");
                String email1 = rs.getString("email1");
                String email2 = rs.getString("email2");
                String status = rs.getString("status");
                Timestamp createdAt = rs.getTimestamp("created_at");

                System.out.printf("│ %-11d │ %-25s │ %-25s │ %-8s │ %-19s │%n",
                    friendshipId,
                    truncate(email1, 25),
                    truncate(email2, 25),
                    status,
                    createdAt.toString().substring(0, 19)
                );
            }

            System.out.println("└─────────────┴───────────────────────────┴───────────────────────────┴──────────┴─────────────────────┘");
            System.out.println("Total friendships: " + count);
        }
    }

    /**
     * Truncate string to max length
     */
    private static String truncate(String str, int maxLength) {
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }
}
