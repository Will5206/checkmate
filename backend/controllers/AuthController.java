package com.checkmate.controllers;

import com.checkmate.models.User;
import com.checkmate.services.AuthService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
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
            
            try {
                String requestBody = readRequestBody(exchange);
                JSONObject json = new JSONObject(requestBody);
                
                String emailOrPhone = json.getString("emailOrPhone");
                String password = json.getString("password");
                
                // authenticate user
                User user = authService.login(emailOrPhone, password);
                
                if (user != null) {
                    // generate session token
                    String token = authService.createSession(user.getUserId());
                    
                    JSONObject response = new JSONObject();
                    response.put("success", true);
                    response.put("userId", user.getUserId());

                    response.put("name", user.getName());
                    response.put("email", user.getEmail());
                    response.put("token", token);

                    
                    sendResponse(exchange, 200, response.toString());
                } else {
                    JSONObject response = new JSONObject();
                    response.put("success", false);
                    response.put("message", "Invalid email or password");
                    
                    sendResponse(exchange, 401, response.toString());
                }
                
            } catch (Exception e) {
                e.printStackTrace();
                JSONObject response = new JSONObject();
                response.put("success", false);
                response.put("message", "Server error: " + e.getMessage());
                
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
            
            try {
                // read request body
                String requestBody = readRequestBody(exchange);
                JSONObject json = new JSONObject(requestBody);

                
                String name = json.getString("name");

                String email = json.getString("email");
                String phoneNumber = json.getString("phoneNumber");

                String password = json.getString("password");
                
                //ccreate user
                User user = authService.signup(name, email, phoneNumber, password);
                
                if (user != null) {
                    JSONObject response = new JSONObject();
                    response.put("success", true);
                    response.put("message", "Account created successfully");
                    response.put("userId", user.getUserId());

                    
                    sendResponse(exchange, 201, response.toString());
                } else {
                    JSONObject response = new JSONObject();
                    response.put("success", false);
                    response.put("message", "Email or phone number already exists");
                    
                    sendResponse(exchange, 400, response.toString());
                }
                
            } catch (Exception e) {
                e.printStackTrace();
                JSONObject response = new JSONObject();
                response.put("success", false);
                response.put("message", "Server error: " + e.getMessage());
                
                sendResponse(exchange, 500, response.toString());
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
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }
}