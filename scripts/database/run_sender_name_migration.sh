#!/bin/bash

# Script to run the sender_name and number_of_items migration for receipts table
# This adds columns to track sender name and item count

set -e

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Running migration: Add 'sender_name' and 'number_of_items' columns to receipts table${NC}"
echo ""

# Check if .env file exists
if [ ! -f .env ]; then
    echo -e "${RED}Error: .env file not found${NC}"
    echo "Please create a .env file with DB_URL, DB_USER, and DB_PASSWORD"
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
# Format: jdbc:mysql://host:port/database
if [[ "$DB_URL" =~ jdbc:mysql://([^:]+):([0-9]+)/(.+) ]]; then
    DB_HOST="${BASH_REMATCH[1]}"
    DB_PORT="${BASH_REMATCH[2]}"
    DB_NAME="${BASH_REMATCH[3]}"
else
    # Fallback: try to extract from non-JDBC format or use defaults
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
echo -e "${YELLOW}User: $DB_USER${NC}"
echo ""

# Confirm before proceeding
read -p "This will add 'sender_name' and 'number_of_items' columns to the receipts table. Continue? (y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Migration cancelled."
    exit 1
fi

# Run the migration
echo ""
echo -e "${YELLOW}Running migration script...${NC}"
mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" < scripts/database/migrate_add_sender_name_and_item_count.sql

if [ $? -eq 0 ]; then
    echo ""
    echo -e "${GREEN}✓ Migration completed successfully!${NC}"
    echo ""
    echo "The 'sender_name' and 'number_of_items' columns have been added to the receipts table."
    echo "Existing receipts have been updated with sender names and item counts."
    echo ""
    echo -e "${YELLOW}Next steps:${NC}"
    echo "1. Restart your backend server"
    echo "2. Test that receipts show correct sender names and item counts"
else
    echo ""
    echo -e "${RED}✗ Migration failed. Please check the error messages above.${NC}"
    exit 1
fi

