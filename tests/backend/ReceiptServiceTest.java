// Default package (no package declaration)

import models.Receipt;
import services.ReceiptService;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

public class ReceiptServiceTest {

    @Test
    void testAddPendingReceipt() {
        ReceiptService service = ReceiptService.getInstance();
        Receipt receipt = new Receipt(1, 10, "Test Store", new Date(), 50.0f, 5.0f, 4.0f, "url", "pending");
        service.addPendingReceipt(2, receipt);

        Receipt retrieved = service.getReceipt(2, 1);
        assertNotNull(retrieved);
        assertEquals(1, retrieved.getReceiptId());
        assertEquals("Test Store", retrieved.getMerchantName());
        assertEquals("pending", service.getReceiptStatus(2, 1));
    }

    @Test
    void testGetReceipt() {
        ReceiptService service = ReceiptService.getInstance();
        Receipt receipt = new Receipt(2, 10, "Another Store", new Date(), 30.0f, 0.0f, 2.5f, "url", "pending");
        service.addPendingReceipt(3, receipt);

        Receipt retrieved = service.getReceipt(3, 2);
        assertNotNull(retrieved);
        assertEquals(2, retrieved.getReceiptId());
        assertEquals(10, retrieved.getUploadedBy());
        assertEquals(30.0f, retrieved.getTotalAmount(), 0.01f);

        // Test non-existent receipt
        assertNull(service.getReceipt(3, 999));
    }

    @Test
    void testAcceptReceipt() {
        ReceiptService service = ReceiptService.getInstance();
        Receipt receipt = new Receipt(3, 10, "Store", new Date(), 25.0f, 0.0f, 2.0f, "url", "pending");
        service.addPendingReceipt(4, receipt);

        boolean accepted = service.acceptReceipt(4, 3);
        assertTrue(accepted);
        assertEquals("accepted", service.getReceiptStatus(4, 3));
        assertEquals("accepted", receipt.getStatus());

        // Test accepting already accepted receipt
        assertFalse(service.acceptReceipt(4, 3));
    }

    @Test
    void testDeclineReceipt() {
        ReceiptService service = ReceiptService.getInstance();
        Receipt receipt = new Receipt(4, 10, "Store", new Date(), 20.0f, 0.0f, 1.5f, "url", "pending");
        service.addPendingReceipt(5, receipt);

        boolean declined = service.declineReceipt(5, 4);
        assertTrue(declined);
        assertEquals("declined", service.getReceiptStatus(5, 4));
        assertEquals("declined", receipt.getStatus());

        // Test declining already declined receipt
        assertFalse(service.declineReceipt(5, 4));
    }
}

