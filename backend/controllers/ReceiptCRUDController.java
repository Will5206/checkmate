package controllers;

import services.ReceiptService;
import models.Receipt;
import models.ReceiptItem;
import models.User;
import database.ReceiptDAO;
import database.UserDAO;
import utils.AuthMiddleware;
import utils.ValidationUtils;
import utils.ErrorResponse;
import utils.Logger;
import utils.Constants;
import utils.RequestUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for handling receipt CRUD operations (create and view).
 * Provides endpoints for creating new receipts and viewing existing receipts.
 */
public class ReceiptCRUDController {

    private static final ReceiptService receiptService = ReceiptService.getInstance();
    private static final String CONTEXT = "ReceiptCRUDController";

    /**
     * Handler for viewing a receipt by ID.
     * GET /api/receipts/view?receiptId=X&userId=Y
     */
    public static class ViewReceiptHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            ErrorResponse.addCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            if (!"GET".equals(exchange.getRequestMethod())) {
                ErrorResponse.sendError(exchange, 405, "Method not allowed");
                return;
            }
            
            Map<String, String> query = RequestUtils.parseQuery(exchange.getRequestURI());
            try {
                String userIdStr = RequestUtils.getStringParam(query, "userId", "");
                int receiptId = RequestUtils.getIntParam(query, "receiptId", 0);
                
                if (userIdStr.isEmpty() || receiptId == 0) {
                    ErrorResponse.sendError(exchange, 400, "userId and receiptId parameters are required");
                    return;
                }
                
                // Get full receipt with items loaded
                ReceiptDAO receiptDAO = receiptService.getReceiptDAO();
                Receipt receipt = receiptDAO.getReceiptById(receiptId);
                
                if (receipt == null) {
                    ErrorResponse.sendError(exchange, 404, "Receipt not found");
                    return;
                }
                
                // Verify user has access (uploader or participant)
                String uploadedBy = receiptDAO.getReceiptUploadedBy(receiptId);
                String participantStatus = receiptDAO.getParticipantStatus(receiptId, userIdStr);
                
                if (uploadedBy == null || (!uploadedBy.equals(userIdStr) && participantStatus == null)) {
                    ErrorResponse.sendError(exchange, 403, "Access denied");
                    return;
                }
                
                // Build receipt JSON with all details including items
                JSONObject receiptJson = ReceiptControllerUtils.buildReceiptJson(receipt);
                
                // Get status using String userId version
                String receiptStatus = receiptService.getReceiptStatus(userIdStr, receiptId);
                receiptJson.put("status", receiptStatus != null ? receiptStatus : "pending");
                
                // Add isUploader and hasPaid flags for frontend
                boolean isUploader = uploadedBy != null && uploadedBy.equals(userIdStr);
                receiptJson.put("isUploader", isUploader);
                
                // CRITICAL: Add complete status so frontend can determine if receipt is completed
                boolean isComplete = receiptDAO.isReceiptComplete(receiptId);
                receiptJson.put("complete", isComplete);
                
                if (!isUploader) {
                    if (participantStatus != null && !participantStatus.equals("declined")) {
                        float paidAmount = receiptDAO.getPaidAmount(receiptId, userIdStr);
                        receiptJson.put("hasPaid", paidAmount > 0.01f);
                        receiptJson.put("paidAmount", paidAmount);
                    } else {
                        receiptJson.put("hasPaid", false);
                        receiptJson.put("paidAmount", 0.0f);
                    }
                } else {
                    receiptJson.put("hasPaid", false);
                    receiptJson.put("paidAmount", 0.0f);
                }
                
                JSONObject resp = ErrorResponse.success(null);
                resp.put("receipt", receiptJson);
                
                ErrorResponse.sendJson(exchange, 200, resp);
            } catch (NumberFormatException e) {
                ErrorResponse.sendError(exchange, 400, "Invalid receiptId format. Expected a number.");
            } catch (Exception e) {
                Logger.error(CONTEXT, "ViewReceiptHandler error: " + e.getMessage(), e);
                ErrorResponse.sendError(exchange, 400, "Invalid parameters: " + e.getMessage());
            }
        }
    }

    /**
     * Handler for creating a new receipt.
     * POST /api/receipts/create?userId=X
     * Body: JSON with receipt data (restaurant_name, total_amount, tax, tip, items, participants)
     */
    public static class CreateReceiptHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            ErrorResponse.addCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                ErrorResponse.sendError(exchange, 405, "Method not allowed");
                return;
            }
            
            // Verify authentication
            String authenticatedUserId = AuthMiddleware.verifyAuth(exchange);
            if (authenticatedUserId == null) {
                AuthMiddleware.sendUnauthorized(exchange);
                return;
            }
            
            try {
                // Get userId from query params
                Map<String, String> query = RequestUtils.parseQuery(exchange.getRequestURI());
                String userIdStr = query.getOrDefault("userId", "");
                
                if (userIdStr.isEmpty()) {
                    ErrorResponse.sendError(exchange, 400, "userId parameter is required");
                    return;
                }
                
                // Verify authenticated user matches
                if (!userIdStr.equals(authenticatedUserId)) {
                    ErrorResponse.sendError(exchange, 403, "Forbidden: Cannot create receipts for another user");
                    return;
                }
                
                // Read and parse request body
                String requestBody = readRequestBody(exchange);
                JSONObject json = new JSONObject(requestBody);
                
                // Extract and validate receipt header data
                ReceiptHeaderData headerData = extractAndValidateReceiptHeader(json, exchange);
                if (headerData == null) return; // Error already sent
                
                // Validate and prepare items
                List<Map<String, Object>> itemsData = validateAndPrepareItems(json, exchange);
                if (itemsData == null) return; // Error already sent
                
                // Validate participants
                JSONArray participantsArray = validateParticipants(json, exchange);
                if (participantsArray == null) return; // Error already sent
                
                // Create receipt in database
                Receipt receipt = createReceiptInDatabase(userIdStr, headerData, exchange);
                if (receipt == null) return; // Error already sent
                
                // Add items to receipt
                addItemsToReceipt(receipt, itemsData);
                
                // Add participants to receipt
                List<String> validParticipantIds = addParticipantsToReceipt(receipt, participantsArray, userIdStr, exchange);
                if (validParticipantIds == null) return; // Error already sent
                
                // Update receipt item count
                ReceiptDAO receiptDAO = receiptService.getReceiptDAO();
                receiptDAO.updateReceiptItemCount(receipt.getReceiptId());
                
                // Build and send success response
                sendReceiptCreatedResponse(exchange, receipt, userIdStr, validParticipantIds.size());
                
            } catch (Exception e) {
                Logger.error(CONTEXT, "Error creating receipt: " + e.getMessage(), e);
                ErrorResponse.sendError(exchange, 400, "Error creating receipt: " + e.getMessage());
            }
        }
    }
    
    // Helper classes and methods for CreateReceiptHandler
    
    /**
     * Data class for receipt header information
     */
    private static class ReceiptHeaderData {
        String merchantName;
        double totalAmount;
        double tax;
        double tip;
        String imageUrl;
    }
    
    /**
     * Extract and validate receipt header data from JSON
     */
    private static ReceiptHeaderData extractAndValidateReceiptHeader(JSONObject json, HttpExchange exchange) throws IOException {
        ReceiptHeaderData data = new ReceiptHeaderData();
        
        // Extract merchant name
        data.merchantName = ValidationUtils.sanitizeBasic(
            json.optString("restaurant_name", json.optString("merchantName", "Unknown Merchant"))
        );
        if (data.merchantName == null || data.merchantName.length() > Constants.MAX_MERCHANT_NAME_LENGTH) {
            ErrorResponse.sendError(exchange, 400, "Invalid merchant name");
            return null;
        }
        
        // Extract and validate total amount
        try {
            data.totalAmount = json.getDouble("total_amount");
        } catch (Exception e) {
            ErrorResponse.sendError(exchange, 400, "Invalid total_amount");
            return null;
        }
        
        if (!ValidationUtils.isValidAmount(data.totalAmount)) {
            ErrorResponse.sendError(exchange, 400, "Invalid amount. Must be between 0.01 and 999,999.99");
            return null;
        }
        
        // Extract optional fields
        data.tax = Math.max(0, json.optDouble("tax", 0.0));
        data.tip = Math.max(0, json.optDouble("tip", 0.0));
        data.imageUrl = ValidationUtils.sanitizeBasic(
            json.optString("imageUrl", json.optString("image_url", ""))
        );
        
        return data;
    }
    
    /**
     * Validate and prepare items data from JSON
     */
    private static List<Map<String, Object>> validateAndPrepareItems(JSONObject json, HttpExchange exchange) throws IOException {
        // Validate items array exists
        if (!json.has("items")) {
            ErrorResponse.sendError(exchange, 400, "items array is required");
            return null;
        }
        
        JSONArray itemsArray = json.getJSONArray("items");
        if (itemsArray.length() == 0) {
            ErrorResponse.sendError(exchange, 400, "At least one item is required");
            return null;
        }
        
        if (itemsArray.length() > Constants.MAX_RECEIPT_ITEMS) {
            ErrorResponse.sendError(exchange, 400, "Too many items. Maximum is " + Constants.MAX_RECEIPT_ITEMS);
            return null;
        }
        
        // Validate and prepare each item
        List<Map<String, Object>> itemsData = new ArrayList<>();
        for (int i = 0; i < itemsArray.length(); i++) {
            JSONObject itemJson = itemsArray.getJSONObject(i);
            
            // Validate item name
            if (!itemJson.has("name") || itemJson.getString("name").trim().isEmpty()) {
                ErrorResponse.sendError(exchange, 400, "Item " + (i + 1) + " is missing a name");
                return null;
            }
            
            String itemName = ValidationUtils.sanitizeBasic(itemJson.getString("name"));
            if (itemName == null || itemName.length() > Constants.MAX_ITEM_NAME_LENGTH) {
                ErrorResponse.sendError(exchange, 400, "Item " + (i + 1) + " has an invalid name");
                return null;
            }
            
            // Validate item price
            double itemPrice;
            try {
                itemPrice = itemJson.getDouble("price");
            } catch (Exception e) {
                ErrorResponse.sendError(exchange, 400, "Item " + (i + 1) + " has an invalid price");
                return null;
            }
            
            if (!ValidationUtils.isValidAmount(itemPrice)) {
                ErrorResponse.sendError(exchange, 400, "Item " + (i + 1) + " has an invalid price amount");
                return null;
            }
            
            // Validate quantity
            int quantity = itemJson.optInt("qty", itemJson.optInt("quantity", 1));
            if (quantity < Constants.MIN_ITEM_QUANTITY || quantity > Constants.MAX_ITEM_QUANTITY) {
                ErrorResponse.sendError(exchange, 400, "Item " + (i + 1) + " has an invalid quantity (must be " + Constants.MIN_ITEM_QUANTITY + "-" + Constants.MAX_ITEM_QUANTITY + ")");
                return null;
            }
            
            // Build item data map
            Map<String, Object> itemData = new HashMap<>();
            itemData.put("name", itemName);
            itemData.put("price", itemPrice);
            itemData.put("quantity", quantity);
            String category = itemJson.optString("category", null);
            if (category != null) {
                itemData.put("category", ValidationUtils.sanitizeBasic(category));
            }
            itemsData.add(itemData);
        }
        
        return itemsData;
    }
    
    /**
     * Validate participants array from JSON
     */
    private static JSONArray validateParticipants(JSONObject json, HttpExchange exchange) throws IOException {
        JSONArray participantsArray = json.optJSONArray("participants");
        if (participantsArray == null) {
            participantsArray = new JSONArray();
        }
        
        if (participantsArray.length() > Constants.MAX_RECEIPT_PARTICIPANTS) {
            ErrorResponse.sendError(exchange, 400, "Too many participants. Maximum is " + Constants.MAX_RECEIPT_PARTICIPANTS);
            return null;
        }
        
        return participantsArray;
    }
    
    /**
     * Create receipt in database
     */
    private static Receipt createReceiptInDatabase(String userIdStr, ReceiptHeaderData headerData, HttpExchange exchange) throws IOException {
        ReceiptDAO receiptDAO = receiptService.getReceiptDAO();
        Date receiptDate = new Date();
        
        Receipt receipt = receiptDAO.createReceipt(
            userIdStr,
            headerData.merchantName,
            receiptDate,
            (float) headerData.totalAmount,
            (float) headerData.tip,
            (float) headerData.tax,
            headerData.imageUrl
        );
        
        if (receipt == null) {
            ErrorResponse.sendError(exchange, 500, "Failed to create receipt");
            return null;
        }
        
        return receipt;
    }
    
    /**
     * Add items to receipt
     */
    private static void addItemsToReceipt(Receipt receipt, List<Map<String, Object>> itemsData) {
        ReceiptDAO receiptDAO = receiptService.getReceiptDAO();
        List<ReceiptItem> createdItems = receiptDAO.addReceiptItemsBatch(receipt.getReceiptId(), itemsData);
        for (ReceiptItem item : createdItems) {
            receipt.addItem(item);
        }
    }
    
    /**
     * Add participants to receipt and return list of valid participant IDs
     */
    private static List<String> addParticipantsToReceipt(Receipt receipt, JSONArray participantsArray, String userIdStr, HttpExchange exchange) throws IOException {
        ReceiptDAO receiptDAO = receiptService.getReceiptDAO();
        UserDAO userDAO = new UserDAO();
        List<String> validParticipantIds = new ArrayList<>();
        
        // Add uploader as participant
        receiptDAO.addReceiptParticipant(receipt.getReceiptId(), userIdStr);
        validParticipantIds.add(userIdStr);
        
        // Extract and validate participant emails
        List<String> participantEmails = new ArrayList<>();
        for (int i = 0; i < participantsArray.length(); i++) {
            String email = participantsArray.getString(i).trim().toLowerCase();
            
            if (!ValidationUtils.isValidEmail(email)) {
                ErrorResponse.sendError(exchange, 400, "Invalid email address: " + email);
                return null;
            }
            
            participantEmails.add(email);
        }
        
        // Batch lookup all users by email
        Map<String, User> emailToUserMap = userDAO.findUsersByEmailsBatch(participantEmails);
        
        // Collect valid participant user IDs (excluding uploader)
        List<String> participantUserIds = new ArrayList<>();
        for (String email : participantEmails) {
            User participantUser = emailToUserMap.get(email);
            if (participantUser != null) {
                String participantUserId = participantUser.getUserId();
                if (!participantUserId.equals(userIdStr)) {
                    participantUserIds.add(participantUserId);
                    validParticipantIds.add(participantUserId);
                }
            }
        }
        
        // Batch insert all participants
        if (!participantUserIds.isEmpty()) {
            receiptDAO.addReceiptParticipantsBatch(receipt.getReceiptId(), participantUserIds);
        }
        
        return validParticipantIds;
    }
    
    /**
     * Send success response for receipt creation
     */
    private static void sendReceiptCreatedResponse(HttpExchange exchange, Receipt receipt, String userIdStr, int participantsCount) throws IOException {
        JSONObject receiptJson = ReceiptControllerUtils.buildReceiptJson(receipt, userIdStr);
        receiptJson.put("status", "accepted");
        
        JSONObject resp = ErrorResponse.success("Receipt created successfully");
        resp.put("receipt", receiptJson);
        resp.put("participantsAdded", participantsCount);
        
        ErrorResponse.sendJson(exchange, 201, resp);
    }

    /**
     * Helper method to read request body as string.
     */
    private static String readRequestBody(HttpExchange exchange) throws IOException {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }
}

