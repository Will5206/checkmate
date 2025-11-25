# How to Run FriendServiceTest

## ✅ Setup Complete
I've created a `pom.xml` file and fixed the package declaration in `FriendServiceTest.java`. Now you can run the tests using one of these methods:

---

## Option 1: Using Maven (Recommended)

### Install Maven
```bash
brew install maven
```

### Run the Tests
```bash
cd /Users/troyfreed/checkmate
mvn test
```

### Run Only FriendServiceTest
```bash
mvn test -Dtest=FriendServiceTest
```

This will:
- Download JUnit 5 dependencies automatically
- Compile your source code and tests
- Run all tests and show the results

---

## Option 2: Using an IDE

### IntelliJ IDEA or Eclipse
1. Open the project in your IDE
2. Right-click on `FriendServiceTest.java`
3. Select "Run 'FriendServiceTest'" (IntelliJ) or "Run As > JUnit Test" (Eclipse)
4. The IDE will automatically handle dependencies and compilation

Most modern IDEs will detect the `pom.xml` and configure everything automatically.

---

## Option 3: Manual Compilation (Advanced)

If you prefer not to use Maven or an IDE:

1. Download JUnit 5 JARs:
   - `junit-jupiter-api-5.10.0.jar`
   - `junit-jupiter-engine-5.10.0.jar`
   - `junit-platform-engine-1.10.0.jar`
   - `junit-platform-commons-1.10.0.jar`
   - `opentest4j-1.3.0.jar`
   - `apiguardian-api-1.1.2.jar`

2. Place them in `backend/lib/` folder

3. Compile and run:
```bash
cd /Users/troyfreed/checkmate

# Compile source files
javac -d build/classes -cp "backend/lib/*" backend/**/*.java

# Compile test files
javac -d build/test-classes -cp "build/classes:backend/lib/*:backend/lib/junit-*.jar" tests/backend/*.java

# Run tests (requires additional setup)
```

This is more complex and not recommended unless you have specific requirements.

---

## Quick Test Command

Once Maven is installed, the simplest way to test is:
```bash
cd /Users/troyfreed/checkmate && mvn test -Dtest=FriendServiceTest
```

---

## Expected Output

When tests pass, you should see:
```
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

