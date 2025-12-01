# Complete Guide: PR and JIRA Process

## ✅ Yes, Tests Will Run Automatically!

**Good news:** Your repository has GitHub Actions configured (`.github/workflows/java-tests.yml` and `maven.yml`). When you push your code or create a PR, GitHub will automatically:

1. ✅ Set up MySQL database
2. ✅ Run `mvn test` to execute all unit tests
3. ✅ Show test results in the PR
4. ✅ Block merge if tests fail (if configured)

---

## Step-by-Step Process

### Step 1: Create JIRA Task

1. Go to your JIRA board (e.g., `https://your-company.atlassian.net`)
2. Navigate to your current sprint
3. Click **"Create"** → **"Task"** (or use the `+` button)
4. Fill in:
   - **Summary**: "Receipt parsing improvements and item claiming functionality"
   - **Description**: 
     ```
     Implement item claiming functionality for accepted receipts:
     - Users can claim/unclaim items from receipts
     - Calculate owed amounts with proportional tax/tip
     - Activity feed shows all receipts with item assignments
     - Improved receipt parsing error handling
     - Better timeout and logging for Python script
     ```
   - **Issue Type**: Task
   - **Sprint**: Current sprint
   - **Assignee**: Yourself
5. Click **"Create"**
6. **Copy the task key** (e.g., `CHEC-123` or `CHECK-456`)

---

### Step 2: Create Feature Branch from JIRA

**Option A: Using JIRA Integration (if configured)**
- In JIRA, click the task → Click **"Create branch"** → Follow prompts

**Option B: Manual (Recommended)**
```bash
# Make sure you're on main and it's up to date
cd /Users/will5206/checkmate
git checkout main
git pull origin main

# Create branch with JIRA task key
# Format: JIRA-KEY-short-description
git checkout -b CHEC-123-receipt-parsing-and-item-claiming

# Replace CHEC-123 with your actual JIRA task key
```

**Branch naming examples:**
- `CHEC-123-receipt-parsing-and-item-claiming`
- `CHEC-123-item-claiming-feature`
- `CHECK-456-receipt-improvements`

---

### Step 3: Apply Your Stashed Changes

```bash
# Check what's in your stash
git stash list

# Apply your stashed changes to the new branch
git stash pop

# If you have multiple stashes, use:
# git stash pop stash@{0}
```

This will restore:
- ✅ All backend changes (ReceiptController, ReceiptDAO, etc.)
- ✅ All frontend changes (BillReview, HomeScreen, etc.)
- ✅ All new files (tests, configs, etc.)
- ✅ All documentation

---

### Step 4: Review Your Changes

```bash
# See what files changed
git status

# Review the changes
git diff

# See a summary
git diff --stat
```

**Expected changes:**
- `backend/controllers/ReceiptController.java` - Item claiming endpoints
- `backend/database/ReceiptDAO.java` - Item assignment methods
- `backend/database/schema.sql` - item_assignments table
- `mobile/screens/BillReview.js` - Item claiming UI
- `tests/backend/*Test.java` - Unit tests
- And more...

---

### Step 5: Stage and Commit

```bash
# Stage all changes
git add -A

# Commit with JIRA task reference in the message
git commit -m "CHEC-123: Implement receipt parsing improvements and item claiming functionality

- Added item claiming functionality for accepted receipts
- Implemented item assignment endpoints (claim/unclaim)
- Added calculateUserOwedAmount with proportional tax/tip
- Improved receipt parsing error handling and timeout
- Enhanced BillReview screen with item claiming UI
- Added comprehensive unit tests for all new functionality
- Fixed item assignments endpoint registration
- Improved OpenAI API error messages

Unit tests included:
- FriendServiceTest.java (10 test cases)
- ReceiptServiceItemClaimingTest.java
- ReceiptDAOTest.java (structure documented)
- ReceiptControllerItemClaimingTest.java
- ReceiptParserErrorHandlingTest.py (10 test cases)"
```

**Important:** 
- Include the JIRA task key (`CHEC-123`) in the commit message
- This links the commit to JIRA automatically
- Describe what was changed, not how

---

### Step 6: Push to GitHub

```bash
# Push your branch to GitHub
git push -u origin CHEC-123-receipt-parsing-and-item-claiming

# Replace with your actual branch name
```

**What happens next:**
1. GitHub receives your push
2. GitHub Actions automatically starts running tests
3. You'll see a yellow dot (🟡) in GitHub → Actions tab
4. Tests run in ~2-5 minutes
5. Status updates to ✅ (pass) or ❌ (fail)

---

### Step 7: Create Pull Request

1. **Go to GitHub** → Your repository
2. You'll see a banner: **"CHEC-123-receipt-parsing-and-item-claiming had recent pushes"**
3. Click **"Compare & pull request"**

   **OR**

