package utils;

import java.util.regex.Pattern;

/**
 * Utility class for input validation and sanitization
 */
public class ValidationUtils {
    
    private static final Pattern EMAIL_PATTERN = 
        Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    
    private static final Pattern PHONE_PATTERN = 
        Pattern.compile("^[\\(]?\\d{3}[\\)]?[\\s-]?\\d{3}[\\s-]?\\d{4}$");
    
    // Password must be at least 8 characters, contain uppercase, lowercase, and number
    private static final Pattern PASSWORD_PATTERN = 
        Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$");

    /**
     * Validate email format
     * @param email Email address to validate
     * @return true if valid email format
     */
    public static boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        // Additional length check
        if (email.length() > 255) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email.trim().toLowerCase()).matches();
    }
    
    /**
     * Validate phone number format
     * @param phone Phone number to validate
     * @return true if valid phone format
     */
    public static boolean isValidPhoneNumber(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return false;
        }
        return PHONE_PATTERN.matcher(phone.trim()).matches();
    }

    /**
     * Validate password strength
     * Requirements: At least 8 characters, contains uppercase, lowercase, and number
     * @param password Password to validate
     * @return true if password meets requirements
     */
    public static boolean isValidPassword(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }
        // Check for minimum complexity
        return PASSWORD_PATTERN.matcher(password).matches();
    }
    
    /**
     * Get password validation error message
     * @param password Password to check
     * @return Error message if invalid, null if valid
     */
    public static String getPasswordError(String password) {
        if (password == null || password.isEmpty()) {
            return "Password is required";
        }
        if (password.length() < 8) {
            return "Password must be at least 8 characters long";
        }
        if (!password.matches(".*[a-z].*")) {
            return "Password must contain at least one lowercase letter";
        }
        if (!password.matches(".*[A-Z].*")) {
            return "Password must contain at least one uppercase letter";
        }
        if (!password.matches(".*\\d.*")) {
            return "Password must contain at least one number";
        }
        return null; // Valid password
    }
    
    /**
     * Sanitize string input to prevent XSS
     * Removes HTML tags and trims whitespace
     * @param input String to sanitize
     * @return Sanitized string
     */
    public static String sanitize(String input) {
        if (input == null) return null;
        String sanitized = input.trim();
        // Remove HTML tags to prevent XSS
        sanitized = sanitized.replaceAll("<[^>]*>", "");
        // Escape special characters that could be used in XSS
        sanitized = sanitized.replace("&", "&amp;")
                            .replace("<", "&lt;")
                            .replace(">", "&gt;")
                            .replace("\"", "&quot;")
                            .replace("'", "&#x27;");
        return sanitized;
    }
    
    /**
     * Sanitize string input without HTML escaping (for non-HTML contexts)
     * Just trims and removes null bytes
     * @param input String to sanitize
     * @return Sanitized string
     */
    public static String sanitizeBasic(String input) {
        if (input == null) return null;
        String sanitized = input.trim();
        // Remove null bytes
        sanitized = sanitized.replace("\0", "");
        // Limit length to prevent DoS
        if (sanitized.length() > 1000) {
            sanitized = sanitized.substring(0, 1000);
        }
        return sanitized;
    }
    
    /**
     * Validate name (alphanumeric, spaces, hyphens, apostrophes)
     * @param name Name to validate
     * @return true if valid name format
     */
    public static boolean isValidName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        if (name.length() > 100) {
            return false;
        }
        // Allow letters, spaces, hyphens, apostrophes
        return name.matches("^[a-zA-Z\\s'-]+$");
    }
    
    /**
     * Validate amount (positive decimal number)
     * @param amount Amount to validate
     * @return true if valid amount
     */
    public static boolean isValidAmount(double amount) {
        return amount > 0 && amount <= 999999.99;
    }
    
    /**
     * Validate receipt ID (positive integer)
     * @param receiptId Receipt ID to validate
     * @return true if valid receipt ID
     */
    public static boolean isValidReceiptId(int receiptId) {
        return receiptId > 0;
    }
}