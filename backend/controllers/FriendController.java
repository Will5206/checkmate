package controllers;

import services.FriendService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONArray;
import org.json.JSONObject;
import utils.ValidationUtils;
import utils.AuthMiddleware;
import utils.ErrorResponse;
import utils.Logger;
import utils.RequestUtils;
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
            ErrorResponse.addCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                ErrorResponse.sendError(exchange, 405, "Method not allowed");
                return;
            }
            
            // Verify authentication
            String authenticatedUserId = AuthMiddleware.verifyAuth(exchange);
            if (authenticatedUserId == null) {
                AuthMiddleware.sendUnauthorized(exchange);
                return;
            }
            
            Map<String, String> query = RequestUtils.parseQuery(exchange.getRequestURI());
            try {
                String userId = query.getOrDefault("userId", "");
                String friendId = query.getOrDefault("friendId", "");

                if (userId.isEmpty() || friendId.isEmpty()) {
                    ErrorResponse.sendError(exchange, 400, "userId and friendId are required");
                    return;
                }
                
                // Verify user can only add friends for themselves
                if (!userId.equals(authenticatedUserId)) {
                    ErrorResponse.sendError(exchange, 403, "Forbidden: Cannot add friends for another user");
                    return;
                }

                boolean added = friendService.addFriend(userId, friendId);
                JSONObject resp = ErrorResponse.success(null);
                resp.put("added", added);
                resp.put("userId", userId);
                resp.put("friendId", friendId);
                ErrorResponse.sendJson(exchange, 200, resp);
            } catch (Exception e) {
                ErrorResponse.sendError(exchange, 400, "Invalid parameters: " + e.getMessage());
            }
        }
    }

    public static class RemoveFriendHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            ErrorResponse.addCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                ErrorResponse.sendError(exchange, 405, "Method not allowed");
                return;
            }
            
            // Verify authentication
            String authenticatedUserId = AuthMiddleware.verifyAuth(exchange);
            if (authenticatedUserId == null) {
                AuthMiddleware.sendUnauthorized(exchange);
                return;
            }
            
            Map<String, String> query = RequestUtils.parseQuery(exchange.getRequestURI());
            try {
                String userId = query.getOrDefault("userId", "");
                String friendId = query.getOrDefault("friendId", "");

                Logger.debug("FriendController", "RemoveFriendHandler: userId=" + userId + ", friendId=" + friendId);

                if (userId.isEmpty() || friendId.isEmpty()) {
                    ErrorResponse.sendError(exchange, 400, "userId and friendId are required");
                    return;
                }
                
                // Verify user can only remove their own friends
                if (!userId.equals(authenticatedUserId)) {
                    ErrorResponse.sendError(exchange, 403, "Forbidden: Cannot remove friends for another user");
                    return;
                }

                boolean removed = friendService.removeFriend(userId, friendId);
                Logger.debug("FriendController", "RemoveFriendHandler: removed=" + removed);
                JSONObject resp = ErrorResponse.success(null);
                resp.put("removed", removed);
                resp.put("userId", userId);
                resp.put("friendId", friendId);
                ErrorResponse.sendJson(exchange, 200, resp);
            } catch (Exception e) {
                ErrorResponse.sendError(exchange, 400, "Invalid parameters: " + e.getMessage());
            }
        }
    }

    public static class AddFriendByEmailHandler implements HttpHandler {
        private static final UserDAO userDAO = new UserDAO();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            ErrorResponse.addCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }

            if (!"POST".equals(exchange.getRequestMethod())) {
                ErrorResponse.sendError(exchange, 405, "Method not allowed");
                return;
            }

            // Verify authentication
            String authenticatedUserId = AuthMiddleware.verifyAuth(exchange);
            if (authenticatedUserId == null) {
                AuthMiddleware.sendUnauthorized(exchange);
                return;
            }

            Map<String, String> query = RequestUtils.parseQuery(exchange.getRequestURI());

            try {
                String userId = query.getOrDefault("userId", "");
                String friendEmail = query.getOrDefault("email", "");

                // Validate inputs
                if (userId.isEmpty()) {
                    ErrorResponse.sendError(exchange, 400, "userId is required");
                    return;
                }
                
                // Verify user can only add friends for themselves
                if (!userId.equals(authenticatedUserId)) {
                    ErrorResponse.sendError(exchange, 403, "Forbidden: Cannot add friends for another user");
                    return;
                }

                if (friendEmail.isEmpty()) {
                    ErrorResponse.sendError(exchange, 400, "Email is required");
                    return;
                }

                // Validate and sanitize email format
                friendEmail = friendEmail.trim().toLowerCase();
                if (!ValidationUtils.isValidEmail(friendEmail)) {
                    ErrorResponse.sendError(exchange, 400, "Invalid email format");
                    return;
                }

                // Lookup user by email
                User friendUser = userDAO.findUserByEmail(friendEmail);

                if (friendUser == null) {
                    ErrorResponse.sendError(exchange, 404, "No user found with that email address");
                    return;
                }

                String friendId = friendUser.getUserId();

                // Prevent self-friending
                if (userId.equals(friendId)) {
                    ErrorResponse.sendError(exchange, 400, "Cannot add yourself as a friend");
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
                Logger.error("FriendController", "Server error: " + e.getMessage(), e);
                ErrorResponse.sendError(exchange, 500, "Server error: " + e.getMessage());
            }
        }
    }

    public static class ListFriendsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            ErrorResponse.addCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            if (!"GET".equals(exchange.getRequestMethod())) {
                ErrorResponse.sendError(exchange, 405, "Method not allowed");
                return;
            }
            
            // Verify authentication
            String authenticatedUserId = AuthMiddleware.verifyAuth(exchange);
            if (authenticatedUserId == null) {
                AuthMiddleware.sendUnauthorized(exchange);
                return;
            }
            
            Map<String, String> query = RequestUtils.parseQuery(exchange.getRequestURI());
            try {
                String userId = query.getOrDefault("userId", "");

                if (userId.isEmpty()) {
                    ErrorResponse.sendError(exchange, 400, "userId is required");
                    return;
                }
                
                // Verify user can only list their own friends
                if (!userId.equals(authenticatedUserId)) {
                    ErrorResponse.sendError(exchange, 403, "Forbidden: Cannot list friends for another user");
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
                ErrorResponse.sendError(exchange, 400, "Invalid parameters: " + e.getMessage());
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
            ErrorResponse.addCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                ErrorResponse.sendError(exchange, 405, "Method not allowed");
                return;
            }
            
            Map<String, String> query = RequestUtils.parseQuery(exchange.getRequestURI());
            try {
                String userId = query.getOrDefault("userId", "");
                String friendId = query.getOrDefault("friendId", "");

                if (userId.isEmpty() || friendId.isEmpty()) {
                    ErrorResponse.sendError(exchange, 400, "userId and friendId are required");
                    return;
                }

                // Verify that current user (userId) is the recipient, not the requester
                Friend friendship = friendService.getFriendship(userId, friendId);
                if (friendship == null || !"pending".equals(friendship.getStatus())) {
                    ErrorResponse.sendError(exchange, 400, "No pending friend request found");
                    return;
                }
                
                // Check if current user is the recipient (not the requester)
                String requestedBy = friendship.getRequestedBy();
                if (requestedBy == null || requestedBy.equals(userId)) {
                    ErrorResponse.sendError(exchange, 400, "Only the recipient can accept a friend request");
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
                Logger.error("FriendController", "Invalid parameters: " + e.getMessage(), e);
                ErrorResponse.sendError(exchange, 400, "Invalid parameters: " + e.getMessage());
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
            ErrorResponse.addCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                ErrorResponse.sendError(exchange, 405, "Method not allowed");
                return;
            }
            
            // Verify authentication
            String authenticatedUserId = AuthMiddleware.verifyAuth(exchange);
            if (authenticatedUserId == null) {
                AuthMiddleware.sendUnauthorized(exchange);
                return;
            }
            
            Map<String, String> query = RequestUtils.parseQuery(exchange.getRequestURI());
            try {
                String userId = query.getOrDefault("userId", "");
                String friendId = query.getOrDefault("friendId", "");

                if (userId.isEmpty() || friendId.isEmpty()) {
                    ErrorResponse.sendError(exchange, 400, "userId and friendId are required");
                    return;
                }
                
                // Verify user can only decline requests for themselves
                if (!userId.equals(authenticatedUserId)) {
                    ErrorResponse.sendError(exchange, 403, "Forbidden: Cannot decline friend requests for another user");
                    return;
                }

                // Verify that current user (userId) is the recipient, not the requester
                Friend friendship = friendService.getFriendship(userId, friendId);
                if (friendship == null || !"pending".equals(friendship.getStatus())) {
                    ErrorResponse.sendError(exchange, 400, "No pending friend request found");
                    return;
                }
                
                // Check if current user is the recipient (not the requester)
                String requestedBy = friendship.getRequestedBy();
                if (requestedBy == null || requestedBy.equals(userId)) {
                    ErrorResponse.sendError(exchange, 400, "Only the recipient can decline a friend request");
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
                Logger.error("FriendController", "Invalid parameters: " + e.getMessage(), e);
                ErrorResponse.sendError(exchange, 400, "Invalid parameters: " + e.getMessage());
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
            ErrorResponse.addCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            if (!"GET".equals(exchange.getRequestMethod())) {
                ErrorResponse.sendError(exchange, 405, "Method not allowed");
                return;
            }
            
            // Verify authentication
            String authenticatedUserId = AuthMiddleware.verifyAuth(exchange);
            if (authenticatedUserId == null) {
                AuthMiddleware.sendUnauthorized(exchange);
                return;
            }
            
            Map<String, String> query = RequestUtils.parseQuery(exchange.getRequestURI());
            try {
                String userId = query.getOrDefault("userId", "");

                if (userId.isEmpty()) {
                    ErrorResponse.sendError(exchange, 400, "userId is required");
                    return;
                }
                
                // Verify user can only list their own pending requests
                if (!userId.equals(authenticatedUserId)) {
                    ErrorResponse.sendError(exchange, 403, "Forbidden: Cannot list pending requests for another user");
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
                Logger.error("FriendController", "Invalid parameters: " + e.getMessage(), e);
                ErrorResponse.sendError(exchange, 400, "Invalid parameters: " + e.getMessage());
            }
        }
    }

    // Deprecated - use RequestUtils.parseQuery() instead
    @Deprecated
    private static Map<String, String> parseQuery(URI uri) {
        return RequestUtils.parseQuery(uri);
    }

    // Deprecated - use ErrorResponse.addCorsHeaders() instead
    @Deprecated
    private static void addCors(HttpExchange exchange) {
        ErrorResponse.addCorsHeaders(exchange);
    }

    // Deprecated - use ErrorResponse.sendJson() instead
    @Deprecated
    private static void sendJson(HttpExchange exchange, int status, JSONObject json) throws IOException {
        ErrorResponse.sendJson(exchange, status, json);
    }
}


