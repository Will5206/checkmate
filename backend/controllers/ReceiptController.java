package controllers;

import services.ReceiptService;
import models.Receipt;
import models.ReceiptItem;
import database.ReceiptDAO;
import database.UserDAO;
import models.User;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Controller for handling receipt operations including parsing, viewing, creation, and management.
 * Provides endpoints for users to parse receipts, view receipts, accept/decline them, and manage receipt data.
 */
public class ReceiptController {

    private static final ReceiptService receiptService = ReceiptService.getInstance();
    private static final String UPLOAD_DIR = "receipts/";
    private static final String PYTHON_SCRIPT = "receipt_parser_local.py";

    /**
     * Handler for parsing a receipt image using OpenAI.
     * POST /api/receipt/parse
     * Body: Raw image bytes (image/jpeg)
     */
    public static class ParseReceiptHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Enable CORS
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405, new JSONObject().put("success", false).put("message", "Method not allowed"));
                return;
            }
            
            Path tempPath = null;
            try {
                System.out.println("========================================");
                System.out.println("[ReceiptController] Received receipt parse request at " + new java.util.Date());
                System.out.println("========================================");
                
                // Read image from request body (simplified - reads raw bytes)
                System.out.println("[ReceiptController] Reading image data from request body...");
                byte[] imageData = exchange.getRequestBody().readAllBytes();
                System.out.println("[ReceiptController] ✓ Read " + imageData.length + " bytes of image data (" + (imageData.length / 1024) + " KB)");
                
                if (imageData.length == 0) {
                    sendJson(exchange, 400, new JSONObject().put("success", false).put("message", "No image data received"));
                    return;
                }
                
                // Save to temp file
                System.out.println("[ReceiptController] Saving image to temp file...");
                String filename = "temp_" + UUID.randomUUID().toString() + ".jpg";
                tempPath = Paths.get(UPLOAD_DIR, filename);
                Files.createDirectories(tempPath.getParent());
                Files.write(tempPath, imageData);
                System.out.println("[ReceiptController] ✓ Saved image to: " + tempPath.toString());
                
                // Call Python parser - use absolute path to project root
                System.out.println("[ReceiptController] Preparing to call Python parser...");
                String projectRoot = System.getProperty("user.dir");
                File projectRootFile = new File(projectRoot);
                File pythonScriptFile = new File(projectRootFile, PYTHON_SCRIPT);
                
                if (!pythonScriptFile.exists()) {
                    System.err.println("[ReceiptController] ERROR: Python script not found at: " + pythonScriptFile.getAbsolutePath());
                    sendJson(exchange, 500, new JSONObject()
                        .put("success", false)
                        .put("message", "Python parser script not found"));
                    return;
                }
                
                System.out.println("[ReceiptController] Python script: " + pythonScriptFile.getAbsolutePath());
                System.out.println("[ReceiptController] Working directory: " + projectRoot);
                System.out.println("[ReceiptController] Image path: " + tempPath.toAbsolutePath());
                
                ProcessBuilder pb = new ProcessBuilder(
                    "python3",
                    pythonScriptFile.getAbsolutePath(),
                    tempPath.toAbsolutePath().toString()
                );
                pb.directory(projectRootFile);
                // do NOT redirect error stream; keep stdout and stderr separate
                System.out.println("[ReceiptController] Starting Python process...");
                long pythonStartTime = System.currentTimeMillis();
                Process process = pb.start();
                System.out.println("[ReceiptController] ✓ Python process started (PID: " + process.pid() + "), waiting for output...");
                
                // Read stdout and stderr in parallel to avoid deadlock
                // Use separate threads to read both streams simultaneously
                StringBuilder output = new StringBuilder();
                StringBuilder errorOutput = new StringBuilder();
                
                Thread stdoutThread = new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            System.out.println("[ReceiptController] Python stdout: " + line);
                            // Keep only the last line, expected to be the JSON
                            output.setLength(0);
                            output.append(line);
                        }
                    } catch (IOException e) {
                        System.err.println("[ReceiptController] Error reading stdout: " + e.getMessage());
                    }
                });
                
                Thread stderrThread = new Thread(() -> {
                    try (BufferedReader errorReader = new BufferedReader(
                            new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                        String errorLine;
                        while ((errorLine = errorReader.readLine()) != null) {
                            errorOutput.append(errorLine).append("\n");
                            System.err.println("[ReceiptController] Python stderr: " + errorLine);
                        }
                    } catch (IOException e) {
                        System.err.println("[ReceiptController] Error reading stderr: " + e.getMessage());
                    }
                });
                
                stdoutThread.start();
                stderrThread.start();
                
                System.out.println("[ReceiptController] Waiting for Python process to complete (timeout: 120 seconds)...");
                
                // Wait for process with timeout (120 seconds for OpenAI API call)
                boolean finished = process.waitFor(120, TimeUnit.SECONDS);
                int exitCode;
                
                if (!finished) {
                    System.err.println("[ReceiptController] Python process timed out after 120 seconds - killing process");
                    process.destroyForcibly();
                    exitCode = -1;
                    
                    // Wait a bit for threads to finish
                    stdoutThread.join(2000);
                    stderrThread.join(2000);
                    
                    sendJson(exchange, 500, new JSONObject()
                        .put("success", false)
                        .put("message", "Receipt parsing timed out. This usually takes 15-30 seconds. Please check:\n1. OpenAI API key is configured\n2. Internet connection is working\n3. Try again with a clearer receipt image"));
                    return;
                } else {
                    exitCode = process.exitValue();
                }
                
                // Wait for threads to finish reading
                stdoutThread.join(5000); // 5 second timeout
                stderrThread.join(5000);
                long pythonDuration = System.currentTimeMillis() - pythonStartTime;
                System.out.println("[ReceiptController] ✓ Python process completed in " + pythonDuration + "ms with exit code: " + exitCode);
                
                // Clean up temp file
                if (tempPath != null) {
                    Files.deleteIfExists(tempPath);
                    System.out.println("[ReceiptController] Cleaned up temp file");
                }
                
                if (exitCode != 0) {
                    System.err.println("[ReceiptController] Python script failed with exit code: " + exitCode);
                    System.err.println("[ReceiptController] Python error output: " + errorOutput.toString());
                    System.err.println("[ReceiptController] Python stdout output: " + output.toString());
                    
                    // Check if output contains an error message (JSON error from Python)
                    String errorMsg = "Error reading receipt. Python script failed with exit code: " + exitCode;
                    String detailedError = "";
                    
                    if (output.length() > 0) {
                        try {
                            JSONObject errorJson = new JSONObject(output.toString());
                            if (errorJson.has("error")) {
                                errorMsg = errorJson.getString("error");
                            }
                        } catch (Exception e) {
                            // Not JSON, check for common error patterns
                            String outputStr = output.toString();
                            if (outputStr.contains("OPENAI_API_KEY")) {
                                errorMsg = "OpenAI API key not configured. Please check your openai_key.env file.";
                            } else if (outputStr.contains("ModuleNotFoundError") || outputStr.contains("ImportError")) {
                                errorMsg = "Python dependencies missing. Please run: pip install openai pillow pillow-heif python-dotenv";
                            } else if (outputStr.length() > 0) {
                                errorMsg = "Python script error: " + outputStr.substring(0, Math.min(200, outputStr.length()));
                            }
                        }
                    }
                    
                    // Include stderr in detailed error if available
                    if (errorOutput.length() > 0) {
                        String stderrStr = errorOutput.toString();
                        detailedError = "\n\nPython error details:\n" + stderrStr.substring(0, Math.min(500, stderrStr.length()));
                        if (stderrStr.contains("OPENAI_API_KEY")) {
                            errorMsg = "OpenAI API key not configured. Please check your openai_key.env file.";
                        } else if (stderrStr.contains("API key") || stderrStr.contains("authentication")) {
                            errorMsg = "OpenAI API authentication failed. Please check your API key is valid.";
                        } else if (stderrStr.contains("rate limit") || stderrStr.contains("quota")) {
                            errorMsg = "OpenAI API rate limit or quota exceeded. Please try again later.";
                        }
                    }
                    
                    sendJson(exchange, 500, new JSONObject()
                        .put("success", false)
                        .put("message", errorMsg + detailedError));
                    return;
                }
                
                // Parse Python output (should be pure JSON on stdout)
                String pythonOutput = output.toString().trim();
                if (pythonOutput.isEmpty()) {
                    System.err.println("[ReceiptController] Python script returned empty output");
                    sendJson(exchange, 500, new JSONObject()
                        .put("success", false)
                        .put("message", "Python script returned no output. Check server logs for details."));
                    return;
                }
                
                System.out.println("[ReceiptController] Parsing Python output (length: " + pythonOutput.length() + ")");
                System.out.println("[ReceiptController] Raw Python output (first 500 chars):");
                System.out.println(pythonOutput.length() > 500 ? pythonOutput.substring(0, 500) + "..." : pythonOutput);
                
                JSONObject receiptData;
                try {
                    receiptData = new JSONObject(pythonOutput);
                } catch (Exception e) {
                    System.err.println("[ReceiptController] Failed to parse Python output as JSON: " + e.getMessage());
                    System.err.println("[ReceiptController] Full output: " + pythonOutput);
                    sendJson(exchange, 500, new JSONObject()
                        .put("success", false)
                        .put("message", "Failed to parse receipt data. Python script may have encountered an error."));
                    return;
                }
                System.out.println("[ReceiptController] Successfully parsed receipt data");
                
                // Build response
                JSONObject response = new JSONObject();
                response.put("success", true);
                response.put("merchant", receiptData.optString("merchant", "Unknown"));
                response.put("date", receiptData.optString("date", ""));
                response.put("items", receiptData.optJSONArray("items"));
                response.put("subtotal", receiptData.optDouble("subtotal", 0));
                response.put("tax", receiptData.optDouble("tax", 0));
                response.put("total", receiptData.optDouble("total", 0));
                
                sendJson(exchange, 200, response);
                
            } catch (Exception e) {
                // Clean up temp file on error
                if (tempPath != null) {
                    try {
                        Files.deleteIfExists(tempPath);
                    } catch (Exception ignored) {}
                }
                
                e.printStackTrace();
                sendJson(exchange, 500, new JSONObject()
                    .put("success", false)
                    .put("message", "Error reading receipt: " + e.getMessage()));
            }
        }
    }

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
                String userIdStr = query.getOrDefault("userId", "");
                
                if (userIdStr.isEmpty()) {
                    sendJson(exchange, 400, new JSONObject()
                        .put("success", false)
                        .put("message", "userId parameter is required"));
                    return;
                }
                
                // Use String userId version (for UUIDs)
                List<Receipt> pendingReceipts = receiptService.getPendingReceipts(userIdStr);
                JSONArray receiptsArray = new JSONArray();
                
                for (Receipt receipt : pendingReceipts) {
                    JSONObject receiptJson = buildReceiptJson(receipt);
                    // Get status using String userId
                    String status = receiptService.getReceiptDAO().getParticipantStatus(
                        receipt.getReceiptId(), userIdStr);
                    receiptJson.put("status", status != null ? status : "pending");
                    receiptsArray.put(receiptJson);
                }
                
                JSONObject resp = new JSONObject()
                    .put("success", true)
                    .put("userId", userIdStr)
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
                String userIdStr = query.getOrDefault("userId", "");
                int receiptId = Integer.parseInt(query.getOrDefault("receiptId", ""));
                
                if (userIdStr.isEmpty()) {
                    sendJson(exchange, 400, new JSONObject()
                        .put("success", false)
                        .put("message", "userId parameter is required"));
                    return;
                }
                
                // Use String userId version
                boolean accepted = receiptService.getReceiptDAO().updateParticipantStatus(
                    receiptId, userIdStr, "accepted");
                
                if (!accepted) {
                    sendJson(exchange, 400, new JSONObject()
                        .put("success", false)
                        .put("message", "Receipt could not be accepted. It may not exist, be already processed, or not be accessible."));
                    return;
                }
                
                JSONObject resp = new JSONObject()
                    .put("success", true)
                    .put("message", "Receipt accepted successfully")
                    .put("userId", userIdStr)
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
     * Handler for creating a new receipt.
     * POST /api/receipts/create?userId=X
     * Body: JSON with receipt data (restaurant_name, total_amount, tax, tip, items, participants)
     */
    public static class CreateReceiptHandler implements HttpHandler {
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
            
            try {
                // Get userId from query params
                Map<String, String> query = parseQuery(exchange.getRequestURI());
                String userIdStr = query.getOrDefault("userId", "");
                
                if (userIdStr.isEmpty()) {
                    sendJson(exchange, 400, new JSONObject()
                        .put("success", false)
                        .put("message", "userId parameter is required"));
                    return;
                }
                
                // Read request body
                String requestBody = readRequestBody(exchange);
                JSONObject json = new JSONObject(requestBody);
                
                // Extract receipt data
                String merchantName = json.optString("restaurant_name", json.optString("merchantName", "Unknown Merchant"));
                double totalAmount = json.getDouble("total_amount");
                double tax = json.optDouble("tax", 0.0);
                double tip = json.optDouble("tip", 0.0);
                String imageUrl = json.optString("imageUrl", json.optString("image_url", ""));
                
                // Get items
                JSONArray itemsArray = json.getJSONArray("items");
                
                // Get participants (email addresses)
                JSONArray participantsArray = json.getJSONArray("participants");
                
                // Create receipt in database
                ReceiptDAO receiptDAO = receiptService.getReceiptDAO();
                Date receiptDate = new Date();
                
                Receipt receipt = receiptDAO.createReceipt(
                    userIdStr,
                    merchantName,
                    receiptDate,
                    (float) totalAmount,
                    (float) tip,
                    (float) tax,
                    imageUrl
                );
                
                if (receipt == null) {
                    sendJson(exchange, 500, new JSONObject()
                        .put("success", false)
                        .put("message", "Failed to create receipt"));
                    return;
                }
                
                // Add items to receipt
                UserDAO userDAO = new UserDAO();
                List<String> validParticipantIds = new ArrayList<>();
                
                for (int i = 0; i < itemsArray.length(); i++) {
                    JSONObject itemJson = itemsArray.getJSONObject(i);
                    String itemName = itemJson.getString("name");
                    double itemPrice = itemJson.getDouble("price");
                    int quantity = itemJson.optInt("qty", itemJson.optInt("quantity", 1));
                    String category = itemJson.optString("category", null);
                    
                    ReceiptItem item = receiptDAO.addReceiptItem(
                        receipt.getReceiptId(),
                        itemName,
                        (float) itemPrice,
                        quantity,
                        category
                    );
                    
                    if (item != null) {
                        receipt.addItem(item);
                    }
                }
                
                // Add uploader as participant (so they can claim items and pay)
                receiptDAO.addReceiptParticipant(receipt.getReceiptId(), userIdStr);
                validParticipantIds.add(userIdStr);
                
                // Convert participant emails to user IDs and add them
                for (int i = 0; i < participantsArray.length(); i++) {
                    String email = participantsArray.getString(i).trim().toLowerCase();
                    
                    // Find user by email
                    User participantUser = userDAO.findUserByEmail(email);
                    
                    if (participantUser != null) {
                        String participantUserId = participantUser.getUserId();
                        
                        // Skip if it's the uploader (already added)
                        if (!participantUserId.equals(userIdStr)) {
                            // Add participant to receipt
                            boolean added = receiptDAO.addReceiptParticipant(receipt.getReceiptId(), participantUserId);
                            
                            if (added) {
                                validParticipantIds.add(participantUserId);
                            }
                        }
                    }
                }
                
                // Reload receipt with items
                receipt = receiptDAO.getReceiptById(receipt.getReceiptId());
                
                // Build response
                JSONObject receiptJson = buildReceiptJson(receipt);
                receiptJson.put("status", "pending");
                
                JSONObject resp = new JSONObject()
                    .put("success", true)
                    .put("message", "Receipt created successfully")
                    .put("receipt", receiptJson)
                    .put("participantsAdded", validParticipantIds.size());
                
                sendJson(exchange, 201, resp);
                
            } catch (Exception e) {
                e.printStackTrace();
                sendJson(exchange, 400, new JSONObject()
                    .put("success", false)
                    .put("message", "Error creating receipt: " + e.getMessage()));
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
                String userIdStr = query.getOrDefault("userId", "");
                int receiptId = Integer.parseInt(query.getOrDefault("receiptId", ""));
                
                if (userIdStr.isEmpty()) {
                    sendJson(exchange, 400, new JSONObject()
                        .put("success", false)
                        .put("message", "userId parameter is required"));
                    return;
                }
                
                // Use String userId version
                boolean declined = receiptService.getReceiptDAO().updateParticipantStatus(
                    receiptId, userIdStr, "declined");
                
                if (!declined) {
                    sendJson(exchange, 400, new JSONObject()
                        .put("success", false)
                        .put("message", "Receipt could not be declined. It may not exist, be already processed, or not be accessible."));
                    return;
                }
                
                JSONObject resp = new JSONObject()
                    .put("success", true)
                    .put("message", "Receipt declined successfully")
                    .put("userId", userIdStr)
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
     * Handler for getting all receipts for a user (activity/history).
     * GET /api/receipts/activity?userId=X
     */
    public static class GetActivityReceiptsHandler implements HttpHandler {
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
                String userIdStr = query.getOrDefault("userId", "");
                
                if (userIdStr.isEmpty()) {
                    sendJson(exchange, 400, new JSONObject()
                        .put("success", false)
                        .put("message", "userId parameter is required"));
                    return;
                }
                
                // Get all receipts for this user (accepted, declined, or uploaded)
                List<Receipt> receipts = receiptService.getAllReceiptsForUser(userIdStr);
                
                JSONArray receiptsArray = new JSONArray();
                for (Receipt receipt : receipts) {
                    JSONObject receiptJson = buildReceiptJson(receipt);
                    receiptsArray.put(receiptJson);
                }
                
                JSONObject resp = new JSONObject()
                    .put("success", true)
                    .put("userId", userIdStr)
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
     * Helper method to build a JSON object from a Receipt model.
     */
    private static JSONObject buildReceiptJson(Receipt receipt) {
        ReceiptDAO receiptDAO = receiptService.getReceiptDAO();
        String uploadedByStr = receiptDAO.getReceiptUploadedBy(receipt.getReceiptId());
        
        JSONObject receiptJson = new JSONObject()
            .put("receiptId", receipt.getReceiptId())
            .put("uploadedBy", uploadedByStr != null ? uploadedByStr : String.valueOf(receipt.getUploadedBy()))
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
                .put("qty", item.getQuantity()) // Also include as 'qty' for frontend compatibility
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
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
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

    /**
     * Handler for claiming/unclaiming items from a receipt.
     * POST /api/receipts/items/claim?receiptId=X&itemId=Y&userId=Z&quantity=1
     * DELETE /api/receipts/items/claim?receiptId=X&itemId=Y&userId=Z (to unclaim)
     */
    public static class ClaimItemHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCors(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            Map<String, String> query = parseQuery(exchange.getRequestURI());
            try {
                String userIdStr = query.getOrDefault("userId", "");
                int receiptId = Integer.parseInt(query.getOrDefault("receiptId", "0"));
                int itemId = Integer.parseInt(query.getOrDefault("itemId", "0"));
                
                if (userIdStr.isEmpty() || receiptId == 0 || itemId == 0) {
                    sendJson(exchange, 400, new JSONObject()
                        .put("success", false)
                        .put("message", "receiptId, itemId, and userId are required"));
                    return;
                }
                
                ReceiptDAO receiptDAO = receiptService.getReceiptDAO();
                
                // Check if item is already paid for
                if (receiptDAO.isItemPaid(itemId)) {
                    sendJson(exchange, 400, new JSONObject()
                        .put("success", false)
                        .put("message", "This item has already been paid for and cannot be claimed"));
                    return;
                }
                
                boolean success;
                
                if ("DELETE".equals(exchange.getRequestMethod())) {
                    // Unclaim item - only if not paid
                    Map<String, Object> paymentInfo = receiptDAO.getItemPaymentInfo(itemId);
                    if (paymentInfo != null) {
                        sendJson(exchange, 400, new JSONObject()
                            .put("success", false)
                            .put("message", "Cannot unclaim an item that has been paid for"));
                        return;
                    }
                    success = receiptDAO.unassignItemFromUser(itemId, userIdStr);
                } else if ("POST".equals(exchange.getRequestMethod())) {
                    // Claim item
                    int quantity = Integer.parseInt(query.getOrDefault("quantity", "1"));
                    success = receiptDAO.assignItemToUser(itemId, userIdStr, quantity);
                } else {
                    sendJson(exchange, 405, new JSONObject()
                        .put("success", false)
                        .put("message", "Method not allowed"));
                    return;
                }
                
                if (success) {
                    // Calculate updated amount owed
                    float owedAmount = receiptDAO.calculateUserOwedAmount(receiptId, userIdStr);
                    
                    JSONObject resp = new JSONObject()
                        .put("success", true)
                        .put("message", "DELETE".equals(exchange.getRequestMethod()) ? "Item unclaimed" : "Item claimed")
                        .put("owedAmount", owedAmount);
                    sendJson(exchange, 200, resp);
                } else {
                    sendJson(exchange, 400, new JSONObject()
                        .put("success", false)
                        .put("message", "Failed to update item assignment"));
                }
            } catch (Exception e) {
                sendJson(exchange, 400, new JSONObject()
                    .put("success", false)
                    .put("message", "Invalid parameters: " + e.getMessage()));
            }
        }
    }

    /**
     * Handler for getting item assignments and owed amount for a user.
     * GET /api/receipts/items/assignments?receiptId=X&userId=Y
     */
    public static class GetItemAssignmentsHandler implements HttpHandler {
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
                String userIdStr = query.getOrDefault("userId", "");
                int receiptId = Integer.parseInt(query.getOrDefault("receiptId", "0"));
                
                if (userIdStr.isEmpty() || receiptId == 0) {
                    sendJson(exchange, 400, new JSONObject()
                        .put("success", false)
                        .put("message", "receiptId and userId are required"));
                    return;
                }
                
                System.out.println("[ReceiptController] GetItemAssignmentsHandler - receiptId: " + receiptId + ", userId: " + userIdStr);
                
                ReceiptDAO receiptDAO = receiptService.getReceiptDAO();
                database.UserDAO userDAO = new database.UserDAO();
                
                // Get user's assignments
                Map<Integer, Integer> assignments = receiptDAO.getItemAssignmentsForUser(receiptId, userIdStr);
                float owedAmount = receiptDAO.calculateUserOwedAmount(receiptId, userIdStr);
                
                // Get all item assignments for receipt (to show payment status)
                List<Map<String, Object>> allAssignments = receiptDAO.getAllItemAssignmentsForReceipt(receiptId);
                
                System.out.println("[ReceiptController] Found " + assignments.size() + " item assignments, owedAmount: " + owedAmount);
                
                // Build assignments JSON with payment info
                JSONObject assignmentsJson = new JSONObject();
                for (Map.Entry<Integer, Integer> entry : assignments.entrySet()) {
                    assignmentsJson.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                
                // Build item payment info (which items are paid and by whom)
                JSONObject itemPaymentInfo = new JSONObject();
                for (Map<String, Object> assignment : allAssignments) {
                    int itemId = (Integer) assignment.get("itemId");
                    Boolean isPaid = (Boolean) assignment.get("isPaid");
                    if (isPaid) {
                        String paidByUserId = (String) assignment.get("paidBy");
                        // Get payer's name
                        models.User payer = userDAO.findUserById(paidByUserId);
                        String payerName = payer != null ? payer.getName() : "Unknown";
                        
                        JSONObject paymentInfo = new JSONObject()
                            .put("paidBy", paidByUserId)
                            .put("payerName", payerName)
                            .put("paidAt", assignment.get("paidAt"));
                        itemPaymentInfo.put(String.valueOf(itemId), paymentInfo);
                    }
                }
                
                JSONObject resp = new JSONObject()
                    .put("success", true)
                    .put("assignments", assignmentsJson)
                    .put("owedAmount", owedAmount)
                    .put("itemPaymentInfo", itemPaymentInfo);
                
                System.out.println("[ReceiptController] Sending response: " + resp.toString());
                sendJson(exchange, 200, resp);
            } catch (Exception e) {
                System.err.println("[ReceiptController] Error in GetItemAssignmentsHandler: " + e.getMessage());
                e.printStackTrace();
                sendJson(exchange, 500, new JSONObject()
                    .put("success", false)
                    .put("message", "Error getting item assignments: " + e.getMessage()));
            }
        }
    }

    /**
     * Handler for paying for a receipt.
     * POST /api/receipts/pay?receiptId=X&userId=Y
     */
    public static class PayReceiptHandler implements HttpHandler {
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
                String userIdStr = query.getOrDefault("userId", "");
                int receiptId = Integer.parseInt(query.getOrDefault("receiptId", "0"));
                
                if (userIdStr.isEmpty() || receiptId == 0) {
                    sendJson(exchange, 400, new JSONObject()
                        .put("success", false)
                        .put("message", "receiptId and userId are required"));
                    return;
                }
                
                ReceiptDAO receiptDAO = receiptService.getReceiptDAO();
                
                // Calculate amount owed
                float owedAmount = receiptDAO.calculateUserOwedAmount(receiptId, userIdStr);
                float paidAmount = receiptDAO.getPaidAmount(receiptId, userIdStr);
                float remainingAmount = owedAmount - paidAmount;
                
                if (remainingAmount <= 0) {
                    sendJson(exchange, 400, new JSONObject()
                        .put("success", false)
                        .put("message", "You have already paid your full amount"));
                    return;
                }
                
                // Check user balance
                services.BalanceService balanceService = new services.BalanceService();
                double currentBalance = balanceService.getCurrentBalance(userIdStr);
                
                if (currentBalance < remainingAmount) {
                    sendJson(exchange, 400, new JSONObject()
                        .put("success", false)
                        .put("message", String.format("Insufficient balance. You have $%.2f, need $%.2f", currentBalance, remainingAmount)));
                    return;
                }
                
                // Get receipt uploader
                String uploaderId = receiptDAO.getReceiptUploadedBy(receiptId);
                if (uploaderId == null) {
                    sendJson(exchange, 400, new JSONObject()
                        .put("success", false)
                        .put("message", "Receipt not found"));
                    return;
                }
                
                // Deduct from payer's balance
                boolean balanceDeducted = balanceService.subtractFromBalance(
                    userIdStr,
                    remainingAmount,
                    services.BalanceService.TYPE_PAYMENT_SENT,
                    "Payment for receipt #" + receiptId,
                    String.valueOf(receiptId),
                    "receipt"
                );
                
                if (!balanceDeducted) {
                    sendJson(exchange, 500, new JSONObject()
                        .put("success", false)
                        .put("message", "Failed to deduct balance"));
                    return;
                }
                
                // Add to uploader's balance
                boolean balanceAdded = balanceService.addToBalance(
                    uploaderId,
                    remainingAmount,
                    services.BalanceService.TYPE_PAYMENT_RECEIVED,
                    "Payment received for receipt #" + receiptId,
                    String.valueOf(receiptId),
                    "receipt"
                );
                
                if (!balanceAdded) {
                    // Rollback: add back to payer (auto-refund)
                    try {
                        boolean refunded = balanceService.addToBalance(
                            userIdStr,
                            remainingAmount,
                            services.BalanceService.TYPE_REFUND,
                            "Refund due to payment processing error",
                            String.valueOf(receiptId),
                            "receipt"
                        );
                        if (!refunded) {
                            System.err.println("CRITICAL: Failed to refund after payment processing error. User: " + userIdStr + ", Amount: " + remainingAmount);
                        }
                    } catch (Exception refundError) {
                        System.err.println("CRITICAL: Exception during refund: " + refundError.getMessage());
                        refundError.printStackTrace();
                    }
                    sendJson(exchange, 500, new JSONObject()
                        .put("success", false)
                        .put("message", "Failed to process payment. Your balance has been refunded."));
                    return;
                }
                
                // Create transaction record
                services.TransactionService transactionService = new services.TransactionService();
                models.Transaction transaction = null;
                try {
                    transaction = transactionService.createTransaction(
                        userIdStr,
                        uploaderId,
                        remainingAmount,
                        services.TransactionService.TYPE_RECEIPT_PAYMENT,
                        "Payment for receipt #" + receiptId,
                        services.TransactionService.STATUS_COMPLETED,
                        String.valueOf(receiptId)
                    );
                } catch (Exception txError) {
                    System.err.println("Warning: Failed to create transaction record: " + txError.getMessage());
                    // Don't fail payment if transaction record fails - balances are already updated
                }
                
                // Record payment in receipt_participants
                boolean paymentRecorded = false;
                try {
                    paymentRecorded = receiptDAO.recordPayment(receiptId, userIdStr, remainingAmount);
                } catch (Exception recordError) {
                    System.err.println("Warning: Failed to record payment in receipt_participants: " + recordError.getMessage());
                    // If payment recording fails, we need to rollback since balances are updated but payment isn't recorded
                    // This could lead to inconsistent state
                    try {
                        // Refund payer
                        balanceService.addToBalance(
                            userIdStr,
                            remainingAmount,
                            services.BalanceService.TYPE_REFUND,
                            "Refund due to payment recording error",
                            String.valueOf(receiptId),
                            "receipt"
                        );
                        // Deduct from uploader
                        balanceService.subtractFromBalance(
                            uploaderId,
                            remainingAmount,
                            services.BalanceService.TYPE_REFUND,
                            "Refund due to payment recording error",
                            String.valueOf(receiptId),
                            "receipt"
                        );
                        System.err.println("Rolled back payment due to recording failure");
                    } catch (Exception rollbackError) {
                        System.err.println("CRITICAL: Failed to rollback after payment recording error: " + rollbackError.getMessage());
                        rollbackError.printStackTrace();
                    }
                    sendJson(exchange, 500, new JSONObject()
                        .put("success", false)
                        .put("message", "Failed to record payment. Your balance has been refunded."));
                    return;
                }
                
                // Mark all items assigned to this user as paid
                int itemsMarkedPaid = receiptDAO.markItemsAsPaid(receiptId, userIdStr);
                System.out.println("Marked " + itemsMarkedPaid + " item assignments as paid for user " + userIdStr);
                
                // Check if receipt should be marked as completed
                boolean isCompleted = receiptDAO.checkAndMarkReceiptCompleted(receiptId);
                
                // Get updated payment status
                float newPaidAmount = receiptDAO.getPaidAmount(receiptId, userIdStr);
                float newOwedAmount = receiptDAO.calculateUserOwedAmount(receiptId, userIdStr);
                
                JSONObject resp = new JSONObject()
                    .put("success", true)
                    .put("message", "Payment processed successfully")
                    .put("amountPaid", remainingAmount)
                    .put("paidAmount", newPaidAmount)
                    .put("owedAmount", newOwedAmount)
                    .put("receiptCompleted", isCompleted);
                
                sendJson(exchange, 200, resp);
                
            } catch (IllegalArgumentException e) {
                sendJson(exchange, 400, new JSONObject()
                    .put("success", false)
                    .put("message", e.getMessage()));
            } catch (Exception e) {
                System.err.println("[ReceiptController] Error in PayReceiptHandler: " + e.getMessage());
                e.printStackTrace();
                sendJson(exchange, 500, new JSONObject()
                    .put("success", false)
                    .put("message", "Error processing payment: " + e.getMessage()));
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
                String userIdStr = query.getOrDefault("userId", "");
                int receiptId = Integer.parseInt(query.getOrDefault("receiptId", "0"));
                
                if (userIdStr.isEmpty() || receiptId == 0) {
                    sendJson(exchange, 400, new JSONObject()
                        .put("success", false)
                        .put("message", "receiptId and userId are required"));
                    return;
                }
                
                // Verify user is the uploader of this receipt
                ReceiptDAO receiptDAO = receiptService.getReceiptDAO();
                String uploaderId = receiptDAO.getReceiptUploadedBy(receiptId);
                
                if (uploaderId == null || !uploaderId.equals(userIdStr)) {
                    sendJson(exchange, 403, new JSONObject()
                        .put("success", false)
                        .put("message", "Only the receipt uploader can add participants"));
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
                
                JSONObject resp = new JSONObject()
                    .put("success", true)
                    .put("message", "Participants added successfully")
                    .put("participantsAdded", validParticipantIds.size());
                
                sendJson(exchange, 200, resp);
                
            } catch (Exception e) {
                e.printStackTrace();
                sendJson(exchange, 400, new JSONObject()
                    .put("success", false)
                    .put("message", "Error adding participants: " + e.getMessage()));
            }
        }
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
