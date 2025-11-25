# Friendship Database Integration - Usage Guide

The friendship functionality has been fully connected to the database! Here's how to use it:

## What's Been Set Up

1. **Database Table**: The `friendships` table already exists in your schema with:
   - `friendship_id` (auto-incrementing primary key)
   - `user_id_1` and `user_id_2` (VARCHAR to match your users table)
   - `status` (ENUM: 'pending', 'accepted', 'declined')
   - `created_at` timestamp
   - Foreign keys to the users table

2. **FriendshipDAO** (`backend/database/FriendshipDAO.java`):
   - Handles all database operations for friendships
   - Methods for add, remove, list, update status, etc.

3. **FriendService** (`backend/services/FriendService.java`):
   - Business logic layer that uses FriendshipDAO
   - Now persists all data to the database instead of memory
   - Still supports observer pattern for notifications

4. **FriendController** (`backend/controllers/FriendController.java`):
   - HTTP endpoints updated to work with String user IDs
   - Three handlers: AddFriend, RemoveFriend, ListFriends

## Using the Friendship System in Code

### Example 1: Add a friendship between two users

```java
import services.FriendService;

FriendService friendService = new FriendService();

// User IDs from your users table (UUIDs as VARCHAR(36))
String user1Id = "550e8400-e29b-41d4-a716-446655440001";
String user2Id = "550e8400-e29b-41d4-a716-446655440002";

// Add friendship (creates pending request, then auto-accepts it)
boolean added = friendService.addFriend(user1Id, user2Id);

if (added) {
    System.out.println("Friendship created and accepted!");
} else {
    System.out.println("Friendship already exists");
}
```

This will:
- Insert a row into the `friendships` table
- Set status to 'accepted' automatically
- Work bidirectionally (user2 is also friends with user1)

### Example 2: List all friends for a user

```java
import services.FriendService;
import java.util.List;

FriendService friendService = new FriendService();
String userId = "550e8400-e29b-41d4-a716-446655440001";

// Get list of friend IDs (only accepted friendships)
List<String> friendIds = friendService.listFriends(userId);

System.out.println("User has " + friendIds.size() + " friends:");
for (String friendId : friendIds) {
    System.out.println("  - Friend ID: " + friendId);
}
```

### Example 3: Get detailed friendship information

```java
import services.FriendService;
import models.Friend;
import java.util.List;

FriendService friendService = new FriendService();
String userId = "550e8400-e29b-41d4-a716-446655440001";

// Get full Friend objects with all details
List<Friend> friendships = friendService.listFriendships(userId);

for (Friend friendship : friendships) {
    System.out.println("Friendship ID: " + friendship.getFriendshipId());
    System.out.println("Between: " + friendship.getUserId1() +
                       " and " + friendship.getUserId2());
    System.out.println("Status: " + friendship.getStatus());
    System.out.println("Created: " + friendship.getCreatedAt());
    System.out.println("---");
}
```

### Example 4: Remove a friendship

```java
import services.FriendService;

FriendService friendService = new FriendService();

String user1Id = "550e8400-e29b-41d4-a716-446655440001";
String user2Id = "550e8400-e29b-41d4-a716-446655440002";

// Remove the friendship
boolean removed = friendService.removeFriend(user1Id, user2Id);

if (removed) {
    System.out.println("Friendship removed from database");
} else {
    System.out.println("Friendship not found");
}
```

### Example 5: Check if a friendship exists

```java
import services.FriendService;
import models.Friend;

FriendService friendService = new FriendService();

String user1Id = "550e8400-e29b-41d4-a716-446655440001";
String user2Id = "550e8400-e29b-41d4-a716-446655440002";

// Get specific friendship
Friend friendship = friendService.getFriendship(user1Id, user2Id);

if (friendship != null) {
    System.out.println("Friendship exists with status: " + friendship.getStatus());
} else {
    System.out.println("No friendship found");
}
```

## Using the HTTP API Endpoints

### Add a friend (POST)
```bash
curl -X POST "http://localhost:8000/friends/add?userId=USER_ID_1&friendId=USER_ID_2"

# Response:
{
  "success": true,
  "added": true,
  "userId": "USER_ID_1",
  "friendId": "USER_ID_2"
}
```

### Remove a friend (POST)
```bash
curl -X POST "http://localhost:8000/friends/remove?userId=USER_ID_1&friendId=USER_ID_2"

# Response:
{
  "success": true,
  "removed": true,
  "userId": "USER_ID_1",
  "friendId": "USER_ID_2"
}
```

### List friends (GET)
```bash
curl "http://localhost:8000/friends/list?userId=USER_ID_1"

# Response:
{
  "success": true,
  "userId": "USER_ID_1",
  "friends": ["USER_ID_2", "USER_ID_3"],
  "friendships": [
    {
      "friendshipId": 1,
      "userId1": "USER_ID_1",
      "userId2": "USER_ID_2",
      "status": "accepted",
      "createdAt": 1234567890000
    }
  ]
}
```

## Advanced Features

### Accept/Decline Friend Requests

If you want to implement a proper friend request flow (pending → accepted/declined):

```java
import services.FriendService;
import models.Friend;

FriendService friendService = new FriendService();

// Create a pending friendship request
Friend friendship = friendService.addFriendship(user1Id, user2Id);
// This creates with status 'pending'

// Later, user2 can accept:
friendService.acceptFriendRequest(user1Id, user2Id);
// Status changes to 'accepted'

// Or decline:
friendService.declineFriendRequest(user1Id, user2Id);
// Status changes to 'declined'
```

### Using the Observer Pattern

You can register observers to be notified when friendship events occur:

```java
import services.FriendService;

FriendService friendService = new FriendService();

// Create an observer
FriendService.FriendEventObserver observer = new FriendService.FriendEventObserver() {
    @Override
    public void onFriendEvent(String userId, String event, String friendId) {
        System.out.println("User " + userId + " - Event: " + event +
                           " - Friend: " + friendId);
        // Send push notification, update UI, etc.
    }
};

// Register it for a user
friendService.addObserver(user1Id, observer);

// Now any friendship changes for user1 will trigger the observer
```

## Testing

Run the test file to verify everything works:

```bash
cd backend
javac -cp ".:lib/*" TestFriendship.java
java -cp ".:lib/*" TestFriendship
```

## Verification

Check the database directly:

```sql
-- View all friendships
SELECT * FROM friendships;

-- View friendships for a specific user
SELECT * FROM friendships
WHERE user_id_1 = 'YOUR_USER_ID' OR user_id_2 = 'YOUR_USER_ID';

-- Count friends per user
SELECT
    user_id_1 as user_id,
    COUNT(*) as friend_count
FROM friendships
WHERE status = 'accepted'
GROUP BY user_id_1;
```

## Notes

- User IDs must be VARCHAR(36) UUIDs that exist in your `users` table
- Friendships are bidirectional (if A is friends with B, then B is friends with A)
- The database stores each friendship once with the smaller user_id first
- The `addFriend()` method automatically accepts friendships for backwards compatibility
- Use `addFriendship()` + `acceptFriendRequest()` for a proper request flow
- All changes are immediately persisted to the database
