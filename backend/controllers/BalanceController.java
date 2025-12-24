package controllers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import services.BalanceService;
import utils.AuthMiddleware;
import utils.ErrorResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for balance-related API endpoints.
 */
public class BalanceController {

    /**
     * Handler for GET /api/balance?userId=xxx
     * Returns the current balance for a user.
     */
    public static class GetBalanceHandler implements HttpHandler {
        private BalanceService balanceService = new BalanceService();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Add CORS headers
            addCors(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            if (!"GET".equals(exchange.getRequestMethod())) {
                ErrorResponse.sendError(exchange, 405, "Method not allowed");
                return;
            }

            // Verify authentication
            String authenticatedUserId = AuthMiddleware.verifyAuth(exchange);
            if (authenticatedUserId == null) {
                AuthMiddleware.sendUnauthorized(exchange);
                return;
            }

            try {
                // Parse query parameters
                URI uri = exchange.getRequestURI();
                Map<String, String> params = parseQueryParams(uri.getQuery());

                String userId = params.get("userId");

                if (userId == null || userId.trim().isEmpty()) {
                    ErrorResponse.sendError(exchange, 400, "userId parameter is required");
                    return;
                }
                
                // Verify user can only access their own balance
                if (!userId.equals(authenticatedUserId)) {
                    ErrorResponse.sendError(exchange, 403, "Forbidden: Cannot access another user's balance");
                    return;
                }

                // Get balance from service
                double balance = balanceService.getCurrentBalance(userId);

                // Return balance as JSON
                String jsonResponse = String.format("{\"balance\": %.2f, \"userId\": \"%s\"}", balance, userId);
                sendResponse(exchange, 200, jsonResponse);

            } catch (Exception e) {
                System.err.println("Error getting balance: " + e.getMessage());
                e.printStackTrace();
                ErrorResponse.sendError(exchange, 500, "Internal server error");
            }
        }

        private Map<String, String> parseQueryParams(String query) {
            Map<String, String> params = new HashMap<>();
            if (query != null && !query.isEmpty()) {
                String[] pairs = query.split("&");
                for (String pair : pairs) {
                    String[] keyValue = pair.split("=");
                    if (keyValue.length == 2) {
                        params.put(keyValue[0], keyValue[1]);
                    }
                }
            }
            return params;
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            addCors(exchange);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, response.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
        
        private void addCors(HttpExchange exchange) {
            // In production, replace with specific allowed origins
            String allowedOrigin = System.getenv("ALLOWED_ORIGIN");
            if (allowedOrigin == null || allowedOrigin.isEmpty()) {
                allowedOrigin = "*"; // Default to wildcard for development
            }
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", allowedOrigin);
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        }
    }

    /**
     * Handler for POST /api/balance/add?userId=xxx&amount=xxx
     * Adds money to a user's balance (test feature).
     */
    public static class AddMoneyHandler implements HttpHandler {
        private BalanceService balanceService = new BalanceService();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCors(exchange);
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

            try {
                URI uri = exchange.getRequestURI();
                Map<String, String> params = parseQueryParams(uri.getQuery());

                String userId = params.get("userId");
                String amountStr = params.get("amount");

                if (userId == null || userId.trim().isEmpty() || amountStr == null || amountStr.trim().isEmpty()) {
                    ErrorResponse.sendError(exchange, 400, "userId and amount parameters are required");
                    return;
                }
                
                // Verify user can only modify their own balance
                if (!userId.equals(authenticatedUserId)) {
                    ErrorResponse.sendError(exchange, 403, "Forbidden: Cannot modify another user's balance");
                    return;
                }

                double amount = Double.parseDouble(amountStr);
                if (amount <= 0) {
                    ErrorResponse.sendError(exchange, 400, "Amount must be positive");
                    return;
                }

                // Add to balance
                boolean success = balanceService.addToBalance(
                    userId,
                    amount,
                    BalanceService.TYPE_ADJUSTMENT,
                    "Manual balance addition (test feature)",
                    null,
                    "manual_adjustment"
                );

                if (success) {
                    double newBalance = balanceService.getCurrentBalance(userId);
                    String jsonResponse = String.format("{\"success\": true, \"balance\": %.2f, \"amountAdded\": %.2f}", 
                        newBalance, amount);
                    sendResponse(exchange, 200, jsonResponse);
                } else {
                    ErrorResponse.sendError(exchange, 500, "Failed to add money");
                }

            } catch (NumberFormatException e) {
                ErrorResponse.sendError(exchange, 400, "Invalid amount format");
            } catch (Exception e) {
                System.err.println("Error adding money: " + e.getMessage());
                e.printStackTrace();
                ErrorResponse.sendError(exchange, 500, "Internal server error");
            }
        }

