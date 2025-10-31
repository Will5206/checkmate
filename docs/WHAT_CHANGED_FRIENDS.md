## Friend System - What Changed

### Overview
Adds a minimal in-memory friend system to the backend. No database changes and no frontend edits. Provides simple HTTP endpoints for add/remove/list and integrates a lightweight observer pattern for friend events.

### New/Updated Files
- backend/services/FriendService.java
  - In-memory storage of friendships using `com.checkmate.models.Friend` objects
  - API:
    - `addFriendship(int userId1, int userId2) -> Friend`
    - `removeFriendship(int userId1, int userId2) -> boolean`
    - `getFriendship(int userId1, int userId2) -> Friend`
    - `listFriends(int userId) -> List<Integer>`
    - `listFriendships(int userId) -> List<Friend>`
    - Backwards-compatible wrappers: `addFriend`, `removeFriend`
  - Simple observer interface `FriendEventObserver` with notifications on add/remove

- backend/controllers/FriendController.java
  - Endpoints:
    - `POST /api/friends/add?userId=U&friendId=F` – create (or no-op if exists)
    - `POST /api/friends/remove?userId=U&friendId=F` – remove (no-op if missing)
    - `GET  /api/friends/list?userId=U` – list friend IDs and detailed Friend records
  - CORS enabled; returns JSON

- backend/Server.java
  - Registers the three friend endpoints
  - Logs example URLs at startup

- backend/models/Friend.java
  - Added package declaration: `package com.checkmate.models;`
  - Used by `FriendService` to represent undirected friendships (status preset to `"accepted"`)

### Behavior Notes
- In-memory only: data resets on server restart (no DB usage per requirement)
- Undirected friendships: stored once per pair `(min(u1,u2), max(u1,u2))`
- Observer notifications fire to both users on add/remove
- `list` endpoint returns both a flat list of friend IDs and an array of friendship objects

### Example Usage
```
POST http://localhost:8080/api/friends/add?userId=1&friendId=2
POST http://localhost:8080/api/friends/remove?userId=1&friendId=2
GET  http://localhost:8080/api/friends/list?userId=1
```

### Future Extensions
- Persist friendships in the database (statuses: pending/accepted/declined)
- Map `User` UUIDs to numeric IDs or align types so controllers accept user UUIDs
- Real notification handling by plugging observers into your existing patterns


