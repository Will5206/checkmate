#!/bin/bash

# Script to test if the History query returns receipt 22

set -e

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Load environment variables
export $(cat .env | grep -v '^#' | xargs)

# Parse DB connection
if [[ "$DB_URL" =~ jdbc:mysql://([^:]+):([0-9]+)/(.+) ]]; then
    DB_HOST="${BASH_REMATCH[1]}"
    DB_PORT="${BASH_REMATCH[2]}"
    DB_NAME="${BASH_REMATCH[3]}"
fi

echo -e "${YELLOW}Testing History query for receipt 22...${NC}"
echo ""

# Get a user ID that has access to receipt 22 (either uploader or participant)
echo "Checking who has access to receipt 22:"
echo ""

echo "1. Uploader:"
mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" -e "SELECT receipt_id, uploaded_by, complete FROM receipts WHERE receipt_id = 22;" 2>&1 | grep -v "Warning" | grep -v "Using a password"

echo ""
echo "2. Participants:"
mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" -e "SELECT receipt_id, user_id, status FROM receipt_participants WHERE receipt_id = 22;" 2>&1 | grep -v "Warning" | grep -v "Using a password"

echo ""
echo "3. Testing History query (complete = 1) for uploader:"
UPLOADER_ID=$(mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" -sN -e "SELECT uploaded_by FROM receipts WHERE receipt_id = 22;" 2>&1 | grep -v "Warning" | grep -v "Using a password")

if [ ! -z "$UPLOADER_ID" ]; then
    echo "Uploader ID: $UPLOADER_ID"
    echo ""
    echo "Query: SELECT r.* FROM receipts r WHERE r.uploaded_by = '$UPLOADER_ID' AND r.complete = 1"
    mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" -e "SELECT receipt_id, merchant_name, complete, status FROM receipts r WHERE r.uploaded_by = '$UPLOADER_ID' AND r.complete = 1;" 2>&1 | grep -v "Warning" | grep -v "Using a password"
fi

echo ""
echo -e "${GREEN}If receipt 22 appears above, the backend query works. If not, there's a query issue.${NC}"

