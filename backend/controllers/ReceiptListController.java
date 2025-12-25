package controllers;

import services.ReceiptService;
import models.Receipt;
import database.ReceiptDAO;
import utils.AuthMiddleware;
import utils.ErrorResponse;
import utils.Logger;
import utils.RequestUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Controller for handling receipt listing operations (pending and activity).
 * Provides endpoints for listing pending receipts and activity receipts for a user.
 */
public class ReceiptListController {

    private static final ReceiptService receiptService = ReceiptService.getInstance();
    private static final String CONTEXT = "ReceiptListController";

    /**
     * Handler for listing all pending receipts for a user.
     * GET /api/receipts/pending?userId=X
     */
    public static class ListPendingReceiptsHandler implements HttpHandler {
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
                String userIdStr = RequestUtils.getStringParam(query, "userId", "");
                
                if (userIdStr.isEmpty()) {
                    ErrorResponse.sendError(exchange, 400, "userId parameter is required");
                    return;
                }
                
                // Verify authenticated user matches requested user
                if (!userIdStr.equals(authenticatedUserId)) {
                    ErrorResponse.sendError(exchange, 403, "Forbidden: Cannot list receipts for another user");
                    return;
                }
                
                // Use String userId version (for UUIDs)
                List<Receipt> pendingReceipts = receiptService.getPendingReceipts(userIdStr);
                Logger.debug(CONTEXT, "ListPendingReceiptsHandler - Found " + pendingReceipts.size() + " pending receipts for user " + userIdStr);
                
                ReceiptDAO receiptDAO = receiptService.getReceiptDAO();
                
                // OPTIMIZATION: Batch fetch all metadata in a single query instead of N queries
                List<Integer> receiptIds = new ArrayList<>();
                for (Receipt receipt : pendingReceipts) {
                    receiptIds.add(receipt.getReceiptId());
                }
                Map<Integer, ReceiptDAO.ReceiptMetadata> metadataMap = receiptDAO.getReceiptsMetadataBatch(receiptIds, userIdStr);
                Logger.debug(CONTEXT, "ListPendingReceiptsHandler - Batch fetched metadata for " + metadataMap.size() + " receipts");
                
                JSONArray receiptsArray = new JSONArray();
                for (Receipt receipt : pendingReceipts) {
                    // Get metadata from batch result
                    ReceiptDAO.ReceiptMetadata metadata = metadataMap.get(receipt.getReceiptId());
                    
                    // Build JSON with uploadedBy from metadata to avoid extra query
                    JSONObject receiptJson = ReceiptControllerUtils.buildReceiptJson(receipt, metadata != null ? metadata.uploadedBy : null);
                    if (metadata != null) {
                        String status = metadata.participantStatus != null ? metadata.participantStatus : "pending";
                        receiptJson.put("status", status);
                        
                        // Check if user is uploader
                        boolean isUploader = metadata.uploadedBy != null && metadata.uploadedBy.equals(userIdStr);
                        receiptJson.put("isUploader", isUploader);
                        
                        // Get participant payment info (if not uploader)
                        if (!isUploader && status != null && !status.equals("declined")) {
                            receiptJson.put("hasPaid", metadata.paidAmount > 0.01f);
                            receiptJson.put("paidAmount", metadata.paidAmount);
                        } else {
                            receiptJson.put("hasPaid", false);
                            receiptJson.put("paidAmount", 0.0f);
                        }
                        
                        // CRITICAL FIX: Get complete status from batch result
                        receiptJson.put("complete", metadata.isComplete);
                    } else {
                        // Fallback to individual queries if batch fetch failed (backward compatibility)
                        String status = receiptDAO.getParticipantStatus(receipt.getReceiptId(), userIdStr);
                        receiptJson.put("status", status != null ? status : "pending");
                        
                        String uploadedBy = receiptDAO.getReceiptUploadedBy(receipt.getReceiptId());
                        boolean isUploader = uploadedBy != null && uploadedBy.equals(userIdStr);
                        receiptJson.put("isUploader", isUploader);
                        
                        if (!isUploader && status != null && !status.equals("declined")) {
                            float paidAmount = receiptDAO.getPaidAmount(receipt.getReceiptId(), userIdStr);
                            receiptJson.put("hasPaid", paidAmount > 0.01f);
                            receiptJson.put("paidAmount", paidAmount);
                        } else {
                            receiptJson.put("hasPaid", false);
                            receiptJson.put("paidAmount", 0.0f);
                        }
                        
                        boolean isComplete = receiptDAO.isReceiptComplete(receipt.getReceiptId());
                        receiptJson.put("complete", isComplete);
                    }
                    
                    receiptsArray.put(receiptJson);
                }
                
                Logger.debug(CONTEXT, "ListPendingReceiptsHandler - Returning " + receiptsArray.length() + " receipts in JSON array");
                
                JSONObject resp = ErrorResponse.success(null);
                resp.put("userId", userIdStr);
                resp.put("receipts", receiptsArray);
                
