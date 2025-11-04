# IDE Setup for Running BalanceServiceTest

## The Problem
The IDE shows "No tests found" even though tests exist and run with Maven. This is a project configuration issue.

## Solution by IDE

### VS Code / Cursor

1. **Reload the Java Project**
   - Press `Cmd+Shift+P` (Mac) or `Ctrl+Shift+P` (Windows/Linux)
   - Type: `Java: Clean Java Language Server Workspace`
   - Select it and choose "Reload and delete"

2. **Ensure Maven is Recognized**
   - Press `Cmd+Shift+P` / `Ctrl+Shift+P`
   - Type: `Java: Configure Java Runtime`
   - Make sure Java 11+ is selected

3. **Install Required Extensions**
   - **Extension Pack for Java** (by Microsoft)
   - **Java Test Runner** (by Microsoft)
   - **Maven for Java** (by Microsoft)

4. **Reload Window**
   - Press `Cmd+Shift+P` / `Ctrl+Shift+P`
   - Type: `Developer: Reload Window`

5. **Try Running Tests Again**
   - Open `tests/backend/BalanceServiceTest.java`
   - Look for green "Run Test" links above `@Test` methods
   - Or right-click the file → "Run Tests"

### IntelliJ IDEA

1. **Import Maven Project**
   - File → Open → Select the `pom.xml` file
   - Click "Open as Project"
   - Wait for Maven to sync

2. **Mark Directories Correctly**
   - Right-click `backend/` folder → Mark Directory as → Sources Root
   - Right-click `tests/backend/` folder → Mark Directory as → Test Sources Root

3. **Reload Maven Project**
   - Right-click `pom.xml` in Project view
   - Select "Maven" → "Reload Project"

4. **Run Tests**
   - Open `BalanceServiceTest.java`
   - Right-click the file → "Run 'BalanceServiceTest'"
   - Or click the green arrow next to `@Test` methods

### If Still Not Working

**Option 1: Use Maven in Terminal (Guaranteed to Work)**
```bash
cd /Users/will5206/checkmate
mvn test -Dtest=BalanceServiceTest
```

**Option 2: Run Specific Test Method**
```bash
mvn test -Dtest=BalanceServiceTest#testAddToBalance_negativeAmount_throwsException
```

**Option 3: Create a `.vscode/settings.json` for VS Code**

Create `.vscode/settings.json`:
```json
{
  "java.project.sourcePaths": ["backend"],
  "java.project.outputPath": "target/classes",
  "java.test.sourcePaths": ["tests/backend"],
  "java.test.outputPath": "target/test-classes",
  "java.configuration.updateBuildConfiguration": "automatic"
}
```

**Option 4: Create `.idea/` configuration for IntelliJ**

If IntelliJ isn't detecting properly:
1. File → Project Structure
2. Under Modules, ensure:
   - `backend` is marked as Sources
   - `tests/backend` is marked as Test Sources
3. Apply and OK

---

## Quick Test (Verify Tests Are There)

Run this in terminal to verify tests exist:
```bash
cd /Users/will5206/checkmate
mvn test -Dtest=BalanceServiceTest 2>&1 | grep -E "(Tests run|BUILD)"
```

Expected output:
```
[INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

If this works, the tests are fine - it's just an IDE configuration issue.

