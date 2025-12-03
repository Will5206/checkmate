#!/bin/bash

# Script to run the requested_by migration for friendships table
# This adds a column to track who sent the friend request

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Running requested_by migration for friendships table...${NC}"

# Check if .env file exists
if [ ! -f .env ]; then
    echo -e "${RED}Error: .env file not found${NC}"
    exit 1
fi

# Load environment variables
source .env

# Check if DB_URL is set
if [ -z "$DB_URL" ]; then
    echo -e "${RED}Error: DB_URL not found in .env file${NC}"
    exit 1
fi

# Parse JDBC URL to extract connection details
# Format: jdbc:mysql://host:port/database?params
if [[ $DB_URL == jdbc:mysql://* ]]; then
    # Remove jdbc:mysql:// prefix
    DB_CONN_STRING=${DB_URL#jdbc:mysql://}
    
    # Extract host, port, database, and params
    # Split by / to get host:port and database?params
    IFS='/' read -ra PARTS <<< "$DB_CONN_STRING"
    HOST_PORT=${PARTS[0]}
    DB_AND_PARAMS=${PARTS[1]}
    
    # Split host:port
    IFS=':' read -ra HOST_PARTS <<< "$HOST_PORT"
    DB_HOST=${HOST_PARTS[0]}
    DB_PORT=${HOST_PARTS[1]:-3306}
    
    # Split database?params
    IFS='?' read -ra DB_PARTS <<< "$DB_AND_PARAMS"
    DB_NAME=${DB_PARTS[0]}
    
    # Extract username and password from params if they exist
    # Or use DB_USER and DB_PASSWORD from .env
    DB_USER=${DB_USER:-root}
    DB_PASSWORD=${DB_PASSWORD:-}
    
    echo -e "${GREEN}Connecting to database:${NC}"
    echo -e "  Host: $DB_HOST"
    echo -e "  Port: $DB_PORT"
    echo -e "  Database: $DB_NAME"
    echo -e "  User: $DB_USER"
    echo ""
    
    # Prompt for confirmation
    read -p "Do you want to proceed with the migration? (y/n): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo -e "${YELLOW}Migration cancelled${NC}"
        exit 0
    fi
    
    # Run the migration
    if [ -z "$DB_PASSWORD" ]; then
        mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" "$DB_NAME" < scripts/database/migrate_add_requested_by.sql
    else
        mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" < scripts/database/migrate_add_requested_by.sql
    fi
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}Migration completed successfully!${NC}"
    else
        echo -e "${RED}Migration failed${NC}"
        exit 1
    fi
else
    echo -e "${RED}Error: Invalid DB_URL format. Expected jdbc:mysql://...${NC}"
    exit 1
fi

