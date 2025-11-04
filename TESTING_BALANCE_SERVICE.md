# How to Run BalanceServiceTest

## Prerequisites

1. **MySQL Database Running**
   - MySQL server must be running on `localhost:3306`
   - Database `checkmate_db` must exist
   - Schema must be initialized (run `schema.sql`)

2. **Maven Installed** (or use IDE)
   ```bash
   brew install maven  # macOS
   ```

3. **Database Connection**
   - Database credentials in `DatabaseConnection.java` must match your setup
   - Default: `root` / `password`

---

## Quick Start: Run Validation Tests Only

The easiest way to start is to run the tests that **don't require database access**. These test input validation:

```bash
cd /Users/will5206/checkmate
mvn test -Dtest=BalanceServiceTest#testAddToBalance_negativeAmount_throwsException
```

Or run all validation tests:
```bash
mvn test -Dtest=BalanceServiceTest
```

**Expected Result**: Tests 4, 5, 6, 7, 9, 13, 14, 15 should pass (these don't need database).

---

## Setup: Create Test User for Database Tests

Before running database-dependent tests, you need to create a test user:

### Option 1: Using MySQL Command Line

```bash
mysql -u root -p checkmate_db
```

Then run:
```sql
INSERT INTO users (user_id, name, email, phone_number, password_hash, balance) 
VALUES ('test-user-123', 'Test User', 'test@example.com', '5551234567', 'test-hash', 0.00);
```

### Option 2: Using AuthService (Programmatic)

Create a helper script or use the signup endpoint:
```bash
curl -X POST http://localhost:8080/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"name":"Test User","email":"test@example.com","phoneNumber":"5551234567","password":"password123"}'
```

Then use the returned `userId` in your tests.

---

## Enable Database-Dependent Tests

1. **Open** `tests/backend/BalanceServiceTest.java`

2. **Find a test** (e.g., `testAddToBalance_success()`)

3. **Uncomment the test code** and **replace** `testUserId` with a real user ID:

```java
@Test
void testAddToBalance_success() {
    // Replace with your actual test user ID
    String realUserId = "test-user-123";  // or use userId from signup
    
    double initialBalance = balanceService.getCurrentBalance(realUserId);
    double amountToAdd = 50.00;
    
    boolean result = balanceService.addToBalance(
        realUserId, 
        amountToAdd, 
        BalanceService.TYPE_PAYMENT_RECEIVED,
        "Test payment received",
        "ref-123",
        "transaction"
    );
    
    assertTrue(result, "Balance addition should succeed");
    
    double newBalance = balanceService.getCurrentBalance(realUserId);
    assertEquals(initialBalance + amountToAdd, newBalance, 0.01, 
                "Balance should increase by the added amount");
    
    // Verify history was recorded
    List<BalanceHistory> history = balanceService.getBalanceHistory(realUserId, 1);
    assertFalse(history.isEmpty(), "History should contain the new record");
    assertEquals(amountToAdd, history.get(0).getAmount(), 0.01, 
                "History amount should match added amount");
}
```

4. **Update `setup()` method** to use your test user:

```java
@BeforeEach
void setup() {
    balanceService = new BalanceService();
    // Use a real user ID from your database
    testUserId = "test-user-123";  // Replace with actual test user ID
}
```

---

## Running Tests

### Option 1: Using Maven (Command Line)

**Run all BalanceServiceTest tests:**
```bash
cd /Users/will5206/checkmate
mvn test -Dtest=BalanceServiceTest
```

**Run a specific test:**
```bash
mvn test -Dtest=BalanceServiceTest#testAddToBalance_success
```

**Run multiple specific tests:**
```bash
mvn test -Dtest=BalanceServiceTest#testAddToBalance_success+BalanceServiceTest#testSubtractFromBalance_success
```

**Run all tests:**
```bash
mvn test
```

### Option 2: Using IDE (IntelliJ IDEA / VS Code / Eclipse)

#### IntelliJ IDEA:
1. Open the project
2. Navigate to `tests/backend/BalanceServiceTest.java`
3. Right-click on the file or a test method
4. Select **"Run 'BalanceServiceTest'"** or **"Run 'testAddToBalance_success()'"**
5. View results in the test runner panel

#### VS Code:
1. Install Java Test Runner extension
2. Open `BalanceServiceTest.java`
3. Click the "Run Test" link above test methods
4. Or use Command Palette: "Java: Run Tests"

#### Eclipse:
1. Right-click on `BalanceServiceTest.java`
2. Select **"Run As > JUnit Test"**

---

## Test Categories

### ✅ Validation Tests (No Database Required)
These tests validate input and will work immediately:
- `testAddToBalance_negativeAmount_throwsException`
- `testAddToBalance_zeroAmount_throwsException`
- `testAddToBalance_nullUserId_throwsException`
- `testAddToBalance_emptyUserId_throwsException`
- `testSubtractFromBalance_negativeAmount_throwsException`
- `testGetCurrentBalance_nullUserId_throwsException`
- `testGetBalanceHistory_nullUserId_throwsException`
- `testGetBalanceHistoryByType_nullType_throwsException`

### 🔄 Database Tests (Require Setup)
These need a real user in the database:
- `testAddToBalance_success`
- `testSubtractFromBalance_success`
- `testGetBalanceHistory_returnsHistory`
- `testSubtractFromBalance_insufficientBalance_throwsException`
- `testBalanceHistory_recordsAllFields`
- `testGetBalanceHistoryByType_filtersCorrectly`
- `testGetCurrentBalance_returnsCorrectBalance`

---

## Expected Output

### Successful Run (Validation Tests):
```
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Successful Run (All Tests):
```
[INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## Troubleshooting

### Error: "Failed to connect to database"
- Check MySQL is running: `mysql -u root -p`
- Verify database exists: `SHOW DATABASES;`
- Check credentials in `DatabaseConnection.java`

### Error: "User not found"
- Create a test user (see Setup section above)
- Update `testUserId` in `setup()` method

### Error: "Table 'balance_history' doesn't exist"
- Run the updated `schema.sql` to create the table:
  ```bash
  mysql -u root -p checkmate_db < backend/database/schema.sql
  ```

### Error: "Compilation failed"
- Make sure Maven dependencies are downloaded:
  ```bash
  mvn clean compile
  mvn test-compile
  ```

---

## Cleaning Up Test Data

After running tests, you may want to clean up test transactions:

```sql
-- View test transactions
SELECT * FROM balance_history WHERE user_id = 'test-user-123';

-- Delete test history (optional)
DELETE FROM balance_history WHERE user_id = 'test-user-123';

-- Reset test user balance (optional)
UPDATE users SET balance = 0.00 WHERE user_id = 'test-user-123';
```

---

## Next Steps

1. ✅ Run validation tests first (they work immediately)
2. ✅ Create a test user in the database
3. ✅ Uncomment and configure one database test
4. ✅ Run that test to verify setup
5. ✅ Gradually enable more tests as needed

Good luck testing! 🚀