4. Go to **"Pull requests"** tab → Click **"New pull request"**
5. Select:
   - **Base branch**: `main` (or `develop` if that's your default)
   - **Compare branch**: `CHEC-123-receipt-parsing-and-item-claiming`

6. **Fill in PR details:**

   **Title:**
   ```
   CHEC-123: Receipt parsing improvements and item claiming functionality
   ```

   **Description:**
   ```markdown
   ## Summary
   Implements item claiming functionality for accepted receipts, improves receipt parsing error handling, and adds comprehensive unit tests.

   ## Changes
   - ✅ Item claiming/unclaiming functionality
   - ✅ Item assignment endpoints (POST/DELETE /api/receipts/items/claim)
   - ✅ Get item assignments endpoint (GET /api/receipts/items/assignments)
   - ✅ Calculate owed amount with proportional tax/tip
   - ✅ Enhanced BillReview screen with item claiming UI
   - ✅ Improved receipt parsing error handling
   - ✅ Better timeout and logging for Python script
   - ✅ Comprehensive unit tests

   ## Testing
   - [x] All unit tests pass locally
   - [x] Manual testing completed
   - [x] Tests will run automatically via GitHub Actions

   ## Related
   - Closes CHEC-123

   ## Screenshots (if applicable)
   [Add screenshots of UI changes]
   ```

7. **Add reviewers** (if required)
8. **Add labels** (e.g., `backend`, `frontend`, `feature`)
9. Click **"Create pull request"**

---

### Step 8: Monitor PR Status

**GitHub will automatically:**

1. ✅ Run tests (you'll see status checks)
2. ✅ Show test results in the PR
3. ✅ Update JIRA task status (if integration configured)

**Check test status:**
- Look for ✅ or ❌ next to "Java Tests" in the PR
- Click "Details" to see full test output
- If tests fail, fix issues and push again

**PR will show:**
```
✅ All checks have passed
   ✅ Java Tests
   ✅ Java CI with Maven
```

---

### Step 9: Address Review Feedback

If reviewers request changes:

```bash
# Make your changes
# ... edit files ...

# Stage and commit
git add -A
git commit -m "CHEC-123: Address review feedback - [describe changes]"

# Push (updates the PR automatically)
git push
```

---

### Step 10: Merge PR

Once approved and tests pass:

1. Click **"Merge pull request"**
2. Choose merge type (usually "Create a merge commit")
3. Click **"Confirm merge"**
4. Delete the branch (optional, GitHub will prompt)

**After merge:**
- ✅ Code is in `main` branch
- ✅ JIRA task auto-updates to "Done" (if integration configured)
- ✅ GitHub Actions runs tests on `main` branch

---

## Troubleshooting

### Tests Fail in GitHub Actions

**Check the error:**
1. Go to PR → Click "Details" on failed check
2. Scroll to "Run Maven tests" section
3. Look for error messages

**Common issues:**
- **Missing dependencies**: Check `pom.xml`
- **Database connection**: Tests use MySQL in CI
- **Compilation errors**: Run `mvn test-compile` locally first

**Fix and push again:**
```bash
# Fix the issue locally
mvn test  # Run tests locally first

# Commit fix
git add -A
git commit -m "CHEC-123: Fix failing tests"
git push
```

### Stash Not Found

```bash
# List all stashes
git stash list

# If you see multiple, apply the right one
git stash pop stash@{0}  # Most recent
git stash pop stash@{1}  # Second most recent
```

### Branch Already Exists

```bash
# Delete local branch
git branch -D CHEC-123-receipt-parsing-and-item-claiming

# Delete remote branch (if exists)
git push origin --delete CHEC-123-receipt-parsing-and-item-claiming

# Create fresh branch
git checkout -b CHEC-123-receipt-parsing-and-item-claiming
```

---

## Quick Reference

```bash
# 1. Create branch
git checkout -b CHEC-XXX-description

# 2. Apply stash
git stash pop

# 3. Review
git status
git diff

# 4. Commit
git add -A
git commit -m "CHEC-XXX: Description"

# 5. Push
git push -u origin CHEC-XXX-description

# 6. Create PR on GitHub
# (Use web interface)
```

---

## Checklist

Before creating PR:
- [ ] JIRA task created
- [ ] Branch created from JIRA task
- [ ] Stashed changes applied
- [ ] All changes reviewed
- [ ] Unit tests pass locally (`mvn test`)
- [ ] Commit message includes JIRA key
- [ ] Code pushed to GitHub
- [ ] PR description filled out
- [ ] Reviewers added (if required)

After PR created:
- [ ] GitHub Actions tests pass
- [ ] PR reviewed
- [ ] Review feedback addressed
- [ ] PR merged
- [ ] Branch deleted

---

## Need Help?

- **JIRA issues**: Check your JIRA admin or team lead
- **Git issues**: `git status` and `git log` are your friends
- **Test failures**: Check GitHub Actions logs for details
- **PR questions**: Ask your team in Slack/Teams

Good luck! 🚀
