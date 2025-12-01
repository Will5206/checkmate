# Fixes Applied for Receipt Parsing and Item Assignments

## Issues Fixed

### 1. Item Assignments Endpoint Error
**Problem**: Getting "JSON Parse error: Unexpected character: <" when clicking receipts in Activity tab.

**Root Cause**: 
- The endpoint `/api/receipts/items/assignments` was registered but the server may not have been restarted
- Error handling wasn't catching non-JSON responses (404 HTML pages)

**Fixes Applied**:
- Added better error handling in `getItemAssignments()` to detect non-JSON responses
- Added detailed logging in `GetItemAssignmentsHandler` to help debug
- Improved error messages to indicate if server needs restart

**Action Required**: 
- **Restart the backend server** to register the new endpoint:
  ```bash
  lsof -ti:8080 | xargs kill -9
  cd /Users/will5206/checkmate && export $(cat .env | grep -v '^#' | xargs) && mvn exec:java -Dexec.mainClass="Server"
  ```

### 2. Receipt Parsing Taking Forever / Not Working
**Problem**: Receipt parsing hangs and never completes.

**Root Causes**:
1. **Missing OpenAI API Key**: The `openai_key.env` file doesn't exist, causing Python script to fail immediately
2. **Process Deadlock**: Java was reading stdout then stderr sequentially, which can deadlock if Python is waiting
3. **Poor Error Handling**: Errors from Python weren't being properly surfaced

**Fixes Applied**:
1. **Python Script (`receipt_parser_local.py`)**:
   - Now checks both `openai_key.env` and `.env` files for API key
   - Provides clear error message if API key is missing
   - Exits gracefully with JSON error response

2. **Java Controller (`ReceiptController.java`)**:
   - Fixed deadlock by reading stdout and stderr in parallel using threads
   - Better error handling to detect and report API key issues
   - Improved logging to show what's happening at each step
   - Handles empty output and JSON parse errors gracefully

**Action Required**:
- **Create `openai_key.env` file** with your OpenAI API key:
  ```bash
  cd /Users/will5206/checkmate
  echo "OPENAI_API_KEY=sk-your-actual-api-key-here" > openai_key.env
  ```
  
  Or add it to your existing `.env` file:
  ```
  OPENAI_API_KEY=sk-your-actual-api-key-here
  ```

  Get your API key from: https://platform.openai.com/api-keys

## Testing

After applying fixes:

1. **Test Item Assignments**:
   - Restart backend server
   - Click on a receipt in Activity tab
   - Should load without errors
   - Should be able to tap items to claim them

2. **Test Receipt Parsing**:
   - Ensure `openai_key.env` exists with valid API key
   - Try parsing a receipt
   - Check backend logs for detailed progress
   - Should complete in ~15-30 seconds

## Logging Improvements

The backend now logs:
- When receipt parse request is received
- Image size and file save location
- Python process start (with PID)
- Python stdout/stderr in real-time
- Process completion time
- Any errors with detailed messages

Check your backend terminal for these logs to debug issues.
