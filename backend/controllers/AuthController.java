package controllers;

import models.User;
import services.AuthService;
import utils.RateLimiter;
import utils.ValidationUtils;
import utils.ErrorResponse;
import utils.Logger;
import utils.Constants;
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
            Logger.debug("AuthController", "LoginHandler.handle() called");
            long startTime = System.currentTimeMillis();
            
            // Enable CORS (Auth endpoints are public)
            ErrorResponse.addCorsHeaders(exchange);
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            if (!"POST".equals(exchange.getRequestMethod())) {
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
                Logger.debug("AuthController", "Reading request body...");
                String requestBody = readRequestBody(exchange);
                Logger.debug("AuthController", "Request body received, length: " + requestBody.length());
                
                JSONObject json = new JSONObject(requestBody);
                
                // Validate required fields
                if (!json.has("emailOrPhone") || json.getString("emailOrPhone").trim().isEmpty()) {
                    ErrorResponse.sendError(exchange, 400, "emailOrPhone is required");
                    return;
                }
                if (!json.has("password") || json.getString("password").trim().isEmpty()) {
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
                
                Logger.debug("AuthController", "Calling authService.login() with emailOrPhone: " + emailOrPhone);
                
                // authenticate user
                long loginStartTime = System.currentTimeMillis();
                User user = authService.login(emailOrPhone, password);
                long loginTime = System.currentTimeMillis() - loginStartTime;
                Logger.timing("AuthController", "authService.login()", loginTime);
                
                if (user != null) {
                    Logger.debug("AuthController", "User authenticated, userId: " + user.getUserId());
                    // generate session token
                    long sessionStartTime = System.currentTimeMillis();
                    String token = authService.createSession(user.getUserId());
                    long sessionTime = System.currentTimeMillis() - sessionStartTime;
                    Logger.timing("AuthController", "Session creation", sessionTime);
                    
                    if (token == null) {
                        ErrorResponse.sendError(exchange, 500, "Failed to create session. Please try again.");
                        return;
                    }
                    
                    JSONObject response = ErrorResponse.success(null);
                    response.put("userId", user.getUserId());
                    response.put("name", user.getName());
                    response.put("email", user.getEmail());
                    response.put("token", token);
                    
                    long totalTime = System.currentTimeMillis() - startTime;
                    Logger.timing("AuthController", "Total login request", totalTime);
                    ErrorResponse.sendJson(exchange, 200, response);
                } else {
                    Logger.debug("AuthController", "User authentication failed (invalid credentials)");
                    ErrorResponse.sendError(exchange, 401, "Invalid email or password");
                }
                
            } catch (Exception e) {
                Logger.error("AuthController", "Exception in LoginHandler: " + e.getMessage(), e);
                ErrorResponse.sendError(exchange, 500, "Server error: " + e.getMessage());
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
                    JSONObject response = ErrorResponse.success("Account created successfully");
                    response.put("userId", user.getUserId());
                    ErrorResponse.sendJson(exchange, 201, response);
                } else {
                    ErrorResponse.sendError(exchange, 400, "Email or phone number already exists");
                }
                
            } catch (Exception e) {
                Logger.error("AuthController", "Exception in SignupHandler: " + e.getMessage(), e);
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