// No package (default package)

import controllers.AuthController;
import controllers.FriendController;
import controllers.ReceiptController;
import database.DatabaseConnection;
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
            server.createContext("/api/friends/add", new FriendController.AddFriendHandler());
            server.createContext("/api/friends/add-by-email", new FriendController.AddFriendByEmailHandler());
            server.createContext("/api/friends/remove", new FriendController.RemoveFriendHandler());
            server.createContext("/api/friends/list", new FriendController.ListFriendsHandler());
            server.createContext("/api/receipts/view", new ReceiptController.ViewReceiptHandler());
            server.createContext("/api/receipts/pending", new ReceiptController.ListPendingReceiptsHandler());
            server.createContext("/api/receipts/accept", new ReceiptController.AcceptReceiptHandler());
            server.createContext("/api/receipts/decline", new ReceiptController.DeclineReceiptHandler());
            
            //start server
            server.setExecutor(null);
            server.start();
            


            System.out.println("CheckMate Server started on port " + PORT);
            System.out.println("Login endpoint: http://localhost:" + PORT + "/api/auth/login");
            System.out.println("Signup endpoint: http://localhost:" + PORT + "/api/auth/signup");
            System.out.println("Add friend: http://localhost:" + PORT + "/api/friends/add?userId=1&friendId=2");
            System.out.println("Remove friend: http://localhost:" + PORT + "/api/friends/remove?userId=1&friendId=2");
            System.out.println("List friends: http://localhost:" + PORT + "/api/friends/list?userId=1");
            System.out.println("View receipt: http://localhost:" + PORT + "/api/receipts/view?receiptId=1&userId=2");
            System.out.println("List pending receipts: http://localhost:" + PORT + "/api/receipts/pending?userId=2");
            System.out.println("Accept receipt: http://localhost:" + PORT + "/api/receipts/accept?receiptId=1&userId=2");
            System.out.println("Decline receipt: http://localhost:" + PORT + "/api/receipts/decline?receiptId=1&userId=2");
            
        } catch (IOException e) {
            
            System.err.println("Failed to start server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}