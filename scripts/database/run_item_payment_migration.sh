#!/bin/bash

# Script to run the item payment tracking migration
# Adds paid_by and paid_at columns to item_assignments table

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Check if .env file exists
if [ ! -f .env ]; then
    echo -e "${RED}Error: .env file not found${NC}"
    echo "Please create a .env file with DB_URL, DB_USER, and DB_PASSWORD"
    exit 1
fi

# Load environment variables from .env file
export $(grep -v '^#' .env | xargs)

# Check if required variables are set
if [ -z "$DB_URL" ] || [ -z "$DB_USER" ] || [ -z "$DB_PASSWORD" ]; then
    echo -e "${RED}Error: DB_URL, DB_USER, or DB_PASSWORD not set in .env file${NC}"
    exit 1
fi

# Parse JDBC URL to extract host, port, and database name
# Format: jdbc:mysql://host:port/database
if [[ "$DB_URL" =~ jdbc:mysql://([^:]+):([0-9]+)/(.+) ]]; then
    DB_HOST="${BASH_REMATCH[1]}"
    DB_PORT="${BASH_REMATCH[2]}"
    DB_NAME="${BASH_REMATCH[3]}"
else
    # Fallback parsing
    DB_HOST="localhost"
    DB_PORT="3306"
    DB_NAME="${DB_URL##*/}"
    if [ -z "$DB_NAME" ] || [ "$DB_NAME" == "$DB_URL" ]; then
        DB_NAME="checkmate_db"
    fi
fi

echo -e "${YELLOW}Running Item Payment Tracking Migration${NC}"
echo -e "${YELLOW}Database: $DB_NAME${NC}"
echo -e "${YELLOW}Host: $DB_HOST${NC}"
echo -e "${YELLOW}Port: $DB_PORT${NC}"
echo -e "${YELLOW}User: $DB_USER${NC}"
echo ""

# Check if columns already exist
echo -e "${YELLOW}Checking if columns already exist...${NC}"
PAID_BY_EXISTS=$(mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" -sN -e "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='item_assignments' AND COLUMN_NAME='paid_by';" 2>&1 | grep -v "Warning" | grep -v "Using a password")
PAID_AT_EXISTS=$(mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" -sN -e "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='item_assignments' AND COLUMN_NAME='paid_at';" 2>&1 | grep -v "Warning" | grep -v "Using a password")

if [ "$PAID_BY_EXISTS" == "1" ] && [ "$PAID_AT_EXISTS" == "1" ]; then
    echo -e "${GREEN}✓ Columns already exist. Migration not needed.${NC}"
    mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" -e "SHOW COLUMNS FROM item_assignments WHERE Field IN ('paid_by', 'paid_at');" 2>&1 | grep -v "Warning" | grep -v "Using a password"
    exit 0
fi

# Run the migration
echo -e "${YELLOW}Running migration...${NC}"
MIGRATION_OUTPUT=$(mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" < scripts/database/migrate_add_item_payment_tracking.sql 2>&1)
MIGRATION_EXIT_CODE=$?

# Filter out warnings but keep error messages
echo "$MIGRATION_OUTPUT" | grep -v "Warning" | grep -v "Using a password" || true

# Check if migration succeeded or if columns already exist (both are success cases)
if [ $MIGRATION_EXIT_CODE -eq 0 ] || echo "$MIGRATION_OUTPUT" | grep -q "already exists"; then
    echo ""
    echo -e "${GREEN}✓ Migration completed successfully!${NC}"
    echo -e "${GREEN}✓ Columns in item_assignments table:${NC}"
    mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" -e "SHOW COLUMNS FROM item_assignments WHERE Field IN ('paid_by', 'paid_at');" 2>&1 | grep -v "Warning" | grep -v "Using a password"
else
    echo ""
    echo -e "${RED}✗ Error running migration. Please check the error messages above.${NC}"
    exit 1
fi

