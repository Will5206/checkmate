package models;

import java.util.Date;

public class Friend {
    private int friendshipId;
    private String userId1;
    private String userId2;
    private Date createdAt;
    private String status;
    private String requestedBy; // Who sent the friend request

    // Constructor
    public Friend(int friendshipId, String userId1, String userId2, Date createdAt, String status) {
        this.friendshipId = friendshipId;
        this.userId1 = userId1;
        this.userId2 = userId2;
        this.createdAt = createdAt;
        this.status = status;
        this.requestedBy = null;
    }

    // Constructor with requestedBy
    public Friend(int friendshipId, String userId1, String userId2, Date createdAt, String status, String requestedBy) {
        this.friendshipId = friendshipId;
        this.userId1 = userId1;
        this.userId2 = userId2;
        this.createdAt = createdAt;
        this.status = status;
        this.requestedBy = requestedBy;
    }

    // Getters
    public int getFriendshipId() { return friendshipId; }
    public String getUserId1() { return userId1; }
    public String getUserId2() { return userId2; }
    public Date getCreatedAt() { return createdAt; }
    public String getStatus() { return status; }
    public String getRequestedBy() { return requestedBy; }

    // Setters
    public void setStatus(String status) { this.status = status; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }

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