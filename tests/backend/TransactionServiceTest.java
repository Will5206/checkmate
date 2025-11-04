// Default package (no package declaration)

import services.TransactionService;
import models.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.sql.Timestamp;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for TransactionService.
 * Tests transaction creation, retrieval, status updates, and filtering.
 * 
 * Note: These tests require a running MySQL database with the checkmate_db schema.
 * Make sure to have test users created before running these tests.
 */
public class TransactionServiceTest {

    private TransactionService transactionService;
    private String testUserId1;
    private String testUserId2;

    @BeforeEach
    void setup() {
        transactionService = new TransactionService();
        // Use test user IDs - in a real scenario, you'd create test users first
        testUserId1 = "test-user-1-" + System.currentTimeMillis();
        testUserId2 = "test-user-2-" + System.currentTimeMillis();
        
        // Note: In a real test setup, you would:
        // 1. Create test users in the database
        // 2. Use those user IDs for testing
        // 3. Clean up after tests
    }

    /**
     * Test 1: Creating a transaction successfully
     */
    @Test
    void testCreateTransaction_success() {
        // This test requires valid user IDs in the database
        // Uncomment and use real user IDs when running:
        /*
        Transaction transaction = transactionService.createTransaction(
            testUserId1,
            testUserId2,
            50.00,
            TransactionService.TYPE_PEER_TO_PEER,
            "Test payment",
            TransactionService.STATUS_PENDING,
            null
        );
        
        assertNotNull(transaction, "Transaction should be created");
        assertEquals(testUserId1, transaction.getFromUserId(), "From user ID should match");
        assertEquals(testUserId2, transaction.getToUserId(), "To user ID should match");
        assertEquals(50.00, transaction.getAmount(), 0.01, "Amount should match");
        assertEquals(TransactionService.TYPE_PEER_TO_PEER, transaction.getTransactionType(), "Transaction type should match");
        assertEquals(TransactionService.STATUS_PENDING, transaction.getStatus(), "Status should be pending");
        assertTrue(transaction.getTransactionId() > 0, "Transaction ID should be generated");
        assertNotNull(transaction.getCreatedAt(), "Created at timestamp should not be null");
        */
    }

    /**
     * Test 2: Creating a transaction with null to_user_id (for pot contributions)
     */
    @Test
    void testCreateTransaction_nullToUserId() {
        // This test requires a valid user ID in the database
        // Uncomment and use real user ID when running:
        /*
        Transaction transaction = transactionService.createTransaction(
            testUserId1,
            null,
            100.00,
            TransactionService.TYPE_POT_CONTRIBUTION,
            "Pot contribution",
            null, // Should default to pending
            "pot-123"
        );
        
        assertNotNull(transaction, "Transaction should be created");
        assertNull(transaction.getToUserId(), "To user ID should be null");
        assertEquals(TransactionService.STATUS_PENDING, transaction.getStatus(), "Status should default to pending");
        assertEquals("pot-123", transaction.getRelatedEntityId(), "Related entity ID should match");
        */
    }

    /**
     * Test 3: Retrieving transaction history for a user
     */
    @Test
    void testGetTransactionHistory_returnsTransactions() {
        // This test requires valid user IDs and existing transactions
        // Uncomment and use real user IDs when running:
        /*
        // Create some transactions
        transactionService.createTransaction(testUserId1, testUserId2, 25.00, 
            TransactionService.TYPE_PEER_TO_PEER, "Payment 1", null, null);
        transactionService.createTransaction(testUserId2, testUserId1, 30.00, 
            TransactionService.TYPE_PEER_TO_PEER, "Payment 2", null, null);
        transactionService.createTransaction(testUserId1, null, 50.00, 
            TransactionService.TYPE_POT_CONTRIBUTION, "Pot contribution", null, "pot-1");
        
        List<Transaction> history = transactionService.getTransactionHistory(testUserId1);
        
        assertTrue(history.size() >= 3, "Should have at least 3 transactions");
        
        // Verify transactions are ordered by most recent first
        for (int i = 0; i < history.size() - 1; i++) {
            assertTrue(history.get(i).getCreatedAt().compareTo(history.get(i + 1).getCreatedAt()) >= 0,
                       "Transactions should be ordered by most recent first");
        }
        */
    }

