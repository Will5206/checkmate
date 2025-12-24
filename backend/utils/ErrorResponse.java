package utils;

import com.sun.net.httpserver.HttpExchange;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for standardizing error responses
 */
public class ErrorResponse {
    
    /**
     * Send a standardized error response
     * @param exchange HttpExchange to send response to
     * @param statusCode HTTP status code
     * @param message Error message
     * @throws IOException if response cannot be sent
     */
    public static void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        JSONObject response = create(message);
        sendJson(exchange, statusCode, response);
    }
    
    /**
     * Send a standardized error response with error code
     * @param exchange HttpExchange to send response to
     * @param statusCode HTTP status code
     * @param message Error message
     * @param errorCode Optional error code
     * @throws IOException if response cannot be sent
     */
    public static void sendError(HttpExchange exchange, int statusCode, String message, String errorCode) throws IOException {
        JSONObject response = create(message, errorCode);
        sendJson(exchange, statusCode, response);
    }
    
    /**
     * Send a JSON response with proper CORS headers
     * @param exchange HttpExchange to send response to
     * @param statusCode HTTP status code
     * @param json JSONObject to send
     * @throws IOException if response cannot be sent
     */
    public static void sendJson(HttpExchange exchange, int statusCode, JSONObject json) throws IOException {
        // Set CORS headers
        String allowedOrigin = System.getenv("ALLOWED_ORIGIN");
        if (allowedOrigin == null || allowedOrigin.isEmpty()) {
            allowedOrigin = "*"; // Default to wildcard for development
        }
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", allowedOrigin);
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        
        // Set content type
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        
        // Send response
        byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
    
    /**
     * Create a standardized error response
     * @param message Error message
     * @param code Optional error code
     * @return JSONObject with standardized error format
     */
    public static JSONObject create(String message, String code) {
        JSONObject error = new JSONObject();
        error.put("success", false);
        error.put("message", message);
        if (code != null) {
            error.put("errorCode", code);
        }
        return error;
    }
    
    /**
     * Create a standardized error response without error code
     */
    public static JSONObject create(String message) {
        return create(message, null);
    }
    
    /**
     * Create a standardized success response
     */
    public static JSONObject success(String message) {
        JSONObject response = new JSONObject();
        response.put("success", true);
        if (message != null) {
            response.put("message", message);
        }
        return response;
    }
    
    /**
     * Create a standardized success response with data
     */
    public static JSONObject success(String message, JSONObject data) {
        JSONObject response = success(message);
        if (data != null) {
            for (String key : data.keySet()) {
                response.put(key, data.get(key));
            }
        }
        return response;
    }
}

