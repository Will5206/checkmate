package models;

import java.util.Date;

public class Friend {
    private int friendshipId;
    private String userId1;
    private String userId2;
    private Date createdAt;
    private String status;

    // Constructor
    public Friend(int friendshipId, String userId1, String userId2, Date createdAt, String status) {
        this.friendshipId = friendshipId;
        this.userId1 = userId1;
        this.userId2 = userId2;
        this.createdAt = createdAt;
        this.status = status;
    }

    // Getters
    public int getFriendshipId() { return friendshipId; }
    public String getUserId1() { return userId1; }
    public String getUserId2() { return userId2; }
    public Date getCreatedAt() { return createdAt; }
    public String getStatus() { return status; }

    // Setters
    public void setStatus(String status) { this.status = status; }

    // Methods from UML
    public void acceptRequest() {
        this.status = "accepted";
        System.out.println("Friend request accepted between user " + userId1 + " and " + userId2);
    }

    public void declineRequest() {
        this.status = "declined";
        System.out.println("Friend request declined between user " + userId1 + " and " + userId2);
    }
}