    /**
     * Test 4: Updating transaction status
     */
    @Test
    void testUpdateTransactionStatus_success() {
        // This test requires a valid transaction ID
        // Uncomment and use real transaction ID when running:
        /*
        // Create a transaction
        Transaction transaction = transactionService.createTransaction(
            testUserId1, testUserId2, 40.00, 
            TransactionService.TYPE_PEER_TO_PEER, "Test", 
            TransactionService.STATUS_PENDING, null
        );
        
        assertNotNull(transaction, "Transaction should be created");
        int transactionId = transaction.getTransactionId();
        
        // Update status to completed
        boolean updated = transactionService.updateTransactionStatus(
            transactionId, 
            TransactionService.STATUS_COMPLETED
        );
        
        assertTrue(updated, "Status update should succeed");
        
        // Verify the update
        Transaction updatedTransaction = transactionService.getTransactionById(transactionId);
        assertNotNull(updatedTransaction, "Transaction should be retrievable");
        assertEquals(TransactionService.STATUS_COMPLETED, updatedTransaction.getStatus(), 
                    "Status should be updated to completed");
        assertNotNull(updatedTransaction.getUpdatedAt(), "Updated at timestamp should be set");
        */
    }

    /**
     * Test 5: Getting recent transactions with limit
     */
    @Test
    void testGetRecentTransactions_withLimit() {
        // This test requires valid user IDs and multiple transactions
        // Uncomment and use real user IDs when running:
        /*
        // Create multiple transactions
        for (int i = 0; i < 10; i++) {
            transactionService.createTransaction(
                testUserId1, testUserId2, 10.00 + i, 
                TransactionService.TYPE_PEER_TO_PEER, "Payment " + i, null, null
            );
        }
        
        List<Transaction> recent = transactionService.getRecentTransactions(testUserId1, 5);
        
        assertEquals(5, recent.size(), "Should return exactly 5 transactions");
        
        // Verify they are the most recent
        List<Transaction> all = transactionService.getTransactionHistory(testUserId1);
        for (int i = 0; i < 5; i++) {
            assertEquals(all.get(i).getTransactionId(), recent.get(i).getTransactionId(),
                        "Recent transactions should match the first 5 from full history");
        }
        */
    }

