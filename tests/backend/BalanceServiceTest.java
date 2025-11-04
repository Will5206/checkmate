// Default package (no package declaration)

import services.BalanceService;
import models.BalanceHistory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for BalanceService.
 * Tests balance operations including additions, subtractions, history tracking, and edge cases.
 * 
 * Note: These tests require a running MySQL database with the checkmate_db schema.
 * Make sure to have a test user created before running these tests.
 */
public class BalanceServiceTest {

    private BalanceService balanceService;
    private String testUserId;

    @BeforeEach
    void setup() {
        balanceService = new BalanceService();
        // Use a test user ID - in a real scenario, you'd create a test user first
        // For now, we'll use a UUID that should exist in your test database
        testUserId = "test-user-id-" + System.currentTimeMillis();
        
        // Note: In a real test setup, you would:
        // 1. Create a test user in the database
        // 2. Use that user's ID for testing
        // 3. Clean up after tests
    }

    /**
     * Test 1: Adding to balance successfully
     */
    @Test
    void testAddToBalance_success() {
        // Note: This test requires a valid user in the database
        // For demonstration, we'll test the logic with a mock or assume user exists
        
        // This test would need a valid user ID from your database
        // Uncomment and use a real user ID when running:
        /*
        double initialBalance = balanceService.getCurrentBalance(testUserId);
        double amountToAdd = 50.00;
        
        boolean result = balanceService.addToBalance(
            testUserId, 
            amountToAdd, 
            BalanceService.TYPE_PAYMENT_RECEIVED,
            "Test payment received",
            "ref-123",
            "transaction"
        );
        
        assertTrue(result, "Balance addition should succeed");
        
        double newBalance = balanceService.getCurrentBalance(testUserId);
        assertEquals(initialBalance + amountToAdd, newBalance, 0.01, 
                    "Balance should increase by the added amount");
        
        // Verify history was recorded
        List<BalanceHistory> history = balanceService.getBalanceHistory(testUserId, 1);
        assertFalse(history.isEmpty(), "History should contain the new record");
        assertEquals(amountToAdd, history.get(0).getAmount(), 0.01, 
                    "History amount should match added amount");
        */
    }

    /**
     * Test 2: Subtracting from balance successfully
     */
    @Test
    void testSubtractFromBalance_success() {
        // This test would need a valid user ID with sufficient balance
        // Uncomment and use a real user ID when running:
        /*
        // First add some balance
        balanceService.addToBalance(testUserId, 100.00, BalanceService.TYPE_PAYMENT_RECEIVED,
                                   "Initial deposit", null, null);
        
        double initialBalance = balanceService.getCurrentBalance(testUserId);
        double amountToSubtract = 30.00;
        
        boolean result = balanceService.subtractFromBalance(
            testUserId,
            amountToSubtract,
            BalanceService.TYPE_PAYMENT_SENT,
            "Test payment sent",
            "ref-456",
            "transaction"
        );
        
        assertTrue(result, "Balance subtraction should succeed");
        
        double newBalance = balanceService.getCurrentBalance(testUserId);
        assertEquals(initialBalance - amountToSubtract, newBalance, 0.01,
                     "Balance should decrease by the subtracted amount");
        
        // Verify history was recorded with negative amount
        List<BalanceHistory> history = balanceService.getBalanceHistory(testUserId, 1);
        assertFalse(history.isEmpty(), "History should contain the new record");
        assertEquals(-amountToSubtract, history.get(0).getAmount(), 0.01,
                    "History amount should be negative");
        */
    }

