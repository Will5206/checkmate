package controllers;

import utils.AuthMiddleware;
import utils.ErrorResponse;
import utils.Logger;
import utils.Constants;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Controller for handling receipt image parsing via OpenAI.
 * Provides endpoint for parsing receipt images and extracting receipt data.
 */
public class ReceiptParsingController {

    private static final String CONTEXT = "ReceiptParsingController";

    /**
     * Handler for parsing a receipt image using OpenAI.
     * POST /api/receipt/parse
     * Body: Raw image bytes (image/jpeg)
     */
    public static class ParseReceiptHandler implements HttpHandler {
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
            
            Path tempPath = null;
            try {
                Logger.debug(CONTEXT, "Received receipt parse request at " + new java.util.Date());
                
                // Read image from request body
                Logger.debug(CONTEXT, "Reading image data from request body...");
                byte[] imageData = exchange.getRequestBody().readAllBytes();
                Logger.debug(CONTEXT, "Read " + imageData.length + " bytes of image data (" + (imageData.length / 1024) + " KB)");
                
                if (imageData.length == 0) {
                    ErrorResponse.sendError(exchange, 400, "No image data received");
                    return;
                }
                
                // Enforce maximum file size
                if (imageData.length > Constants.MAX_FILE_SIZE_BYTES) {
                    double fileSizeMB = imageData.length / (1024.0 * 1024.0);
                    ErrorResponse.sendError(exchange, 400, 
                        String.format("Image file too large. Maximum size is %dMB. Your file is %.2fMB", 
                            Constants.MAX_FILE_SIZE_MB, fileSizeMB));
                    return;
                }
                
                // Save to temp file
                tempPath = saveImageToTempFile(imageData);
                
                // Call Python parser
                String projectRoot = System.getProperty("user.dir");
                File projectRootFile = new File(projectRoot);
                File pythonScriptFile = new File(projectRootFile, Constants.PYTHON_SCRIPT_PATH);
                
                if (!pythonScriptFile.exists()) {
                    Logger.error(CONTEXT, "Python script not found at: " + pythonScriptFile.getAbsolutePath());
                    ErrorResponse.sendError(exchange, 500, "Python parser script not found");
                    return;
                }
                
                Logger.debug(CONTEXT, "Python script: " + pythonScriptFile.getAbsolutePath());
                Logger.debug(CONTEXT, "Working directory: " + projectRoot);
                Logger.debug(CONTEXT, "Image path: " + tempPath.toAbsolutePath());
                
                // Execute Python parser
                ProcessResult processResult = executePythonParser(pythonScriptFile, projectRootFile, tempPath);
                
                if (processResult.timedOut) {
                    ErrorResponse.sendError(exchange, 500, 
                        "Receipt parsing timed out. This usually takes 15-30 seconds. Please check:\n" +
                        "1. OpenAI API key is configured\n" +
                        "2. Internet connection is working\n" +
                        "3. Try again with a clearer receipt image");
                    return;
                }
                
                Logger.timing(CONTEXT, "Python process", processResult.duration);
                
                if (processResult.exitCode != 0) {
                    String errorMsg = parsePythonError(processResult.output, processResult.errorOutput, processResult.exitCode);
                    ErrorResponse.sendError(exchange, 500, errorMsg);
                    return;
                }
                
                // Clean up temp file
                cleanupTempFile(tempPath);
                
                // Parse Python output (should be pure JSON on stdout)
                String pythonOutput = processResult.output.toString().trim();
                if (pythonOutput.isEmpty()) {
                    Logger.error(CONTEXT, "Python script returned empty output");
                    ErrorResponse.sendError(exchange, 500, "Python script returned no output. Check server logs for details.");
                    return;
                }
                
                Logger.debug(CONTEXT, "Parsing Python output (length: " + pythonOutput.length() + ")");
                String preview = pythonOutput.length() > Constants.MAX_PYTHON_OUTPUT_PREVIEW_LENGTH 
                    ? pythonOutput.substring(0, Constants.MAX_PYTHON_OUTPUT_PREVIEW_LENGTH) + "..." 
                    : pythonOutput;
                Logger.debug(CONTEXT, "Raw Python output preview: " + preview);
                
                JSONObject receiptData;
                try {
                    receiptData = new JSONObject(pythonOutput);
                } catch (Exception e) {
                    Logger.error(CONTEXT, "Failed to parse Python output as JSON: " + e.getMessage(), e);
                    Logger.error(CONTEXT, "Full output: " + pythonOutput);
                    ErrorResponse.sendError(exchange, 500, "Failed to parse receipt data. Python script may have encountered an error.");
                    return;
                }
                Logger.debug(CONTEXT, "Successfully parsed receipt data");
                
                // Build response
                JSONObject response = ErrorResponse.success(null);
                response.put("merchant", receiptData.optString("merchant", "Unknown"));
                response.put("date", receiptData.optString("date", ""));
                response.put("items", receiptData.optJSONArray("items"));
                response.put("subtotal", receiptData.optDouble("subtotal", 0));
                response.put("tax", receiptData.optDouble("tax", 0));
                response.put("total", receiptData.optDouble("total", 0));
                
                ErrorResponse.sendJson(exchange, 200, response);
                
            } catch (Exception e) {
                // Clean up temp file on error
                cleanupTempFile(tempPath);
                
                Logger.error(CONTEXT, "Unexpected error in ParseReceiptHandler", e);
                ErrorResponse.sendError(exchange, 500, "Error reading receipt: " + e.getMessage());
            }
        }
    }
    
    // Helper methods for ParseReceiptHandler
    
    /**
     * Save image data to a temporary file
     */
    private static Path saveImageToTempFile(byte[] imageData) throws IOException {
        Logger.debug(CONTEXT, "Saving image to temp file...");
        String filename = "temp_" + UUID.randomUUID().toString() + ".jpg";
        Path tempPath = Paths.get(Constants.UPLOAD_DIR, filename);
        Files.createDirectories(tempPath.getParent());
        Files.write(tempPath, imageData);
        Logger.debug(CONTEXT, "Saved image to: " + tempPath.toString());
        return tempPath;
    }
    
    /**
     * Result class for Python process execution
     */
    private static class ProcessResult {
        StringBuilder output = new StringBuilder();
        StringBuilder errorOutput = new StringBuilder();
        int exitCode;
        boolean timedOut;
        long duration;
    }
    
    /**
     * Execute Python parser process and capture output
     */
    private static ProcessResult executePythonParser(File pythonScript, File workingDir, Path imagePath) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
            "python3",
            pythonScript.getAbsolutePath(),
            imagePath.toAbsolutePath().toString()
        );
        pb.directory(workingDir);
        
        Logger.debug(CONTEXT, "Starting Python process...");
        long startTime = System.currentTimeMillis();
        Process process = pb.start();
        Logger.debug(CONTEXT, "Python process started (PID: " + process.pid() + "), waiting for output...");
        
        ProcessResult result = new ProcessResult();
        
        // Read stdout and stderr in parallel to avoid deadlock
        Thread stdoutThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Logger.debug(CONTEXT, "Python stdout: " + line);
                    // Keep only the last line, expected to be the JSON
                    result.output.setLength(0);
                    result.output.append(line);
                }
            } catch (IOException e) {
                Logger.error(CONTEXT, "Error reading stdout: " + e.getMessage(), e);
            }
        });
        
        Thread stderrThread = new Thread(() -> {
            try (BufferedReader errorReader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String errorLine;
                while ((errorLine = errorReader.readLine()) != null) {
                    result.errorOutput.append(errorLine).append("\n");
                    Logger.warn(CONTEXT, "Python stderr: " + errorLine);
                }
            } catch (IOException e) {
                Logger.error(CONTEXT, "Error reading stderr: " + e.getMessage(), e);
            }
        });
        
        stdoutThread.start();
        stderrThread.start();
        
        Logger.debug(CONTEXT, "Waiting for Python process to complete (timeout: " + Constants.PYTHON_PARSER_TIMEOUT_SECONDS + " seconds)...");
        
        // Wait for process with timeout
        boolean finished = process.waitFor(Constants.PYTHON_PARSER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        
        if (!finished) {
            Logger.error(CONTEXT, "Python process timed out after " + Constants.PYTHON_PARSER_TIMEOUT_SECONDS + " seconds - killing process");
            process.destroyForcibly();
            result.exitCode = -1;
            result.timedOut = true;
            
            // Wait for threads to finish (convert ms to seconds for join)
            stdoutThread.join(Constants.PYTHON_PARSER_THREAD_JOIN_TIMEOUT_MS);
            stderrThread.join(Constants.PYTHON_PARSER_THREAD_JOIN_TIMEOUT_MS);
            result.duration = System.currentTimeMillis() - startTime;
            return result;
        }
        
        result.exitCode = process.exitValue();
        
        // Wait for threads to finish reading
        stdoutThread.join(Constants.PYTHON_PARSER_THREAD_JOIN_TIMEOUT_MS);
        stderrThread.join(Constants.PYTHON_PARSER_THREAD_JOIN_TIMEOUT_MS);
        result.duration = System.currentTimeMillis() - startTime;
        
        return result;
    }
    
    /**
     * Parse Python error output into user-friendly error message
     */
    private static String parsePythonError(StringBuilder output, StringBuilder errorOutput, int exitCode) {
        String errorMsg = "Error reading receipt. Python script failed with exit code: " + exitCode;
        String detailedError = "";
        
        if (output.length() > 0) {
            try {
                JSONObject errorJson = new JSONObject(output.toString());
                if (errorJson.has("error")) {
                    errorMsg = errorJson.getString("error");
                    return errorMsg;
                }
            } catch (Exception e) {
                // Not JSON, check for common error patterns
                String outputStr = output.toString();
                if (outputStr.contains("OPENAI_API_KEY")) {
                    errorMsg = "OpenAI API key not configured. Please check your .env file.";
                } else if (outputStr.contains("ModuleNotFoundError") || outputStr.contains("ImportError")) {
                    errorMsg = "Python dependencies missing. Please run: pip install openai pillow pillow-heif python-dotenv";
                } else if (outputStr.length() > 0) {
                    int maxLen = Math.min(Constants.MAX_ERROR_DETAIL_LENGTH, outputStr.length());
                    errorMsg = "Python script error: " + outputStr.substring(0, maxLen);
                }
            }
        }
        
        // Include stderr in detailed error if available
        if (errorOutput.length() > 0) {
            String stderrStr = errorOutput.toString();
            int maxLen = Math.min(Constants.MAX_ERROR_MESSAGE_LENGTH, stderrStr.length());
            detailedError = "\n\nPython error details:\n" + stderrStr.substring(0, maxLen);
            
            if (stderrStr.contains("OPENAI_API_KEY")) {
                errorMsg = "OpenAI API key not configured. Please check your .env file.";
            } else if (stderrStr.contains("API key") || stderrStr.contains("authentication")) {
                errorMsg = "OpenAI API authentication failed. Please check your API key is valid.";
            } else if (stderrStr.contains("rate limit") || stderrStr.contains("quota")) {
                errorMsg = "OpenAI API rate limit or quota exceeded. Please try again later.";
            }
        }
        
        return errorMsg + detailedError;
    }
    
    /**
     * Clean up temporary file
     */
    private static void cleanupTempFile(Path tempPath) {
        if (tempPath != null) {
            try {
                Files.deleteIfExists(tempPath);
                Logger.debug(CONTEXT, "Cleaned up temp file");
            } catch (Exception e) {
                Logger.warn(CONTEXT, "Failed to cleanup temp file: " + e.getMessage());
            }
        }
    }
}

