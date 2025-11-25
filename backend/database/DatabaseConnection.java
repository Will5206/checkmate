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

    // db config stuff - uses environment variables if available, otherwise defaults to localhost
    private static final String DB_URL = System.getenv("DB_URL") != null
        ? System.getenv("DB_URL")
        : "jdbc:mysql://localhost:3306/checkmate_db";
    private static final String DB_USER = System.getenv("DB_USER") != null
        ? System.getenv("DB_USER")
        : "root";
    private static final String DB_PASSWORD = System.getenv("DB_PASSWORD") != null
        ? System.getenv("DB_PASSWORD")
        : "password";

    //singleton instance
    private static DatabaseConnection instance;
    private Connection connection;
    
    /**
     * private constructor for singleton pattern
     */
    private DatabaseConnection() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            


            this.connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            System.out.println("Database connection established successfully");
            
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL JDBC Driver not found");
            e.printStackTrace();
        } catch (SQLException e) {
            System.err.println("Failed to connect to database");
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
     * get the active db connection
     * @return Connection object
     */
    public Connection getConnection() {
        try {
            // check if connection is still valid --- reconnect if needed
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            }
        } catch (SQLException e) {
            System.err.println("error checking/reestablishing connection");
            e.printStackTrace();
        }
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
     * testt db connection
     * @return true if connection is valid
     */
    public boolean testConnection() {
        try {
            Connection conn = getConnection();
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
    



    /**
     * close db connection
     */
    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("Database connection closed");
            }
        } catch (SQLException e) {
            System.err.println("Error closing database connection");
            e.printStackTrace();
        }
    }
}