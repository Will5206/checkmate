// Default package (no package declaration)

import models.Receipt;
import services.FriendService;
import services.ReceiptNotificationService;
import patterns.FriendObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class ReceiptNotificationServiceTest {

    private ReceiptNotificationService notificationService;
    private MockFriendService mockFriendService;
    private MockReceipt mockReceipt;

    // --- simple mock dependencies ---

    static class MockFriendService extends FriendService {
        @Override
        public List<String> listFriends(String userId) {
            return Arrays.asList("2", "3", "4");
        }
    }

    static class MockReceipt extends Receipt {
        private final List<Integer> observerIds = new ArrayList<>();

        public MockReceipt() {
            super(1, 1, "Test Store", new java.util.Date(), 50.0f, 5.0f, 4.0f, "http://test.com/image.jpg", "pending");
        }

        @Override
        public void addFriendObserver(FriendObserver obs) {
            super.addFriendObserver(obs);
            observerIds.add(obs.getFriendId());
        }

        public int getObserverCount() {
            return observerIds.size();
        }

        public List<Integer> getObserverIds() {
            return new ArrayList<>(observerIds);
        }
    }

    @BeforeEach
    void setup() {
        mockFriendService = new MockFriendService();
        notificationService = new ReceiptNotificationService(mockFriendService);
        mockReceipt = new MockReceipt();
    }

    // 1. normal case: adds one observer per friend
    @Test
    void testEnableReceiptNotifications_addsObservers() {
        int count = notificationService.enableReceiptNotifications(mockReceipt, 1);
        
        assertEquals(3, count);
        assertEquals(3, mockReceipt.getObserverCount());
        assertTrue(mockReceipt.getObserverIds().containsAll(Arrays.asList(2, 3, 4)));
    }

    // 2. null receipt should throw exception
    @Test
    void testEnableReceiptNotifications_nullReceipt_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                notificationService.enableReceiptNotifications(null, 1));
    }

    // 3. specific friends filtering: only real friends are notified
    @Test
    void testEnableReceiptNotificationsForSpecificFriends_filtersNonFriends() {
        List<Integer> input = Arrays.asList(2, 99);
        int count = notificationService.enableReceiptNotificationsForSpecificFriends(mockReceipt, 1, input);
        
        assertEquals(1, count);
        assertEquals(1, mockReceipt.getObserverCount());
        assertTrue(mockReceipt.getObserverIds().contains(2));
        assertFalse(mockReceipt.getObserverIds().contains(99));
    }

    // 4. sendReceiptToFriends triggers same count as enableReceiptNotifications
    @Test
    void testSendReceiptToFriends_returnsFriendCount() {
        int count = notificationService.sendReceiptToFriends(mockReceipt, 1);
        
        assertEquals(3, count);
        assertEquals(3, mockReceipt.getObserverCount());
    }

    // 5. sendReceiptToSpecificFriends filters and notifies correctly
    @Test
    void testSendReceiptToSpecificFriends_filtersNonFriends() {
        List<Integer> input = Arrays.asList(2, 3, 99);
        int count = notificationService.sendReceiptToSpecificFriends(mockReceipt, 1, input);
        
        assertEquals(2, count); // Only 2 and 3 are actual friends
        assertEquals(2, mockReceipt.getObserverCount());
    }

    // 6. empty friends list returns 0
    @Test
    void testEnableReceiptNotifications_emptyFriendsList() {
        MockFriendService emptyFriendService = new MockFriendService() {
            @Override
            public List<String> listFriends(String userId) {
                return Collections.emptyList();
            }
        };
        ReceiptNotificationService service = new ReceiptNotificationService(emptyFriendService);

        int count = service.enableReceiptNotifications(mockReceipt, 1);

        assertEquals(0, count);
        assertEquals(0, mockReceipt.getObserverCount());
    }
}

