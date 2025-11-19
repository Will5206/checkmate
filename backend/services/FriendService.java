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
     * Tracks friendships using a key made from two user IDs.
     */
    private final Map<String, Friend> pairKeyToFriendship = new HashMap<>();
    
    /**
     * Tracks which users are friends with each user.
     * Key: user ID, Value: set of friend user IDs
     */
    private final Map<Integer, Set<Integer>> userIdToFriends = new HashMap<>();

    /**
     * Observers per user to be notified when that user's friend list changes.
     */
    private final Map<Integer, List<FriendEventObserver>> userIdToObservers = new HashMap<>();

    /**
     * The next friendship ID to use when creating a new friendship.
     */
    private int nextFriendshipId = 1;

    /**
     * Create or return existing friendship between two users (undirected).
     * Also updates each user's friend adjacency. Returns the Friend model.
     */
    public Friend addFriendship(int userId1, int userId2) {
        // Check if friendship already exists
        String key = toPairKey(userId1, userId2);
        Friend friendship = pairKeyToFriendship.get(key);
        
        if (friendship == null) {
            // Create and save the new friendship
            friendship = createNewFriendship(userId1, userId2);
            pairKeyToFriendship.put(key, friendship);
            
            // Update both users' friend lists
            addFriendToBothUsers(userId1, userId2);
            
            // Notify both users about the new friendship
            notifyBothUsers(userId1, userId2, "friend_added");
        }
        
        return friendship;
    }

    /**
     * Remove friendship between two users (undirected). Returns true if removed.
     */
    public boolean removeFriendship(int userId1, int userId2) {
        // Create a key from the two user IDs
        String key = toPairKey(userId1, userId2);
        
        // Try to remove the friendship
        Friend removed = pairKeyToFriendship.remove(key);
        
        // Check if the friendship actually existed
        boolean friendshipExisted = (removed != null);
        
        if (friendshipExisted) {
            // Remove both users from each other's friend lists
            removeFriendFromBothUsers(userId1, userId2);
            
            // Notify both users about the removed friendship
            notifyBothUsers(userId1, userId2, "friend_removed");
        }
        
        return friendshipExisted;
    }

    /**
     * Backwards-compatible: one-way add wrapper uses undirected friendship.
     */
    public boolean addFriend(int userId, int friendId) {
        // Check if friendship already exists
        Friend existingFriendship = getFriendship(userId, friendId);
        boolean wasNewFriendship = (existingFriendship == null);
        
        // Add the friendship
        addFriendship(userId, friendId);
        
        // Return true if this was a new friendship
        return wasNewFriendship;
    }

    /**
     * Backwards-compatible: one-way remove wrapper uses undirected friendship.
     */
    public boolean removeFriend(int userId, int friendId) {
        return removeFriendship(userId, friendId);
    }

    /**
     * Return a copy of the user's friend IDs (never null).
     */
    public List<Integer> listFriends(int userId) {
        Set<Integer> friends = userIdToFriends.get(userId);
        
        if (friends == null) {
            // User has no friends, return an empty list
            return Collections.emptyList();
        }
        
        // Create a new list from the set and return it
        List<Integer> friendsList = new ArrayList<>(friends);
        return friendsList;
    }

    /**
     * Return the Friend model object between two users, or null if none.
     */
    public Friend getFriendship(int userId1, int userId2) {
        String key = toPairKey(userId1, userId2);
        Friend friendship = pairKeyToFriendship.get(key);
        return friendship;
    }

    /**
     * Return Friend model objects for a given user's friendships.
     */
    public List<Friend> listFriendships(int userId) {
        List<Friend> result = new ArrayList<>();
        
        // Go through all friendships
        for (Friend friendship : pairKeyToFriendship.values()) {
            int friendUserId1 = friendship.getUserId1();
            int friendUserId2 = friendship.getUserId2();
            
            // Check if this friendship involves the user we're looking for
            if (friendUserId1 == userId || friendUserId2 == userId) {
                result.add(friendship);
            }
        }
        
        return result;
    }

    /**
     * Register an observer for a given user.
     */
    public void addObserver(int userId, FriendEventObserver observer) {
        // Get the list of observers for this user
        List<FriendEventObserver> observers = userIdToObservers.get(userId);
        
        if (observers == null) {
            // This user has no observers yet, so create a new list
            observers = new ArrayList<>();
            userIdToObservers.put(userId, observers);
        }
        
        // Add the new observer to the list
        observers.add(observer);
    }

    /**
     * Remove an observer for a given user.
     */
    public void removeObserver(int userId, FriendEventObserver observer) {
        // Get the list of observers for this user
        List<FriendEventObserver> observers = userIdToObservers.get(userId);
        
        if (observers != null) {
            // Remove the observer from the list
            observers.remove(observer);
            
            // If the list is now empty, remove it from the map
            if (observers.isEmpty()) {
                userIdToObservers.remove(userId);
            }
        }
    }

    /**
     * Notify all observers for a user about a friend event.
     */
    private void notifyObservers(int userId, String event, int friendId) {
        // Get the list of observers for this user
        List<FriendEventObserver> observers = userIdToObservers.get(userId);
        
        if (observers == null) {
            // No observers, so nothing to do
            return;
        }
        
        // Create a copy of the observers list to avoid issues if it changes
        List<FriendEventObserver> observersCopy = new ArrayList<>(observers);
        
        // Notify each observer
        for (FriendEventObserver observer : observersCopy) {
            try {
                observer.onFriendEvent(userId, event, friendId);
            } catch (RuntimeException e) {
                // If an observer throws an error, ignore it and continue with other observers
                // (This prevents one bad observer from breaking everything)
            }
        }
    }

    // ========== Private Helper Methods ==========

    /**
     * Create a new Friend object with the next available ID.
     */
    private Friend createNewFriendship(int userId1, int userId2) {
        // Get the current friendship ID and then increase it by 1 for next time
        int friendshipId = nextFriendshipId;
        nextFriendshipId = nextFriendshipId + 1;
        
        // Find the smaller and larger user ID
        int smallerUserId = Math.min(userId1, userId2);
        int largerUserId = Math.max(userId1, userId2);
        
        // Create a new Friend object
        Date currentDate = new Date();
        String status = "accepted";
        Friend friendship = new Friend(friendshipId, smallerUserId, largerUserId, currentDate, status);
        
        return friendship;
    }

    /**
     * Add both users to each other's friend lists.
     * This combines the duplicate code that was doing the same thing for user1 and user2.
     */
    private void addFriendToBothUsers(int userId1, int userId2) {
        // Add user2 to user1's friend list
        addFriendToUserList(userId1, userId2);
        
        // Add user1 to user2's friend list
        addFriendToUserList(userId2, userId1);
    }

    /**
     * Add a friend to a user's friend list.
     * Creates the list if it doesn't exist.
     */
    private void addFriendToUserList(int userId, int friendId) {
        Set<Integer> userFriends = userIdToFriends.get(userId);
        if (userFriends == null) {
            userFriends = new HashSet<>();
            userIdToFriends.put(userId, userFriends);
        }
        userFriends.add(friendId);
    }

    /**
     * Remove both users from each other's friend lists.
     * This combines the duplicate code that was doing the same thing for user1 and user2.
     */
    private void removeFriendFromBothUsers(int userId1, int userId2) {
        // Remove user2 from user1's friend list
        removeFriendFromUserList(userId1, userId2);
        
        // Remove user1 from user2's friend list
        removeFriendFromUserList(userId2, userId1);
    }

    /**
     * Remove a friend from a user's friend list.
     */
    private void removeFriendFromUserList(int userId, int friendId) {
        Set<Integer> userFriends = userIdToFriends.get(userId);
        if (userFriends != null) {
            userFriends.remove(friendId);
        }
    }

    /**
     * Notify both users about a friend event.
     * This combines the duplicate notification code.
     */
    private void notifyBothUsers(int userId1, int userId2, String event) {
        notifyObservers(userId1, event, userId2);
        notifyObservers(userId2, event, userId1);
    }

    /**
     * Create a unique key from two user IDs.
     * Always puts the smaller ID first, so the same two users always get the same key.
     */
    private String toPairKey(int userId1, int userId2) {
        int smallerId = Math.min(userId1, userId2);
        int largerId = Math.max(userId1, userId2);
        String key = smallerId + ":" + largerId;
        return key;
    }
}