    /**
     * Test 6: Edge case - null from_user_id throws exception
     */
    @Test
    void testCreateTransaction_nullFromUserId_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            transactionService.createTransaction(
                null,
                testUserId2,
                50.00,
                TransactionService.TYPE_PEER_TO_PEER,
                "Test",
                null,
                null
            );
        }, "Null from user ID should throw IllegalArgumentException");
    }

    /**
     * Test 7: Edge case - empty from_user_id throws exception
     */
    @Test
    void testCreateTransaction_emptyFromUserId_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            transactionService.createTransaction(
                "",
                testUserId2,
                50.00,
                TransactionService.TYPE_PEER_TO_PEER,
                "Test",
                null,
                null
            );
        }, "Empty from user ID should throw IllegalArgumentException");
    }

    /**
     * Test 8: Edge case - negative amount throws exception
     */
    @Test
    void testCreateTransaction_negativeAmount_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            transactionService.createTransaction(
                testUserId1,
                testUserId2,
                -10.00,
                TransactionService.TYPE_PEER_TO_PEER,
                "Test",
                null,
                null
            );
        }, "Negative amount should throw IllegalArgumentException");
    }

    /**
     * Test 9: Edge case - zero amount throws exception
     */
    @Test
    void testCreateTransaction_zeroAmount_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            transactionService.createTransaction(
                testUserId1,
                testUserId2,
                0.00,
                TransactionService.TYPE_PEER_TO_PEER,
                "Test",
                null,
                null
            );
        }, "Zero amount should throw IllegalArgumentException");
    }

    /**
     * Test 10: Edge case - null transaction type throws exception
     */
    @Test
    void testCreateTransaction_nullTransactionType_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            transactionService.createTransaction(
                testUserId1,
                testUserId2,
                50.00,
                null,
                "Test",
                null,
                null
            );
        }, "Null transaction type should throw IllegalArgumentException");
    }

    /**
     * Test 11: Getting transactions by status
     */
    @Test
    void testGetTransactionsByStatus_filtersCorrectly() {
        // This test requires valid user IDs and transactions with different statuses
        // Uncomment and use real user IDs when running:
        /*
        // Create transactions with different statuses
        Transaction t1 = transactionService.createTransaction(
            testUserId1, testUserId2, 20.00, 
            TransactionService.TYPE_PEER_TO_PEER, "Pending", 
            TransactionService.STATUS_PENDING, null
        );
        Transaction t2 = transactionService.createTransaction(
            testUserId1, testUserId2, 30.00, 
            TransactionService.TYPE_PEER_TO_PEER, "Completed", 
            TransactionService.STATUS_COMPLETED, null
        );
        Transaction t3 = transactionService.createTransaction(
            testUserId1, testUserId2, 40.00, 
            TransactionService.TYPE_PEER_TO_PEER, "Pending 2", 
            TransactionService.STATUS_PENDING, null
        );
        
        List<Transaction> pending = transactionService.getTransactionsByStatus(
            testUserId1, 
            TransactionService.STATUS_PENDING
        );
        
        assertTrue(pending.size() >= 2, "Should have at least 2 pending transactions");
        for (Transaction t : pending) {
            assertEquals(TransactionService.STATUS_PENDING, t.getStatus(),
                        "All transactions should be pending");
        }
        */
    }

    /**
     * Test 12: Getting transactions by type
     */
    @Test
    void testGetTransactionsByType_filtersCorrectly() {
        // This test requires valid user IDs and transactions of different types
        // Uncomment and use real user IDs when running:
        /*
        // Create transactions of different types
        transactionService.createTransaction(
            testUserId1, testUserId2, 25.00, 
            TransactionService.TYPE_PEER_TO_PEER, "P2P", null, null
        );
        transactionService.createTransaction(
            testUserId1, null, 50.00, 
            TransactionService.TYPE_POT_CONTRIBUTION, "Pot", null, "pot-1"
        );
        transactionService.createTransaction(
            testUserId1, testUserId2, 35.00, 
            TransactionService.TYPE_PEER_TO_PEER, "P2P 2", null, null
        );
        
        List<Transaction> p2pTransactions = transactionService.getTransactionsByType(
            testUserId1,
            TransactionService.TYPE_PEER_TO_PEER
        );
        
        assertTrue(p2pTransactions.size() >= 2, "Should have at least 2 peer-to-peer transactions");
        for (Transaction t : p2pTransactions) {
            assertEquals(TransactionService.TYPE_PEER_TO_PEER, t.getTransactionType(),
                        "All transactions should be peer-to-peer type");
        }
        */
    }

    /**
     * Test 13: Getting transactions by date range
     */
    @Test
    void testGetTransactionsByDateRange_filtersCorrectly() {
        // This test requires valid user IDs and transactions
        // Uncomment and use real user IDs when running:
        /*
        // Create transactions
        transactionService.createTransaction(
            testUserId1, testUserId2, 25.00, 
            TransactionService.TYPE_PEER_TO_PEER, "Transaction 1", null, null
        );
        
        // Wait a moment
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        Timestamp startTime = new Timestamp(System.currentTimeMillis());
        
        transactionService.createTransaction(
            testUserId1, testUserId2, 30.00, 
            TransactionService.TYPE_PEER_TO_PEER, "Transaction 2", null, null
        );
        
        Timestamp endTime = new Timestamp(System.currentTimeMillis());
        
        List<Transaction> inRange = transactionService.getTransactionsByDateRange(
            testUserId1,
            startTime,
            endTime
        );
        
        assertTrue(inRange.size() >= 1, "Should have at least 1 transaction in range");
        for (Transaction t : inRange) {
            assertTrue(t.getCreatedAt().compareTo(startTime) >= 0 &&
                      t.getCreatedAt().compareTo(endTime) <= 0,
                      "Transaction should be within date range");
        }
        */
    }

    /**
     * Test 14: Edge case - getTransactionHistory with null user ID throws exception
     */
    @Test
    void testGetTransactionHistory_nullUserId_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            transactionService.getTransactionHistory(null);
        }, "Null user ID should throw IllegalArgumentException");
    }

    /**
     * Test 15: Edge case - getRecentTransactions with invalid limit throws exception
     */
    @Test
    void testGetRecentTransactions_invalidLimit_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            transactionService.getRecentTransactions(testUserId1, 0);
        }, "Zero limit should throw IllegalArgumentException");
        
        assertThrows(IllegalArgumentException.class, () -> {
            transactionService.getRecentTransactions(testUserId1, -1);
        }, "Negative limit should throw IllegalArgumentException");
    }

    /**
     * Test 16: Edge case - updateTransactionStatus with null status throws exception
     */
    @Test
    void testUpdateTransactionStatus_nullStatus_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            transactionService.updateTransactionStatus(1, null);
        }, "Null status should throw IllegalArgumentException");
    }

    /**
     * Test 17: Edge case - getTransactionById with non-existent ID returns null
     */
    @Test
    void testGetTransactionById_nonExistentId_returnsNull() {
        Transaction transaction = transactionService.getTransactionById(999999);
        assertNull(transaction, "Non-existent transaction ID should return null");
    }

    /**
     * Test 18: Edge case - getTransactionsByDateRange with invalid range throws exception
     */
    @Test
    void testGetTransactionsByDateRange_invalidRange_throwsException() {
        Timestamp start = new Timestamp(System.currentTimeMillis());
        Timestamp end = new Timestamp(System.currentTimeMillis() - 1000); // End before start
        
        assertThrows(IllegalArgumentException.class, () -> {
            transactionService.getTransactionsByDateRange(testUserId1, start, end);
        }, "Start date after end date should throw IllegalArgumentException");
    }

    @AfterEach
    void cleanup() {
        // In a real test scenario, you would clean up test data here
        // For example, delete test transactions or reset test user balances
    }
}

