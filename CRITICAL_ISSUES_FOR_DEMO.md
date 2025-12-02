# Critical Issues for Receipt Splitting Demo

## 🔴 CRITICAL - BLOCKING DEMO FLOW

### 1. **PAYMENT FUNCTIONALITY COMPLETELY MISSING**
**Location**: `mobile/screens/BillReview.js:372`
- **Problem**: Pay button exists but has NO `onPress` handler - clicking it does nothing
- **Impact**: Users cannot pay for their items, breaking the entire payment flow
- **Fix Required**:
  - Add `onPress` handler to Pay button
  - Create payment API endpoint: `POST /api/receipts/pay`
  - Implement payment processing that:
    - Deducts balance from payer
    - Adds balance to receipt uploader
    - Creates transaction record
    - Marks user as "paid" for this receipt
    - Checks if all participants have paid → mark receipt as "completed"

### 2. **NO PAYMENT API ENDPOINT**
**Location**: `backend/controllers/ReceiptController.java`
- **Problem**: No endpoint exists to process payments
- **Impact**: Cannot complete the payment step of the demo
- **Fix Required**:
  - Create `PayReceiptHandler` class
  - Endpoint: `POST /api/receipts/pay?receiptId=X&userId=Y&amount=Z`
  - Should:
    - Validate user has sufficient balance
    - Deduct from user's balance (BalanceService)
    - Add to uploader's balance
    - Create transaction record (TransactionService)
    - Mark payment status in database
    - Check completion status

### 3. **NO PAYMENT TRACKING IN DATABASE**
**Location**: `backend/database/schema.sql`
- **Problem**: No table/column to track which participants have paid
- **Impact**: Cannot determine when receipt should be marked "completed"
- **Fix Required**:
  - Add `paid_amount` and `paid_at` columns to `receipt_participants` table
  - OR create new `receipt_payments` table
  - Track: `receipt_id`, `user_id`, `amount_paid`, `paid_at`, `status`

### 4. **NO COMPLETION LOGIC**
**Location**: `backend/database/ReceiptDAO.java`, `backend/services/ReceiptService.java`
- **Problem**: No method to check if all participants have paid and mark receipt as "completed"
- **Impact**: Receipts never move to "completed" status
- **Fix Required**:
  - Create method: `checkAndMarkReceiptCompleted(int receiptId)`
  - Logic:
    1. Get all participants for receipt
    2. Calculate each participant's owed amount (from item assignments)
    3. Check if all have paid their full amount
    4. If yes, update receipt status to "completed"
  - Call this after each payment

### 5. **RECEIPT UPLOADER NOT ADDED AS PARTICIPANT**
**Location**: `backend/controllers/ReceiptController.java:543-559`
- **Problem**: When creating receipt, the uploader is not automatically added as a participant
- **Impact**: Uploader cannot claim items or pay for their portion
- **Fix Required**:
  - After creating receipt, add uploader as participant:
  ```java
  receiptDAO.addReceiptParticipant(receipt.getReceiptId(), userIdStr);
  ```