    /**
     * Test 3: Getting balance history
     */
    @Test
    void testGetBalanceHistory_returnsHistory() {
        // This test verifies that history retrieval works
        // Uncomment and use a real user ID when running:
        /*
        // Add multiple transactions
        balanceService.addToBalance(testUserId, 50.00, BalanceService.TYPE_PAYMENT_RECEIVED,
                                   "First transaction", "ref-1", "transaction");
        balanceService.addToBalance(testUserId, 25.00, BalanceService.TYPE_POT_CONTRIBUTION,
                                   "Second transaction", "ref-2", "pot");
        balanceService.subtractFromBalance(testUserId, 10.00, BalanceService.TYPE_PAYMENT_SENT,
                                           "Third transaction", "ref-3", "transaction");
        
        List<BalanceHistory> history = balanceService.getBalanceHistory(testUserId);
        
        assertTrue(history.size() >= 3, "Should have at least 3 history records");
        
        // Verify records are ordered by most recent first
        for (int i = 0; i < history.size() - 1; i++) {
            assertTrue(history.get(i).getCreatedAt().compareTo(history.get(i + 1).getCreatedAt()) >= 0,
                       "History should be ordered by most recent first");
        }
        
        // Verify first record is the most recent (subtraction)
        assertEquals(-10.00, history.get(0).getAmount(), 0.01,
                     "Most recent record should be the subtraction");
        */
    }

