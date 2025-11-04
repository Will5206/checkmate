package models;

public class ReceiptItem {
    private int itemId;
    private int receiptId;
    private String name;
    private float price;
    private int quantity;
    private String category;

    public ReceiptItem(int itemId, int receiptId, String name, float price, int quantity, String category) {
        this.itemId = itemId;
        this.receiptId = receiptId;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
        this.category = category;
    }

    public int getItemId() { return itemId; }
    public int getReceiptId() { return receiptId; }
    public String getName() { return name; }
    public float getPrice() { return price; }
    public int getQuantity() { return quantity; }
    public String getCategory() { return category; }
}
