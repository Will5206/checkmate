// Default package (no package declaration)

import models.Receipt;
import models.ReceiptItem;
import database.ReceiptDAO;
import database.DatabaseConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ReceiptDAO item loading functionality.
 * 
 * Tests that receipt items are properly loaded when fetching receipts.
 * This validates the fix where items are added using addItem() method
 * instead of getItems().addAll() to ensure items are actually persisted
 * in the Receipt object.
 * 
 * NOTE: These tests require a running MySQL database with the checkmate schema.
 */
public class ReceiptDAOItemLoadingTest {

    private ReceiptDAO receiptDAO;
    private DatabaseConnection dbConnection;
    private String testUserId1;
    private String testUserId2;
    private int testReceiptId1;
    private int testReceiptId2;
    private int testItemId1;
    private int testItemId2;
    private int testItemId3;

    @BeforeEach
    void setup() {
        receiptDAO = new ReceiptDAO();
        dbConnection = DatabaseConnection.getInstance();
        
        // Generate unique test user IDs
        testUserId1 = "test-user-" + UUID.randomUUID().toString();
        testUserId2 = "test-user-2-" + UUID.randomUUID().toString();
        
        // Create test users in database
        createTestUser(testUserId1, "Test User 1", "test1@example.com", "5550001");
        createTestUser(testUserId2, "Test User 2", "test2@example.com", "5550002");
        
        // Create test receipts with items
        testReceiptId1 = createTestReceiptWithItems(testUserId1, "Test Store 1");
        testReceiptId2 = createTestReceiptWithItems(testUserId2, "Test Store 2");
        
        Assumptions.assumeTrue(testReceiptId1 > 0, "Receipt 1 creation failed - database may not be set up");
        Assumptions.assumeTrue(testReceiptId2 > 0, "Receipt 2 creation failed - database may not be set up");
    }

    @AfterEach
    void cleanup() {
        // Clean up test data
        try (Connection conn = dbConnection.getConnection()) {
            // Delete item assignments
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "DELETE FROM item_assignments WHERE receipt_id IN (?, ?)")) {
                pstmt.setInt(1, testReceiptId1);
                pstmt.setInt(2, testReceiptId2);
                pstmt.executeUpdate();
            }
            
