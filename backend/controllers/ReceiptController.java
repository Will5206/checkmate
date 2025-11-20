package controllers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

public class ReceiptController implements HttpHandler {
    private static final String UPLOAD_DIR = "receipts/";
    private static final String PYTHON_SCRIPT = "reciept_parser_local.py";
    
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
            sendResponse(exchange, 405, "{\"success\": false, \"message\": \"Method not allowed\"}");
            return;
        }
        
        Path tempPath = null;
        try {
            System.out.println("[ReceiptController] Received receipt parse request");
            
            // Read image from request body (simplified - reads raw bytes)
            byte[] imageData = exchange.getRequestBody().readAllBytes();
            System.out.println("[ReceiptController] Read " + imageData.length + " bytes of image data");
            
            if (imageData.length == 0) {
                sendResponse(exchange, 400, "{\"success\": false, \"message\": \"No image data received\"}");
                return;
            }
            
            // Save to temp file
            String filename = "temp_" + UUID.randomUUID().toString() + ".jpg";
            tempPath = Paths.get(UPLOAD_DIR, filename);
            Files.createDirectories(tempPath.getParent());
            Files.write(tempPath, imageData);
            System.out.println("[ReceiptController] Saved image to: " + tempPath.toString());
            
            // Call Python parser - use absolute path to project root
            String projectRoot = System.getProperty("user.dir");
            File projectRootFile = new File(projectRoot);
            File pythonScriptFile = new File(projectRootFile, PYTHON_SCRIPT);
            
            System.out.println("[ReceiptController] Calling Python parser: " + pythonScriptFile.getAbsolutePath());
            System.out.println("[ReceiptController] Working directory: " + projectRoot);
            System.out.println("[ReceiptController] Image path: " + tempPath.toAbsolutePath());
            
            ProcessBuilder pb = new ProcessBuilder(
                "python3",
                pythonScriptFile.getAbsolutePath(),
                tempPath.toAbsolutePath().toString()
            );
            pb.directory(projectRootFile);
            // do NOT redirect error stream; keep stdout and stderr separate
            Process process = pb.start();
            System.out.println("[ReceiptController] Python process started, waiting for output...");
            
            // Read stdout (JSON)
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
            );
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[ReceiptController] Python stdout: " + line);
                // keep only the last line, expected to be the JSON
                output.setLength(0);
                output.append(line);
            }
            
            // Read stderr (warnings, debug)
            BufferedReader errorReader = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8)
            );
            StringBuilder errorOutput = new StringBuilder();
            String errorLine;
            while ((errorLine = errorReader.readLine()) != null) {
                errorOutput.append(errorLine).append("\n");
                System.err.println("[ReceiptController] Python stderr: " + errorLine);
            }
            
            System.out.println("[ReceiptController] Waiting for Python process to complete...");
            int exitCode = process.waitFor();
            System.out.println("[ReceiptController] Python process exited with code: " + exitCode);
            
            // Clean up temp file
            if (tempPath != null) {
                Files.deleteIfExists(tempPath);
                System.out.println("[ReceiptController] Cleaned up temp file");
            }
            
            if (exitCode != 0) {
                System.err.println("[ReceiptController] Python script failed with exit code: " + exitCode);
                System.err.println("[ReceiptController] Python error output: " + errorOutput.toString());
                sendResponse(exchange, 500, "{\"success\": false, \"message\": \"Error reading receipt. Python script failed: " + exitCode + "\"}");
                return;
            }
            
            // Parse Python output (should be pure JSON on stdout)
            String pythonOutput = output.toString().trim();
            System.out.println("[ReceiptController] Parsing Python output (length: " + pythonOutput.length() + ")");
            System.out.println("[ReceiptController] Raw Python output:");
            System.out.println(pythonOutput);
            
            JSONObject receiptData = new JSONObject(pythonOutput);
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
            
            sendResponse(exchange, 200, response.toString());
            
        } catch (Exception e) {
            // Clean up temp file on error
            if (tempPath != null) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (Exception ignored) {}
            }
            
            e.printStackTrace();
            JSONObject response = new JSONObject();
            response.put("success", false);
            response.put("message", "Error reading receipt: " + e.getMessage());
            sendResponse(exchange, 500, response.toString());
        }
    }
    
    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }
}
