package com.checkmate.tests;



import com.checkmate.database.DatabaseConnection;
import java.sql.Connection;




/**
 * for testing the db connection
 */
public class DatabaseConnectionTest {
    
    public static void main(String[] args) {
        System.out.println("Testing database connection...");
        


        DatabaseConnection dbConnection = DatabaseConnection.getInstance();



        if (dbConnection.testConnection()) {
            System.out.println("✓ Database connection successful!");
            
            // init schema
            String schemaPath = "backend/database/schema.sql";
            if (dbConnection.initializeSchema(schemaPath)) {
                System.out.println("✓ Database schema initialized!");
            } else {
                System.out.println("✗ Failed to initialize schema");
            }
            
            
        } else {
            System.out.println("✗ Database connection failed");
        }
        


        dbConnection.closeConnection();
    }
}