package services;

import models.Receipt;
import patterns.FriendObserver;

import java.util.List;

/**
 * Service for enabling notifications when sending receipts to friends.
 * Integrates FriendService with the Receipt observer pattern.
 * Foundational implementation - no database or frontend dependencies.
 * 
 * Usage example:
 * <pre>
 *   FriendService friendService = new FriendService();
 *   ReceiptNotificationService notificationService = new ReceiptNotificationService(friendService);
 *   
 *   // Create a receipt
 *   Receipt receipt = new Receipt(1, userId, "Store Name", new Date(), 50.0f, 5.0f, 4.0f, "url", "pending");
 *   
 *   // Send receipt to all friends and notify them
 *   int notifiedCount = notificationService.sendReceiptToFriends(receipt, userId);
 *   
 *   // Or send to specific friends
 *   List&lt;Integer&gt; specificFriends = Arrays.asList(2, 3);
 *   notificationService.sendReceiptToSpecificFriends(receipt, userId, specificFriends);
 * </pre>
 */
public class ReceiptNotificationService {

    private final FriendService friendService;
    private final ReceiptService receiptService;

    public ReceiptNotificationService(FriendService friendService) {
        this.friendService = friendService;
        this.receiptService = ReceiptService.getInstance();
    }

    public ReceiptNotificationService(FriendService friendService, ReceiptService receiptService) {
        this.friendService = friendService;
        this.receiptService = receiptService;
    }

    /**
     * Enable notifications for sending a receipt to all friends of a user.
     * This sets up FriendObserver instances for each friend and attaches them to the receipt.
     * 
     * @param receipt The receipt to send notifications for
     * @param userId The user who owns the receipt (and whose friends should be notified)
     * @return The number of friends who will receive notifications
     */
    public int enableReceiptNotifications(Receipt receipt, int userId) {
        if (receipt == null) {
            throw new IllegalArgumentException("Receipt cannot be null");
        }

        // Get all friends for this user
        List<Integer> friendIds = friendService.listFriends(userId);
        
        if (friendIds.isEmpty()) {
            System.out.println("User " + userId + " has no friends to notify about receipt " + receipt.getReceiptId());
            return 0;
        }

        // Create and attach a FriendObserver for each friend
        for (Integer friendId : friendIds) {
            FriendObserver observer = new FriendObserver(friendId);
            receipt.addFriendObserver(observer);
        }

        System.out.println("Enabled notifications for receipt " + receipt.getReceiptId() + 
                          " to " + friendIds.size() + " friends of user " + userId);
        
        return friendIds.size();
    }

    /**
     * Enable notifications for sending a receipt to specific friends only.
     * 
     * @param receipt The receipt to send notifications for
     * @param userId The user who owns the receipt
     * @param friendIds List of specific friend IDs to notify
     * @return The number of friends who will receive notifications
     */
    public int enableReceiptNotificationsForSpecificFriends(Receipt receipt, int userId, List<Integer> friendIds) {
        if (receipt == null) {
            throw new IllegalArgumentException("Receipt cannot be null");
        }
        if (friendIds == null || friendIds.isEmpty()) {
            return 0;
        }

        // Verify that all specified IDs are actually friends
        List<Integer> actualFriends = friendService.listFriends(userId);
        int notifiedCount = 0;

        for (Integer friendId : friendIds) {
            if (actualFriends.contains(friendId)) {
                FriendObserver observer = new FriendObserver(friendId);
                receipt.addFriendObserver(observer);
                notifiedCount++;
            } else {
                System.out.println("Warning: User " + friendId + " is not a friend of user " + userId + 
                                  ", skipping notification");
            }
        }

        System.out.println("Enabled notifications for receipt " + receipt.getReceiptId() + 
                          " to " + notifiedCount + " specific friends of user " + userId);
        
        return notifiedCount;
    }

    /**
     * Send a receipt to all friends and trigger notifications.
     * This is a convenience method that combines enabling notifications and sending.
     * 
     * @param receipt The receipt to send
     * @param userId The user sending the receipt
     * @return The number of friends notified
     */
    public int sendReceiptToFriends(Receipt receipt, int userId) {
        int friendCount = enableReceiptNotifications(receipt, userId);
        
        if (friendCount > 0) {
            // Get friend IDs for the sendToFriends method
            List<Integer> friendIds = friendService.listFriends(userId);
            receipt.sendToFriends(friendIds);
            
            // Add receipt to each friend's pending receipts list
            for (Integer friendId : friendIds) {
                receiptService.addPendingReceipt(friendId, receipt);
            }
            
            // Trigger notifications via observer pattern
            String message = "Receipt from " + receipt.getMerchantName() + 
                           " for $" + receipt.getTotalAmount() + 
                           " has been shared with you";
            receipt.notifyFriends(message);
        }
        
        return friendCount;
    }

    /**
     * Send a receipt to specific friends and trigger notifications.
     * 
     * @param receipt The receipt to send
     * @param userId The user sending the receipt
     * @param friendIds List of specific friend IDs to send to
     * @return The number of friends notified
     */
    public int sendReceiptToSpecificFriends(Receipt receipt, int userId, List<Integer> friendIds) {
        int friendCount = enableReceiptNotificationsForSpecificFriends(receipt, userId, friendIds);
        
        if (friendCount > 0) {
            // Filter to only actual friends (create a new list to avoid modifying the input)
            List<Integer> actualFriends = friendService.listFriends(userId);
            List<Integer> validFriendIds = new java.util.ArrayList<>();
            for (Integer friendId : friendIds) {
                if (actualFriends.contains(friendId)) {
                    validFriendIds.add(friendId);
                }
            }
            
            receipt.sendToFriends(validFriendIds);
            
            // Add receipt to each friend's pending receipts list
            for (Integer friendId : validFriendIds) {
                receiptService.addPendingReceipt(friendId, receipt);
            }
            
            // Trigger notifications via observer pattern
            String message = "Receipt from " + receipt.getMerchantName() + 
                           " for $" + receipt.getTotalAmount() + 
                           " has been shared with you";
            receipt.notifyFriends(message);
        }
        
        return friendCount;
    }

    /**
     * Remove all observers from a receipt (cleanup method).
     * 
     * @param receipt The receipt to clean up
     */
    public void clearReceiptNotifications(Receipt receipt) {
        if (receipt == null) {
            return;
        }
        
        // Note: This is a limitation - we'd need to track observers to remove them
        // For now, this is a placeholder for future enhancement
        System.out.println("Clearing notifications for receipt " + receipt.getReceiptId());
    }
}

