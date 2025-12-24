package utils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Simple in-memory rate limiter to prevent abuse
 * Uses sliding window algorithm
 */
public class RateLimiter {
    
    private static final ConcurrentHashMap<String, RequestCounter> requestCounts = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService cleanupExecutor = Executors.newScheduledThreadPool(1);
    
    // Default limits
    private static final int DEFAULT_MAX_REQUESTS = 100; // per window
    private static final long DEFAULT_WINDOW_SECONDS = 60; // 1 minute window
    
    // Stricter limits for auth endpoints
    private static final int AUTH_MAX_REQUESTS = 5; // 5 login attempts per minute
    private static final long AUTH_WINDOW_SECONDS = 60;
    
    static {
        // Cleanup old entries every 5 minutes
        cleanupExecutor.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            requestCounts.entrySet().removeIf(entry -> {
                RequestCounter counter = entry.getValue();
                return (now - counter.windowStart) > (counter.windowSeconds * 1000);
            });
        }, 5, 5, TimeUnit.MINUTES);
    }
    
    /**
     * Check if request should be allowed
     * @param identifier IP address or user ID
     * @param isAuthEndpoint Whether this is an authentication endpoint
     * @return true if request is allowed, false if rate limited
     */
    public static boolean isAllowed(String identifier, boolean isAuthEndpoint) {
        if (identifier == null || identifier.isEmpty()) {
            return false;
        }
        
        String key = identifier + (isAuthEndpoint ? ":auth" : ":api");
        long now = System.currentTimeMillis();
        
        RequestCounter counter = requestCounts.computeIfAbsent(key, k -> {
            int maxRequests = isAuthEndpoint ? AUTH_MAX_REQUESTS : DEFAULT_MAX_REQUESTS;
            long windowSeconds = isAuthEndpoint ? AUTH_WINDOW_SECONDS : DEFAULT_WINDOW_SECONDS;
            return new RequestCounter(maxRequests, windowSeconds);
        });
        
        // Reset window if expired
        if (now - counter.windowStart > (counter.windowSeconds * 1000)) {
            counter.count = 0;
            counter.windowStart = now;
        }
        
        // Check if limit exceeded
        if (counter.count >= counter.maxRequests) {
            return false;
        }
        
        // Increment counter
        counter.count++;
        return true;
    }
    
    /**
     * Get remaining requests in current window
     * @param identifier IP address or user ID
     * @param isAuthEndpoint Whether this is an authentication endpoint
     * @return Number of remaining requests
     */
    public static int getRemainingRequests(String identifier, boolean isAuthEndpoint) {
        if (identifier == null || identifier.isEmpty()) {
            return 0;
        }
        
        String key = identifier + (isAuthEndpoint ? ":auth" : ":api");
        RequestCounter counter = requestCounts.get(key);
        
        if (counter == null) {
            int maxRequests = isAuthEndpoint ? AUTH_MAX_REQUESTS : DEFAULT_MAX_REQUESTS;
            return maxRequests;
        }
        
        long now = System.currentTimeMillis();
        if (now - counter.windowStart > (counter.windowSeconds * 1000)) {
            // Window expired, reset
            return counter.maxRequests;
        }
        
        return Math.max(0, counter.maxRequests - counter.count);
    }
    
    private static class RequestCounter {
        int count = 0;
        long windowStart = System.currentTimeMillis();
        final int maxRequests;
        final long windowSeconds;
        
        RequestCounter(int maxRequests, long windowSeconds) {
            this.maxRequests = maxRequests;
            this.windowSeconds = windowSeconds;
        }
    }
}

