package com.checkmate.models;

import java.sql.Timestamp;
import java.util.UUID;






/**
 * user model representing a checkmate user account
 */
public class User {
    
    private String userId;
    private String name;
    private String email;
    private String phoneNumber;
    private String passwordHash;
    private double balance;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    
    /**
     * constructor for creating new user
     */

    public User(String name, String email, String phoneNumber, String passwordHash) {
        this.userId = UUID.randomUUID().toString();
        this.name = name;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.passwordHash = passwordHash;

        this.balance = 0.00;
        this.createdAt = new Timestamp(System.currentTimeMillis());

        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }
    
    /**
     * constructor for loading existing user from db
     */


    public User(String userId, String name, String email, String phoneNumber, 
                String passwordHash, double balance, Timestamp createdAt, Timestamp updatedAt) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.passwordHash = passwordHash;
        this.balance = balance;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
    
    // getters!
    public String getUserId() { return userId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getPasswordHash() { return passwordHash; }
    public double getBalance() { return balance; }

    public Timestamp getCreatedAt() { return createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    
    //setters!
    public void setName(String name) { 
        this.name = name;
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }
    
    public void setEmail(String email) { 
        this.email = email;
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }
    

    public void setPhoneNumber(String phoneNumber) { 
        this.phoneNumber = phoneNumber;
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }
    
    public void setPasswordHash(String passwordHash) { 
        this.passwordHash = passwordHash;
        this.updatedAt = new Timestamp(System.currentTimeMillis());

        
    }
    
    public void setBalance(double balance) { 
        this.balance = balance;
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }
    



    @Override
    public String toString() {
        return "User{" +
                "userId='" + userId + '\'' +
                ", name='" + name + '\'' +

                ", email='" + email + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", balance=" + balance +
                ", createdAt=" + createdAt +
                '}';
    }
}