package patterns;

import models.Receipt;

//Make a new observer class using the ReceiptObserver interface
//This observer will represent a friend who gets notified when a receipt is added to the user's account

public class FriendObserver implements ReceiptObserver {
    private int friendId;

    public FriendObserver(int friendId) {
        this.friendId = friendId;
    }

    public int getFriendId() {
        return friendId;
    }

    @Override
    public void update(Receipt receipt, String message) {
        System.out.println("Friend " + friendId + " notified: " + message + 
                            " (Receipt ID: " + receipt.getReceiptId() + ")");
        // Implement API at some point to send real notifications
    }
}