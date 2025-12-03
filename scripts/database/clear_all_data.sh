#!/bin/bash

# Script to clear all test data from CheckMate database
# This will delete ALL data from all tables - use with caution!

# Load environment variables from .env file if it exists
if [ -f .env ]; then
    while IFS='=' read -r key value; do
        [[ $key =~ ^#.*$ ]] && continue
        [[ -z $key ]] && continue
        value=$(echo "$value" | sed -e 's/^"//' -e 's/"$//' -e "s/^'//" -e "s/'$//")
        eval "export $key='$value'"
    done < .env
fi

# Extract database connection info from DB_URL
# Format: jdbc:mysql://host:port/database
DB_URL=${DB_URL:-"jdbc:mysql://localhost:3306/checkmate_db"}
DB_USER=${DB_USER:-"root"}
DB_PASSWORD=${DB_PASSWORD:-"password"}

# Parse DB_URL to extract host, port, and database name
if [[ $DB_URL =~ jdbc:mysql://([^:]+):([0-9]+)/(.+) ]]; then
    DB_HOST="${BASH_REMATCH[1]}"
    DB_PORT="${BASH_REMATCH[2]}"
    DB_NAME="${BASH_REMATCH[3]}"
else
    echo "Error: Could not parse DB_URL: $DB_URL"
    echo "Expected format: jdbc:mysql://host:port/database"
    exit 1
fi

echo "=========================================="
echo "WARNING: This will delete:"
echo "  - All receipts and receipt items"
echo "  - All transactions and balance history"
echo "  - All item assignments and participants"
echo "  - All sessions"
echo "  - All friendships"
echo ""
echo "KEEPING:"
echo "  - All user accounts ONLY"
echo ""
echo "Database: $DB_NAME"
echo "Host: $DB_HOST:$DB_PORT"
echo "=========================================="
echo ""
read -p "Are you sure you want to continue? (yes/no): " confirm

if [ "$confirm" != "yes" ]; then
    echo "Cancelled. No data was deleted."
    exit 0
fi

echo ""
echo "Clearing all data from database..."

# Run the SQL script
mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" < "$(dirname "$0")/clear_all_data.sql"

if [ $? -eq 0 ]; then
    echo ""
    echo "✓ Successfully cleared all data!"
    echo "Only user accounts have been preserved."
    echo "All other tables are now empty and AUTO_INCREMENT counters have been reset."
else
    echo ""
    echo "✗ Error clearing database. Please check the error messages above."
    exit 1
fi

