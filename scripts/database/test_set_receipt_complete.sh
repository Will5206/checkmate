#!/bin/bash

# Script to set receipt 22 to complete = 1 for testing

set -e

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Setting receipt 22 to complete = 1 for testing${NC}"
echo ""

# Check if .env file exists
if [ ! -f .env ]; then
    echo -e "${RED}Error: .env file not found${NC}"
    exit 1
fi

# Load environment variables
export $(cat .env | grep -v '^#' | xargs)

# Check if required variables are set
if [ -z "$DB_URL" ] || [ -z "$DB_USER" ] || [ -z "$DB_PASSWORD" ]; then
    echo -e "${RED}Error: DB_URL, DB_USER, or DB_PASSWORD not set in .env file${NC}"
    exit 1
fi

# Parse JDBC URL to extract host, port, and database name
if [[ "$DB_URL" =~ jdbc:mysql://([^:]+):([0-9]+)/(.+) ]]; then
    DB_HOST="${BASH_REMATCH[1]}"
    DB_PORT="${BASH_REMATCH[2]}"
    DB_NAME="${BASH_REMATCH[3]}"
else
    DB_HOST="localhost"
    DB_PORT="3306"
    DB_NAME="${DB_URL##*/}"
    if [ -z "$DB_NAME" ] || [ "$DB_NAME" == "$DB_URL" ]; then
        DB_NAME="checkmate_db"
    fi
fi

echo -e "${YELLOW}Database: $DB_NAME${NC}"
echo -e "${YELLOW}Host: $DB_HOST${NC}"
echo -e "${YELLOW}Port: $DB_PORT${NC}"
echo ""

# Show current status
echo -e "${YELLOW}Current status of receipt 22:${NC}"
mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" -e "SELECT receipt_id, status, complete, merchant_name FROM receipts WHERE receipt_id = 22;" 2>&1 | grep -v "Warning" | grep -v "Using a password"

echo ""
echo -e "${YELLOW}Updating receipt 22 to complete = 1...${NC}"
mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" < scripts/database/test_set_receipt_complete.sql 2>&1 | grep -v "Warning" | grep -v "Using a password"

if [ $? -eq 0 ]; then
    echo ""
    echo -e "${GREEN}✓ Receipt 22 updated successfully!${NC}"
    echo ""
    echo -e "${YELLOW}New status:${NC}"
    mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" -e "SELECT receipt_id, status, complete, merchant_name FROM receipts WHERE receipt_id = 22;" 2>&1 | grep -v "Warning" | grep -v "Using a password"
    echo ""
    echo -e "${GREEN}Now check the History tab in your app - receipt 22 should appear there.${NC}"
else
    echo ""
    echo -e "${RED}✗ Update failed. Please check the error messages above.${NC}"
    exit 1
fi

