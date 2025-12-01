# PR Strategy Guide

## Current Situation
- You've made significant changes to the codebase (receipt persistence, friend requests, new screens, etc.)
- Your teammate has an open PR with OpenAI receipt parsing implementation
- You need to decide: merge their PR first, or create your PR first?

## Recommendation: **Merge Teammate's PR First**

### Why Merge Their PR First?

1. **Fewer Conflicts**: If you merge their PR first, you'll only need to resolve conflicts once when creating your PR. If you create your PR first, they'll need to resolve conflicts when merging theirs.

2. **Easier Integration**: Their PR likely touches `HomeScreen.js` (receipt parsing), which you've also modified. Merging theirs first lets you see their changes and integrate your photo persistence and navigation logic cleanly.

3. **Better Testing**: After merging their PR, you can test the full flow (their parsing + your persistence) before creating your PR.

4. **Standard Workflow**: The general best practice is to merge the older/ready PR first, then create new PRs on top of it.

### Steps to Follow:

#### Step 1: Review Teammate's PR
```bash
# Checkout their branch
git fetch origin
git checkout <their-branch-name>

# Review the changes
git diff main...<their-branch-name>
```

**Key things to check:**
- What files did they modify?
- How does their receipt parsing work?
- What data format do they return?
- Do they have any database changes?

#### Step 2: Merge Their PR
- If approved, merge their PR into `main` (or your base branch)
- Pull the latest `main`:
  ```bash
  git checkout main
  git pull origin main
  ```

#### Step 3: Rebase Your Changes
```bash
# Create/checkout your feature branch
git checkout -b your-feature-branch

# Rebase on top of main (or merge main into your branch)
git rebase main
# OR
git merge main
```

#### Step 4: Resolve Conflicts
**Most likely conflicts:**
- `mobile/screens/HomeScreen.js` - Their parsing vs your photo persistence
- `backend/controllers/ReceiptController.java` - Different endpoints?
- `backend/database/schema.sql` - Table definitions

**Resolution strategy:**
- Keep their OpenAI parsing logic
- Keep your photo persistence (AsyncStorage)
- Keep your database tables (they're already in Railway)
- Keep your new endpoints (`/api/receipts/create`, `/api/receipts/activity`, etc.)
- Ensure their parsing output matches what `BillReview.js` expects

#### Step 5: Test Everything
- Test receipt parsing with their OpenAI implementation
- Test receipt creation and sharing
- Test pending receipts
- Test activity screen
- Test friend requests

#### Step 6: Create Your PR
Once everything works:
```bash
git push origin your-feature-branch
# Create PR on GitHub
```

## Alternative: If Their PR Isn't Ready

If their PR isn't ready to merge (still has issues, needs review, etc.):

1. **Create your PR now** - Document what you've done
2. **Add a note** in your PR description about potential conflicts
3. **Coordinate with teammate** - Let them know you've created a PR
4. **They can merge yours first** - Then rebase their PR on top

## What to Include in Your PR Description

```markdown
## Summary
- Added database persistence for receipts (receipts, receipt_items, receipt_participants tables)
- Implemented receipt creation and sharing with friends
- Added PendingScreen for pending receipts
- Added ProfileScreen to replace duplicate Home screen
- Fixed friend requests to require acceptance (no auto-accept)
- Added ActivityScreen integration with backend
- Added photo persistence in HomeScreen

## Potential Conflicts
- `HomeScreen.js` - Teammate's PR has OpenAI parsing, this PR has photo persistence
- May need to merge teammate's parsing logic with photo persistence

## Testing
- [x] Receipt creation works
- [x] Receipt sharing works
- [x] Pending receipts display
- [x] Accept/decline receipts
- [x] Activity screen shows accepted receipts
- [ ] Need to test with teammate's OpenAI parsing (after merge)
```

## Files Changed (Your PR)

### Backend:
- `backend/database/schema.sql` - Added receipt tables
- `backend/database/ReceiptDAO.java` - New file
- `backend/services/ReceiptService.java` - Updated for database
- `backend/controllers/ReceiptController.java` - New endpoints
- `backend/services/FriendService.java` - Fixed auto-accept
- `backend/controllers/FriendController.java` - Added accept/decline endpoints
- `backend/Server.java` - Registered new endpoints

### Frontend:
- `mobile/screens/PendingScreen.js` - New file
- `mobile/screens/ProfileScreen.js` - New file
- `mobile/screens/ActivityScreen.js` - Updated to fetch real data
- `mobile/screens/HomeScreen.js` - Added photo persistence
- `mobile/screens/BillReview.js` - Connected to backend
- `mobile/services/receiptsService.js` - New file
- `mobile/config.js` - New file
- `mobile/App.js` - Updated navigation
- `mobile/services/authService.js` - Updated API URL
- `mobile/services/friendsService.js` - Updated API URL

## Next Steps

1. **Right now**: Review teammate's PR
2. **If ready**: Merge their PR, then rebase your changes
3. **If not ready**: Create your PR with notes about conflicts
4. **Coordinate**: Talk to teammate about merge order

## Questions to Ask Teammate

1. Is your PR ready to merge?
2. What files did you modify?
3. What data format does your OpenAI parser return?
4. Do you have any database schema changes?
5. Can we coordinate the merge order?