### 6. **NO TRANSACTION CREATION ON PAYMENT**
**Location**: Payment flow (doesn't exist yet)
- **Problem**: When user pays, no transaction record is created
- **Impact**: No audit trail, balance history incomplete
- **Fix Required**:
  - In payment handler, create transaction:
    - `from_user_id`: payer
    - `to_user_id`: receipt uploader
    - `amount`: amount paid
    - `transaction_type`: "receipt_payment"
    - `related_entity_id`: receipt_id
    - `status`: "completed"

### 7. **NO BALANCE UPDATES ON PAYMENT**
**Location**: Payment flow (doesn't exist yet)
- **Problem**: Payment doesn't update user balances
- **Impact**: Balances remain incorrect after payment
- **Fix Required**:
  - Use `BalanceService.subtractFromBalance()` for payer
  - Use `BalanceService.addToBalance()` for receipt uploader
  - Include proper transaction types and descriptions

---

## 🟡 HIGH PRIORITY - BREAKS USER EXPERIENCE

### 8. **ITEM CLAIMING HAS NO VALIDATION**
**Location**: `backend/database/ReceiptDAO.java:119-159`
- **Problem**: Users can claim more items than exist (no quantity validation)
- **Impact**: Can claim quantity 100 of an item that only has quantity 1
- **Fix Required**:
  - Before assigning, check: `assignedQty <= item.getQuantity()`
  - Return error if trying to claim more than available

### 9. **ACTIVITY SCREEN SHOWS ALL RECEIPTS**
**Location**: `backend/database/ReceiptDAO.java:321-354`, `mobile/screens/ActivityScreen.js`
- **Problem**: Activity screen shows pending, accepted, declined, and completed receipts all mixed together
- **Impact**: Confusing UX, can't see which receipts are completed
- **Fix Required**:
  - Filter to show only "accepted" or "completed" receipts
  - OR add status filtering in UI
  - Separate sections: "Active" vs "Completed"

### 10. **NO PAYMENT STATUS IN ACTIVITY VIEW**
**Location**: `mobile/screens/ActivityScreen.js`, `backend/controllers/ReceiptController.java:694-722`
- **Problem**: Activity screen doesn't show payment status (paid/unpaid)
- **Impact**: Users can't see if they've paid or if receipt is completed
- **Fix Required**:
  - Include payment status in `buildReceiptJson()`
  - Show visual indicator: "Paid", "Unpaid", "Completed"
  - Show amount owed vs amount paid

### 11. **PENDING SCREEN ACCEPT BUTTON DOESN'T NAVIGATE**
**Location**: `mobile/screens/PendingScreen.js:196`
- **Problem**: Accepting receipt just removes it from list, doesn't let user claim items
- **Impact**: After accepting, user has to manually navigate to Activity to find receipt
- **Fix Required**:
  - After accept, navigate to BillReview screen with receipt data
  - OR navigate to Activity screen with focus on that receipt

### 12. **NO ERROR HANDLING FOR INSUFFICIENT BALANCE**
**Location**: Payment flow (doesn't exist yet)
- **Problem**: No check if user has enough balance before allowing payment
- **Impact**: Payment will fail silently or cause errors
- **Fix Required**:
  - Check balance before processing payment
  - Return clear error: "Insufficient balance. You have $X, need $Y"

### 13. **CLAIM ITEM DOESN'T RETURN UPDATED OWED AMOUNT**
**Location**: `backend/controllers/ReceiptController.java:802-820`
- **Problem**: After claiming item, response doesn't include updated `owedAmount`
- **Impact**: Frontend can't update the "Amount Owed" display
- **Fix Required**:
  - Calculate and return `owedAmount` in claim item response:
  ```java
  float owedAmount = receiptDAO.calculateUserOwedAmount(receiptId, userIdStr);
  resp.put("owedAmount", owedAmount);
  ```

---

## 🟠 MEDIUM PRIORITY - POLISH ISSUES

### 14. **NO RECEIPT DATE PARSING**
**Location**: `receipt_parser_local.py`, `backend/controllers/ReceiptController.java:499`
- **Problem**: Receipt date from parser is ignored, always uses current date
- **Impact**: All receipts show today's date instead of actual receipt date
- **Fix Required**:
  - Parse date from receipt JSON
  - Pass date to `createReceipt()` method

### 15. **IMAGE NOT SAVED ON RECEIPT CREATION**
**Location**: `backend/controllers/ReceiptController.java:489`
- **Problem**: `imageUrl` is optional and often empty when creating receipt
- **Impact**: Receipts don't have images attached
- **Fix Required**:
  - Save uploaded image to `receipts/` directory
  - Store path in `image_url` column
  - Return image URL in receipt response

### 16. **NO VALIDATION FOR DUPLICATE PARTICIPANTS**
**Location**: `backend/controllers/ReceiptController.java:543-559`
- **Problem**: Same email can be added multiple times
- **Impact**: Duplicate participants in receipt
- **Fix Required**:
  - Check if participant already exists before adding
  - Use `ON DUPLICATE KEY UPDATE` (already in schema, but should validate)

### 17. **QUANTITY HANDLING IN ITEM CLAIMING**
**Location**: `backend/database/ReceiptDAO.java:119-159`
- **Problem**: When claiming items, quantity is set but not validated against item's actual quantity
- **Impact**: Can claim more than available
- **Fix Required**:
  - Validate: `claimedQty <= (item.quantity - alreadyClaimedQty)`
  - Track total claimed quantity per item across all users

### 18. **NO PROPORTIONAL TAX/TIP CALCULATION VALIDATION**
**Location**: `backend/database/ReceiptDAO.java:225-273`
- **Problem**: Tax/tip are calculated proportionally, but no validation that total adds up
- **Impact**: Rounding errors could cause totals to not match
- **Fix Required**:
  - Validate: `sum(all_participant_amounts) == receipt.total_amount`
  - Handle rounding discrepancies (assign remainder to largest payer)

### 19. **MISSING USER ID IN RECEIPT CREATION**
**Location**: `mobile/services/receiptsService.js:15-46`
- **Problem**: `createReceipt()` doesn't include `userId` in request
- **Impact**: Backend might not know who created the receipt
- **Fix Required**:
  - Get userId from AsyncStorage
  - Include in API call: `/api/receipts/create?userId=...`

---

## 🔵 LOW PRIORITY - NICE TO HAVE

### 21. **NO RECEIPT DELETION/CANCELLATION**
- Users can't cancel a receipt if they made a mistake

### 22. **NO NOTIFICATIONS WHEN RECEIPT IS COMPLETED**
- Users aren't notified when all participants have paid

### 23. **NO RECEIPT EDITING**
- Can't modify receipt after creation (items, participants, etc.)

### 24. **NO PARTIAL PAYMENT SUPPORT**
- Must pay full amount, can't make partial payments

### 25. **NO RECEIPT SEARCH/FILTER**
- Can't search receipts by merchant, date, amount

---

## 📋 IMPLEMENTATION PRIORITY FOR DEMO

### MUST FIX (Demo Won't Work):
1. ✅ Add payment button handler (Issue #1)
3. ✅ Create payment API endpoint (Issue #2)
4. ✅ Add payment tracking to database (Issue #3)
5. ✅ Implement completion logic (Issue #4)
6. ✅ Add uploader as participant (Issue #6)
7. ✅ Create transactions on payment (Issue #7)
8. ✅ Update balances on payment (Issue #8)

### SHOULD FIX (Demo Will Be Broken):
8. ✅ Add payment status to Activity screen (Issue #10)
9. ✅ Filter Activity by status (Issue #9)
10. ✅ Return owed amount in claim response (Issue #13)
11. ✅ Add balance validation (Issue #12)

### NICE TO FIX (Demo Will Work But Be Confusing):
12. ✅ Navigate after accepting receipt (Issue #11)
13. ✅ Validate item quantities (Issue #8)
14. ✅ Parse receipt date (Issue #14)

---

## 🎯 DEMO FLOW CHECKLIST

- [ ] User can scan receipt → **WORKS** (after Python deps fix)
- [ ] User can claim items → **WORKS** (but no validation)
- [ ] User can share receipt with friends → **WORKS** (but uploader not added)
- [ ] Friends receive receipt in pending → **WORKS**
- [ ] Friends can accept receipt → **WORKS** (but doesn't navigate)
- [ ] Friends can claim their items → **WORKS** (but no validation)
- [ ] Friends can pay for items → **BROKEN** (no payment functionality)
- [ ] Receipt moves to completed when everyone pays → **BROKEN** (no completion logic)
- [ ] Completed receipts show in Activity → **BROKEN** (no status filtering)

---

## 🚀 QUICK FIX SUMMARY

**Minimum viable demo requires:**
1. Add payment endpoint (2 hours)
2. Add payment tracking column (15 min)
3. Add completion check (1 hour)
4. Wire up Pay button (30 min)
5. Add uploader as participant (5 min)

**Total estimated time: ~4 hours of focused development**