            // Delete receipt items
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "DELETE FROM receipt_items WHERE receipt_id IN (?, ?)")) {
                pstmt.setInt(1, testReceiptId1);
                pstmt.setInt(2, testReceiptId2);
                pstmt.executeUpdate();
            }
            
            // Delete receipt participants
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "DELETE FROM receipt_participants WHERE receipt_id IN (?, ?)")) {
                pstmt.setInt(1, testReceiptId1);
                pstmt.setInt(2, testReceiptId2);
                pstmt.executeUpdate();
            }
            
            // Delete receipts
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "DELETE FROM receipts WHERE receipt_id IN (?, ?)")) {
                pstmt.setInt(1, testReceiptId1);
                pstmt.setInt(2, testReceiptId2);
                pstmt.executeUpdate();
            }
            
            // Delete test users
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "DELETE FROM users WHERE user_id IN (?, ?)")) {
                pstmt.setString(1, testUserId1);
                pstmt.setString(2, testUserId2);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Error cleaning up test data: " + e.getMessage());
        }
    }

    /**
     * Test that getReceiptById properly loads items into the Receipt object.
     * This tests the fix where items are added using addItem() method.
     */
    @Test
    void testGetReceiptById_LoadsItems() {
        // Get receipt by ID
        Receipt receipt = receiptDAO.getReceiptById(testReceiptId1);
        
        assertNotNull(receipt, "Receipt should be found");
        assertEquals(testReceiptId1, receipt.getReceiptId(), "Receipt ID should match");
        
        // Verify items are loaded
        List<ReceiptItem> items = receipt.getItems();
        assertNotNull(items, "Items list should not be null");
        assertEquals(2, items.size(), "Receipt should have 2 items loaded");
        
        // Verify item details
        ReceiptItem item1 = items.get(0);
        assertNotNull(item1, "First item should not be null");
        assertEquals("Test Item 1", item1.getName(), "Item 1 name should match");
        assertEquals(10.50f, item1.getPrice(), 0.01f, "Item 1 price should match");
        assertEquals(1, item1.getQuantity(), "Item 1 quantity should match");
        assertEquals(testReceiptId1, item1.getReceiptId(), "Item 1 receipt ID should match");
        
        ReceiptItem item2 = items.get(1);
        assertNotNull(item2, "Second item should not be null");
        assertEquals("Test Item 2", item2.getName(), "Item 2 name should match");
        assertEquals(15.75f, item2.getPrice(), 0.01f, "Item 2 price should match");
        assertEquals(2, item2.getQuantity(), "Item 2 quantity should match");
    }

    /**
     * Test that getAllReceiptsForUser properly loads items for all receipts.
     * This tests the fix in getAllReceiptsForUser method.
     */
    @Test
    void testGetAllReceiptsForUser_LoadsItems() {
        // Get all receipts for user 1 (should include receipt 1)
        List<Receipt> receipts = receiptDAO.getAllReceiptsForUser(testUserId1);
        
        assertNotNull(receipts, "Receipts list should not be null");
        assertTrue(receipts.size() >= 1, "Should have at least 1 receipt for user 1");
        
        // Find our test receipt
        Receipt receipt = receipts.stream()
                .filter(r -> r.getReceiptId() == testReceiptId1)
                .findFirst()
                .orElse(null);
        
        assertNotNull(receipt, "Test receipt should be in the list");
        
        // Verify items are loaded
        List<ReceiptItem> items = receipt.getItems();
        assertNotNull(items, "Items list should not be null");
        assertEquals(2, items.size(), "Receipt should have 2 items loaded");
        
        // Verify we can access items multiple times (tests defensive copy works)
        List<ReceiptItem> items2 = receipt.getItems();
        assertEquals(items.size(), items2.size(), "Getting items twice should return same count");
        assertEquals(items.get(0).getName(), items2.get(0).getName(), "Items should match");
    }

    /**
     * Test that getPendingReceiptsForUser properly loads items.
     * This tests the fix in getPendingReceiptsForUser method.
     */
    @Test
    void testGetPendingReceiptsForUser_LoadsItems() {
        // Add user 2 as participant to receipt 1
        receiptDAO.addReceiptParticipant(testReceiptId1, testUserId2);
        
        // Get pending receipts for user 2
        List<Receipt> receipts = receiptDAO.getPendingReceiptsForUser(testUserId2);
        
        assertNotNull(receipts, "Receipts list should not be null");
        assertTrue(receipts.size() >= 1, "Should have at least 1 pending receipt");
        
        // Find our test receipt
        Receipt receipt = receipts.stream()
                .filter(r -> r.getReceiptId() == testReceiptId1)
                .findFirst()
                .orElse(null);
        
        assertNotNull(receipt, "Test receipt should be in pending receipts");
        
        // Verify items are loaded
        List<ReceiptItem> items = receipt.getItems();
        assertNotNull(items, "Items list should not be null");
        assertEquals(2, items.size(), "Receipt should have 2 items loaded");
    }

    /**
     * Test that items are properly loaded even when receipt has many items.
     */
    @Test
    void testGetReceiptById_LoadsMultipleItems() {
        // Create a receipt with more items
        int receiptId = createTestReceiptWithMultipleItems(testUserId1, "Multi-Item Store", 5);
        
        Assumptions.assumeTrue(receiptId > 0, "Receipt creation failed");
        
        try {
            // Get receipt
            Receipt receipt = receiptDAO.getReceiptById(receiptId);
            assertNotNull(receipt, "Receipt should be found");
            
            // Verify all items are loaded
            List<ReceiptItem> items = receipt.getItems();
            assertNotNull(items, "Items list should not be null");
            assertEquals(5, items.size(), "Receipt should have 5 items loaded");
            
            // Verify each item
            for (int i = 0; i < items.size(); i++) {
                ReceiptItem item = items.get(i);
                assertNotNull(item, "Item " + i + " should not be null");
                assertTrue(item.getItemId() > 0, "Item " + i + " should have valid ID");
                assertEquals("Item " + (i + 1), item.getName(), "Item " + i + " name should match");
                assertEquals(receiptId, item.getReceiptId(), "Item " + i + " receipt ID should match");
            }
        } finally {
            // Clean up
            cleanupReceipt(receiptId);
        }
    }

    /**
     * Test that receipt with no items returns empty list (not null).
     */
    @Test
    void testGetReceiptById_NoItems() {
        // Create receipt without items
        Receipt receipt = receiptDAO.createReceipt(
                testUserId1, "Empty Store", new Date(), 0.0f, 0.0f, 0.0f, "url"
        );
        
        Assumptions.assumeTrue(receipt != null, "Receipt creation failed");
        
        try {
            int receiptId = receipt.getReceiptId();
            
            // Get receipt
            Receipt retrieved = receiptDAO.getReceiptById(receiptId);
            assertNotNull(retrieved, "Receipt should be found");
            
            // Verify items list is not null but empty
            List<ReceiptItem> items = retrieved.getItems();
            assertNotNull(items, "Items list should not be null");
            assertEquals(0, items.size(), "Receipt should have 0 items");
        } finally {
            // Clean up
            if (receipt != null) {
                cleanupReceipt(receipt.getReceiptId());
            }
        }
    }

    // Helper methods

    private void createTestUser(String userId, String name, String email, String phone) {
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "INSERT INTO users (user_id, name, email, phone_number, password_hash, balance) " +
                     "VALUES (?, ?, ?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE name = VALUES(name)")) {
            
            pstmt.setString(1, userId);
            pstmt.setString(2, name);
            pstmt.setString(3, email);
            pstmt.setString(4, phone);
            pstmt.setString(5, "test-hash");
            pstmt.setDouble(6, 0.0);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error creating test user: " + e.getMessage());
        }
    }

    private int createTestReceiptWithItems(String userId, String merchantName) {
        try (Connection conn = dbConnection.getConnection()) {
            // Create receipt
            Receipt receipt = receiptDAO.createReceipt(
                    userId, merchantName, new Date(), 41.75f, 5.0f, 3.5f, "test-url"
            );
            
            if (receipt == null) {
                return -1;
            }
            
            int receiptId = receipt.getReceiptId();
            
            // Add items using ReceiptDAO
            ReceiptItem item1 = receiptDAO.addReceiptItem(receiptId, "Test Item 1", 10.50f, 1, "Food");
            ReceiptItem item2 = receiptDAO.addReceiptItem(receiptId, "Test Item 2", 15.75f, 2, "Drink");
            
            if (item1 == null || item2 == null) {
                System.err.println("Failed to add items to receipt");
                return -1;
            }
            
            return receiptId;
        } catch (Exception e) {
            System.err.println("Error creating test receipt: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }

    private int createTestReceiptWithMultipleItems(String userId, String merchantName, int itemCount) {
        try {
            Receipt receipt = receiptDAO.createReceipt(
                    userId, merchantName, new Date(), 100.0f, 10.0f, 8.0f, "test-url"
            );
            
            if (receipt == null) {
                return -1;
            }
            
            int receiptId = receipt.getReceiptId();
            
            // Add multiple items
            for (int i = 1; i <= itemCount; i++) {
                ReceiptItem item = receiptDAO.addReceiptItem(
                        receiptId, "Item " + i, (float)(10.0 * i), 1, "Category" + i
                );
                if (item == null) {
                    System.err.println("Failed to add item " + i);
                    return -1;
                }
            }
            
            return receiptId;
        } catch (Exception e) {
            System.err.println("Error creating test receipt with multiple items: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }

    private void cleanupReceipt(int receiptId) {
        try (Connection conn = dbConnection.getConnection()) {
            // Delete item assignments
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "DELETE FROM item_assignments WHERE receipt_id = ?")) {
                pstmt.setInt(1, receiptId);
                pstmt.executeUpdate();
            }
            
            // Delete receipt items
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "DELETE FROM receipt_items WHERE receipt_id = ?")) {
                pstmt.setInt(1, receiptId);
                pstmt.executeUpdate();
            }
            
            // Delete receipt participants
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "DELETE FROM receipt_participants WHERE receipt_id = ?")) {
                pstmt.setInt(1, receiptId);
                pstmt.executeUpdate();
            }
            
            // Delete receipt
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "DELETE FROM receipts WHERE receipt_id = ?")) {
                pstmt.setInt(1, receiptId);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Error cleaning up receipt: " + e.getMessage());
        }
    }
}
