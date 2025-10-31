package com.checkmate.controllers;

import com.checkmate.services.FriendService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONArray;
import org.json.JSONObject;

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
                int userId = Integer.parseInt(query.getOrDefault("userId", ""));
                int friendId = Integer.parseInt(query.getOrDefault("friendId", ""));
                boolean added = friendService.addFriend(userId, friendId);
                JSONObject resp = new JSONObject()
                        .put("success", true)
                        .put("added", added)
                        .put("userId", userId)
                        .put("friendId", friendId);
                sendJson(exchange, 200, resp);
            } catch (Exception e) {
                sendJson(exchange, 400, new JSONObject().put("success", false).put("message", "Invalid parameters"));
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
                int userId = Integer.parseInt(query.getOrDefault("userId", ""));
                int friendId = Integer.parseInt(query.getOrDefault("friendId", ""));
                boolean removed = friendService.removeFriend(userId, friendId);
                JSONObject resp = new JSONObject()
                        .put("success", true)
                        .put("removed", removed)
                        .put("userId", userId)
                        .put("friendId", friendId);
                sendJson(exchange, 200, resp);
            } catch (Exception e) {
                sendJson(exchange, 400, new JSONObject().put("success", false).put("message", "Invalid parameters"));
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
                int userId = Integer.parseInt(query.getOrDefault("userId", ""));
                List<Integer> friendIds = friendService.listFriends(userId);
                JSONArray friendsArray = new JSONArray(friendIds);

                // Also include detailed Friend model data
                JSONArray friendshipsArray = new JSONArray();
                friendService.listFriendships(userId).forEach(f -> {
                    JSONObject obj = new JSONObject()
                            .put("friendshipId", f.getFriendshipId())
                            .put("userId1", f.getUserId1())
                            .put("userId2", f.getUserId2())
                            .put("status", f.getStatus())
                            .put("createdAt", f.getCreatedAt().getTime());
                    friendshipsArray.put(obj);
                });

                JSONObject resp = new JSONObject()
                        .put("success", true)
                        .put("userId", userId)
                        .put("friends", friendsArray)
                        .put("friendships", friendshipsArray);
                sendJson(exchange, 200, resp);
            } catch (Exception e) {
                sendJson(exchange, 400, new JSONObject().put("success", false).put("message", "Invalid parameters"));
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


