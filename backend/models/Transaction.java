package models;

import java.sql.Timestamp;

/**
 * Model representing a financial transaction in CheckMate.
 * Tracks all transactions between users including payments, pot contributions, and refunds.
 */
public class Transaction {
    
    private int transactionId;
    private String fromUserId;
    private String toUserId;
    private double amount;
    private String transactionType;
    private String description;
    private String status;
    private String relatedEntityId;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    
    /**
     * Constructor for creating a new transaction (before saving to DB)
     */
    public Transaction(String fromUserId, String toUserId, double amount, 
                      String transactionType, String description, String status, 
                      String relatedEntityId) {
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.amount = amount;
        this.transactionType = transactionType;
        this.description = description;
        this.status = status != null ? status : "pending";
        this.relatedEntityId = relatedEntityId;
        this.createdAt = new Timestamp(System.currentTimeMillis());
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }
    
    /**
     * Constructor for loading existing transaction from database
     */
    public Transaction(int transactionId, String fromUserId, String toUserId, 
                      double amount, String transactionType, String description,
                      String status, String relatedEntityId, 
                      Timestamp createdAt, Timestamp updatedAt) {
        this.transactionId = transactionId;
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.amount = amount;
        this.transactionType = transactionType;
        this.description = description;
        this.status = status;
        this.relatedEntityId = relatedEntityId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
    
    // Getters
    public int getTransactionId() { return transactionId; }
    public String getFromUserId() { return fromUserId; }
    public String getToUserId() { return toUserId; }
    public double getAmount() { return amount; }
    public String getTransactionType() { return transactionType; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public String getRelatedEntityId() { return relatedEntityId; }
    public Timestamp getCreatedAt() { return createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    
    // Setters
    public void setStatus(String status) { 
        this.status = status;
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }
    
    public void setDescription(String description) { 
        this.description = description;
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }
    
    public void setRelatedEntityId(String relatedEntityId) {
        this.relatedEntityId = relatedEntityId;
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }
    
    @Override
    public String toString() {
        return "Transaction{" +
                "transactionId=" + transactionId +
                ", fromUserId='" + fromUserId + '\'' +
                ", toUserId='" + toUserId + '\'' +
                ", amount=" + amount +
                ", transactionType='" + transactionType + '\'' +
                ", description='" + description + '\'' +
                ", status='" + status + '\'' +
                ", relatedEntityId='" + relatedEntityId + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}

