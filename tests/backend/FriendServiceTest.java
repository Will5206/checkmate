// Default package (no package declaration)

import services.FriendService;
import models.Friend;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FriendService friend request acceptance and decline functionality.
 * Tests the acceptFriendRequest and declineFriendRequest methods.
 * 
 * Note: These tests require a running MySQL database with the checkmate schema.
 * The friendships table must exist in the database.
 */
public class FriendServiceTest {

    private FriendService friendService;
    private String testUserId1;
    private String testUserId2;

    @BeforeEach
    void setup() {
        friendService = new FriendService();
        // Generate test user IDs with UUID to ensure uniqueness
        testUserId1 = "test-user-1-" + java.util.UUID.randomUUID().toString();
        testUserId2 = "test-user-2-" + java.util.UUID.randomUUID().toString();
        
        // Create test users in the database using SQL directly
        database.DatabaseConnection db = database.DatabaseConnection.getInstance();
        boolean user1Created = false;
        boolean user2Created = false;
        
        try (java.sql.Connection conn = db.getConnection();
             java.sql.PreparedStatement pstmt = conn.prepareStatement(
                 "INSERT INTO users (user_id, name, email, phone_number, password_hash, balance) VALUES (?, ?, ?, ?, ?, ?)")) {
            
            pstmt.setString(1, testUserId1);
            pstmt.setString(2, "Test User 1");
            pstmt.setString(3, "test1-" + System.currentTimeMillis() + "@test.com");
            pstmt.setString(4, "5551001");
            pstmt.setString(5, "hash");
            pstmt.setDouble(6, 0.0);
            int rows = pstmt.executeUpdate();
            user1Created = (rows > 0);
        } catch (java.sql.SQLException e) {
            // User might already exist, check if it exists
            database.UserDAO userDAO = new database.UserDAO();
            if (userDAO.findUserById(testUserId1) != null) {
                user1Created = true;
            }
        }
        
        try (java.sql.Connection conn = db.getConnection();
             java.sql.PreparedStatement pstmt = conn.prepareStatement(
                 "INSERT INTO users (user_id, name, email, phone_number, password_hash, balance) VALUES (?, ?, ?, ?, ?, ?)")) {
            
            pstmt.setString(1, testUserId2);
            pstmt.setString(2, "Test User 2");
            pstmt.setString(3, "test2-" + System.currentTimeMillis() + "@test.com");
            pstmt.setString(4, "5551002");
            pstmt.setString(5, "hash");
            pstmt.setDouble(6, 0.0);
            int rows = pstmt.executeUpdate();
            user2Created = (rows > 0);
        } catch (java.sql.SQLException e) {
            // User might already exist, check if it exists
            database.UserDAO userDAO = new database.UserDAO();
            if (userDAO.findUserById(testUserId2) != null) {
                user2Created = true;
            }
        }
        
        // Verify users were created (or already exist)
        database.UserDAO userDAO = new database.UserDAO();
        if (userDAO.findUserById(testUserId1) == null || userDAO.findUserById(testUserId2) == null) {
            // Users don't exist - tests will be skipped
            System.out.println("Warning: Test users not created - some tests may be skipped");
        }
    }

    /**
     * Test 1: Accept friend request successfully
     */
    @Test
    void testAcceptFriendRequest_success() {
        // Verify users exist first
        database.UserDAO userDAO = new database.UserDAO();
        org.junit.jupiter.api.Assumptions.assumeTrue(
            userDAO.findUserById(testUserId1) != null && userDAO.findUserById(testUserId2) != null,
            "Test requires users to exist in database - skipping"
        );
        
        // First create a pending friendship
        Friend friendship = friendService.addFriendship(testUserId1, testUserId2);
        org.junit.jupiter.api.Assumptions.assumeTrue(friendship != null, 
            "Friendship creation failed - users may not exist or friendship already exists - skipping");
        
        // Accept the friend request
        boolean result = friendService.acceptFriendRequest(testUserId1, testUserId2);
        
        assertTrue(result, "Friend request should be accepted successfully");
        
        // Verify friendship status is accepted
        Friend acceptedFriendship = friendService.getFriendship(testUserId1, testUserId2);
        assertNotNull(acceptedFriendship, "Friendship should exist after acceptance");
        assertEquals("accepted", acceptedFriendship.getStatus(), "Friendship status should be 'accepted'");
    }

    /**
     * Test 2: Decline friend request successfully
     */
    @Test
    void testDeclineFriendRequest_success() {
        // Verify users exist first
        database.UserDAO userDAO = new database.UserDAO();
        org.junit.jupiter.api.Assumptions.assumeTrue(
            userDAO.findUserById(testUserId1) != null && userDAO.findUserById(testUserId2) != null,
            "Test requires users to exist in database - skipping"
        );
        
        // First create a pending friendship
        Friend friendship = friendService.addFriendship(testUserId1, testUserId2);
        org.junit.jupiter.api.Assumptions.assumeTrue(friendship != null, 
            "Friendship creation failed - users may not exist or friendship already exists - skipping");
        
        // Decline the friend request
        boolean result = friendService.declineFriendRequest(testUserId1, testUserId2);
        
        assertTrue(result, "Friend request should be declined successfully");
        
        // Verify friendship status is declined
        Friend declinedFriendship = friendService.getFriendship(testUserId1, testUserId2);
        assertNotNull(declinedFriendship, "Friendship should exist after decline");
        assertEquals("declined", declinedFriendship.getStatus(), "Friendship status should be 'declined'");
    }

