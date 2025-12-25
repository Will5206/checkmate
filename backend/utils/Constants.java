package utils;

/**
 * Centralized constants for the CheckMate application.
 * All magic numbers and hardcoded values should be defined here.
 */
public class Constants {
    
    // File upload limits
    public static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10MB
    public static final int MAX_FILE_SIZE_MB = 10;
    
    // Receipt limits
    public static final int MAX_RECEIPT_ITEMS = 100;
    public static final int MAX_RECEIPT_PARTICIPANTS = 50;
    public static final int MAX_MERCHANT_NAME_LENGTH = 255;
    public static final int MAX_ITEM_NAME_LENGTH = 255;
    public static final int MIN_ITEM_QUANTITY = 1;
    public static final int MAX_ITEM_QUANTITY = 100;
    
    // Timeouts
    public static final int PYTHON_PARSER_TIMEOUT_SECONDS = 120;
    public static final int PYTHON_PARSER_THREAD_JOIN_TIMEOUT_MS = 5000;
    public static final int DATABASE_QUERY_TIMEOUT_SECONDS = 10;
    public static final int DATABASE_CONNECTION_VALIDATION_TIMEOUT_SECONDS = 5;
    public static final int DATABASE_CONNECTION_CHECK_TIMEOUT_SECONDS = 2;
    
    // Rate limiting
    public static final int RATE_LIMIT_LOGIN_REQUESTS = 5;
    public static final int RATE_LIMIT_LOGIN_WINDOW_SECONDS = 60;
    public static final int RATE_LIMIT_SIGNUP_REQUESTS = 5;
    public static final int RATE_LIMIT_SIGNUP_WINDOW_SECONDS = 60;
    public static final int RATE_LIMIT_GENERAL_REQUESTS = 100;
    public static final int RATE_LIMIT_GENERAL_WINDOW_SECONDS = 60;
    
    // Session management
    public static final int SESSION_DURATION_DAYS = 30;
    public static final long SESSION_DURATION_MS = 30L * 24 * 60 * 60 * 1000;
    
    // BCrypt password hashing
    public static final int BCRYPT_ROUNDS = 12;
    
    // Database connection parameters
    public static final int ASYNC_UPDATE_THREAD_POOL_SIZE = 5;
    
    // Precision for BigDecimal calculations
    public static final int DECIMAL_PRECISION_SCALE = 10;
    public static final int MONEY_DECIMAL_PLACES = 2;
    
    // Error message truncation
    public static final int MAX_ERROR_MESSAGE_LENGTH = 500;
    public static final int MAX_PYTHON_OUTPUT_PREVIEW_LENGTH = 500;
    public static final int MAX_ERROR_DETAIL_LENGTH = 200;
    
    // File paths (relative to project root)
    public static final String UPLOAD_DIR = "receipts/";
    public static final String PYTHON_SCRIPT_PATH = "scripts/receipt_parser_local.py";
    
    // Private constructor to prevent instantiation
    private Constants() {
        throw new AssertionError("Constants class should not be instantiated");
    }
}

