package controllers;

import services.ReceiptService;
import models.Receipt;
import models.ReceiptItem;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for handling receipt viewing and acceptance/decline operations.
 * Provides endpoints for users to view receipts sent to them and accept or decline them.
 */
public class ReceiptController {

    private static final ReceiptService receiptService = ReceiptService.getInstance();

    /**
     * Handler for viewing a receipt by ID.
     * GET /api/receipts/view?receiptId=X&userId=Y
     */
    public static class ViewReceiptHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCors(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405, new JSONObject().put("success", false).put("message", "Method not allowed"));
                return;
            }
            
            Map<String, String> query = parseQuery(exchange.getRequestURI());
            try {
                int userId = Integer.parseInt(query.getOrDefault("userId", ""));
                int receiptId = Integer.parseInt(query.getOrDefault("receiptId", ""));
                
                Receipt receipt = receiptService.getReceipt(userId, receiptId);
                
                if (receipt == null) {
                    sendJson(exchange, 404, new JSONObject()
                        .put("success", false)
                        .put("message", "Receipt not found"));
                    return;
                }
                
                // Build receipt JSON with all details including items
                JSONObject receiptJson = buildReceiptJson(receipt);
                receiptJson.put("status", receiptService.getReceiptStatus(userId, receiptId));
                
                JSONObject resp = new JSONObject()
                    .put("success", true)
                    .put("receipt", receiptJson);
                
                sendJson(exchange, 200, resp);
            } catch (Exception e) {
                sendJson(exchange, 400, new JSONObject()
                    .put("success", false)
                    .put("message", "Invalid parameters: " + e.getMessage()));
            }
        }
    }

    /**
     * Handler for listing all pending receipts for a user.
     * GET /api/receipts/pending?userId=X
     */
    public static class ListPendingReceiptsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCors(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405, new JSONObject().put("success", false).put("message", "Method not allowed"));
                return;
            }
            
            Map<String, String> query = parseQuery(exchange.getRequestURI());
            try {
                int userId = Integer.parseInt(query.getOrDefault("userId", ""));
                
                List<Receipt> pendingReceipts = receiptService.getPendingReceipts(userId);
                JSONArray receiptsArray = new JSONArray();
                
                for (Receipt receipt : pendingReceipts) {
                    JSONObject receiptJson = buildReceiptJson(receipt);
                    receiptJson.put("status", receiptService.getReceiptStatus(userId, receipt.getReceiptId()));
                    receiptsArray.put(receiptJson);
                }
                
                JSONObject resp = new JSONObject()
                    .put("success", true)
                    .put("userId", userId)
                    .put("receipts", receiptsArray);
                
                sendJson(exchange, 200, resp);
            } catch (Exception e) {
                sendJson(exchange, 400, new JSONObject()
                    .put("success", false)
                    .put("message", "Invalid parameters: " + e.getMessage()));
            }
        }
    }

    /**
     * Handler for accepting a receipt.
     * POST /api/receipts/accept?receiptId=X&userId=Y
     */
    public static class AcceptReceiptHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCors(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405, new JSONObject().put("success", false).put("message", "Method not allowed"));
                return;
            }
            
            Map<String, String> query = parseQuery(exchange.getRequestURI());
            try {
                int userId = Integer.parseInt(query.getOrDefault("userId", ""));
                int receiptId = Integer.parseInt(query.getOrDefault("receiptId", ""));
                
                boolean accepted = receiptService.acceptReceipt(userId, receiptId);
                
                if (!accepted) {
                    sendJson(exchange, 400, new JSONObject()
                        .put("success", false)
                        .put("message", "Receipt could not be accepted. It may not exist, be already processed, or not be accessible."));
                    return;
                }
                
                JSONObject resp = new JSONObject()
                    .put("success", true)
                    .put("message", "Receipt accepted successfully")
                    .put("userId", userId)
                    .put("receiptId", receiptId);
                
                sendJson(exchange, 200, resp);
            } catch (Exception e) {
                sendJson(exchange, 400, new JSONObject()
                    .put("success", false)
                    .put("message", "Invalid parameters: " + e.getMessage()));
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
            addCors(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405, new JSONObject().put("success", false).put("message", "Method not allowed"));
                return;
            }
            
            Map<String, String> query = parseQuery(exchange.getRequestURI());
            try {
                int userId = Integer.parseInt(query.getOrDefault("userId", ""));
                int receiptId = Integer.parseInt(query.getOrDefault("receiptId", ""));
                
                boolean declined = receiptService.declineReceipt(userId, receiptId);
                
                if (!declined) {
                    sendJson(exchange, 400, new JSONObject()
                        .put("success", false)
                        .put("message", "Receipt could not be declined. It may not exist, be already processed, or not be accessible."));
                    return;
                }
                
                JSONObject resp = new JSONObject()
                    .put("success", true)
                    .put("message", "Receipt declined successfully")
                    .put("userId", userId)
                    .put("receiptId", receiptId);
                
                sendJson(exchange, 200, resp);
            } catch (Exception e) {
                sendJson(exchange, 400, new JSONObject()
                    .put("success", false)
                    .put("message", "Invalid parameters: " + e.getMessage()));
            }
        }
    }

    /**
     * Helper method to build a JSON object from a Receipt model.
     */
    private static JSONObject buildReceiptJson(Receipt receipt) {
        JSONObject receiptJson = new JSONObject()
            .put("receiptId", receipt.getReceiptId())
            .put("uploadedBy", receipt.getUploadedBy())
            .put("merchantName", receipt.getMerchantName())
            .put("date", receipt.getDate().getTime())
            .put("totalAmount", receipt.getTotalAmount())
            .put("tipAmount", receipt.getTipAmount())
            .put("taxAmount", receipt.getTaxAmount())
            .put("imageUrl", receipt.getImageUrl())
            .put("status", receipt.getStatus());
        
        // Add items array
        JSONArray itemsArray = new JSONArray();
        for (ReceiptItem item : receipt.getItems()) {
            JSONObject itemJson = new JSONObject()
                .put("itemId", item.getItemId())
                .put("receiptId", item.getReceiptId())
                .put("name", item.getName())
                .put("price", item.getPrice())
                .put("quantity", item.getQuantity())
                .put("category", item.getCategory());
            itemsArray.put(itemJson);
        }
        receiptJson.put("items", itemsArray);
        
        return receiptJson;
    }

    private static Map<String, String> parseQuery(URI uri) {
        String query = uri.getQuery();
        if (query == null || query.isEmpty()) {
            return Map.of();
        }
        return java.util.Arrays.stream(query.split("&"))
                .map(kv -> kv.split("=", 2))
                .collect(Collectors.toMap(
                        kv -> kv[0],
                        kv -> kv.length > 1 ? kv[1] : ""
                ));
    }

    private static void addCors(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }

    private static void sendJson(HttpExchange exchange, int status, JSONObject json) throws IOException {
        byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }
}
