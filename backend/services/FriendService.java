package services;

import models.Friend;

import java.util.*;

/**
 * Minimal in-memory friend management with Friend model objects and a simple observer pattern.
 * No database writes. Suitable as a foundation to extend later.
 */
public class FriendService {

    /**
     * Observer for friend events for a specific user.
     */
    public interface FriendEventObserver {
        void onFriendEvent(int userId, String event, int friendId);
    }

    /**
     * Tracks undirected friendships via a normalized pair key, and per-user adjacency for quick lookups.
     */
    private final Map<String, Friend> pairKeyToFriendship = new HashMap<>();
    private final Map<Integer, Set<Integer>> userIdToFriends = new HashMap<>();

    /**
     * Observers per user to be notified when that user's friend list changes.
     */
    private final Map<Integer, List<FriendEventObserver>> userIdToObservers = new HashMap<>();

    private int nextFriendshipId = 1;

    /**
     * Create or return existing friendship between two users (undirected).
     * Also updates each user's friend adjacency. Returns the Friend model.
     */
    public synchronized Friend addFriendship(int userId1, int userId2) {
        String key = toPairKey(userId1, userId2);
        Friend friendship = pairKeyToFriendship.get(key);
        if (friendship == null) {
            friendship = new Friend(nextFriendshipId++, Math.min(userId1, userId2), Math.max(userId1, userId2), new Date(), "accepted");
            pairKeyToFriendship.put(key, friendship);
            userIdToFriends.computeIfAbsent(userId1, k -> new HashSet<>()).add(userId2);
            userIdToFriends.computeIfAbsent(userId2, k -> new HashSet<>()).add(userId1);
            notifyObservers(userId1, "friend_added", userId2);
            notifyObservers(userId2, "friend_added", userId1);
        }
        return friendship;
    }

    /**
     * Remove friendship between two users (undirected). Returns true if removed.
     */
    public synchronized boolean removeFriendship(int userId1, int userId2) {
        String key = toPairKey(userId1, userId2);
        Friend removed = pairKeyToFriendship.remove(key);
        boolean existed = removed != null;
        if (existed) {
            Set<Integer> f1 = userIdToFriends.get(userId1);
            Set<Integer> f2 = userIdToFriends.get(userId2);
            if (f1 != null) f1.remove(userId2);
            if (f2 != null) f2.remove(userId1);
            notifyObservers(userId1, "friend_removed", userId2);
            notifyObservers(userId2, "friend_removed", userId1);
        }
        return existed;
    }

    /**
     * Backwards-compatible: one-way add wrapper uses undirected friendship.
     */
    public synchronized boolean addFriend(int userId, int friendId) {
        Friend before = getFriendship(userId, friendId);
        addFriendship(userId, friendId);
        return before == null;
    }

    /**
     * Backwards-compatible: one-way remove wrapper uses undirected friendship.
     */
    public synchronized boolean removeFriend(int userId, int friendId) {
        return removeFriendship(userId, friendId);
    }

    /**
     * Return a copy of the user's friend IDs (never null).
     */
    public synchronized List<Integer> listFriends(int userId) {
        Set<Integer> friends = userIdToFriends.get(userId);
        if (friends == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(friends);
    }

    /**
     * Return the Friend model object between two users, or null if none.
     */
    public synchronized Friend getFriendship(int userId1, int userId2) {
        return pairKeyToFriendship.get(toPairKey(userId1, userId2));
    }

    /**
     * Return Friend model objects for a given user's friendships.
     */
    public synchronized List<Friend> listFriendships(int userId) {
        List<Friend> result = new ArrayList<>();
        for (Friend f : pairKeyToFriendship.values()) {
            if (f.getUserId1() == userId || f.getUserId2() == userId) {
                result.add(f);
            }
        }
        return result;
    }

    /**
     * Register an observer for a given user.
     */
    public synchronized void addObserver(int userId, FriendEventObserver observer) {
        userIdToObservers.computeIfAbsent(userId, k -> new ArrayList<>()).add(observer);
    }

    /**
     * Remove an observer for a given user.
     */
    public synchronized void removeObserver(int userId, FriendEventObserver observer) {
        List<FriendEventObserver> observers = userIdToObservers.get(userId);
        if (observers != null) {
            observers.remove(observer);
            if (observers.isEmpty()) {
                userIdToObservers.remove(userId);
            }
        }
    }

    private void notifyObservers(int userId, String event, int friendId) {
        List<FriendEventObserver> observers;
        synchronized (this) {
            observers = userIdToObservers.get(userId) == null
                ? Collections.emptyList()
                : new ArrayList<>(userIdToObservers.get(userId));
        }
        for (FriendEventObserver observer : observers) {
            try {
                observer.onFriendEvent(userId, event, friendId);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private String toPairKey(int a, int b) {
        int x = Math.min(a, b);
        int y = Math.max(a, b);
        return x + ":" + y;
    }
}


