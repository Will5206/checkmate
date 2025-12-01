#!/bin/bash

# Load environment variables from .env file if it exists
if [ -f .env ]; then
    export $(cat .env | grep -v '^#' | xargs)
    echo "✓ Loaded environment variables from .env"
else
    echo "⚠ No .env file found, using localhost defaults"
fi

# Start the backend server
echo "Starting CheckMate backend server..."
mvn exec:java -Dexec.mainClass="Server"
