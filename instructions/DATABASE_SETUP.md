# Database Setup Guide

## Quick Start

### 1. Set Up Environment Variables

```bash
# Copy the example file to create your .env
cp .env.example .env
```

### 2. Get Railway Credentials

Ask your team lead for the Railway MySQL credentials and add them to your `.env` file:

```bash
DB_URL=jdbc:mysql://[HOST]:[PORT]/railway
DB_USER=root
DB_PASSWORD=[PASSWORD]
```

### 3. Start the Backend Server

```bash
# This will automatically load your .env file and connect to Railway
./start-backend.sh
```

You should see:
```
✓ Loaded environment variables from .env
Database connection established successfully
Database schema initialized successfully
CheckMate Server started on port 8080
```

## Database Architecture

- **Database**: MySQL 8.0 hosted on Railway
- **Tables**: users, sessions, friendships, balance_history, transactions
- **Connection**: Shared across all team members
- **Schema**: Auto-initialized from `backend/database/schema.sql`

## Important Notes

⚠️ **NEVER commit the `.env` file to Git!** It contains passwords and is ignored by `.gitignore`.

✅ **DO commit** `.env.example` so teammates know what variables are needed.

## Viewing the Database

You can connect directly to Railway's MySQL using:

```bash
mysql -h [HOST] -u root -p --port [PORT] railway
# Enter password when prompted
```

Or use MySQL Workbench:
- Host: [from .env]
- Port: [from .env]
- Username: root
- Password: [from .env]

## Troubleshooting

### "Access denied for user 'root'"
- Double-check your password in `.env` matches Railway exactly
- Make sure there are no extra spaces or quotes

### "Failed to connect to database"
- Check that Railway's Public Networking is enabled
- Verify the host and port are correct
- Ensure you're connected to the internet

### "Table already exists" errors
- This is normal! The schema uses `CREATE TABLE IF NOT EXISTS`
- Tables are created once and persist across restarts

## Local Development

If Railway is down or you want to develop offline:

1. Delete or rename your `.env` file
2. The code will fall back to `localhost:3306` with user `root` and password `password`
3. Make sure you have MySQL installed locally: `brew install mysql`
4. Run: `brew services start mysql`

## Files Overview

- `.env` - Your local credentials (NOT in Git)
- `.env.example` - Template showing what credentials are needed (IN Git)
- `start-backend.sh` - Script to load `.env` and start server
- `env/` - Legacy folder (ignored by Git)
