package controllers;

import models.Receipt;
import models.ReceiptItem;
import database.ReceiptDAO;
import services.ReceiptService;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Shared utility methods for receipt controllers.
 * Contains helper methods used by multiple receipt-related controllers.
 */
public class ReceiptControllerUtils {

    private static final ReceiptService receiptService = ReceiptService.getInstance();

    /**
     * Helper method to build a JSON object from a Receipt model.
     * @param receipt The receipt to convert to JSON
     * @return JSONObject representing the receipt
     */
    public static JSONObject buildReceiptJson(Receipt receipt) {
        return buildReceiptJson(receipt, null);
    }
    
    /**
     * Helper method to build a JSON object from a Receipt model.
     * @param receipt The receipt to convert to JSON
     * @param uploadedBy Optional uploadedBy string to avoid extra query (if already fetched)
     * @return JSONObject representing the receipt
     */
    public static JSONObject buildReceiptJson(Receipt receipt, String uploadedBy) {
        ReceiptDAO receiptDAO = receiptService.getReceiptDAO();
        String uploadedByStr = uploadedBy != null ? uploadedBy : receiptDAO.getReceiptUploadedBy(receipt.getReceiptId());
        
        JSONObject receiptJson = new JSONObject()
            .put("receiptId", receipt.getReceiptId())
            .put("uploadedBy", uploadedByStr != null ? uploadedByStr : String.valueOf(receipt.getUploadedBy()))
            .put("merchantName", receipt.getMerchantName())
            .put("date", receipt.getDate().getTime())
            .put("totalAmount", receipt.getTotalAmount())
            .put("tipAmount", receipt.getTipAmount())
            .put("taxAmount", receipt.getTaxAmount())
            .put("imageUrl", receipt.getImageUrl())
            .put("status", receipt.getStatus())
            .put("senderName", receipt.getSenderName() != null ? receipt.getSenderName() : "")
            .put("numberOfItems", receipt.getNumberOfItems());
        
        // Add items array
        JSONArray itemsArray = new JSONArray();
        for (ReceiptItem item : receipt.getItems()) {
            JSONObject itemJson = new JSONObject()
                .put("itemId", item.getItemId())
                .put("receiptId", item.getReceiptId())
                .put("name", item.getName())
                .put("price", item.getPrice())
                .put("quantity", item.getQuantity())
                .put("qty", item.getQuantity()) // Also include as 'qty' for frontend compatibility
                .put("category", item.getCategory());
            itemsArray.put(itemJson);
        }
        receiptJson.put("items", itemsArray);
        
        return receiptJson;
    }
}

