package controllers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import database.DatabaseConnection;
import org.json.JSONObject;
import utils.ErrorResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;

/**
 * Health check endpoint for monitoring
 * GET /health - Returns server and database status
 */
public class HealthController {
    
    public static class HealthCheckHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Enable CORS
            String allowedOrigin = System.getenv("ALLOWED_ORIGIN");
            if (allowedOrigin == null || allowedOrigin.isEmpty()) {
                allowedOrigin = "*";
            }
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", allowedOrigin);
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            if (!"GET".equals(exchange.getRequestMethod())) {
                ErrorResponse.sendError(exchange, 405, "Method not allowed");
                return;
            }
            
            JSONObject health = new JSONObject();
            health.put("status", "healthy");
            health.put("timestamp", System.currentTimeMillis());
            
            // Check database connection
            boolean dbHealthy = false;
            try {
                DatabaseConnection dbConnection = DatabaseConnection.getInstance();
                Connection conn = dbConnection.getConnection();
                if (conn != null && !conn.isClosed() && conn.isValid(2)) {
                    dbHealthy = true;
                }
            } catch (Exception e) {
                // Database connection failed
            }
            
            health.put("database", dbHealthy ? "connected" : "disconnected");
            
            int statusCode = dbHealthy ? 200 : 503; // 503 Service Unavailable if DB is down
            if (!dbHealthy) {
                health.put("status", "degraded");
            }
            
            sendResponse(exchange, statusCode, health.toString());
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
}

