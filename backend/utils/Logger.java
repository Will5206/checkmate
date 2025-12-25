package utils;

/**
 * Centralized logging utility for CheckMate application.
 * Replaces System.out.println with configurable logging levels.
 */
public class Logger {
    
    // Log levels controlled by environment variable
    private static final boolean DEBUG_ENABLED = "true".equals(System.getenv("DEBUG")) || 
                                                 "true".equals(System.getProperty("debug"));
    private static final boolean INFO_ENABLED = true; // Always log info and above
    
    /**
     * Log a debug message (only shown when DEBUG=true)
     */
    public static void debug(String message) {
        if (DEBUG_ENABLED) {
            System.out.println("[DEBUG] " + message);
        }
    }
    
    /**
     * Log a debug message with context
     */
    public static void debug(String context, String message) {
        if (DEBUG_ENABLED) {
            System.out.println("[DEBUG] [" + context + "] " + message);
        }
    }
    
    /**
     * Log an info message (always shown)
     */
    public static void info(String message) {
        if (INFO_ENABLED) {
            System.out.println("[INFO] " + message);
        }
    }
    
    /**
     * Log an info message with context
     */
    public static void info(String context, String message) {
        if (INFO_ENABLED) {
            System.out.println("[INFO] [" + context + "] " + message);
        }
    }
    
    /**
     * Log a warning message
     */
    public static void warn(String message) {
        System.err.println("[WARN] " + message);
    }
    
    /**
     * Log a warning message with context
     */
    public static void warn(String context, String message) {
        System.err.println("[WARN] [" + context + "] " + message);
    }
    
    /**
     * Log an error message
     */
    public static void error(String message) {
        System.err.println("[ERROR] " + message);
    }
    
    /**
     * Log an error message with context
     */
    public static void error(String context, String message) {
        System.err.println("[ERROR] [" + context + "] " + message);
    }
    
    /**
     * Log an error message with exception
     */
    public static void error(String message, Throwable throwable) {
        System.err.println("[ERROR] " + message);
        if (throwable != null) {
            throwable.printStackTrace();
        }
    }
    
    /**
     * Log an error message with context and exception
     */
    public static void error(String context, String message, Throwable throwable) {
        System.err.println("[ERROR] [" + context + "] " + message);
        if (throwable != null) {
            throwable.printStackTrace();
        }
    }
    
    /**
     * Log timing information (debug level)
     */
    public static void timing(String context, String operation, long durationMs) {
        if (DEBUG_ENABLED) {
            debug(context, operation + " completed in " + durationMs + "ms");
        }
    }
}

