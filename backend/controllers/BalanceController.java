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
}
