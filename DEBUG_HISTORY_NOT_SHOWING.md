# Debug: Receipt 22 Not Showing in History

## Test Results

### ✅ Database Status
- Receipt 22: `complete = 1` (updated successfully)
- Uploader: `4c90de10-d1cf-4e69-9aec-2f6bd9ce6008`
- Participants: 2 users with `status = 'pending'`

### ✅ Backend Query Test
The SQL query **DOES return receipt 22**:
```sql
SELECT r.* FROM receipts r 
WHERE r.uploaded_by = '4c90de10-d1cf-4e69-9aec-2f6bd9ce6008' 
AND r.complete = 1
```
**Result**: Receipt 22 appears ✅

## Possible Issues

### Issue #1: Participant Status
**Problem**: Participants have `status = 'pending'`, not `'accepted'`

**History Query** (`getAllReceiptsForUser`):
```sql
WHERE rp.user_id = ? AND rp.status != 'declined' AND r.complete = TRUE
```

**This should work** - `status != 'declined'` includes both `'pending'` and `'accepted'`.

### Issue #2: Frontend Not Refreshing
**Problem**: Frontend might be showing cached data

**Solution**: 
1. Pull down to refresh on History tab
2. Navigate away and back to History tab
3. Restart the app

### Issue #3: Backend Not Running Latest Code
**Problem**: Backend might be running old compiled code

**Solution**:
```bash
# Stop backend
# Recompile
mvn clean compile

# Restart backend
./scripts/start-backend.sh
```

### Issue #4: User ID Mismatch
**Problem**: Frontend might be using different user ID than expected

**Check**: 
- Verify the user ID in frontend matches the uploader/participant ID
- Check backend logs for the actual user ID being queried

## Debugging Steps

### Step 1: Check Backend Logs
Look for:
```
[ReceiptDAO] STEP C1: getAllReceiptsForUser called for userId: ...
[ReceiptDAO] STEP C8: Added receipt 22 to History (complete = TRUE, total: X)
```

### Step 2: Test API Directly
```bash
# Replace USER_ID with actual user ID
curl "http://YOUR_BACKEND_URL:8080/api/receipts/activity?userId=USER_ID"
```

Should return receipt 22 in the response.

### Step 3: Check Frontend Logs
Look for:
```
[ActivityScreen] STEP 3: Received response from API
[ActivityScreen] receiptsCount: X
```

### Step 4: Verify Database
```sql
SELECT receipt_id, complete, status 
FROM receipts 
WHERE receipt_id = 22;
-- Should show: complete = 1
```

## Quick Test Commands

### Reset receipt 22 to incomplete (for testing)
```bash
mysql -h metro.proxy.rlwy.net -P 28784 -u root -p"$DB_PASSWORD" railway -e "UPDATE receipts SET complete = 0 WHERE receipt_id = 22;"
```

### Set receipt 22 to complete again
```bash
./scripts/database/test_set_receipt_complete.sh
```

### Check if receipt appears in query
```bash
./scripts/database/test_history_query.sh
```

