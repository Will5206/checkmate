# PR Merge Guide - Receipt Processing Integration

## Overview
This document outlines the changes made and how to handle merging with a teammate's PR that implements actual receipt processing with OpenAI.

## Changes Made in This Session

### Backend Changes:
1. **Database Schema** (`backend/database/schema.sql`)
   - Added `receipts` table
   - Added `receipt_items` table  
   - Added `receipt_participants` table

2. **New Files Created:**
   - `backend/database/ReceiptDAO.java` - Database access for receipts
   - Updated `backend/services/ReceiptService.java` - Now uses database instead of in-memory

3. **New Endpoints:**
   - `POST /api/receipts/create` - Create receipt and share with friends
   - Updated `GET /api/receipts/pending` - Now uses String userId (UUID)
   - Updated `POST /api/receipts/accept` - Now uses String userId
   - Updated `POST /api/receipts/decline` - Now uses String userId

### Frontend Changes:
1. **New Files:**
   - `mobile/screens/PendingScreen.js` - Display pending receipts
   - `mobile/screens/ProfileScreen.js` - User profile screen
   - `mobile/services/receiptsService.js` - API service for receipts
   - `mobile/config.js` - Centralized API URL configuration

2. **Updated Files:**
   - `mobile/screens/BillReview.js` - Connected to backend API
   - `mobile/screens/HomeScreen.js` - Added photo persistence
   - `mobile/App.js` - Added PendingScreen, ProfileScreen routes
   - `mobile/services/authService.js` - Uses config.js for API URL
   - `mobile/services/friendsService.js` - Uses config.js for API URL

## Potential Conflicts with Teammate's PR

### Areas Likely to Conflict:

1. **`mobile/screens/HomeScreen.js`**
   - **Current:** Uses placeholder/simulated receipt parsing
   - **Teammate's PR:** Likely implements actual OpenAI receipt parsing
   - **Resolution:** Merge the OpenAI parsing logic from teammate's PR, but keep:
     - Photo persistence (AsyncStorage)
     - Navigation to BillReview
     - The data format we're using

2. **Receipt Data Format**
   - **Current format we're using:**
     ```javascript
     {
       merchant: 'Demo Store',
       total: 11.0,
       subtotal: 8.75,
       tax: 0.70,
       tip: 1.55,
       items: [
         { name: 'Taco', qty: 2, price: 3.5 },
         { name: 'Soda', qty: 1, price: 1.75 }
       ]
     }
     ```
   - **Action:** Ensure teammate's OpenAI parser returns data in this format, or update `transformReceiptData()` in BillReview.js to handle their format

3. **Backend Receipt Creation**
   - **Current:** `POST /api/receipts/create` endpoint exists and works
   - **Teammate's PR:** May have different endpoint or different data structure
   - **Action:** Review their endpoint and merge/update as needed

4. **Database Tables**
   - **Current:** We added receipt tables to schema.sql
   - **Action:** Ensure teammate's PR doesn't have conflicting table definitions
   - **Note:** Tables are already created in Railway database

## Recommended Merge Strategy

### Step 1: Review Teammate's PR
- Check what files they modified
- Look for:
  - Receipt parsing implementation
  - Any new endpoints
  - Database schema changes
  - Frontend receipt processing flow

### Step 2: Identify Conflicts
- Use `git merge` or `git rebase` to see conflicts
- Pay special attention to:
  - `HomeScreen.js` (parsing logic)
  - `BillReview.js` (data transformation)
  - `backend/controllers/ReceiptController.java` (endpoints)
  - `backend/database/schema.sql` (table definitions)

### Step 3: Merge Strategy
1. **Keep our database changes** - Receipt tables are already in Railway
2. **Keep our new endpoints** - `/api/receipts/create`, updated pending/accept/decline
3. **Merge parsing logic** - Use teammate's OpenAI implementation, but:
   - Keep our photo persistence
   - Ensure output format matches what BillReview expects
4. **Keep our new screens** - PendingScreen, ProfileScreen
5. **Keep our service files** - receiptsService.js, config.js

### Step 4: Testing After Merge
1. Test receipt parsing with actual OpenAI
2. Verify receipt creation still works
3. Test pending receipts display
4. Verify friend requests are pending (not auto-accepted)

## Files to Pay Attention To

### High Conflict Risk:
- `mobile/screens/HomeScreen.js` - Parsing implementation
- `backend/controllers/ReceiptController.java` - May have different endpoints
- `backend/database/schema.sql` - Table definitions

### Low Conflict Risk (but check):
- `mobile/screens/BillReview.js` - Data transformation
- `mobile/services/receiptsService.js` - API calls
- `backend/services/ReceiptService.java` - Service logic

### Safe (unlikely to conflict):
- `mobile/screens/PendingScreen.js` - New file
- `mobile/screens/ProfileScreen.js` - New file
- `backend/database/ReceiptDAO.java` - New file
- `mobile/config.js` - New file

## Current State Summary

✅ **Working:**
- Receipt database tables (in Railway)
- Receipt creation endpoint
- Pending receipts endpoint
- Accept/decline endpoints
- Frontend receipt creation flow
- PendingScreen display
- ProfileScreen

⚠️ **Needs Integration:**
- Actual OpenAI receipt parsing (teammate's PR)
- Friend request approval flow (currently pending, but no UI to accept/decline)

## Next Steps

1. **Before merging:** Review teammate's PR thoroughly
2. **During merge:** Prioritize keeping database structure and new endpoints
3. **After merge:** Test end-to-end flow with real receipt parsing
4. **Consider:** Adding friend request approval UI if teammate hasn't

## Questions to Ask Teammate

1. What format does their OpenAI parser return?
2. Do they have a different receipt creation endpoint?
3. Have they modified the database schema?
4. What's their approach to receipt parsing (sync/async, error handling)?
