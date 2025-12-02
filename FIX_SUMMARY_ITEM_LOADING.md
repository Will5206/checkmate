# Fix Summary: Receipt Items Not Loading in Activity Tab

## Problem
When users clicked on a receipt in the Activity tab, the receipt page would open but itemized items were not displayed, preventing users from claiming items and paying for their portion.

## Root Cause
1. **Backend Bug**: In `ReceiptDAO.java`, items were being added to receipts using `receipt.getItems().addAll()`, but since `Receipt.getItems()` returns a defensive copy (`new ArrayList<>(items)`), the items were being added to a copy that was immediately discarded.

2. **Frontend Bug**: In `BillReview.js`, the `transformReceiptData()` function was not preserving the `itemId` field needed for item claiming functionality.

## Files Changed

### Backend
- **`backend/database/ReceiptDAO.java`**
  - Fixed `getReceiptById()` to use `addItem()` method instead of `getItems().addAll()`
  - Fixed `getPendingReceiptsForUser()` to use `addItem()` method
  - Fixed `getAllReceiptsForUser()` to use `addItem()` method
  - This ensures items are properly loaded into Receipt objects

### Frontend  
- **`mobile/screens/ActivityScreen.js`**
  - Added support for both `quantity` and `qty` fields from backend
  - Added console logging for debugging receipt data flow
  - Improved item data transformation to preserve itemId

- **`mobile/screens/BillReview.js`**
  - Updated `transformReceiptData()` to preserve `itemId` when present
  - Added support for both `quantity` and `qty` fields
  - Added empty state message when no items are found
  - Added console logging for debugging

### Tests
- **`tests/backend/ReceiptDAOItemLoadingTest.java`** (NEW)
  - Comprehensive unit tests for item loading functionality
  - Tests `getReceiptById()` item loading
  - Tests `getAllReceiptsForUser()` item loading
  - Tests `getPendingReceiptsForUser()` item loading
  - Tests edge cases (no items, multiple items)

### Other Changes
- `mobile/config.js` - Modified (part of previous changes)
- `mobile/package-lock.json` - Modified (part of previous changes)

## Testing
Run the new unit test:
```bash
mvn test -Dtest=ReceiptDAOItemLoadingTest
```

## Next Steps
1. Create Jira task for this fix
2. Create branch from Jira task (e.g., `CHEC-XXX-fix-receipt-items-not-loading`)
3. Commit changes to branch
4. Create PR
