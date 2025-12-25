package utils;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for parsing HTTP request data.
 * Centralizes common request parsing operations.
 */
public class RequestUtils {
    
    /**
     * Parse query parameters from URI into a Map.
     * @param uri The URI containing query parameters
     * @return Map of parameter names to values
     */
    public static Map<String, String> parseQuery(URI uri) {
        String query = uri.getQuery();
        if (query == null || query.isEmpty()) {
            return new HashMap<>();
        }
        
        Map<String, String> params = new HashMap<>();
        String[] pairs = query.split("&");
        
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                String key = pair.substring(0, idx);
                String value = pair.substring(idx + 1);
                // URL decode if needed (basic implementation)
                params.put(key, value);
            } else {
                params.put(pair, "");
            }
        }
        
        return params;
    }
    
    /**
     * Parse query string directly.
     * @param queryString The query string (e.g., "key1=value1&key2=value2")
     * @return Map of parameter names to values
     */
    public static Map<String, String> parseQueryString(String queryString) {
        if (queryString == null || queryString.isEmpty()) {
            return new HashMap<>();
        }
        
        Map<String, String> params = new HashMap<>();
        String[] pairs = queryString.split("&");
        
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                String key = pair.substring(0, idx);
                String value = pair.substring(idx + 1);
                params.put(key, value);
            } else {
                params.put(pair, "");
            }
        }
        
        return params;
    }
    
    /**
     * Get a query parameter as an integer with default value.
     * @param params The parsed query parameters
     * @param key The parameter key
     * @param defaultValue The default value if not found or invalid
     * @return The integer value or default
     */
    public static int getIntParam(Map<String, String> params, String key, int defaultValue) {
        String value = params.get(key);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * Get a query parameter as a string with default value.
     * @param params The parsed query parameters
     * @param key The parameter key
     * @param defaultValue The default value if not found
     * @return The string value or default
     */
    public static String getStringParam(Map<String, String> params, String key, String defaultValue) {
        String value = params.get(key);
        return (value == null || value.isEmpty()) ? defaultValue : value;
    }
    
    private RequestUtils() {
        throw new AssertionError("RequestUtils class should not be instantiated");
    }
}

