package models;

import patterns.ReceiptSubject;
import patterns.FriendObserver;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Receipt {
    private int receiptId;
    private int uploadedBy;
    private String merchantName;
    private Date date;
    private float totalAmount;
    private float tipAmount;
    private float taxAmount;
    private String imageUrl;
    private String status;

    private List<ReceiptItem> items;

    private ReceiptSubject subject = new ReceiptSubject();

    // Constructor
    public Receipt(int receiptId, int uploadedBy, String merchantName, Date date,
                    float totalAmount, float tipAmount, float taxAmount,
                    String imageUrl, String status) {
        this.receiptId = receiptId;
        this.uploadedBy = uploadedBy;
        this.merchantName = merchantName;
        this.date = date;
        this.totalAmount = totalAmount;
        this.tipAmount = tipAmount;
        this.taxAmount = taxAmount;
        this.imageUrl = imageUrl;
        this.status = status;
        this.items = new ArrayList<>();
    }

    // Getters
    public int getReceiptId() { return receiptId; }
    public int getUploadedBy() { return uploadedBy; }
    public String getMerchantName() { return merchantName; }
    public Date getDate() { return date; }
    public float getTotalAmount() { return totalAmount; }
    public float getTipAmount() { return tipAmount; }
    public float getTaxAmount() { return taxAmount; }
    public String getImageUrl() { return imageUrl; }
    public String getStatus() { return status; }
    public List<ReceiptItem> getItems() { return new ArrayList<>(items); }

    // Setters
    public void setStatus(String status) { this.status = status; }

    // Methods from UML
    public void addItem(ReceiptItem item) {
        items.add(item);
        System.out.println("Item added: " + item.getName());
    }

    //Methods for observer pattern which notifies friends when a receipt is added
    public void addFriendObserver(FriendObserver friendObserver) {
        subject.addObserver(friendObserver);
    }

    public void removeFriendObserver(FriendObserver friendObserver) {
        subject.removeObserver(friendObserver);
    }

    public void notifyFriends(String message) {
        subject.notifyObservers(this, message);
    }
    public void processOCR() {
        // placeholder for OCR logic
        System.out.println("Processing OCR for receipt " + receiptId);
    }

    public void sendToFriends(List<Integer> friendIds) {
        System.out.println("Sharing receipt " + receiptId + " with friends: " + friendIds);
    }
    public String generateShareLink() {
        String baseUrl = "https://yourapp.com/receipts/";
        String token = java.util.UUID.randomUUID().toString(); // unique link token
        return baseUrl + receiptId + "?token=" + token;
    }
}
