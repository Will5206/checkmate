#!/bin/bash

# Quick script to connect to Railway MySQL database
# Usage: ./view-database.sh

echo "Connecting to Railway MySQL database..."
echo "Password is loaded from your .env file"
echo ""

# Load environment variables
if [ -f .env ]; then
    export $(cat .env | grep -v '^#' | xargs)
fi

# Connect to MySQL
mysql -h metro.proxy.rlwy.net \
      -u root \
      -p'pNJrfPcIGAVDDsVNuuUECXWTeFlxRrUq' \
      --port 28784 \
      railway

echo ""
echo "Disconnected from database."
