package controllers;

import models.User;
import services.AuthService;
import utils.RateLimiter;
import utils.ValidationUtils;
import utils.ErrorResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;





/**
 * REST API controller for authentication endpoints
 * handles /api/auth/login and /api/auth/signup
 */


public class AuthController {
    
    private AuthService authService;
    
    public AuthController() {
        this.authService = new AuthService();
    }
    




    /**
     * log in handler
     */
    public static class LoginHandler implements HttpHandler {
        private AuthService authService = new AuthService();
        
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            System.out.println("🟢 [BACKEND STEP 1/10] LoginHandler.handle() called");
            long startTime = System.currentTimeMillis();
            
            // Enable CORS (Auth endpoints are public)
            System.out.println("🟢 [BACKEND STEP 2/10] Setting CORS headers");
            String allowedOrigin = System.getenv("ALLOWED_ORIGIN");
            if (allowedOrigin == null || allowedOrigin.isEmpty()) {
                allowedOrigin = "*"; // Default to wildcard for development
            }
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", allowedOrigin);
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                System.out.println("🟢 [BACKEND STEP 2/10] OPTIONS request, returning 200");
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            if (!"POST".equals(exchange.getRequestMethod())) {
                System.out.println("🟢 [BACKEND STEP 2/10] Method not POST, returning 405");
                ErrorResponse.sendError(exchange, 405, "Method not allowed");
                return;
            }
            
            // Rate limiting for login endpoint
            String clientIP = getClientIP(exchange);
            if (!RateLimiter.isAllowed(clientIP, true)) {
                ErrorResponse.sendError(exchange, 429, "Too many requests. Please try again later.");
                return;
            }
            