                ErrorResponse.sendJson(exchange, 200, resp);
            } catch (Exception e) {
                Logger.error(CONTEXT, "ListPendingReceiptsHandler error: " + e.getMessage(), e);
                ErrorResponse.sendError(exchange, 400, "Invalid parameters: " + e.getMessage());
            }
        }
    }

    /**
     * Handler for getting all receipts for a user (activity/history).
     * GET /api/receipts/activity?userId=X
     */
    public static class GetActivityReceiptsHandler implements HttpHandler {
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
                Logger.debug(CONTEXT, "GetActivityReceiptsHandler - Request received");
                String userIdStr = RequestUtils.getStringParam(query, "userId", "");
                
                if (userIdStr.isEmpty()) {
                    ErrorResponse.sendError(exchange, 400, "userId parameter is required");
                    return;
                }
                
                // Verify authenticated user matches
                if (!userIdStr.equals(authenticatedUserId)) {
                    ErrorResponse.sendError(exchange, 403, "Forbidden: Cannot access activity for another user");
                    return;
                }
                
                // Get all receipts for this user
                List<Receipt> receipts = receiptService.getAllReceiptsForUser(userIdStr);
                Logger.debug(CONTEXT, "Received " + receipts.size() + " receipts from service");
                
                // Batch fetch metadata and owed amounts
                ReceiptMetadataBatch metadataBatch = fetchReceiptMetadataBatch(receipts, userIdStr);
                
                // Build JSON array with payment info
                JSONArray receiptsArray = buildReceiptsJsonArray(receipts, metadataBatch, userIdStr);
                
                // Send response
                sendActivityReceiptsResponse(exchange, userIdStr, receiptsArray);
            } catch (Exception e) {
                Logger.error(CONTEXT, "GetActivityReceiptsHandler error: " + e.getMessage(), e);
                ErrorResponse.sendError(exchange, 400, "Invalid parameters: " + e.getMessage());
            }
        }
    }
    
    // Helper classes and methods for GetActivityReceiptsHandler
    
    /**
     * Batch metadata for receipts
     */
    private static class ReceiptMetadataBatch {
        Map<Integer, ReceiptDAO.ReceiptMetadata> metadataMap;
        Map<Integer, Float> owedAmountsMap;
    }
    
    /**
     * Fetch metadata and owed amounts for receipts in batch
     */
    private static ReceiptMetadataBatch fetchReceiptMetadataBatch(List<Receipt> receipts, String userIdStr) {
        ReceiptDAO receiptDAO = receiptService.getReceiptDAO();
        
        List<Integer> receiptIds = new ArrayList<>();
        for (Receipt receipt : receipts) {
            receiptIds.add(receipt.getReceiptId());
        }
        
        ReceiptMetadataBatch batch = new ReceiptMetadataBatch();
        batch.metadataMap = receiptDAO.getReceiptsMetadataBatch(receiptIds, userIdStr);
        batch.owedAmountsMap = receiptDAO.calculateUserOwedAmountsBatch(receiptIds, userIdStr);
        
        Logger.debug(CONTEXT, "Batch fetched metadata and owed amounts for " + batch.metadataMap.size() + " receipts");
        return batch;
    }
    
    /**
     * Build JSON array of receipts with payment information
     */
    private static JSONArray buildReceiptsJsonArray(List<Receipt> receipts, ReceiptMetadataBatch metadataBatch, String userIdStr) {
        ReceiptDAO receiptDAO = receiptService.getReceiptDAO();
        JSONArray receiptsArray = new JSONArray();
        
        Logger.debug(CONTEXT, "Building JSON array for " + receipts.size() + " receipts");
        
        for (int i = 0; i < receipts.size(); i++) {
            Receipt receipt = receipts.get(i);
            Logger.debug(CONTEXT, "Processing receipt " + (i+1) + "/" + receipts.size() + " (ID: " + receipt.getReceiptId() + ")");
            
            ReceiptDAO.ReceiptMetadata metadata = metadataBatch.metadataMap.get(receipt.getReceiptId());
            JSONObject receiptJson = ReceiptControllerUtils.buildReceiptJson(receipt, metadata != null ? metadata.uploadedBy : null);
            
            Float owedAmount = metadataBatch.owedAmountsMap.get(receipt.getReceiptId());
            
            if (metadata != null && owedAmount != null) {
                addPaymentInfoToReceiptJson(receiptJson, owedAmount, metadata.paidAmount);
            } else {
                // Fallback to individual queries
                float owedAmountFallback = receiptDAO.calculateUserOwedAmount(receipt.getReceiptId(), userIdStr);
                float paidAmountFallback = receiptDAO.getPaidAmount(receipt.getReceiptId(), userIdStr);
                addPaymentInfoToReceiptJson(receiptJson, owedAmountFallback, paidAmountFallback);
            }
            
            receiptsArray.put(receiptJson);
            Logger.debug(CONTEXT, "Added receipt " + receipt.getReceiptId() + " to JSON array");
        }
        
        return receiptsArray;
    }
    
    /**
     * Add payment information to receipt JSON
     */
    private static void addPaymentInfoToReceiptJson(JSONObject receiptJson, float owedAmount, float paidAmount) {
        boolean hasPaid = false;
        if (owedAmount > 0.01f) {
            hasPaid = paidAmount >= owedAmount - 0.01f; // Allow small rounding differences
        } else if (paidAmount > 0.01f) {
            hasPaid = true;
        }
        
        receiptJson.put("userOwedAmount", owedAmount);
        receiptJson.put("userPaidAmount", paidAmount);
        receiptJson.put("userHasPaid", hasPaid);
    }
    
    /**
     * Send activity receipts response
     */
    private static void sendActivityReceiptsResponse(HttpExchange exchange, String userIdStr, JSONArray receiptsArray) throws IOException {
        Logger.debug(CONTEXT, "Returning response with " + receiptsArray.length() + " receipts");
        
        JSONObject resp = ErrorResponse.success(null);
        resp.put("userId", userIdStr);
        resp.put("receipts", receiptsArray);
        
        ErrorResponse.sendJson(exchange, 200, resp);
        Logger.debug(CONTEXT, "Response sent successfully");
    }
}

