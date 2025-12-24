package services;

import database.DatabaseConnection;
import models.User;
import utils.ValidationUtils;

import java.sql.*;
import java.util.UUID;
import org.mindrot.jbcrypt.BCrypt;




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
        System.out.println("🟡 [SERVICE STEP 1/8] AuthService.login() called");
        System.out.println("🟡 [SERVICE STEP 1/8] emailOrPhone: " + emailOrPhone);
        
        try {
            System.out.println("🟡 [SERVICE STEP 2/8] Getting database connection...");
            long connStartTime = System.currentTimeMillis();
            Connection conn = dbConnection.getConnection();
            long connTime = System.currentTimeMillis() - connStartTime;
            System.out.println("🟡 [SERVICE STEP 2/8] Database connection obtained in " + connTime + "ms");
            
            // Validate connection before proceeding
            if (conn == null || conn.isClosed()) {
                System.err.println("🔴 [SERVICE ERROR] Database connection is null or closed");
                return null;
            }
            
            // Test connection with a quick timeout
            if (!conn.isValid(5)) {
                System.err.println("🔴 [SERVICE ERROR] Database connection is not valid");
                return null;
            }
            
            System.out.println("🟡 [SERVICE STEP 3/8] Determining if email or phone...");
            boolean isEmail = emailOrPhone.contains("@");
            
            String sql;
            if (isEmail) {
                sql = "SELECT * FROM users WHERE email = ?";
                System.out.println("🟡 [SERVICE STEP 3/8] Using email query");
            } else {
                sql = "SELECT * FROM users WHERE phone_number = ?";
                System.out.println("🟡 [SERVICE STEP 3/8] Using phone query");
            }
            
            System.out.println("🟡 [SERVICE STEP 4/8] Preparing SQL statement...");
            PreparedStatement stmt = conn.prepareStatement(sql);
            // Set query timeout to 10 seconds to prevent indefinite hanging
            stmt.setQueryTimeout(10);
            System.out.println("🟡 [SERVICE STEP 4/8] Query timeout set to 10 seconds");
            stmt.setString(1, emailOrPhone);
            System.out.println("🟡 [SERVICE STEP 4/8] SQL prepared: " + sql);
            System.out.println("🟡 [SERVICE STEP 4/8] Parameter set: emailOrPhone = " + emailOrPhone);
            
            System.out.println("🟡 [SERVICE STEP 5/8] About to execute query...");
            System.out.println("🟡 [SERVICE STEP 5/8] Connection status - isClosed: " + conn.isClosed() + ", isValid: " + conn.isValid(1));
            long queryStartTime = System.currentTimeMillis();
            System.out.println("🟡 [SERVICE STEP 5/8] Calling stmt.executeQuery() at " + queryStartTime);
            ResultSet rs = stmt.executeQuery();
            long queryTime = System.currentTimeMillis() - queryStartTime;
            System.out.println("🟡 [SERVICE STEP 5/8] Query executed successfully in " + queryTime + "ms");
            
            System.out.println("🟡 [SERVICE STEP 6/8] Checking if user found...");
            if (rs.next()) {
                System.out.println("🟡 [SERVICE STEP 6/8] User found in database");
                String storedPasswordHash = rs.getString("password_hash");
                System.out.println("🟡 [SERVICE STEP 7/8] Verifying password with BCrypt...");
                
                // verify password using BCrypt
                System.out.println("🟡 [SERVICE STEP 7/8] Comparing password hashes...");
                if (BCrypt.checkpw(password, storedPasswordHash)) {
                    System.out.println("🟡 [SERVICE STEP 7/8] Password matches!");
                    // creat User object
                    System.out.println("🟡 [SERVICE STEP 8/8] Creating User object...");
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
                    
                    System.out.println("🟡 [SERVICE STEP 8/8] User object created, userId: " + user.getUserId());
                    stmt.close();
                    System.out.println("🟡 [SERVICE STEP 8/8] Returning user");
                    return user;
                } else {
                    System.out.println("🟡 [SERVICE STEP 7/8] Password does NOT match");
                }
            } else {
                System.out.println("🟡 [SERVICE STEP 6/8] User NOT found in database");
            }
            
            System.out.println("🟡 [SERVICE STEP 8/8] Returning null (authentication failed)");
            stmt.close();
            return null;
            
        } catch (SQLException e) {
            System.err.println("🔴 [SERVICE ERROR] SQLException during login:");
            System.err.println("🔴 [SERVICE ERROR] Message: " + e.getMessage());
            System.err.println("🔴 [SERVICE ERROR] SQL State: " + e.getSQLState());
            System.err.println("🔴 [SERVICE ERROR] Error Code: " + e.getErrorCode());
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
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, email);
                checkStmt.setString(2, phoneNumber);
                
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        // usser already exists!
                        return null;
                    }
                }
            }
            


            // create new user

            String userId = UUID.randomUUID().toString();
            // Hash password with BCrypt (cost factor 12 for good security/performance balance)
            String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt(12));
            
            String insertSql = "INSERT INTO users (user_id, name, email, phone_number, password_hash, balance) " +
                             "VALUES (?, ?, ?, ?, ?, 0.00)";
            
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                insertStmt.setString(1, userId);
                insertStmt.setString(2, name);
                insertStmt.setString(3, email);
                insertStmt.setString(4, phoneNumber);
                insertStmt.setString(5, passwordHash);
                
                int rowsInserted = insertStmt.executeUpdate();
                
                if (rowsInserted > 0) {
                    // return created user with the actual userId that was inserted
                    Timestamp now = new Timestamp(System.currentTimeMillis());
                    User user = new User(userId, name, email, phoneNumber, passwordHash, 0.00, now, now);
                    return user;
                }
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
        System.out.println("🟡 [SERVICE STEP 1/6] AuthService.createSession() called");
        System.out.println("🟡 [SERVICE STEP 1/6] userId: " + userId);
        
        try {
            System.out.println("🟡 [SERVICE STEP 2/6] Getting database connection...");
            long connStartTime = System.currentTimeMillis();
            Connection conn = dbConnection.getConnection();
            long connTime = System.currentTimeMillis() - connStartTime;
            System.out.println("🟡 [SERVICE STEP 2/6] Database connection obtained in " + connTime + "ms");
            
            System.out.println("🟡 [SERVICE STEP 3/6] Generating session ID and token...");
            String sessionId = UUID.randomUUID().toString();
            String token = UUID.randomUUID().toString();
            System.out.println("🟡 [SERVICE STEP 3/6] sessionId: " + sessionId);
            System.out.println("🟡 [SERVICE STEP 3/6] token: " + token.substring(0, 8) + "...");
            
            //session expires in 30 days !
            Timestamp expiresAt = new Timestamp(System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000));
            System.out.println("🟡 [SERVICE STEP 3/6] expiresAt: " + expiresAt);

            System.out.println("🟡 [SERVICE STEP 4/6] Preparing INSERT statement...");
            String sql = "INSERT INTO sessions (session_id, user_id, token, expires_at) VALUES (?, ?, ?, ?)";
            System.out.println("🟡 [SERVICE STEP 4/6] SQL: " + sql);
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, sessionId);
                stmt.setString(2, userId);
                stmt.setString(3, token);
                stmt.setTimestamp(4, expiresAt);
                
                System.out.println("🟡 [SERVICE STEP 5/6] Executing INSERT...");
                long insertStartTime = System.currentTimeMillis();
                int rowsInserted = stmt.executeUpdate();
                long insertTime = System.currentTimeMillis() - insertStartTime;
                System.out.println("🟡 [SERVICE STEP 5/6] INSERT executed in " + insertTime + "ms, rowsInserted: " + rowsInserted);
                
                if (rowsInserted > 0) {
                    System.out.println("🟡 [SERVICE STEP 6/6] Session created successfully, returning token");
                    return token;
                } else {
                    System.err.println("🔴 [SERVICE ERROR] Failed to insert session - no rows affected");
                    return null;
                }
            }
            
        } catch (SQLException e) {
            System.err.println("🔴 [SERVICE ERROR] SQLException creating session:");
            System.err.println("🔴 [SERVICE ERROR] Message: " + e.getMessage());
            System.err.println("🔴 [SERVICE ERROR] SQL State: " + e.getSQLState());
            System.err.println("🔴 [SERVICE ERROR] Error Code: " + e.getErrorCode());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Verify session token and return user ID if valid
     * @param token Session token
     * @return User ID if token is valid, null otherwise
     */
    public String verifyToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        
        try {
            Connection conn = dbConnection.getConnection();
            String sql = "SELECT user_id FROM sessions WHERE token = ? AND expires_at > NOW()";
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, token);
                stmt.setQueryTimeout(5);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("user_id");
                    }
                }
            }
            
            return null;
        } catch (SQLException e) {
            System.err.println("Error verifying token: " + e.getMessage());
            return null;
        }
    }
}