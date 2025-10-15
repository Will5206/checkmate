package com.checkmate;

import com.checkmate.controllers.AuthController;
import com.checkmate.database.DatabaseConnection;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;





/**
 * main server class for CheckMate backend
 */
public class Server {
    
    private static final int PORT = 8080;



    
    public static void main(String[] args) {
        try {
            // init db
            DatabaseConnection dbConnection = DatabaseConnection.getInstance();
            if (!dbConnection.testConnection()) {
                System.err.println("Failed to connect to database");
                return;
            }
            
            // init db schema
            dbConnection.initializeSchema("backend/database/schema.sql");
            
            // create HTTP server
            HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
            
            // register endpoints
            server.createContext("/api/auth/login", new AuthController.LoginHandler());
            server.createContext("/api/auth/signup", new AuthController.SignupHandler());
            
            //start server
            server.setExecutor(null);
            server.start();
            


            System.out.println("CheckMate Server started on port " + PORT);
            System.out.println("Login endpoint: http://localhost:" + PORT + "/api/auth/login");
            System.out.println("Signup endpoint: http://localhost:" + PORT + "/api/auth/signup");
            
        } catch (IOException e) {
            
            System.err.println("Failed to start server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}