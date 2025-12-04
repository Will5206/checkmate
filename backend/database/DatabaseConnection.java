package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;





/**
 * this manages dathe db connections for checkmate app
 * singleton pattern to ensure only one connection pool!!
 */





public class DatabaseConnection {

    // db config stuff - checks system properties first (from Maven -D flags), then environment variables, then defaults
    private static final String DB_URL = System.getProperty("DB_URL",
        System.getenv("DB_URL") != null ? System.getenv("DB_URL") : "jdbc:mysql://localhost:3306/checkmate_db");
    private static final String DB_USER = System.getProperty("DB_USER",
        System.getenv("DB_USER") != null ? System.getenv("DB_USER") : "root");
    private static final String DB_PASSWORD = System.getProperty("DB_PASSWORD",
        System.getenv("DB_PASSWORD") != null ? System.getenv("DB_PASSWORD") : "password");

    //singleton instance
    private static DatabaseConnection instance;
    private Connection connection;
    
    /**
     * private constructor for singleton pattern
     */
    private DatabaseConnection() {
        System.out.println("🔵 [DATABASE INIT] DatabaseConnection constructor called");
        System.out.println("🔵 [DATABASE INIT] DB_URL: " + DB_URL.replace(DB_PASSWORD, "***"));
        System.out.println("🔵 [DATABASE INIT] DB_USER: " + DB_USER);
        
        try {
            System.out.println("🔵 [DATABASE INIT] Loading MySQL JDBC driver...");
            Class.forName("com.mysql.cj.jdbc.Driver");
            System.out.println("🔵 [DATABASE INIT] JDBC driver loaded successfully");
            
            System.out.println("🔵 [DATABASE INIT] Attempting to connect to database...");
            long connStartTime = System.currentTimeMillis();
            this.connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            long connTime = System.currentTimeMillis() - connStartTime;
            System.out.println("🔵 [DATABASE INIT] Database connection established in " + connTime + "ms");
            System.out.println("Database connection established successfully");
            
        } catch (ClassNotFoundException e) {
            System.err.println("🔴 [DATABASE INIT ERROR] MySQL JDBC Driver not found");
            System.err.println("🔴 [DATABASE INIT ERROR] Message: " + e.getMessage());
            e.printStackTrace();
        } catch (SQLException e) {
            System.err.println("🔴 [DATABASE INIT ERROR] Failed to connect to database");
            System.err.println("🔴 [DATABASE INIT ERROR] Message: " + e.getMessage());
            System.err.println("🔴 [DATABASE INIT ERROR] SQL State: " + e.getSQLState());
            System.err.println("🔴 [DATABASE INIT ERROR] Error Code: " + e.getErrorCode());
            e.printStackTrace();
        }
    }
    
    /**
     * get singleton instance of DatabaseConnection
     * @return DatabaseConnection instance
     */
    public static DatabaseConnection getInstance() {
        if (instance == null) {
            synchronized (DatabaseConnection.class) {
                if (instance == null) {
                    instance = new DatabaseConnection();
                }
            }
        }
        return instance;
    }
    
    /**
     * get the active database connection
     * This is now thread-safe ---- ensures only one connection is created at a time
     * @return Connection object (never null)
     * @throws SQLException if connection cannot be established
     */
    public synchronized Connection getConnection() throws SQLException {
        System.out.println("🔵 [DATABASE STEP 1/4] DatabaseConnection.getConnection() called");
        
        //check if connection is still valid - reconnect if needed
        //synchronized to prevent race conditions -------when multiple threads
        // check simultaneously
        System.out.println("🔵 [DATABASE STEP 2/4] Checking connection status...");
        boolean needsReconnect = false;
        
        if (connection == null) {
            System.out.println("🔵 [DATABASE STEP 2/4] Connection is null");
            needsReconnect = true;
        } else {
            try {
                if (connection.isClosed()) {
                    System.out.println("🔵 [DATABASE STEP 2/4] Connection is closed");
                    needsReconnect = true;
                } else {
                    // Test if connection is still valid (with 5 second timeout)
                    System.out.println("🔵 [DATABASE STEP 2/4] Testing connection validity...");
                    boolean isValid = connection.isValid(5);
                    if (!isValid) {
                        System.out.println("🔵 [DATABASE STEP 2/4] Connection is not valid");
                        needsReconnect = true;
                    } else {
                        System.out.println("🔵 [DATABASE STEP 2/4] Connection is valid");
                    }
                }
            } catch (SQLException e) {
                System.err.println("🔴 [DATABASE STEP 2/4 ERROR] Error checking connection: " + e.getMessage());
                needsReconnect = true;
            }
        }
        
        if (needsReconnect) {
            System.out.println("🔵 [DATABASE STEP 3/4] Creating new connection...");
            System.out.println("🔵 [DATABASE STEP 3/4] DB_URL: " + DB_URL.replace(DB_PASSWORD, "***"));
            System.out.println("🔵 [DATABASE STEP 3/4] DB_USER: " + DB_USER);
            
            long connStartTime = System.currentTimeMillis();
            try {
                connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                long connTime = System.currentTimeMillis() - connStartTime;
                System.out.println("🔵 [DATABASE STEP 3/4] Connection established in " + connTime + "ms");
                System.out.println("Database connection established/reconnected");
            } catch (SQLException e) {
                System.err.println("🔴 [DATABASE STEP 3/4 ERROR] Failed to create connection:");
                System.err.println("🔴 [DATABASE STEP 3/4 ERROR] Message: " + e.getMessage());
                System.err.println("🔴 [DATABASE STEP 3/4 ERROR] SQL State: " + e.getSQLState());
                System.err.println("🔴 [DATABASE STEP 3/4 ERROR] Error Code: " + e.getErrorCode());
                throw e;
            }
        } else {
            System.out.println("🔵 [DATABASE STEP 3/4] Using existing connection");
        }
        
        System.out.println("🔵 [DATABASE STEP 4/4] Returning connection");
        return connection;
    }
    
    /**
     * init db schema from sql file
     * @param schemaFilePath path to schema.sql file
     * @return true if successful, false otherwise
     */
    public boolean initializeSchema(String schemaFilePath) {
        try {
            Connection conn = getConnection();
            Statement stmt = conn.createStatement();
            

            BufferedReader reader = new BufferedReader(new FileReader(schemaFilePath));
            StringBuilder sql = new StringBuilder();
            String line;
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                
                if (line.isEmpty() || line.startsWith("--")) {
                    continue;
                }
                sql.append(line).append(" ");
            }
            reader.close();
            
            //split by semicolon and execute each statement
            String[] statements = sql.toString().split(";");
            for (String statement : statements) {
                if (!statement.trim().isEmpty()) {
                    stmt.execute(statement);
                }
            }
            


            System.out.println("Database schema initialized successfully");
            stmt.close();
            return true;
            
        } catch (SQLException e) {
            System.err.println("SQL error initializing schema");
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            System.err.println("Error reading schema file");
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Test database connection.
     * @return true if connection is valid, false otherwise
     */
    public boolean testConnection() {
        try {
            Connection conn = getConnection();
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            System.err.println("Connection test failed: " + e.getMessage());
            return false;
        }
    }




    /**
     * Close database connection.
     * Thread-safe method to safely close the shared connection.
     */
    public synchronized void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("Database connection closed");
            }
        } catch (SQLException e) {
            System.err.println("Error closing database connection: " + e.getMessage());
            e.printStackTrace();
        }
    }
}