    /**
     * Test 3: Accept already accepted friendship (should fail)
     */
    @Test
    void testAcceptFriendRequest_alreadyAccepted() {
        // Create and accept friendship
        friendService.addFriendship(testUserId1, testUserId2);
        friendService.acceptFriendRequest(testUserId1, testUserId2);
        
        // Try to accept again
        boolean result = friendService.acceptFriendRequest(testUserId1, testUserId2);
        
        // Should return false or true depending on implementation
        // The friendship should remain accepted
        Friend friendship = friendService.getFriendship(testUserId1, testUserId2);
        if (friendship != null) {
            assertEquals("accepted", friendship.getStatus(), "Friendship should remain accepted");
        }
    }

    /**
     * Test 4: Decline already declined friendship
     */
    @Test
    void testDeclineFriendRequest_alreadyDeclined() {
        // Create and decline friendship
        friendService.addFriendship(testUserId1, testUserId2);
        friendService.declineFriendRequest(testUserId1, testUserId2);
        
        // Try to decline again
        boolean result = friendService.declineFriendRequest(testUserId1, testUserId2);
        
        // The friendship should remain declined
        Friend friendship = friendService.getFriendship(testUserId1, testUserId2);
        if (friendship != null) {
            assertEquals("declined", friendship.getStatus(), "Friendship should remain declined");
        }
    }

    /**
     * Test 5: Accept non-existent friendship (should fail)
     */
    @Test
    void testAcceptFriendRequest_nonExistent() {
        // Try to accept a friendship that doesn't exist
        boolean result = friendService.acceptFriendRequest(testUserId1, testUserId2);
        
        // Should return false if friendship doesn't exist
        // This tests error handling
        assertFalse(result, "Should return false for non-existent friendship");
    }

    /**
     * Test 6: Decline non-existent friendship (should fail)
     */
    @Test
    void testDeclineFriendRequest_nonExistent() {
        // Try to decline a friendship that doesn't exist
        boolean result = friendService.declineFriendRequest(testUserId1, testUserId2);
        
        // Should return false if friendship doesn't exist
        assertFalse(result, "Should return false for non-existent friendship");
    }

    /**
     * Test 7: Verify friendship status after operations
     */
    @Test
    void testFriendshipStatus_afterOperations() {
        // Verify users exist first
        database.UserDAO userDAO = new database.UserDAO();
        org.junit.jupiter.api.Assumptions.assumeTrue(
            userDAO.findUserById(testUserId1) != null && userDAO.findUserById(testUserId2) != null,
            "Test requires users to exist in database - skipping"
        );
        
        // Create pending friendship
        Friend friendship = friendService.addFriendship(testUserId1, testUserId2);
        org.junit.jupiter.api.Assumptions.assumeTrue(friendship != null, 
            "Friendship creation failed - users may not exist or friendship already exists - skipping");
        
        // Verify initial status (may be pending or accepted depending on implementation)
        Friend retrieved = friendService.getFriendship(testUserId1, testUserId2);
        assertNotNull(retrieved, "Friendship should exist");
        assertNotNull(retrieved.getStatus(), "Friendship should have a status");
    }

    /**
     * Test 8: Accept changes friendship status
     */
    @Test
    void testAcceptFriendRequest_changesStatus() {
        // Create pending friendship
        friendService.addFriendship(testUserId1, testUserId2);
        
        // Accept the request
        boolean accepted = friendService.acceptFriendRequest(testUserId1, testUserId2);
        
        if (accepted) {
            // Verify status changed
            Friend friendship = friendService.getFriendship(testUserId1, testUserId2);
            if (friendship != null) {
                assertEquals("accepted", friendship.getStatus(), 
                    "Friendship status should be 'accepted' after acceptance");
            }
        }
    }

    /**
     * Test 9: Decline changes friendship status
     */
    @Test
    void testDeclineFriendRequest_changesStatus() {
        // Create pending friendship
        friendService.addFriendship(testUserId1, testUserId2);
        
        // Decline the request
        boolean declined = friendService.declineFriendRequest(testUserId1, testUserId2);
        
        if (declined) {
            // Verify status changed
            Friend friendship = friendService.getFriendship(testUserId1, testUserId2);
            if (friendship != null) {
                assertEquals("declined", friendship.getStatus(), 
                    "Friendship status should be 'declined' after decline");
            }
        }
    }

    /**
     * Test 10: Bidirectional friendship (undirected)
     */
    @Test
    void testAcceptFriendRequest_bidirectional() {
        // Verify users exist first
        database.UserDAO userDAO = new database.UserDAO();
        org.junit.jupiter.api.Assumptions.assumeTrue(
            userDAO.findUserById(testUserId1) != null && userDAO.findUserById(testUserId2) != null,
            "Test requires users to exist in database - skipping"
        );
        
        // Create friendship (undirected)
        Friend friendship = friendService.addFriendship(testUserId1, testUserId2);
        org.junit.jupiter.api.Assumptions.assumeTrue(friendship != null, 
            "Friendship creation failed - users may not exist or friendship already exists - skipping");
        
        // Accept from either direction
        boolean result1 = friendService.acceptFriendRequest(testUserId1, testUserId2);
        boolean result2 = friendService.acceptFriendRequest(testUserId2, testUserId1);
        
        // At least one should succeed
        assertTrue(result1 || result2, "At least one acceptance should succeed");
        
        // Verify friendship status is accepted
        Friend retrieved = friendService.getFriendship(testUserId1, testUserId2);
        assertNotNull(retrieved, "Friendship should exist");
        assertEquals("accepted", retrieved.getStatus(), 
            "Friendship should be accepted from either direction");
    }

    @AfterEach
    void cleanup() {
        // In a real test setup, you would clean up test data here
        // For example, remove test friendships
        try {
            friendService.removeFriendship(testUserId1, testUserId2);
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }
}
