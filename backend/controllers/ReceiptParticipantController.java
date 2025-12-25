package controllers;

import services.ReceiptService;
import models.User;
import database.ReceiptDAO;
import database.UserDAO;
import utils.AuthMiddleware;
import utils.ValidationUtils;
import utils.ErrorResponse;
import utils.Logger;
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
import java.util.List;
import java.util.Map;

/**
 * Controller for handling receipt participant operations (accept, decline, add).
 * Provides endpoints for managing receipt participants.
 */
public class ReceiptParticipantController {

    private static final ReceiptService receiptService = ReceiptService.getInstance();
    private static final String CONTEXT = "ReceiptParticipantController";

    /**
     * Handler for accepting a receipt.
     * POST /api/receipts/accept?receiptId=X&userId=Y
     */
    public static class AcceptReceiptHandler implements HttpHandler {
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
            
            Map<String, String> query = RequestUtils.parseQuery(exchange.getRequestURI());
            try {
                String userIdStr = query.getOrDefault("userId", "");
                int receiptId = Integer.parseInt(query.getOrDefault("receiptId", ""));
                
                if (userIdStr.isEmpty()) {
                    ErrorResponse.sendError(exchange, 400, "userId parameter is required");
                    return;
                }
                
                // Verify authenticated user matches
                if (!userIdStr.equals(authenticatedUserId)) {
                    ErrorResponse.sendError(exchange, 403, "Forbidden: Cannot accept receipts for another user");
                    return;
                }
                
                // Use String userId version
                boolean accepted = receiptService.getReceiptDAO().updateParticipantStatus(
                    receiptId, userIdStr, "accepted");
                
                if (!accepted) {
                    ErrorResponse.sendError(exchange, 400, "Receipt could not be accepted. It may not exist, be already processed, or not be accessible.");
                    return;
                }
                
                JSONObject resp = ErrorResponse.success("Receipt accepted successfully");
                resp.put("userId", userIdStr);
                resp.put("receiptId", receiptId);
                
                ErrorResponse.sendJson(exchange, 200, resp);
            } catch (Exception e) {
                Logger.error(CONTEXT, "AcceptReceiptHandler error: " + e.getMessage(), e);
                ErrorResponse.sendError(exchange, 400, "Invalid parameters: " + e.getMessage());
            }
        }
    }

    /**
     * Handler for declining a receipt.
     * POST /api/receipts/decline?receiptId=X&userId=Y
     */
    public static class DeclineReceiptHandler implements HttpHandler {
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
            
            Map<String, String> query = RequestUtils.parseQuery(exchange.getRequestURI());
            try {
                String userIdStr = query.getOrDefault("userId", "");
                int receiptId = Integer.parseInt(query.getOrDefault("receiptId", ""));
                
                if (userIdStr.isEmpty()) {
                    ErrorResponse.sendError(exchange, 400, "userId parameter is required");
                    return;
                }
                
                // Use String userId version
                boolean declined = receiptService.getReceiptDAO().updateParticipantStatus(
                    receiptId, userIdStr, "declined");
                
                if (!declined) {
                    ErrorResponse.sendError(exchange, 400, "Receipt could not be declined. It may not exist, be already processed, or not be accessible.");
                    return;
                }
                
                JSONObject resp = ErrorResponse.success("Receipt declined successfully");
                resp.put("userId", userIdStr);
                resp.put("receiptId", receiptId);
                
                ErrorResponse.sendJson(exchange, 200, resp);
            } catch (Exception e) {
                Logger.error(CONTEXT, "DeclineReceiptHandler error: " + e.getMessage(), e);
                ErrorResponse.sendError(exchange, 400, "Invalid parameters: " + e.getMessage());
            }
        }
    }

    /**
     * Handler for adding participants to an existing receipt.
     * POST /api/receipts/add-participants?receiptId=X&userId=Y
     * Body: JSON with array of participant emails: {"participants": ["email1", "email2"]}
     */
    public static class AddParticipantsHandler implements HttpHandler {
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
            
            Map<String, String> query = RequestUtils.parseQuery(exchange.getRequestURI());
            try {
                String userIdStr = query.getOrDefault("userId", "");
                int receiptId = Integer.parseInt(query.getOrDefault("receiptId", "0"));
                
                if (userIdStr.isEmpty() || receiptId == 0) {
                    ErrorResponse.sendError(exchange, 400, "receiptId and userId are required");
                    return;
                }
                
                // Verify authenticated user matches (only receipt uploader can add participants)
                if (!userIdStr.equals(authenticatedUserId)) {
                    ErrorResponse.sendError(exchange, 403, "Forbidden: Cannot add participants to receipts for another user");
                    return;
                }
                
                // Verify user is the uploader of this receipt
                ReceiptDAO receiptDAO = receiptService.getReceiptDAO();
                String uploaderId = receiptDAO.getReceiptUploadedBy(receiptId);
                
                if (uploaderId == null || !uploaderId.equals(userIdStr)) {
                    ErrorResponse.sendError(exchange, 403, "Only the receipt uploader can add participants");
                    return;
                }
                
                // Read request body
                String requestBody = readRequestBody(exchange);
                JSONObject json = new JSONObject(requestBody);
                JSONArray participantsArray = json.getJSONArray("participants");
                
                // Convert participant emails to user IDs and add them
                UserDAO userDAO = new UserDAO();
                List<String> validParticipantIds = new ArrayList<>();
                
                for (int i = 0; i < participantsArray.length(); i++) {
                    String email = participantsArray.getString(i).trim().toLowerCase();
                    
                    // Find user by email
                    User participantUser = userDAO.findUserByEmail(email);
                    
                    if (participantUser != null) {
                        String participantUserId = participantUser.getUserId();
                        
                        // Skip if it's the uploader (already added)
                        if (!participantUserId.equals(userIdStr)) {
                            // Add participant to receipt
                            boolean added = receiptDAO.addReceiptParticipant(receiptId, participantUserId);
                            
                            if (added) {
                                validParticipantIds.add(participantUserId);
                            }
                        }
                    }
                }
                
                JSONObject resp = ErrorResponse.success("Participants added successfully");
                resp.put("participantsAdded", validParticipantIds.size());
                
                ErrorResponse.sendJson(exchange, 200, resp);
                
            } catch (Exception e) {
                Logger.error(CONTEXT, "Error adding participants: " + e.getMessage(), e);
                ErrorResponse.sendError(exchange, 400, "Error adding participants: " + e.getMessage());
            }
        }
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

