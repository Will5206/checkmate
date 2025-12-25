package controllers;

import services.ReceiptService;
import models.ReceiptItem;
import database.ReceiptDAO;
import database.UserDAO;
import models.User;
import utils.AuthMiddleware;
import utils.ErrorResponse;
import utils.Logger;
import utils.RequestUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;

/**
 * Controller for handling receipt item operations (claiming and assignments).
 * Provides endpoints for claiming/unclaiming items and getting item assignments.
 */
public class ReceiptItemController {

    private static final ReceiptService receiptService = ReceiptService.getInstance();
    private static final String CONTEXT = "ReceiptItemController";

    /**
     * Handler for claiming/unclaiming items from a receipt.
     * POST /api/receipts/items/claim?receiptId=X&itemId=Y&userId=Z&quantity=1
     * DELETE /api/receipts/items/claim?receiptId=X&itemId=Y&userId=Z (to unclaim)
     */
    public static class ClaimItemHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            ErrorResponse.addCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            // Verify authentication
            String authenticatedUserId = AuthMiddleware.verifyAuth(exchange);
            if (authenticatedUserId == null) {
                AuthMiddleware.sendUnauthorized(exchange);
                return;
            }
            
            Map<String, String> query = RequestUtils.parseQuery(exchange.getRequestURI());
            try {
                String userIdStr = query.getOrDefault("userId", "");
                int receiptId = Integer.parseInt(query.getOrDefault("receiptId", "0"));
                int itemId = Integer.parseInt(query.getOrDefault("itemId", "0"));
                
                if (userIdStr.isEmpty() || receiptId == 0 || itemId == 0) {
                    ErrorResponse.sendError(exchange, 400, "receiptId, itemId, and userId are required");
                    return;
                }
                
                // Verify authenticated user matches
                if (!userIdStr.equals(authenticatedUserId)) {
                    ErrorResponse.sendError(exchange, 403, "Forbidden: Cannot claim items for another user");
                    return;
                }
                
                ReceiptDAO receiptDAO = receiptService.getReceiptDAO();
                boolean isDelete = "DELETE".equals(exchange.getRequestMethod());
                
                // Handle unclaim (DELETE)
                if (isDelete) {
                    handleUnclaimItem(exchange, receiptDAO, itemId, userIdStr, receiptId);
                } else if ("POST".equals(exchange.getRequestMethod())) {
                    // Handle claim (POST)
                    int quantity = RequestUtils.getIntParam(query, "quantity", 1);
                    handleClaimItem(exchange, receiptDAO, itemId, userIdStr, receiptId, quantity);
                } else {
                    ErrorResponse.sendError(exchange, 405, "Method not allowed");
                }
            } catch (NumberFormatException e) {
                Logger.error(CONTEXT, "NumberFormatException - " + e.getMessage(), e);
                ErrorResponse.sendError(exchange, 400, "Invalid parameters: " + e.getMessage());
            } catch (Exception e) {
                Logger.error(CONTEXT, "Exception in claim handler: " + e.getMessage(), e);
                if (e.getCause() != null) {
                    Logger.error(CONTEXT, "Caused by: " + e.getCause().getClass().getName() + " - " + e.getCause().getMessage());
                }
                ErrorResponse.sendError(exchange, 400, "Invalid parameters: " + e.getMessage());
            }
        }
    }
    
    // Helper methods for ClaimItemHandler
    
    /**
     * Handle unclaiming an item
     */
    private static void handleUnclaimItem(HttpExchange exchange, ReceiptDAO receiptDAO, int itemId, String userIdStr, int receiptId) throws IOException {
        // Check if item has been paid for
        Map<String, Object> paymentInfo = receiptDAO.getItemPaymentInfo(itemId);
        if (paymentInfo != null) {
            ErrorResponse.sendError(exchange, 400, "Cannot unclaim an item that has been paid for");
            return;
        }
        
        boolean success = receiptDAO.unassignItemFromUser(itemId, userIdStr);
        if (success) {
            sendItemClaimSuccessResponse(exchange, receiptDAO, receiptId, userIdStr, true);
        } else {
            ErrorResponse.sendError(exchange, 400, "Failed to unclaim item");
        }
    }
    
    /**
     * Handle claiming an item
     */
    private static void handleClaimItem(HttpExchange exchange, ReceiptDAO receiptDAO, int itemId, String userIdStr, int receiptId, int quantity) throws IOException {
        // Check if item has been paid for
        if (receiptDAO.isItemPaid(itemId)) {
            ErrorResponse.sendError(exchange, 400, "This item has already been paid for and cannot be claimed");
            return;
        }
        
        Logger.debug(CONTEXT, "Starting claim process - itemId=" + itemId + ", userId=" + userIdStr + ", quantity=" + quantity);
        
        // Get item claim info (item details + quantities)
        ReceiptDAO.ItemClaimInfo claimInfo = receiptDAO.getItemClaimInfo(itemId, userIdStr);
        if (claimInfo == null || claimInfo.item == null) {
            Logger.error(CONTEXT, "getItemClaimInfo returned null - item not found or query failed");
            ErrorResponse.sendError(exchange, 400, "Item not found or database error occurred");
            return;
        }
        
        Logger.debug(CONTEXT, "ItemClaimInfo retrieved - itemName=" + claimInfo.item.getName() + 
                     ", userClaimedQty=" + claimInfo.userClaimedQuantity + 
                     ", totalClaimedQty=" + claimInfo.totalClaimedQuantity);
        
        // Calculate available quantity
        ReceiptItem item = claimInfo.item;
        int userCurrentQty = claimInfo.userClaimedQuantity;
        int totalClaimedByOthers = claimInfo.totalClaimedQuantity - userCurrentQty;
        int availableQty = item.getQuantity() - totalClaimedByOthers;
        
        Logger.debug(CONTEXT, "Validation - itemQuantity=" + item.getQuantity() + 
                     ", userCurrentQty=" + userCurrentQty + 
                     ", totalClaimedByOthers=" + totalClaimedByOthers + 
                     ", availableQty=" + availableQty);
        
        // Validate quantity
        if (!validateClaimQuantity(exchange, quantity, availableQty, item, totalClaimedByOthers)) {
            return; // Error already sent
        }
        
        // Assign item to user
        Logger.debug(CONTEXT, "Validation passed, calling assignItemToUser...");
        long assignStartTime = System.currentTimeMillis();
        boolean success = receiptDAO.assignItemToUser(itemId, userIdStr, quantity);
        long assignDuration = System.currentTimeMillis() - assignStartTime;
        Logger.timing(CONTEXT, "assignItemToUser", assignDuration);
        Logger.debug(CONTEXT, "assignItemToUser returned success=" + success);
        
        if (success) {
            sendItemClaimSuccessResponse(exchange, receiptDAO, receiptId, userIdStr, false);
        } else {
            Logger.error(CONTEXT, "assignItemToUser returned false - assignment failed");
            ErrorResponse.sendError(exchange, 400, "Failed to update item assignment");
        }
    }
    
    /**
     * Validate claim quantity
     */
    private static boolean validateClaimQuantity(HttpExchange exchange, int quantity, int availableQty, ReceiptItem item, int totalClaimedByOthers) throws IOException {
        if (quantity <= 0) {
            Logger.error(CONTEXT, "Invalid quantity=" + quantity);
            ErrorResponse.sendError(exchange, 400, "Quantity must be greater than 0");
            return false;
        }
        
        if (quantity > availableQty) {
            Logger.error(CONTEXT, "Quantity exceeds available - quantity=" + quantity + ", available=" + availableQty);
            ErrorResponse.sendError(exchange, 400, String.format("Cannot claim %d. Only %d available (item quantity: %d, already claimed by others: %d)", 
                    quantity, availableQty, item.getQuantity(), totalClaimedByOthers));
            return false;
        }
        
        return true;
    }
    
    /**
     * Send success response after claiming/unclaiming item
     */
    private static void sendItemClaimSuccessResponse(HttpExchange exchange, ReceiptDAO receiptDAO, int receiptId, String userIdStr, boolean isDelete) throws IOException {
        Logger.debug(CONTEXT, "Assignment successful, calculating owed amounts...");
        
        try {
            long calcStartTime = System.currentTimeMillis();
            float[] amounts = receiptDAO.calculateBothOwedAmounts(receiptId, userIdStr);
            long calcDuration = System.currentTimeMillis() - calcStartTime;
            Logger.timing(CONTEXT, "calculateBothOwedAmounts", calcDuration);
            
            float owedAmount = amounts[0];
            float owedAmountExcludingPaid = amounts[1];
            
            Logger.debug(CONTEXT, "Owed amounts calculated - owedAmount=" + owedAmount + 
                         ", owedAmountExcludingPaid=" + owedAmountExcludingPaid);
            
            JSONObject resp = ErrorResponse.success(isDelete ? "Item unclaimed" : "Item claimed");
            resp.put("owedAmount", owedAmount);
            resp.put("owedAmountExcludingPaid", owedAmountExcludingPaid);
            
            Logger.debug(CONTEXT, "Sending success response");
            ErrorResponse.sendJson(exchange, 200, resp);
            Logger.debug(CONTEXT, "Claim process completed successfully");
        } catch (Exception e) {
            Logger.error(CONTEXT, "Exception calculating owed amounts: " + e.getMessage(), e);
            ErrorResponse.sendError(exchange, 500, "Failed to calculate owed amounts: " + e.getMessage());
        }
    }

    /**
     * Handler for getting item assignments and owed amount for a user.
     * GET /api/receipts/items/assignments?receiptId=X&userId=Y
     */
    public static class GetItemAssignmentsHandler implements HttpHandler {
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
            
            // Verify authentication
            String authenticatedUserId = AuthMiddleware.verifyAuth(exchange);
            if (authenticatedUserId == null) {
                AuthMiddleware.sendUnauthorized(exchange);
                return;
            }
            
            Map<String, String> query = RequestUtils.parseQuery(exchange.getRequestURI());
            try {
                String userIdStr = query.getOrDefault("userId", "");
                int receiptId = Integer.parseInt(query.getOrDefault("receiptId", "0"));
                
                if (userIdStr.isEmpty() || receiptId == 0) {
                    ErrorResponse.sendError(exchange, 400, "receiptId and userId are required");
                    return;
                }
                
                // Verify authenticated user matches
                if (!userIdStr.equals(authenticatedUserId)) {
                    ErrorResponse.sendError(exchange, 403, "Forbidden: Cannot access assignments for another user");
                    return;
                }
                
                Logger.debug(CONTEXT, "GetItemAssignmentsHandler - receiptId: " + receiptId + ", userId: " + userIdStr);
                
                ReceiptDAO receiptDAO = receiptService.getReceiptDAO();
                UserDAO userDAO = new UserDAO();
                
                // OPTIMIZED: Get assignments and both owed amounts in fewer queries
                Map<Integer, Integer> assignments = receiptDAO.getItemAssignmentsForUser(receiptId, userIdStr);
                
                // OPTIMIZED: Use calculateBothOwedAmounts instead of calling both separately (saves 1 query)
                float[] amounts = receiptDAO.calculateBothOwedAmounts(receiptId, userIdStr);
                float owedAmount = amounts[0];
                float owedAmountExcludingPaid = amounts[1];
                
                Logger.debug(CONTEXT, "Found " + assignments.size() + " item assignments, owedAmount: " + owedAmount + ", owedAmountExcludingPaid: " + owedAmountExcludingPaid);
                
                // Build assignments JSON
                JSONObject assignmentsJson = new JSONObject();
                for (Map.Entry<Integer, Integer> entry : assignments.entrySet()) {
                    assignmentsJson.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                
                // Get item payment info from receipt_items table (new approach)
                Map<Integer, Map<String, Object>> itemPaymentMap = receiptDAO.getItemPaymentInfoForReceipt(receiptId);
                
                // OPTIMIZED: Batch fetch all payer user IDs first, then batch fetch user names
                Set<String> payerUserIds = new HashSet<>();
                for (Map<String, Object> paymentData : itemPaymentMap.values()) {
                    String paidByUserId = (String) paymentData.get("paidBy");
                    if (paidByUserId != null && !paidByUserId.isEmpty()) {
                        payerUserIds.add(paidByUserId);
                    }
                }
                
                // Batch fetch all payer names in a single query
                Map<String, User> payerUsersMap = userDAO.findUsersByIdsBatch(new ArrayList<>(payerUserIds));
                
                // Build item payment info JSON with payer names
                JSONObject itemPaymentInfo = new JSONObject();
                for (Map.Entry<Integer, Map<String, Object>> entry : itemPaymentMap.entrySet()) {
                    int itemId = entry.getKey();
                    Map<String, Object> paymentData = entry.getValue();
                    String paidByUserId = (String) paymentData.get("paidBy");
                    
                    // Get payer's name from batch-fetched map
                    User payer = payerUsersMap.get(paidByUserId);
                    String payerName = payer != null ? payer.getName() : "Unknown";
                    
                    JSONObject paymentJson = new JSONObject()
                        .put("paidBy", paidByUserId)
                        .put("payerName", payerName)
                        .put("paidAt", paymentData.get("paidAt"));
                    itemPaymentInfo.put(String.valueOf(itemId), paymentJson);
                }
                
                Logger.debug(CONTEXT, "Found payment info for " + itemPaymentInfo.length() + " items");
                
                JSONObject resp = ErrorResponse.success(null);
                resp.put("assignments", assignmentsJson);
                resp.put("owedAmount", owedAmount);
                resp.put("owedAmountExcludingPaid", owedAmountExcludingPaid);
                resp.put("itemPaymentInfo", itemPaymentInfo);
                
                Logger.debug(CONTEXT, "Sending response: " + resp.toString());
                ErrorResponse.sendJson(exchange, 200, resp);
            } catch (Exception e) {
                Logger.error(CONTEXT, "Error in GetItemAssignmentsHandler: " + e.getMessage(), e);
                ErrorResponse.sendError(exchange, 500, "Error getting item assignments: " + e.getMessage());
            }
        }
    }
}

