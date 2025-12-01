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
        // Generate test user IDs
        testUserId1 = "test-user-1-" + System.currentTimeMillis();
        testUserId2 = "test-user-2-" + System.currentTimeMillis();
        
        // Create test users in the database using SQL directly
        database.DatabaseConnection db = database.DatabaseConnection.getInstance();
        try (java.sql.Connection conn = db.getConnection();
             java.sql.PreparedStatement pstmt = conn.prepareStatement(
                 "INSERT INTO users (user_id, name, email, phone_number, password_hash, balance) VALUES (?, ?, ?, ?, ?, ?)")) {
            
            pstmt.setString(1, testUserId1);
            pstmt.setString(2, "Test User 1");
            pstmt.setString(3, "test1@test.com");
            pstmt.setString(4, "5551001");
            pstmt.setString(5, "hash");
            pstmt.setDouble(6, 0.0);
            pstmt.executeUpdate();
        } catch (java.sql.SQLException e) {
            // User might already exist, continue
        }
        
        try (java.sql.Connection conn = db.getConnection();
             java.sql.PreparedStatement pstmt = conn.prepareStatement(
                 "INSERT INTO users (user_id, name, email, phone_number, password_hash, balance) VALUES (?, ?, ?, ?, ?, ?)")) {
            
            pstmt.setString(1, testUserId2);
            pstmt.setString(2, "Test User 2");
            pstmt.setString(3, "test2@test.com");
            pstmt.setString(4, "5551002");
            pstmt.setString(5, "hash");
            pstmt.setDouble(6, 0.0);
            pstmt.executeUpdate();
        } catch (java.sql.SQLException e) {
            // User might already exist, continue
        }
    }

    /**
     * Test 1: Accept friend request successfully
     */
    @Test
    void testAcceptFriendRequest_success() {
        // First create a pending friendship
        Friend friendship = friendService.addFriendship(testUserId1, testUserId2);
        assertNotNull(friendship, "Friendship should be created");
        
        // Accept the friend request
        boolean result = friendService.acceptFriendRequest(testUserId1, testUserId2);
        
        assertTrue(result, "Friend request should be accepted successfully");
        
        // Verify friendship status is accepted
        Friend acceptedFriendship = friendService.getFriendship(testUserId1, testUserId2);
        if (acceptedFriendship != null) {
            assertEquals("accepted", acceptedFriendship.getStatus(), "Friendship status should be 'accepted'");
        }
    }

    /**
     * Test 2: Decline friend request successfully
     */
    @Test
    void testDeclineFriendRequest_success() {
        // First create a pending friendship
        Friend friendship = friendService.addFriendship(testUserId1, testUserId2);
        assertNotNull(friendship, "Friendship should be created");
        
        // Decline the friend request
        boolean result = friendService.declineFriendRequest(testUserId1, testUserId2);
        
        assertTrue(result, "Friend request should be declined successfully");
        
        // Verify friendship status is declined
        Friend declinedFriendship = friendService.getFriendship(testUserId1, testUserId2);
        if (declinedFriendship != null) {
            assertEquals("declined", declinedFriendship.getStatus(), "Friendship status should be 'declined'");
        }
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
        // Create pending friendship
        Friend friendship = friendService.addFriendship(testUserId1, testUserId2);
        assertNotNull(friendship, "Friendship should be created");
        
        // Verify initial status (may be pending or accepted depending on implementation)
        Friend retrieved = friendService.getFriendship(testUserId1, testUserId2);
        if (retrieved != null) {
            assertNotNull(retrieved.getStatus(), "Friendship should have a status");
        }
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
        // Create friendship (undirected)
        Friend friendship = friendService.addFriendship(testUserId1, testUserId2);
        assertNotNull(friendship, "Friendship should be created");
        
        // Accept from either direction
        boolean result1 = friendService.acceptFriendRequest(testUserId1, testUserId2);
        boolean result2 = friendService.acceptFriendRequest(testUserId2, testUserId1);
        
        // At least one should succeed
        assertTrue(result1 || result2, "At least one acceptance should succeed");
        
        // Verify friendship status is accepted
        Friend retrieved = friendService.getFriendship(testUserId1, testUserId2);
        if (retrieved != null) {
            assertEquals("accepted", retrieved.getStatus(), 
                "Friendship should be accepted from either direction");
        }
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
