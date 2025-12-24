package utils;

import services.AuthService;
import com.sun.net.httpserver.HttpExchange;
import org.json.JSONObject;
import java.io.IOException;

/**
 * Authentication middleware for protecting API endpoints
 */
public class AuthMiddleware {
    
    private static final AuthService authService = new AuthService();
    
    /**
     * Verify authentication token from request headers
     * @param exchange HTTP exchange
     * @return User ID if authenticated, null otherwise
     */
    public static String verifyAuth(HttpExchange exchange) {
        // Get token from Authorization header
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        
        if (authHeader == null || authHeader.isEmpty()) {
            return null;
        }
        
        // Support "Bearer <token>" format
        String token;
        if (authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else {
            token = authHeader;
        }
        
        // Verify token
        return authService.verifyToken(token);
    }
    
    /**
     * Send unauthorized response
     */
    public static void sendUnauthorized(HttpExchange exchange) throws IOException {
        JSONObject response = new JSONObject();
        response.put("success", false);
        response.put("message", "Authentication required");
        
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(401, response.toString().getBytes().length);
        try (var os = exchange.getResponseBody()) {
            os.write(response.toString().getBytes());
        }
    }
    
    /**
     * Check if endpoint requires authentication
     * Public endpoints: /api/auth/login, /api/auth/signup
     */
    public static boolean isPublicEndpoint(String path) {
        return path.equals("/api/auth/login") || 
               path.equals("/api/auth/signup") ||
               path.startsWith("/api/auth/login") ||
               path.startsWith("/api/auth/signup");
    }
}

