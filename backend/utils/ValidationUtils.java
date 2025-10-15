package com.checkmate.utils;

import java.util.regex.Pattern;




/**
 * utility class for input validation
 */
public class ValidationUtils {
    
    private static final Pattern EMAIL_PATTERN = 
        Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    
    private static final Pattern PHONE_PATTERN = 
        Pattern.compile("^[\\(]?\\d{3}[\\)]?[\\s-]?\\d{3}[\\s-]?\\d{4}$");
    

    /**
     * validate email format
     */
    public static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }
    
    /**
     * balidate phone number format
     */
    public static boolean isValidPhoneNumber(String phone) {
        return phone != null && PHONE_PATTERN.matcher(phone).matches();
    }
    


    /**
     * validate password strength
     */
    public static boolean isValidPassword(String password) {
        return password != null && password.length() >= 8;
    }
    
    /**
     * sanitize string input
     */
    public static String sanitize(String input) {
        if (input == null) return null;
        return input.trim();
    }
}