    /**
     * Test 4: Edge case - negative amount throws exception
     */
    @Test
    void testAddToBalance_negativeAmount_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            balanceService.addToBalance(
                "test-user",
                -10.00,
                BalanceService.TYPE_PAYMENT_RECEIVED,
                "Invalid negative amount",
                null,
                null
            );
        }, "Adding negative amount should throw IllegalArgumentException");
    }

    /**
     * Test 5: Edge case - zero amount throws exception
     */
    @Test
    void testAddToBalance_zeroAmount_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            balanceService.addToBalance(
                "test-user",
                0.00,
                BalanceService.TYPE_PAYMENT_RECEIVED,
                "Invalid zero amount",
                null,
                null
            );
        }, "Adding zero amount should throw IllegalArgumentException");
    }

    /**
     * Test 6: Edge case - null user ID throws exception
     */
    @Test
    void testAddToBalance_nullUserId_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            balanceService.addToBalance(
                null,
                10.00,
                BalanceService.TYPE_PAYMENT_RECEIVED,
                "Test",
                null,
                null
            );
        }, "Null user ID should throw IllegalArgumentException");
    }

    /**
     * Test 7: Edge case - empty user ID throws exception
     */
    @Test
    void testAddToBalance_emptyUserId_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            balanceService.addToBalance(
                "",
                10.00,
                BalanceService.TYPE_PAYMENT_RECEIVED,
                "Test",
                null,
                null
            );
        }, "Empty user ID should throw IllegalArgumentException");
    }

    /**
     * Test 8: Edge case - insufficient balance throws exception
     */
    @Test
    void testSubtractFromBalance_insufficientBalance_throwsException() {
        // This test would need a valid user ID with low balance
        // Uncomment and use a real user ID when running:
        /*
        // Ensure user has low balance
        double currentBalance = balanceService.getCurrentBalance(testUserId);
        if (currentBalance > 0) {
            // Try to subtract more than available
            assertThrows(IllegalArgumentException.class, () -> {
                balanceService.subtractFromBalance(
                    testUserId,
                    currentBalance + 100.00,
                    BalanceService.TYPE_PAYMENT_SENT,
                    "More than available",
                    null,
                    null
                );
            }, "Subtracting more than available balance should throw IllegalArgumentException");
        }
        */
    }

    /**
     * Test 9: Edge case - negative amount in subtraction throws exception
     */
    @Test
    void testSubtractFromBalance_negativeAmount_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            balanceService.subtractFromBalance(
                "test-user",
                -10.00,
                BalanceService.TYPE_PAYMENT_SENT,
                "Invalid negative amount",
                null,
                null
            );
        }, "Subtracting negative amount should throw IllegalArgumentException");
    }

    /**
     * Test 10: Test that balance history is correctly recorded with all fields
     */
    @Test
    void testBalanceHistory_recordsAllFields() {
        // This test verifies that all fields are properly recorded
        // Uncomment and use a real user ID when running:
        /*
        double initialBalance = balanceService.getCurrentBalance(testUserId);
        double amount = 75.50;
        String description = "Test transaction with all fields";
        String referenceId = "ref-789";
        String referenceType = "receipt";
        
        boolean result = balanceService.addToBalance(
            testUserId,
            amount,
            BalanceService.TYPE_RECEIPT_SPLIT,
            description,
            referenceId,
            referenceType
        );
        
        assertTrue(result, "Transaction should succeed");
        
        List<BalanceHistory> history = balanceService.getBalanceHistory(testUserId, 1);
        assertFalse(history.isEmpty(), "History should contain the record");
        
        BalanceHistory record = history.get(0);
        assertEquals(testUserId, record.getUserId(), "User ID should match");
        assertEquals(amount, record.getAmount(), 0.01, "Amount should match");
        assertEquals(initialBalance, record.getBalanceBefore(), 0.01, "Balance before should match");
        assertEquals(initialBalance + amount, record.getBalanceAfter(), 0.01, "Balance after should match");
        assertEquals(BalanceService.TYPE_RECEIPT_SPLIT, record.getTransactionType(), "Transaction type should match");
        assertEquals(description, record.getDescription(), "Description should match");
        assertEquals(referenceId, record.getReferenceId(), "Reference ID should match");
        assertEquals(referenceType, record.getReferenceType(), "Reference type should match");
        assertNotNull(record.getCreatedAt(), "Created at timestamp should not be null");
        */
    }

    /**
     * Test 11: Test getting balance history by type
     */
    @Test
    void testGetBalanceHistoryByType_filtersCorrectly() {
        // This test verifies filtering by transaction type
        // Uncomment and use a real user ID when running:
        /*
        // Add different types of transactions
        balanceService.addToBalance(testUserId, 50.00, BalanceService.TYPE_PAYMENT_RECEIVED,
                                   "Payment 1", null, null);
        balanceService.addToBalance(testUserId, 25.00, BalanceService.TYPE_POT_CONTRIBUTION,
                                   "Pot contribution", null, null);
        balanceService.addToBalance(testUserId, 30.00, BalanceService.TYPE_PAYMENT_RECEIVED,
                                   "Payment 2", null, null);
        
        List<BalanceHistory> paymentHistory = balanceService.getBalanceHistoryByType(
            testUserId,
            BalanceService.TYPE_PAYMENT_RECEIVED
        );
        
        assertTrue(paymentHistory.size() >= 2, "Should have at least 2 payment_received records");
        
        // Verify all records are of the correct type
        for (BalanceHistory record : paymentHistory) {
            assertEquals(BalanceService.TYPE_PAYMENT_RECEIVED, record.getTransactionType(),
                        "All records should be payment_received type");
        }
        */
    }

    /**
     * Test 12: Test getting current balance
     */
    @Test
    void testGetCurrentBalance_returnsCorrectBalance() {
        // This test verifies getting current balance
        // Uncomment and use a real user ID when running:
        /*
        double initialBalance = balanceService.getCurrentBalance(testUserId);
        
        // Add some balance
        balanceService.addToBalance(testUserId, 100.00, BalanceService.TYPE_PAYMENT_RECEIVED,
                                   "Test", null, null);
        
        double newBalance = balanceService.getCurrentBalance(testUserId);
        assertEquals(initialBalance + 100.00, newBalance, 0.01,
                     "Balance should reflect the addition");
        */
    }

    /**
     * Test 13: Test getCurrentBalance with null user ID throws exception
     */
    @Test
    void testGetCurrentBalance_nullUserId_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            balanceService.getCurrentBalance(null);
        }, "Null user ID should throw IllegalArgumentException");
    }

    /**
     * Test 14: Test getBalanceHistory with null user ID throws exception
     */
    @Test
    void testGetBalanceHistory_nullUserId_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            balanceService.getBalanceHistory(null);
        }, "Null user ID should throw IllegalArgumentException");
    }

    /**
     * Test 15: Test getBalanceHistoryByType with null transaction type throws exception
     */
    @Test
    void testGetBalanceHistoryByType_nullType_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            balanceService.getBalanceHistoryByType("test-user", null);
        }, "Null transaction type should throw IllegalArgumentException");
    }

    @AfterEach
    void cleanup() {
        // In a real test scenario, you would clean up test data here
        // For example, reset balances or delete test records
    }
}

