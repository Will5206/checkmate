package controllers;

import services.FriendService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONArray;
import org.json.JSONObject;
import utils.ValidationUtils;
import database.UserDAO;
import models.User;
import models.Friend;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FriendController {

    private static final FriendService friendService = new FriendService();

    public static class AddFriendHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCors(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405, new JSONObject().put("success", false).put("message", "Method not allowed"));
                return;
            }
            Map<String, String> query = parseQuery(exchange.getRequestURI());
            try {
                String userId = query.getOrDefault("userId", "");
                String friendId = query.getOrDefault("friendId", "");

                if (userId.isEmpty() || friendId.isEmpty()) {
                    sendJson(exchange, 400, new JSONObject().put("success", false).put("message", "userId and friendId are required"));
                    return;
                }

                boolean added = friendService.addFriend(userId, friendId);
                JSONObject resp = new JSONObject()
                        .put("success", true)
                        .put("added", added)
                        .put("userId", userId)
                        .put("friendId", friendId);
                sendJson(exchange, 200, resp);
            } catch (Exception e) {
                sendJson(exchange, 400, new JSONObject().put("success", false).put("message", "Invalid parameters: " + e.getMessage()));
            }
        }
    }

    public static class RemoveFriendHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCors(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405, new JSONObject().put("success", false).put("message", "Method not allowed"));
                return;
            }
            Map<String, String> query = parseQuery(exchange.getRequestURI());
            try {
                String userId = query.getOrDefault("userId", "");
                String friendId = query.getOrDefault("friendId", "");

                System.out.println("🔴 [4/8] RemoveFriendHandler: userId=" + userId + ", friendId=" + friendId);

                if (userId.isEmpty() || friendId.isEmpty()) {
                    sendJson(exchange, 400, new JSONObject().put("success", false).put("message", "userId and friendId are required"));
                    return;
                }

                boolean removed = friendService.removeFriend(userId, friendId);
                System.out.println("🔴 [7/8] RemoveFriendHandler: removed=" + removed);
                JSONObject resp = new JSONObject()
                        .put("success", true)
                        .put("removed", removed)
                        .put("userId", userId)
                        .put("friendId", friendId);
                sendJson(exchange, 200, resp);
            } catch (Exception e) {
                sendJson(exchange, 400, new JSONObject().put("success", false).put("message", "Invalid parameters: " + e.getMessage()));
            }
        }
    }

    public static class AddFriendByEmailHandler implements HttpHandler {
        private static final UserDAO userDAO = new UserDAO();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCors(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }

            if (!"POST".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405, new JSONObject()
                    .put("success", false)
                    .put("message", "Method not allowed"));
                return;
            }

            Map<String, String> query = parseQuery(exchange.getRequestURI());

            try {
                String userId = query.getOrDefault("userId", "");
                String friendEmail = query.getOrDefault("email", "");

                // Validate inputs
                if (userId.isEmpty()) {
                    sendJson(exchange, 400, new JSONObject()
                        .put("success", false)
                        .put("message", "userId is required"));
                    return;
                }

                if (friendEmail.isEmpty()) {
                    sendJson(exchange, 400, new JSONObject()
                        .put("success", false)
                        .put("message", "email is required"));
                    return;
                }

                // Validate email format
                if (!ValidationUtils.isValidEmail(friendEmail)) {
                    sendJson(exchange, 400, new JSONObject()
                        .put("success", false)
                        .put("message", "Invalid email format"));
                    return;
                }

                // Lookup user by email
                User friendUser = userDAO.findUserByEmail(friendEmail);

                if (friendUser == null) {
                    sendJson(exchange, 404, new JSONObject()
                        .put("success", false)
                        .put("message", "No user found with that email address"));
                    return;
                }

                String friendId = friendUser.getUserId();

                // Prevent self-friending
                if (userId.equals(friendId)) {
                    sendJson(exchange, 400, new JSONObject()
                        .put("success", false)
                        .put("message", "Cannot add yourself as a friend"));
                    return;
                }

                // Check if friendship already exists
                Friend existingFriendship = friendService.getFriendship(userId, friendId);
                
                if (existingFriendship != null) {
                    // Friendship exists - check status
                    String status = existingFriendship.getStatus();
                    
                    if ("accepted".equals(status)) {
                        // Already accepted friends
                        JSONObject resp = new JSONObject()
                            .put("success", true)
                            .put("added", false)
                            .put("message", "You are already friends with this user")
                            .put("userId", userId)
                            .put("friendId", friendId)
                            .put("friendName", friendUser.getName())
                            .put("friendEmail", friendUser.getEmail());
                        sendJson(exchange, 200, resp);
                        return;
                    } else if ("pending".equals(status)) {
                        // Check if current user is the requester (they sent the request)
                        String requestedBy = existingFriendship.getRequestedBy();
                        if (requestedBy != null && requestedBy.equals(userId)) {
                            // Current user already sent the request - show pending message
                            JSONObject resp = new JSONObject()
                                .put("success", true)
                                .put("added", false)
                                .put("message", "Friend request pending")
                                .put("userId", userId)
                                .put("friendId", friendId)
                                .put("friendName", friendUser.getName())
                                .put("friendEmail", friendUser.getEmail());
                            sendJson(exchange, 200, resp);
                            return;
                        } else {
                            // Current user is the recipient - they can't send a request back
                            // They should accept/decline the existing request instead
                            JSONObject resp = new JSONObject()
                                .put("success", true)
                                .put("added", false)
                                .put("message", "You have a pending friend request from this user")
                                .put("userId", userId)
                                .put("friendId", friendId)
                                .put("friendName", friendUser.getName())
                                .put("friendEmail", friendUser.getEmail());
                            sendJson(exchange, 200, resp);
                            return;
                        }
                    } else if ("declined".equals(status)) {
                        // Previously declined - create new pending request
                        // First remove the declined friendship, then create new pending one
                        friendService.removeFriendship(userId, friendId);
                        // Continue to create new pending request below
                    }
                }

                // Add new friendship (creates as 'pending' status)
                boolean added = friendService.addFriend(userId, friendId);

                JSONObject resp = new JSONObject()
                    .put("success", true)
                    .put("added", added)
                    .put("userId", userId)
                    .put("friendId", friendId)
                    .put("friendName", friendUser.getName())
                    .put("friendEmail", friendUser.getEmail());

                sendJson(exchange, 200, resp);

            } catch (Exception e) {
                sendJson(exchange, 500, new JSONObject()
                    .put("success", false)
                    .put("message", "Server error: " + e.getMessage()));
            }
        }
    }

    public static class ListFriendsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCors(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405, new JSONObject().put("success", false).put("message", "Method not allowed"));
                return;
            }
            Map<String, String> query = parseQuery(exchange.getRequestURI());
            try {
                String userId = query.getOrDefault("userId", "");

                if (userId.isEmpty()) {
                    sendJson(exchange, 400, new JSONObject().put("success", false).put("message", "userId is required"));
                    return;
                }

                // Get accepted friends only (pending requests are not included)
                List<String> friendIds = friendService.listFriends(userId);

                // Create UserDAO to fetch user details
                UserDAO userDAO = new UserDAO();

                // Build enriched friends array with user details
                JSONArray friendsArray = new JSONArray();
                for (String friendId : friendIds) {
                    User friendUser = userDAO.findUserById(friendId);
                    if (friendUser != null) {
                        JSONObject friendObj = new JSONObject()
                            .put("userId", friendUser.getUserId())
                            .put("name", friendUser.getName())
                            .put("email", friendUser.getEmail());
                        friendsArray.put(friendObj);
                    }
                }

                JSONObject resp = new JSONObject()
                        .put("success", true)
                        .put("userId", userId)
                        .put("friends", friendsArray);
                sendJson(exchange, 200, resp);
            } catch (Exception e) {
                sendJson(exchange, 400, new JSONObject().put("success", false).put("message", "Invalid parameters: " + e.getMessage()));
            }
        }
    }

    /**
     * Handler for accepting a friend request.
     * POST /api/friends/accept?userId=X&friendId=Y
     */
    public static class AcceptFriendRequestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCors(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405, new JSONObject().put("success", false).put("message", "Method not allowed"));
                return;
            }
            
            Map<String, String> query = parseQuery(exchange.getRequestURI());
            try {
                String userId = query.getOrDefault("userId", "");
                String friendId = query.getOrDefault("friendId", "");

                if (userId.isEmpty() || friendId.isEmpty()) {
                    sendJson(exchange, 400, new JSONObject()
                        .put("success", false)
                        .put("message", "userId and friendId are required"));
                    return;
                }

                // Verify that current user (userId) is the recipient, not the requester
                Friend friendship = friendService.getFriendship(userId, friendId);
                if (friendship == null || !"pending".equals(friendship.getStatus())) {
                    sendJson(exchange, 400, new JSONObject()
                        .put("success", false)
                        .put("message", "No pending friend request found"));
                    return;
                }
                
                // Check if current user is the recipient (not the requester)
                String requestedBy = friendship.getRequestedBy();
                if (requestedBy == null || requestedBy.equals(userId)) {
                    sendJson(exchange, 400, new JSONObject()
                        .put("success", false)
                        .put("message", "Only the recipient can accept a friend request"));
                    return;
                }

                boolean accepted = friendService.acceptFriendRequest(userId, friendId);
                
                JSONObject resp = new JSONObject()
                    .put("success", accepted)
                    .put("message", accepted ? "Friend request accepted" : "Failed to accept friend request")
                    .put("userId", userId)
                    .put("friendId", friendId);
                
                sendJson(exchange, accepted ? 200 : 400, resp);
            } catch (Exception e) {
                sendJson(exchange, 400, new JSONObject()
                    .put("success", false)
                    .put("message", "Invalid parameters: " + e.getMessage()));
            }
        }
    }

    /**
     * Handler for declining a friend request.
     * POST /api/friends/decline?userId=X&friendId=Y
     */
    public static class DeclineFriendRequestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCors(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405, new JSONObject().put("success", false).put("message", "Method not allowed"));
                return;
            }
            
            Map<String, String> query = parseQuery(exchange.getRequestURI());
            try {
                String userId = query.getOrDefault("userId", "");
                String friendId = query.getOrDefault("friendId", "");

                if (userId.isEmpty() || friendId.isEmpty()) {
                    sendJson(exchange, 400, new JSONObject()
                        .put("success", false)
                        .put("message", "userId and friendId are required"));
                    return;
                }

                // Verify that current user (userId) is the recipient, not the requester
                Friend friendship = friendService.getFriendship(userId, friendId);
                if (friendship == null || !"pending".equals(friendship.getStatus())) {
                    sendJson(exchange, 400, new JSONObject()
                        .put("success", false)
                        .put("message", "No pending friend request found"));
                    return;
                }
                
                // Check if current user is the recipient (not the requester)
                String requestedBy = friendship.getRequestedBy();
                if (requestedBy == null || requestedBy.equals(userId)) {
                    sendJson(exchange, 400, new JSONObject()
                        .put("success", false)
                        .put("message", "Only the recipient can decline a friend request"));
                    return;
                }

                boolean declined = friendService.declineFriendRequest(userId, friendId);
                
                JSONObject resp = new JSONObject()
                    .put("success", declined)
                    .put("message", declined ? "Friend request declined" : "Failed to decline friend request")
                    .put("userId", userId)
                    .put("friendId", friendId);
                
                sendJson(exchange, declined ? 200 : 400, resp);
            } catch (Exception e) {
                sendJson(exchange, 400, new JSONObject()
                    .put("success", false)
                    .put("message", "Invalid parameters: " + e.getMessage()));
            }
        }
    }

    /**
     * Handler for listing pending friend requests.
     * GET /api/friends/pending?userId=X
     */
    public static class ListPendingFriendRequestsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCors(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405, new JSONObject().put("success", false).put("message", "Method not allowed"));
                return;
            }
            
            Map<String, String> query = parseQuery(exchange.getRequestURI());
            try {
                String userId = query.getOrDefault("userId", "");

                if (userId.isEmpty()) {
                    sendJson(exchange, 400, new JSONObject()
                        .put("success", false)
                        .put("message", "userId is required"));
                    return;
                }

                // Get all friendships for this user (including pending)
                List<Friend> allFriendships = friendService.listFriendships(userId);
                
                // Filter to only pending requests where current user is the RECIPIENT (not the requester)
                // Only show requests where someone else sent a request TO the current user
                UserDAO userDAO = new UserDAO();
                JSONArray pendingRequests = new JSONArray();
                
                for (Friend friendship : allFriendships) {
                    if ("pending".equals(friendship.getStatus())) {
                        String requestedBy = friendship.getRequestedBy();
                        
                        // Only show if current user is NOT the requester (i.e., they are the recipient)
                        if (requestedBy != null && !requestedBy.equals(userId)) {
                            // Current user is the recipient, show the request
                            User requester = userDAO.findUserById(requestedBy);
                            if (requester != null) {
                                JSONObject requestObj = new JSONObject()
                                    .put("friendshipId", friendship.getFriendshipId())
                                    .put("userId", requester.getUserId())
                                    .put("name", requester.getName())
                                    .put("email", requester.getEmail())
                                    .put("status", friendship.getStatus())
                                    .put("createdAt", friendship.getCreatedAt() != null ? friendship.getCreatedAt().getTime() : System.currentTimeMillis());
                                pendingRequests.put(requestObj);
                            }
                        }
                    }
                }
                
                JSONObject resp = new JSONObject()
                    .put("success", true)
                    .put("userId", userId)
                    .put("pendingRequests", pendingRequests);
                
                sendJson(exchange, 200, resp);
            } catch (Exception e) {
                sendJson(exchange, 400, new JSONObject()
                    .put("success", false)
                    .put("message", "Invalid parameters: " + e.getMessage()));
            }
        }
    }

    private static Map<String, String> parseQuery(URI uri) {
        String query = uri.getQuery();
        if (query == null || query.isEmpty()) {
            return Map.of();
        }
        return java.util.Arrays.stream(query.split("&"))
                .map(kv -> kv.split("=", 2))
                .collect(Collectors.toMap(
                        kv -> kv[0],
                        kv -> kv.length > 1 ? kv[1] : ""
                ));
    }

    private static void addCors(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }

    private static void sendJson(HttpExchange exchange, int status, JSONObject json) throws IOException {
        byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }
}


