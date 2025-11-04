package models;

import java.sql.Timestamp;






/**
 * Model representing a balance history record in CheckMate.
 * Tracks all changes to user account balances over time.
 */
public class BalanceHistory {
    
    private int historyId;
    private String userId;
    private double amount; // positive for additions, negative for subtractions
    private double balanceBefore;
    private double balanceAfter;
    private String transactionType;
    private String description;
    private String referenceId; // ID of related transaction, pot, receipt, etc.
    private String referenceType; // Type of reference (transaction, pot, receipt, etc.)
    private Timestamp createdAt;
    
    /**
     * Constructor for creating a new balance history record (before saving to DB)
     */
    public BalanceHistory(String userId, double amount, double balanceBefore, 
                         double balanceAfter, String transactionType, 
                         String description, String referenceId, String referenceType) {
        this.userId = userId;
        this.amount = amount;
        this.balanceBefore = balanceBefore;
        this.balanceAfter = balanceAfter;
        this.transactionType = transactionType;
        this.description = description;
        this.referenceId = referenceId;
        this.referenceType = referenceType;
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }
    
    /**
     * Constructor for loading existing balance history from database
     */
    public BalanceHistory(int historyId, String userId, double amount, 
                         double balanceBefore, double balanceAfter, 
                         String transactionType, String description, 
                         String referenceId, String referenceType, Timestamp createdAt) {
        this.historyId = historyId;
        this.userId = userId;
        this.amount = amount;
        this.balanceBefore = balanceBefore;
        this.balanceAfter = balanceAfter;
        this.transactionType = transactionType;
        this.description = description;
        this.referenceId = referenceId;
        this.referenceType = referenceType;
        this.createdAt = createdAt;
    }
    
    // Getters
    public int getHistoryId() { return historyId; }
    public String getUserId() { return userId; }
    public double getAmount() { return amount; }
    public double getBalanceBefore() { return balanceBefore; }
    public double getBalanceAfter() { return balanceAfter; }
    public String getTransactionType() { return transactionType; }
    public String getDescription() { return description; }
    public String getReferenceId() { return referenceId; }
    public String getReferenceType() { return referenceType; }
    public Timestamp getCreatedAt() { return createdAt; }
    
    // Setters (only for fields that can be updated)
    public void setDescription(String description) { 
        this.description = description; 
    }
    
    @Override
    public String toString() {
        return "BalanceHistory{" +
                "historyId=" + historyId +
                ", userId='" + userId + '\'' +
                ", amount=" + amount +
                ", balanceBefore=" + balanceBefore +
                ", balanceAfter=" + balanceAfter +
                ", transactionType='" + transactionType + '\'' +
                ", description='" + description + '\'' +
                ", referenceId='" + referenceId + '\'' +
                ", referenceType='" + referenceType + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}

