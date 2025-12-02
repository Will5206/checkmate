package controllers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import services.BalanceService;

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
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
                return;
            }

            try {
                // Parse query parameters
                URI uri = exchange.getRequestURI();
                Map<String, String> params = parseQueryParams(uri.getQuery());

                String userId = params.get("userId");

                if (userId == null || userId.trim().isEmpty()) {
                    sendResponse(exchange, 400, "{\"error\": \"userId parameter is required\"}");
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
                sendResponse(exchange, 500, "{\"error\": \"Internal server error\"}");
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
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(statusCode, response.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
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
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
                exchange.sendResponseHeaders(200, -1);
                return;
            }

            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\": false, \"error\": \"Method not allowed\"}");
                return;
            }

            try {
                URI uri = exchange.getRequestURI();
                Map<String, String> params = parseQueryParams(uri.getQuery());

                String userId = params.get("userId");
                String amountStr = params.get("amount");

                if (userId == null || userId.trim().isEmpty() || amountStr == null || amountStr.trim().isEmpty()) {
                    sendResponse(exchange, 400, "{\"success\": false, \"error\": \"userId and amount parameters are required\"}");
                    return;
                }

                double amount = Double.parseDouble(amountStr);
                if (amount <= 0) {
                    sendResponse(exchange, 400, "{\"success\": false, \"error\": \"Amount must be positive\"}");
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
                    sendResponse(exchange, 500, "{\"success\": false, \"error\": \"Failed to add money\"}");
                }

            } catch (NumberFormatException e) {
                sendResponse(exchange, 400, "{\"success\": false, \"error\": \"Invalid amount format\"}");
            } catch (Exception e) {
                System.err.println("Error adding money: " + e.getMessage());
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"success\": false, \"error\": \"Internal server error\"}");
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
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
                exchange.sendResponseHeaders(200, -1);
                return;
            }

            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\": false, \"error\": \"Method not allowed\"}");
                return;
            }

            try {
                URI uri = exchange.getRequestURI();
                Map<String, String> params = parseQueryParams(uri.getQuery());

                String userId = params.get("userId");
                String amountStr = params.get("amount");

                if (userId == null || userId.trim().isEmpty() || amountStr == null || amountStr.trim().isEmpty()) {
                    sendResponse(exchange, 400, "{\"success\": false, \"error\": \"userId and amount parameters are required\"}");
                    return;
                }

                double amount = Double.parseDouble(amountStr);
                if (amount <= 0) {
                    sendResponse(exchange, 400, "{\"success\": false, \"error\": \"Amount must be positive\"}");
                    return;
                }

                // Check balance first
                double currentBalance = balanceService.getCurrentBalance(userId);
                if (currentBalance < amount) {
                    String jsonResponse = String.format("{\"success\": false, \"error\": \"Insufficient balance. You have $%.2f, trying to cash out $%.2f\"}", 
                        currentBalance, amount);
                    sendResponse(exchange, 400, jsonResponse);
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
                    sendResponse(exchange, 500, "{\"success\": false, \"error\": \"Failed to cash out\"}");
                }

            } catch (IllegalArgumentException e) {
                sendResponse(exchange, 400, String.format("{\"success\": false, \"error\": \"%s\"}", e.getMessage()));
            } catch (NumberFormatException e) {
                sendResponse(exchange, 400, "{\"success\": false, \"error\": \"Invalid amount format\"}");
            } catch (Exception e) {
                System.err.println("Error cashing out: " + e.getMessage());
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"success\": false, \"error\": \"Internal server error\"}");
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
}