        private Map<String, String> parseQueryParams(String query) {
            Map<String, String> params = new HashMap<>();
            if (query != null && !query.isEmpty()) {
                String[] pairs = query.split("&");
                for (String pair : pairs) {
                    String[] keyValue = pair.split("=", 2);
                    if (keyValue.length == 2) {
                        try {
                            params.put(keyValue[0], java.net.URLDecoder.decode(keyValue[1], "UTF-8"));
                        } catch (java.io.UnsupportedEncodingException e) {
                            params.put(keyValue[0], keyValue[1]);
                        }
                    }
                }
            }
            return params;
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(statusCode, response.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    /**
     * Handler for POST /api/balance/cashout?userId=xxx&amount=xxx
     * Withdraws money from a user's balance (test feature).
     */
    public static class CashOutHandler implements HttpHandler {
        private BalanceService balanceService = new BalanceService();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCors(exchange);
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

            try {
                URI uri = exchange.getRequestURI();
                Map<String, String> params = parseQueryParams(uri.getQuery());

                String userId = params.get("userId");
                String amountStr = params.get("amount");

                if (userId == null || userId.trim().isEmpty() || amountStr == null || amountStr.trim().isEmpty()) {
                    ErrorResponse.sendError(exchange, 400, "userId and amount parameters are required");
                    return;
                }
                
                // Verify user can only modify their own balance
                if (!userId.equals(authenticatedUserId)) {
                    ErrorResponse.sendError(exchange, 403, "Forbidden: Cannot modify another user's balance");
                    return;
                }

                double amount = Double.parseDouble(amountStr);
                if (amount <= 0) {
                    ErrorResponse.sendError(exchange, 400, "Amount must be positive");
                    return;
                }

                // Check balance first
                double currentBalance = balanceService.getCurrentBalance(userId);
                if (currentBalance < amount) {
                    ErrorResponse.sendError(exchange, 400, 
                        String.format("Insufficient balance. You have $%.2f, trying to cash out $%.2f", 
                        currentBalance, amount));
                    return;
                }

                // Subtract from balance
                boolean success = balanceService.subtractFromBalance(
                    userId,
                    amount,
                    BalanceService.TYPE_ADJUSTMENT,
                    "Manual balance withdrawal (test feature)",
                    null,
                    "manual_adjustment"
                );

                if (success) {
                    double newBalance = balanceService.getCurrentBalance(userId);
                    String jsonResponse = String.format("{\"success\": true, \"balance\": %.2f, \"amountWithdrawn\": %.2f}", 
                        newBalance, amount);
                    sendResponse(exchange, 200, jsonResponse);
                } else {
                    ErrorResponse.sendError(exchange, 500, "Failed to cash out");
                }

            } catch (IllegalArgumentException e) {
                ErrorResponse.sendError(exchange, 400, e.getMessage());
            } catch (Exception e) {
                System.err.println("Error cashing out: " + e.getMessage());
                e.printStackTrace();
                ErrorResponse.sendError(exchange, 500, "Internal server error");
            }
        }

        private Map<String, String> parseQueryParams(String query) {
            Map<String, String> params = new HashMap<>();
            if (query != null && !query.isEmpty()) {
                String[] pairs = query.split("&");
                for (String pair : pairs) {
                    String[] keyValue = pair.split("=", 2);
                    if (keyValue.length == 2) {
                        try {
                            params.put(keyValue[0], java.net.URLDecoder.decode(keyValue[1], "UTF-8"));
                        } catch (java.io.UnsupportedEncodingException e) {
                            params.put(keyValue[0], keyValue[1]);
                        }
                    }
                }
            }
            return params;
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            addCors(exchange);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, response.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
        
        private void addCors(HttpExchange exchange) {
            // In production, replace with specific allowed origins
            String allowedOrigin = System.getenv("ALLOWED_ORIGIN");
            if (allowedOrigin == null || allowedOrigin.isEmpty()) {
                allowedOrigin = "*"; // Default to wildcard for development
            }
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", allowedOrigin);
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        }
    }
    
    // Shared CORS helper
    private static void addCors(HttpExchange exchange) {
        // In production, replace with specific allowed origins
        String allowedOrigin = System.getenv("ALLOWED_ORIGIN");
        if (allowedOrigin == null || allowedOrigin.isEmpty()) {
            allowedOrigin = "*"; // Default to wildcard for development
        }
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", allowedOrigin);
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }
}
