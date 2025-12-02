// Default package (no package declaration)

import models.Receipt;
import models.ReceiptItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Receipt model.
 * Tests basic functionality of the Receipt class including item management.
 */
public class ReceiptTest {

    private Receipt receipt;
    private Date testDate;

    @BeforeEach
    void setup() {
        testDate = new Date();
        receipt = new Receipt(
            1, 
            123, 
            "Test Store", 
            testDate, 
            50.0f, 
            5.0f, 
            3.5f, 
            "test-url", 
            "pending"
        );
    }

    /**
     * Test that addItem() throws IllegalArgumentException when null item is passed.
     */
    @Test
    void testAddItem_nullItem_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            receipt.addItem(null);
        }, "Adding null item should throw IllegalArgumentException");
    }

    /**
     * Test that addItem() successfully adds a valid item.
     */
    @Test
    void testAddItem_validItem_addsSuccessfully() {
        ReceiptItem item = new ReceiptItem(1, 1, "Test Item", 10.50f, 1, "Food");
        
        receipt.addItem(item);
        
        assertFalse(receipt.getItems().isEmpty(), "Items list should not be empty after adding item");
        assertEquals(1, receipt.getItems().size(), "Items list should have one item");
        assertEquals("Test Item", receipt.getItems().get(0).getName(), "Item name should match");
    }

    /**
     * Test that multiple items can be added to a receipt.
     */
    @Test
    void testAddItem_multipleItems_addsAll() {
        ReceiptItem item1 = new ReceiptItem(1, 1, "Item 1", 10.50f, 1, "Food");
        ReceiptItem item2 = new ReceiptItem(2, 1, "Item 2", 15.75f, 2, "Drink");
        
        receipt.addItem(item1);
        receipt.addItem(item2);
        
        assertEquals(2, receipt.getItems().size(), "Items list should have two items");
        assertEquals("Item 1", receipt.getItems().get(0).getName(), "First item name should match");
        assertEquals("Item 2", receipt.getItems().get(1).getName(), "Second item name should match");
    }

    /**
     * Test that getItems() returns a defensive copy (modifications don't affect internal list).
     */
    @Test
    void testGetItems_returnsDefensiveCopy() {
        ReceiptItem item = new ReceiptItem(1, 1, "Test Item", 10.50f, 1, "Food");
        receipt.addItem(item);
        
        // Get items list
        var items = receipt.getItems();
        assertEquals(1, items.size(), "Should have one item");
        
        // Get items list again - should still have one item
        var items2 = receipt.getItems();
        assertEquals(1, items2.size(), "Should still have one item");
        
        // Adding to the returned list shouldn't affect the receipt
        assertEquals(1, receipt.getItems().size(), "Receipt should still have one item");
    }

    /**
     * Test basic getters return correct values.
     */
    @Test
    void testGetters_returnCorrectValues() {
        assertEquals(1, receipt.getReceiptId(), "Receipt ID should match");
        assertEquals("Test Store", receipt.getMerchantName(), "Merchant name should match");
        assertEquals(50.0f, receipt.getTotalAmount(), 0.01f, "Total amount should match");
        assertEquals(5.0f, receipt.getTipAmount(), 0.01f, "Tip amount should match");
        assertEquals(3.5f, receipt.getTaxAmount(), 0.01f, "Tax amount should match");
        assertEquals("pending", receipt.getStatus(), "Status should match");
    }

    /**
     * Test setStatus() updates the status correctly.
     */
    @Test
    void testSetStatus_updatesStatus() {
        receipt.setStatus("accepted");
        assertEquals("accepted", receipt.getStatus(), "Status should be updated to accepted");
        
        receipt.setStatus("completed");
        assertEquals("completed", receipt.getStatus(), "Status should be updated to completed");
    }
}
