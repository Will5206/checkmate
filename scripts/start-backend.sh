#!/bin/bash

# Load environment variables from .env file if it exists
if [ -f .env ]; then
    # Read .env and set variables
    while IFS='=' read -r key value; do
        # Skip comments and empty lines
        [[ $key =~ ^#.*$ ]] && continue
        [[ -z $key ]] && continue
        # Remove any quotes from value
        value=$(echo "$value" | sed -e 's/^"//' -e 's/"$//' -e "s/^'//" -e "s/'$//")
        eval "export $key='$value'"
    done < .env
    echo "✓ Loaded environment variables from .env"
    echo "  DB_URL=$DB_URL"
else
    echo "⚠ No .env file found, using localhost defaults"
fi

# Start the backend server with environment variables passed as system properties
echo "Starting CheckMate backend server..."
mvn exec:java -Dexec.mainClass="Server" \
    -Dexec.systemProperties \
    -DDB_URL="$DB_URL" \
    -DDB_USER="$DB_USER" \
    -DDB_PASSWORD="$DB_PASSWORD"
