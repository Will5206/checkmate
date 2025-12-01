# Code Smell Refactoring Presentation: Unprotected Shared Data / Race Condition

## SLIDE 1: The Problem - Original Code

### Code Block (Image):
```java
public Connection getConnection() {
    try {
        // check if connection is still valid --- reconnect if needed !!
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
        }
    } catch (SQLException e) {
        System.err.println("error checking/reestablishing connection");
        e.printStackTrace();
    }
    return connection;
}
```

### Bullet Points:

**What the code does:**
• Checks if the database connection is null or closed
• If invalid, creates a new connection using DriverManager
• Returns the connection object to the caller
• Used by multiple services (AuthService, BalanceService, TransactionService) concurrently

**Why it's problematic (Code Smell):**
• **Race Condition (#49)**: Multiple threads can check `connection.isClosed()` simultaneously, then all try to create new connections at the same time
• **Unprotected Shared Data (#50)**: The `connection` field is shared mutable state without synchronization - two threads can modify it concurrently
• **Silent Failure (#25)**: Catches SQLException but continues execution, potentially returning a null connection that causes NullPointerException downstream
• **Thread Safety Violation**: In a multi-threaded HTTP server, concurrent requests can corrupt the shared connection state

**Real-world impact:**
• Under load, multiple threads may create multiple connections unnecessarily
• Connection leaks and resource exhaustion
• Unpredictable behavior - sometimes returns null, causing crashes
• Data corruption risk if transactions overlap on the same connection

---

## SLIDE 2: The Solution - Refactored Code

### What I Did to Fix It:

• Added `synchronized` keyword - ensures only one thread can check/create connection at a time
• Changed to `throws SQLException` - forces proper error handling, prevents null returns
• Synchronized `closeConnection()` - maintains thread-safety for all connection operations

### Refactored Code:
```java
/**
 * Get the active database connection.
 * Thread-safe method that ensures only one connection is created at a time.
 * Reconnects automatically if the connection is closed or invalid.
 * 
 * @return Connection object (never null)
 * @throws SQLException if connection cannot be established
 */
public synchronized Connection getConnection() throws SQLException {
    // Check if connection is still valid - reconnect if needed
    // Synchronized to prevent race conditions when multiple threads check simultaneously
    if (connection == null || connection.isClosed()) {
        connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
        System.out.println("Database connection established/reconnected");
    }
    return connection;
}
```

### Benefits:
✓ Thread-safe: eliminates race conditions
✓ Fail-fast: proper exception handling
✓ Production-ready: handles concurrent access correctly

