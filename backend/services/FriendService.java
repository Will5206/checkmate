package services;

import models.Friend;
import database.FriendshipDAO;

import java.util.*;

/**
 * Friend management service with database persistence and observer pattern support.
 * All friendship data is now stored in the database via FriendshipDAO.
 */
public class FriendService {

    /**
     * Observer for friend events for a specific user.
     */
    public interface FriendEventObserver {
        void onFriendEvent(String userId, String event, String friendId);
    }

    /**
     * Data Access Object for database operations
     */
    private final FriendshipDAO friendshipDAO;

    /**
     * Observers per user to be notified when that user's friend list changes.
     */
    private final Map<String, List<FriendEventObserver>> userIdToObservers = new HashMap<>();

    /**
     * Constructor initializes the DAO
     */
    public FriendService() {
        this.friendshipDAO = new FriendshipDAO();
    }

    /**
     * Create or return existing friendship between two users (undirected).
     * Returns the Friend model.
     */
    public Friend addFriendship(String userId1, String userId2) {
        // Check if friendship already exists
        Friend friendship = friendshipDAO.getFriendship(userId1, userId2);

        if (friendship == null) {
            // Create and save the new friendship in database
            friendship = friendshipDAO.addFriendship(userId1, userId2);

            if (friendship != null) {
                // Notify both users about the new friendship
                notifyBothUsers(userId1, userId2, "friend_added");
            }
        }

        return friendship;
    }

    /**
     * Accept a friend request by updating its status to 'accepted'.
     */
    public boolean acceptFriendRequest(String userId1, String userId2) {
        boolean updated = friendshipDAO.updateFriendshipStatus(userId1, userId2, "accepted");

        if (updated) {
            notifyBothUsers(userId1, userId2, "friend_accepted");
        }

        return updated;
    }

    /**
     * Decline a friend request by updating its status to 'declined'.
     */
    public boolean declineFriendRequest(String userId1, String userId2) {
        boolean updated = friendshipDAO.updateFriendshipStatus(userId1, userId2, "declined");

        if (updated) {
            notifyBothUsers(userId1, userId2, "friend_declined");
        }

        return updated;
    }

    /**
     * Remove friendship between two users (undirected). Returns true if removed.
     */
    public boolean removeFriendship(String userId1, String userId2) {
        // Try to remove the friendship from database
        boolean removed = friendshipDAO.removeFriendship(userId1, userId2);

        if (removed) {
            // Notify both users about the removed friendship
            notifyBothUsers(userId1, userId2, "friend_removed");
        }

        return removed;
    }

    /**
     * Backwards-compatible: one-way add wrapper uses undirected friendship.
     */
    public boolean addFriend(String userId, String friendId) {
        // Check if friendship already exists
        Friend existingFriendship = getFriendship(userId, friendId);
        boolean wasNewFriendship = (existingFriendship == null);

        // Add the friendship
        Friend friendship = addFriendship(userId, friendId);

        // Automatically accept the friendship for backwards compatibility
        if (friendship != null && wasNewFriendship) {
            acceptFriendRequest(userId, friendId);
        }

        // Return true if this was a new friendship
        return wasNewFriendship;
    }

    /**
     * Backwards-compatible: one-way remove wrapper uses undirected friendship.
     */
    public boolean removeFriend(String userId, String friendId) {
        return removeFriendship(userId, friendId);
    }

    /**
     * Return a list of the user's accepted friend IDs (never null).
     */
    public List<String> listFriends(String userId) {
        return friendshipDAO.listFriendIds(userId);
    }

    /**
     * Return the Friend model object between two users, or null if none.
     */
    public Friend getFriendship(String userId1, String userId2) {
        return friendshipDAO.getFriendship(userId1, userId2);
    }

    /**
     * Return Friend model objects for a given user's friendships.
     */
    public List<Friend> listFriendships(String userId) {
        return friendshipDAO.listFriendships(userId);
    }

    /**
     * Register an observer for a given user.
     */
    public void addObserver(String userId, FriendEventObserver observer) {
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
    public void removeObserver(String userId, FriendEventObserver observer) {
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
    private void notifyObservers(String userId, String event, String friendId) {
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

    /**
     * Notify both users about a friend event.
     * This combines the duplicate notification code.
     */
    private void notifyBothUsers(String userId1, String userId2, String event) {
        notifyObservers(userId1, event, userId2);
        notifyObservers(userId2, event, userId1);
    }
}
