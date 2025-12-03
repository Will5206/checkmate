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
import java.util.HashMap;
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
                String userIdStr = query.getOrDefault("userId", "");
                int receiptId = Integer.parseInt(query.getOrDefault("receiptId", "0"));
                
                if (userIdStr.isEmpty() || receiptId == 0) {
                    sendJson(exchange, 400, new JSONObject()
                        .put("success", false)
                        .put("message", "userId and receiptId parameters are required"));
                    return;
                }
                
                // Get full receipt with items loaded
                ReceiptDAO receiptDAO = receiptService.getReceiptDAO();
                Receipt receipt = receiptDAO.getReceiptById(receiptId);
                
                if (receipt == null) {
                    sendJson(exchange, 404, new JSONObject()
                        .put("success", false)
                        .put("message", "Receipt not found"));
                    return;
                }
                
                // Verify user has access (uploader or participant)
                String uploadedBy = receiptDAO.getReceiptUploadedBy(receiptId);
                String participantStatus = receiptDAO.getParticipantStatus(receiptId, userIdStr);
                
                if (uploadedBy == null || (!uploadedBy.equals(userIdStr) && participantStatus == null)) {
                    sendJson(exchange, 403, new JSONObject()
                        .put("success", false)
                        .put("message", "Access denied"));
                    return;
                }
                
                // Build receipt JSON with all details including items
                JSONObject receiptJson = buildReceiptJson(receipt);
                
                // Get status using String userId version (explicitly call String overload)
                String receiptStatus = receiptService.getReceiptStatus(userIdStr, receiptId);
                receiptJson.put("status", receiptStatus != null ? receiptStatus : "pending");
                
                // Add isUploader and hasPaid flags for frontend
                // Note: uploadedBy and participantStatus are already defined above
                boolean isUploader = uploadedBy != null && uploadedBy.equals(userIdStr);
                receiptJson.put("isUploader", isUploader);
                
                // CRITICAL: Add complete status so frontend can determine if receipt is completed
                // Frontend will hide pay button and show completed UI when complete = true
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
                
                JSONObject resp = new JSONObject()
                    .put("success", true)
                    .put("receipt", receiptJson);
                
                sendJson(exchange, 200, resp);
            } catch (NumberFormatException e) {
                sendJson(exchange, 400, new JSONObject()
                    .put("success", false)
                    .put("message", "Invalid receiptId format. Expected a number."));
            } catch (Exception e) {
                System.err.println("[ReceiptController] ViewReceiptHandler error: " + e.getMessage());
                e.printStackTrace();
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
                System.out.println("[ReceiptController] ListPendingReceiptsHandler - Found " + pendingReceipts.size() + " pending receipts for user " + userIdStr);
                
                ReceiptDAO receiptDAO = receiptService.getReceiptDAO();
                
                // OPTIMIZATION: Batch fetch all metadata in a single query instead of N queries
                List<Integer> receiptIds = new ArrayList<>();
                for (Receipt receipt : pendingReceipts) {
                    receiptIds.add(receipt.getReceiptId());
                }
                Map<Integer, ReceiptDAO.ReceiptMetadata> metadataMap = receiptDAO.getReceiptsMetadataBatch(receiptIds, userIdStr);
                System.out.println("[ReceiptController] ListPendingReceiptsHandler - Batch fetched metadata for " + metadataMap.size() + " receipts");
                
                JSONArray receiptsArray = new JSONArray();
                for (Receipt receipt : pendingReceipts) {
                    // Get metadata from batch result
                    ReceiptDAO.ReceiptMetadata metadata = metadataMap.get(receipt.getReceiptId());
                    
                    // Build JSON with uploadedBy from metadata to avoid extra query
                    JSONObject receiptJson = buildReceiptJson(receipt, metadata != null ? metadata.uploadedBy : null);
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
                
                System.out.println("[ReceiptController] ListPendingReceiptsHandler - Returning " + receiptsArray.length() + " receipts in JSON array");
                
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
                boolean accepted = true;
                
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
                
                // Add uploader as participant with 'pending' status (so they see it in Pending screen like everyone else)
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
                
                // Update number_of_items in receipts table after all items are added
                receiptDAO.updateReceiptItemCount(receipt.getReceiptId());
                
                // Reload receipt with items
                receipt = receiptDAO.getReceiptById(receipt.getReceiptId());
                
                // Build response
                JSONObject receiptJson = buildReceiptJson(receipt);
                receiptJson.put("status", "accepted");
                
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
                System.out.println("[ReceiptController] STEP B1: GetActivityReceiptsHandler - Request received");
                String userIdStr = query.getOrDefault("userId", "");
                System.out.println("[ReceiptController] STEP B2: Extracted userId: " + userIdStr);
                
                if (userIdStr.isEmpty()) {
                    System.out.println("[ReceiptController] ERROR: userId is empty");
                    sendJson(exchange, 400, new JSONObject()
                        .put("success", false)
                        .put("message", "userId parameter is required"));
                    return;
                }
                
                System.out.println("[ReceiptController] STEP B3: Calling receiptService.getAllReceiptsForUser(" + userIdStr + ")");
                // Get all receipts for this user (accepted, declined, or uploaded)
                List<Receipt> receipts = receiptService.getAllReceiptsForUser(userIdStr);
                System.out.println("[ReceiptController] STEP B4: Received " + receipts.size() + " receipts from service");
                for (int i = 0; i < receipts.size(); i++) {
                    Receipt r = receipts.get(i);
                    System.out.println("[ReceiptController] STEP B5: Receipt " + (i+1) + ": ID=" + r.getReceiptId() + ", merchant=" + r.getMerchantName());
                }
                
                ReceiptDAO receiptDAO = receiptService.getReceiptDAO();
                
                // OPTIMIZATION: Batch fetch all metadata and owed amounts in single queries
                List<Integer> receiptIds = new ArrayList<>();
                for (Receipt receipt : receipts) {
                    receiptIds.add(receipt.getReceiptId());
                }
                Map<Integer, ReceiptDAO.ReceiptMetadata> metadataMap = receiptDAO.getReceiptsMetadataBatch(receiptIds, userIdStr);
                Map<Integer, Float> owedAmountsMap = receiptDAO.calculateUserOwedAmountsBatch(receiptIds, userIdStr);
                System.out.println("[ReceiptController] STEP B6: Batch fetched metadata and owed amounts for " + metadataMap.size() + " receipts");
                
                System.out.println("[ReceiptController] STEP B7: Building JSON array for " + receipts.size() + " receipts");
                JSONArray receiptsArray = new JSONArray();
                for (int i = 0; i < receipts.size(); i++) {
                    Receipt receipt = receipts.get(i);
                    System.out.println("[ReceiptController] STEP B8: Processing receipt " + (i+1) + "/" + receipts.size() + " (ID: " + receipt.getReceiptId() + ")");
                    
                    // Get metadata and owed amount from batch results
                    ReceiptDAO.ReceiptMetadata metadata = metadataMap.get(receipt.getReceiptId());
                    
                    // Build JSON with uploadedBy from metadata to avoid extra query
                    JSONObject receiptJson = buildReceiptJson(receipt, metadata != null ? metadata.uploadedBy : null);
                    System.out.println("[ReceiptController] STEP B9: Built JSON for receipt " + receipt.getReceiptId());
                    Float owedAmount = owedAmountsMap.get(receipt.getReceiptId());
                    
                    if (metadata != null && owedAmount != null) {
                        // Use batch-fetched data
                        float paidAmount = metadata.paidAmount;
                        
                        // Only mark as paid if:
                        // 1. User has items claimed (owedAmount > 0.01) AND has paid for them
                        // 2. OR user has no items claimed (owedAmount <= 0.01) but has made a payment (paidAmount > 0.01)
                        boolean hasPaid = false;
                        if (owedAmount > 0.01f) {
                            hasPaid = paidAmount >= owedAmount - 0.01f; // Allow small rounding differences
                        } else if (paidAmount > 0.01f) {
                            hasPaid = true;
                        }
                        
                        receiptJson.put("userOwedAmount", owedAmount);
                        receiptJson.put("userPaidAmount", paidAmount);
                        receiptJson.put("userHasPaid", hasPaid);
                    } else {
                        // Fallback to individual queries if batch fetch failed (backward compatibility)
                        float owedAmountFallback = receiptDAO.calculateUserOwedAmount(receipt.getReceiptId(), userIdStr);
                        float paidAmountFallback = receiptDAO.getPaidAmount(receipt.getReceiptId(), userIdStr);
                        
                        boolean hasPaid = false;
                        if (owedAmountFallback > 0.01f) {
                            hasPaid = paidAmountFallback >= owedAmountFallback - 0.01f;
                        } else if (paidAmountFallback > 0.01f) {
                            hasPaid = true;
                        }
                        
                        receiptJson.put("userOwedAmount", owedAmountFallback);
                        receiptJson.put("userPaidAmount", paidAmountFallback);
                        receiptJson.put("userHasPaid", hasPaid);
                    }
                    
                    receiptsArray.put(receiptJson);
                    System.out.println("[ReceiptController] STEP B10: Added receipt " + receipt.getReceiptId() + " to JSON array (size now: " + receiptsArray.length() + ")");
                }
                
                System.out.println("[ReceiptController] STEP B10: Final JSON array size: " + receiptsArray.length());
                System.out.println("[ReceiptController] STEP B11: Returning response with " + receiptsArray.length() + " receipts");
                
                JSONObject resp = new JSONObject()
                    .put("success", true)
                    .put("userId", userIdStr)
                    .put("receipts", receiptsArray);
                
                sendJson(exchange, 200, resp);
                System.out.println("[ReceiptController] STEP B12: Response sent successfully");
            } catch (Exception e) {
                sendJson(exchange, 400, new JSONObject()
                    .put("success", false)
                    .put("message", "Invalid parameters: " + e.getMessage()));
            }
        }
    }

    /**
     * Helper method to build a JSON object from a Receipt model.
     * @param receipt The receipt to convert to JSON
     * @param uploadedBy Optional uploadedBy string to avoid extra query (if already fetched)
     */
    private static JSONObject buildReceiptJson(Receipt receipt) {
        return buildReceiptJson(receipt, null);
    }
    
    /**
     * Helper method to build a JSON object from a Receipt model.
     * @param receipt The receipt to convert to JSON
     * @param uploadedBy Optional uploadedBy string to avoid extra query (if already fetched)
     */
    private static JSONObject buildReceiptJson(Receipt receipt, String uploadedBy) {
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
                
                boolean success;
                boolean isDelete = "DELETE".equals(exchange.getRequestMethod());
                
                // Optimized: Check payment status and perform operation in one go
                // For DELETE, check if paid before attempting unclaim
                if (isDelete) {
                    Map<String, Object> paymentInfo = receiptDAO.getItemPaymentInfo(itemId);
                    if (paymentInfo != null) {
                        sendJson(exchange, 400, new JSONObject()
                            .put("success", false)
                            .put("message", "Cannot unclaim an item that has been paid for"));
                        return;
                    }
                    success = receiptDAO.unassignItemFromUser(itemId, userIdStr);
                } else if ("POST".equals(exchange.getRequestMethod())) {
                    // For POST, check if paid before attempting claim
                    if (receiptDAO.isItemPaid(itemId)) {
                        sendJson(exchange, 400, new JSONObject()
                            .put("success", false)
                            .put("message", "This item has already been paid for and cannot be claimed"));
                        return;
                    }
                    // Claim item
                    int quantity = Integer.parseInt(query.getOrDefault("quantity", "1"));
                    
                    // Get item info to validate quantity
                    models.ReceiptItem item = receiptDAO.getReceiptItemById(itemId);
                    if (item == null) {
                        sendJson(exchange, 400, new JSONObject()
                            .put("success", false)
                            .put("message", "Item not found"));
                        return;
                    }
                    
                    // Get current user's claimed quantity
                    int userCurrentQty = receiptDAO.getUserClaimedQuantity(itemId, userIdStr);
                    int totalClaimedByOthers = receiptDAO.getTotalClaimedQuantity(itemId) - userCurrentQty;
                    int availableQty = item.getQuantity() - totalClaimedByOthers;
                    
                    // Validate quantity
                    if (quantity <= 0) {
                        sendJson(exchange, 400, new JSONObject()
                            .put("success", false)
                            .put("message", "Quantity must be greater than 0"));
                        return;
                    }
                    
                    if (quantity > availableQty) {
                        sendJson(exchange, 400, new JSONObject()
                            .put("success", false)
                            .put("message", String.format("Cannot claim %d. Only %d available (item quantity: %d, already claimed by others: %d)", 
                                quantity, availableQty, item.getQuantity(), totalClaimedByOthers)));
                        return;
                    }
                    
                    success = receiptDAO.assignItemToUser(itemId, userIdStr, quantity);
                } else {
                    sendJson(exchange, 405, new JSONObject()
                        .put("success", false)
                        .put("message", "Method not allowed"));
                    return;
                }
                
                if (success) {
                    // Calculate updated amount owed (optimized: single query)
                    float owedAmount = receiptDAO.calculateUserOwedAmount(receiptId, userIdStr);
                    // Also calculate amount excluding paid items
                    float owedAmountExcludingPaid = receiptDAO.calculateUserOwedAmountExcludingPaid(receiptId, userIdStr);
                    
                    JSONObject resp = new JSONObject()
                        .put("success", true)
                        .put("message", isDelete ? "Item unclaimed" : "Item claimed")
                        .put("owedAmount", owedAmount)
                        .put("owedAmountExcludingPaid", owedAmountExcludingPaid);
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
                // Calculate amount owed excluding paid items (for "Amount Owed" section)
                float owedAmountExcludingPaid = receiptDAO.calculateUserOwedAmountExcludingPaid(receiptId, userIdStr);
                
                System.out.println("[ReceiptController] Found " + assignments.size() + " item assignments, owedAmount: " + owedAmount + ", owedAmountExcludingPaid: " + owedAmountExcludingPaid);
                
                // Build assignments JSON
                JSONObject assignmentsJson = new JSONObject();
                for (Map.Entry<Integer, Integer> entry : assignments.entrySet()) {
                    assignmentsJson.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                
                // Get item payment info from receipt_items table (new approach)
                Map<Integer, Map<String, Object>> itemPaymentMap = receiptDAO.getItemPaymentInfoForReceipt(receiptId);
                
                // Build item payment info JSON with payer names
                JSONObject itemPaymentInfo = new JSONObject();
                for (Map.Entry<Integer, Map<String, Object>> entry : itemPaymentMap.entrySet()) {
                    int itemId = entry.getKey();
                    Map<String, Object> paymentData = entry.getValue();
                    String paidByUserId = (String) paymentData.get("paidBy");
                    
                    // Get payer's name
                    models.User payer = userDAO.findUserById(paidByUserId);
                    String payerName = payer != null ? payer.getName() : "Unknown";
                    
                    JSONObject paymentJson = new JSONObject()
                        .put("paidBy", paidByUserId)
                        .put("payerName", payerName)
                        .put("paidAt", paymentData.get("paidAt"));
                    itemPaymentInfo.put(String.valueOf(itemId), paymentJson);
                }
                
                System.out.println("[ReceiptController] Found payment info for " + itemPaymentInfo.length() + " items");
                
                JSONObject resp = new JSONObject()
                    .put("success", true)
                    .put("assignments", assignmentsJson)
                    .put("owedAmount", owedAmount)
                    .put("owedAmountExcludingPaid", owedAmountExcludingPaid)
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
                
                // CRITICAL FIX: Record payment and mark items in a single atomic transaction
                // This ensures data consistency - either both operations succeed or both fail
                int itemsMarkedPaid = receiptDAO.recordPaymentAndMarkItems(receiptId, userIdStr, remainingAmount);
                
                if (itemsMarkedPaid < 0) {
                    // Payment recording failed - rollback balance operations
                    System.err.println("CRITICAL: Payment recording failed, rolling back balance operations");
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
                
                System.out.println("[ReceiptController] Successfully recorded payment and marked " + itemsMarkedPaid + " items as paid");
                
                // Check if receipt should be marked as completed
                boolean isCompleted = receiptDAO.checkAndMarkReceiptCompleted(receiptId);
                
                // Optimized: Calculate new amounts (we know paidAmount = old paidAmount + remainingAmount)
                float newPaidAmount = paidAmount + remainingAmount;
                float newOwedAmount = owedAmount - remainingAmount; // Should be 0 or close to 0
                // Calculate amount excluding paid items (should be 0 or close to 0 after payment)
                float newOwedAmountExcludingPaid = receiptDAO.calculateUserOwedAmountExcludingPaid(receiptId, userIdStr);
                
                // Get user's name for payment info (needed for item payment display)
                database.UserDAO userDAO = new database.UserDAO();
                models.User payer = userDAO.findUserById(userIdStr);
                String payerName = payer != null ? payer.getName() : "You";
                
                // Get item payment info from receipt_items table (after marking as paid)
                // This includes all items that are now paid, not just the user's assignments
                Map<Integer, Map<String, Object>> itemPaymentMap = receiptDAO.getItemPaymentInfoForReceipt(receiptId);
                
                // Build item payment info JSON with payer names
                JSONObject itemPaymentInfo = new JSONObject();
                for (Map.Entry<Integer, Map<String, Object>> entry : itemPaymentMap.entrySet()) {
                    int itemId = entry.getKey();
                    Map<String, Object> paymentData = entry.getValue();
                    String paidByUserId = (String) paymentData.get("paidBy");
                    
                    // Get payer's name (might be different user if they paid for this item)
                    models.User itemPayer = userDAO.findUserById(paidByUserId);
                    String itemPayerName = itemPayer != null ? itemPayer.getName() : "Unknown";
                    
                    JSONObject paymentJson = new JSONObject()
                        .put("paidBy", paidByUserId)
                        .put("payerName", itemPayerName)
                        .put("paidAt", paymentData.get("paidAt"));
                    itemPaymentInfo.put(String.valueOf(itemId), paymentJson);
                }
                
                JSONObject resp = new JSONObject()
                    .put("success", true)
                    .put("message", "Payment processed successfully")
                    .put("amountPaid", remainingAmount)
                    .put("paidAmount", newPaidAmount)
                    .put("owedAmount", newOwedAmount)
                    .put("owedAmountExcludingPaid", newOwedAmountExcludingPaid)
                    .put("receiptCompleted", isCompleted)
                    .put("itemPaymentInfo", itemPaymentInfo); // Include payment info to avoid reload
                
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
