# Frontend Code: Pending vs History Receipt Display

## Answer: The Frontend Does NOT Filter by `complete` Status

**Important**: The frontend code does **NOT** contain logic to filter receipts by `complete` status. Instead, it relies on **different API endpoints** that the backend filters appropriately.

---

## Frontend Flow

### 1. PENDING SCREEN - Shows Receipts with `complete = FALSE`

**File**: `mobile/screens/PendingScreen.js`

**Line 35-60**: `loadPendingReceipts()` function
```javascript
const loadPendingReceipts = async () => {
  setIsLoading(true);
  try {
    const response = await getPendingReceipts();  // ← Calls service
    if (response.success) {
      const receiptsList = response.receipts || [];
      setReceipts(receiptsList);  // ← Just displays what backend returns
    }
  } catch (error) {
    // error handling
  }
};
```

**File**: `mobile/services/receiptsService.js`

**Line 52-83**: `getPendingReceipts()` function
```javascript
export async function getPendingReceipts() {
  const userId = await AsyncStorage.getItem('userId');
  const response = await fetch(
    `${API_BASE_URL}/receipts/pending?userId=${encodeURIComponent(userId)}`,  // ← API endpoint
    {
      method: 'GET',
      headers: { 'Content-Type': 'application/json' },
    }
  );
  const data = await response.json();
  return data;  // ← Returns whatever backend sends
}
```

**Key Point**: Frontend just calls `/api/receipts/pending` and displays the response. **No filtering logic in frontend.**

---

### 2. HISTORY SCREEN - Shows Receipts with `complete = TRUE`

**File**: `mobile/screens/ActivityScreen.js`

**Line 25-58**: `loadReceipts()` function
```javascript
const loadReceipts = async () => {
  try {
    setLoading(true);
    const response = await getActivityReceipts();  // ← Calls service
    if (response.success && response.receipts) {
      setReceipts(response.receipts);  // ← Just displays what backend returns
    }
  } catch (error) {
    // error handling
  }
};
```

**File**: `mobile/services/receiptsService.js`

**Line 166-207**: `getActivityReceipts()` function
```javascript
export async function getActivityReceipts() {
  const userId = await AsyncStorage.getItem('userId');
  const url = `${API_BASE_URL}/receipts/activity?userId=${encodeURIComponent(userId)}`;  // ← Different API endpoint
  const response = await fetch(url, {
    method: 'GET',
    headers: { 'Content-Type': 'application/json' },
  });
  const data = await response.json();
  return data;  // ← Returns whatever backend sends
}
```

**Key Point**: Frontend just calls `/api/receipts/activity` and displays the response. **No filtering logic in frontend.**

---

## Where the Filtering Actually Happens: BACKEND

### Backend Filters by `complete` Status

**File**: `backend/database/ReceiptDAO.java`

#### For Pending (complete = FALSE):
**Line 738-779**: `getPendingReceiptsForUser()`
```java
String sql = "SELECT DISTINCT r.* FROM (" +
             "  SELECT r.* FROM receipts r WHERE r.uploaded_by = ? AND r.complete = FALSE " +  // ← Filters by complete = FALSE
             "  UNION " +
             "  SELECT r.* FROM receipts r " +
             "  INNER JOIN receipt_participants rp ON r.receipt_id = rp.receipt_id " +
             "  WHERE rp.user_id = ? AND rp.status IN ('pending', 'accepted') AND r.complete = FALSE" +  // ← Filters by complete = FALSE
             ") AS r " +
             "ORDER BY r.created_at DESC";
```

#### For History (complete = TRUE):
**Line 789-849**: `getAllReceiptsForUser()`
```java
String sql = "SELECT DISTINCT r.* FROM (" +
             "  SELECT r.* FROM receipts r WHERE r.uploaded_by = ? AND r.complete = TRUE " +  // ← Filters by complete = TRUE
             "  UNION " +
             "  SELECT r.* FROM receipts r " +
             "  INNER JOIN receipt_participants rp ON r.receipt_id = rp.receipt_id " +
             "  WHERE rp.user_id = ? AND rp.status != 'declined' AND r.complete = TRUE" +  // ← Filters by complete = TRUE
             ") AS r " +
             "ORDER BY r.created_at DESC";
```

---

## Summary

### Frontend Code Locations:

1. **Pending Screen**: 
   - `mobile/screens/PendingScreen.js` (Line 35-60)
   - Calls `getPendingReceipts()` from service
   - **No filtering logic** - just displays API response

2. **History Screen**: 
   - `mobile/screens/ActivityScreen.js` (Line 25-58)
   - Calls `getActivityReceipts()` from service
   - **No filtering logic** - just displays API response

3. **Service Layer**:
   - `mobile/services/receiptsService.js`
   - `getPendingReceipts()` → Calls `/api/receipts/pending` (Line 52-83)
   - `getActivityReceipts()` → Calls `/api/receipts/activity` (Line 166-207)
   - **No filtering logic** - just makes API calls

### Backend Code Locations (Where Filtering Happens):

1. **Pending Query**: 
   - `backend/database/ReceiptDAO.java:738` - `getPendingReceiptsForUser()`
   - Filters: `WHERE r.complete = FALSE`

2. **History Query**: 
   - `backend/database/ReceiptDAO.java:789` - `getAllReceiptsForUser()`
   - Filters: `WHERE r.complete = TRUE`

---

## Answer to Teacher's Question

**Q**: "Where is the frontend code that only displays receipts in pending if they are complete versus in history when complete?"

**A**: **There is NO frontend code that filters by `complete` status.** 

The frontend:
- Uses **different API endpoints** (`/receipts/pending` vs `/receipts/activity`)
- Simply **displays whatever the backend returns**
- Relies on the **backend to filter** by `complete` status

The filtering happens in the **backend**:
- `getPendingReceiptsForUser()` filters by `complete = FALSE`
- `getAllReceiptsForUser()` filters by `complete = TRUE`

This is a **separation of concerns** design pattern:
- **Frontend**: Handles UI/display
- **Backend**: Handles business logic and data filtering