            try {
                System.out.println("🟢 [BACKEND STEP 3/10] Reading request body...");
                String requestBody = readRequestBody(exchange);
                System.out.println("🟢 [BACKEND STEP 3/10] Request body received, length: " + requestBody.length());
                
                System.out.println("🟢 [BACKEND STEP 4/10] Parsing JSON...");
                JSONObject json = new JSONObject(requestBody);
                
                // Validate required fields
                System.out.println("🟢 [BACKEND STEP 5/10] Validating required fields...");
                if (!json.has("emailOrPhone") || json.getString("emailOrPhone").trim().isEmpty()) {
                    System.out.println("🟢 [BACKEND STEP 5/10] Missing emailOrPhone, returning 400");
                    ErrorResponse.sendError(exchange, 400, "emailOrPhone is required");
                    return;
                }
                if (!json.has("password") || json.getString("password").trim().isEmpty()) {
                    System.out.println("🟢 [BACKEND STEP 5/10] Missing password, returning 400");
                    ErrorResponse.sendError(exchange, 400, "password is required");
                    return;
                }
                
                String emailOrPhone = ValidationUtils.sanitizeBasic(json.getString("emailOrPhone"));
                String password = json.getString("password");
                
                // Validate email/phone format
                boolean isEmail = emailOrPhone.contains("@");
                if (isEmail && !ValidationUtils.isValidEmail(emailOrPhone)) {
                    ErrorResponse.sendError(exchange, 400, "Invalid email format");
                    return;
                } else if (!isEmail && !ValidationUtils.isValidPhoneNumber(emailOrPhone)) {
                    ErrorResponse.sendError(exchange, 400, "Invalid phone number format");
                    return;
                }
                
                System.out.println("🟢 [BACKEND STEP 6/10] Calling authService.login() with emailOrPhone: " + emailOrPhone);
                
                // authenticate user
                long loginStartTime = System.currentTimeMillis();
                User user = authService.login(emailOrPhone, password);
                long loginTime = System.currentTimeMillis() - loginStartTime;
                System.out.println("🟢 [BACKEND STEP 6/10] authService.login() completed in " + loginTime + "ms");
                
                if (user != null) {
                    System.out.println("🟢 [BACKEND STEP 7/10] User authenticated, userId: " + user.getUserId());
                    System.out.println("🟢 [BACKEND STEP 8/10] Creating session...");
                    // generate session token
                    long sessionStartTime = System.currentTimeMillis();
                    String token = authService.createSession(user.getUserId());
                    long sessionTime = System.currentTimeMillis() - sessionStartTime;
                    System.out.println("🟢 [BACKEND STEP 8/10] Session creation completed in " + sessionTime + "ms");
                    
                    if (token == null) {
                        System.out.println("🟢 [BACKEND STEP 8/10] Session creation failed, returning 500");
                        ErrorResponse.sendError(exchange, 500, "Failed to create session. Please try again.");
                        return;
                    }
                    
                    System.out.println("🟢 [BACKEND STEP 9/10] Building success response...");
                    JSONObject response = new JSONObject();
                    response.put("success", true);
                    response.put("userId", user.getUserId());
                    response.put("name", user.getName());
                    response.put("email", user.getEmail());
                    response.put("token", token);
                    
                    System.out.println("🟢 [BACKEND STEP 10/10] Sending 200 response");
                    long totalTime = System.currentTimeMillis() - startTime;
                    System.out.println("🟢 [BACKEND STEP 10/10] Total request time: " + totalTime + "ms");
                    sendResponse(exchange, 200, response.toString());
                } else {
                    System.out.println("🟢 [BACKEND STEP 7/10] User authentication failed (invalid credentials)");
                    System.out.println("🟢 [BACKEND STEP 10/10] Sending 401 response");
                    ErrorResponse.sendError(exchange, 401, "Invalid email or password");
                }
                
            } catch (Exception e) {
                System.err.println("🔴 [BACKEND ERROR] Exception in LoginHandler:");
                System.err.println("🔴 [BACKEND ERROR] Message: " + e.getMessage());
                System.err.println("🔴 [BACKEND ERROR] Class: " + e.getClass().getName());
                e.printStackTrace();
                JSONObject response = new JSONObject();
                response.put("success", false);
                response.put("message", "Server error: " + e.getMessage());
                
                System.err.println("🔴 [BACKEND ERROR] Sending 500 error response");
                sendResponse(exchange, 500, response.toString());
            }
        }
    }



    
    /**
     * sign up handler
     */
    public static class SignupHandler implements HttpHandler {
        private AuthService authService = new AuthService();
        
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            //enable CORS
            String allowedOrigin = System.getenv("ALLOWED_ORIGIN");
            if (allowedOrigin == null || allowedOrigin.isEmpty()) {
                allowedOrigin = "*"; // Default to wildcard for development
            }
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", allowedOrigin);
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            if (!"POST".equals(exchange.getRequestMethod())) {
                ErrorResponse.sendError(exchange, 405, "Method not allowed");
                return;
            }
            
            // Rate limiting for signup endpoint
            String clientIP = getClientIP(exchange);
            if (!RateLimiter.isAllowed(clientIP, true)) {
                ErrorResponse.sendError(exchange, 429, "Too many requests. Please try again later.");
                return;
            }
            
            try {
                // read request body
                String requestBody = readRequestBody(exchange);
                JSONObject json = new JSONObject(requestBody);

                // Validate required fields
                if (!json.has("name") || json.getString("name").trim().isEmpty()) {
                    ErrorResponse.sendError(exchange, 400, "name is required");
                    return;
                }
                if (!json.has("email") || json.getString("email").trim().isEmpty()) {
                    ErrorResponse.sendError(exchange, 400, "email is required");
                    return;
                }
                if (!json.has("phoneNumber") || json.getString("phoneNumber").trim().isEmpty()) {
                    ErrorResponse.sendError(exchange, 400, "phoneNumber is required");
                    return;
                }
                if (!json.has("password") || json.getString("password").trim().isEmpty()) {
                    ErrorResponse.sendError(exchange, 400, "password is required");
                    return;
                }
                
                String name = ValidationUtils.sanitizeBasic(json.getString("name"));
                String email = json.getString("email").trim().toLowerCase();
                String phoneNumber = json.getString("phoneNumber").trim();
                String password = json.getString("password");
                
                // Validate inputs
                if (!ValidationUtils.isValidName(name)) {
                    ErrorResponse.sendError(exchange, 400, "Invalid name format");
                    return;
                }
                
                if (!ValidationUtils.isValidEmail(email)) {
                    ErrorResponse.sendError(exchange, 400, "Invalid email format");
                    return;
                }
                
                if (!ValidationUtils.isValidPhoneNumber(phoneNumber)) {
                    ErrorResponse.sendError(exchange, 400, "Invalid phone number format");
                    return;
                }
                
                // Validate password strength
                String passwordError = ValidationUtils.getPasswordError(password);
                if (passwordError != null) {
                    ErrorResponse.sendError(exchange, 400, passwordError);
                    return;
                }
                
                //ccreate user
                User user = authService.signup(name, email, phoneNumber, password);
                
                if (user != null) {
                    JSONObject response = new JSONObject();
                    response.put("success", true);
                    response.put("message", "Account created successfully");
                    response.put("userId", user.getUserId());

                    
                    sendResponse(exchange, 201, response.toString());
                } else {
                    ErrorResponse.sendError(exchange, 400, "Email or phone number already exists");
                }
                
            } catch (Exception e) {
                e.printStackTrace();
                ErrorResponse.sendError(exchange, 500, "Server error: " + e.getMessage());
            }
        }
    }
    




    /**
     * helper method to read request body
     */
    private static String readRequestBody(HttpExchange exchange) throws IOException {
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)
        );
        StringBuilder body = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            body.append(line);
        }
        return body.toString();
    }
    
    /**
     * helper method to send response
     */

    private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        // Ensure CORS headers are set on all responses
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        String allowedOrigin = System.getenv("ALLOWED_ORIGIN");
        if (allowedOrigin == null || allowedOrigin.isEmpty()) {
            allowedOrigin = "*"; // Default to wildcard for development
        }
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", allowedOrigin);
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }
    
    /**
     * Get client IP address from request
     */
    private static String getClientIP(HttpExchange exchange) {
        // Try to get real IP from X-Forwarded-For header (for proxies)
        String forwardedFor = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            return forwardedFor.split(",")[0].trim();
        }
        
        // Fall back to remote address
        InetSocketAddress remoteAddress = exchange.getRemoteAddress();
        if (remoteAddress != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        
        return "unknown";
    }
}