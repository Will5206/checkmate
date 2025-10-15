package com.checkmate.services;

import com.checkmate.database.DatabaseConnection;
import com.checkmate.models.User;
import com.checkmate.utils.ValidationUtils;

import java.sql.*;
import java.util.UUID;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;




/**
 * athentication service handling user login and signup
 */
public class AuthService {
    
    private DatabaseConnection dbConnection;
    
    public AuthService() {
        this.dbConnection = DatabaseConnection.getInstance();
    }
    
    /**
     * log in user with email or phone number
     * @param emailOrPhone email address or phone number
     * @param password plain text password
     * @return user object if successful, null otherwise
     */
    public User login(String emailOrPhone, String password) {
        try {
            Connection conn = dbConnection.getConnection();
            



            boolean isEmail = emailOrPhone.contains("@");
            
            String sql;
            if (isEmail) {
                sql = "SELECT * FROM users WHERE email = ?";
            } else {
                sql = "SELECT * FROM users WHERE phone_number = ?";
            }
            
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, emailOrPhone);
            
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                String storedPasswordHash = rs.getString("password_hash");
                String inputPasswordHash = hashPassword(password);
                
                // verify password
                if (storedPasswordHash.equals(inputPasswordHash)) {
                    // creat User object
                    User user = new User(
                        rs.getString("user_id"),
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getString("phone_number"),
                        storedPasswordHash,
                        rs.getDouble("balance"),
                        rs.getTimestamp("created_at"),
                        rs.getTimestamp("updated_at")
                    );
                    


                    stmt.close();
                    return user;
                }
            }
            
            stmt.close();
            return null;
            
        } catch (SQLException e) {
            System.err.println("Error during login: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * sign up new user
     * @param name full name
     * @param email email address
     * @param phoneNumber phone number
     * @param password plain text password
     * @return usser object if successful, null if user already exists
     */
    public User signup(String name, String email, String phoneNumber, String password) {
        try {
            Connection conn = dbConnection.getConnection();
            


            //check if email or phone already exists
            String checkSql = "SELECT user_id FROM users WHERE email = ? OR phone_number = ?";
            PreparedStatement checkStmt = conn.prepareStatement(checkSql);
            checkStmt.setString(1, email);
            checkStmt.setString(2, phoneNumber);
            
            ResultSet rs = checkStmt.executeQuery();
            if (rs.next()) {
                // usser already exists!
                checkStmt.close();
                return null;
            }
            checkStmt.close();
            


            // create new user

            String userId = UUID.randomUUID().toString();
            String passwordHash = hashPassword(password);
            
            String insertSql = "INSERT INTO users (user_id, name, email, phone_number, password_hash, balance) " +
                             "VALUES (?, ?, ?, ?, ?, 0.00)";
            
            PreparedStatement insertStmt = conn.prepareStatement(insertSql);
            insertStmt.setString(1, userId);
            insertStmt.setString(2, name);
            insertStmt.setString(3, email);
            insertStmt.setString(4, phoneNumber);
            insertStmt.setString(5, passwordHash);
            
            int rowsInserted = insertStmt.executeQuery().getUpdateCount();
            insertStmt.close();
            
            if (rowsInserted > 0) {
                // return created user
                User user = new User(name, email, phoneNumber, passwordHash);
                return user;
            }
            
            return null;


            
        } catch (SQLException e) {
            System.err.println("Error during signup: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * create session for user
     * @param userId User ID
     * @return Session token
     */

    public String createSession(String userId) {
        try {
            Connection conn = dbConnection.getConnection();
            
            String sessionId = UUID.randomUUID().toString();
            String token = UUID.randomUUID().toString();
            
            //session expires in 30 days !
            Timestamp expiresAt = new Timestamp(System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000));
            


            String sql = "INSERT INTO sessions (session_id, user_id, token, expires_at) VALUES (?, ?, ?, ?)";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, sessionId);
            stmt.setString(2, userId);
            stmt.setString(3, token);
            stmt.setTimestamp(4, expiresAt);
            
            stmt.executeUpdate();


            stmt.close();
            
            return token;
            
        } catch (SQLException e) {
            System.err.println("Error creating session: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }



    
    /**
     * hash password using SHA-256
     * in production---- use bcrypt or similar
     */
    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes());
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            



            return hexString.toString();
            
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error hashing password", e);
        }
    }
}