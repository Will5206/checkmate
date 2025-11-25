#!/bin/bash

# Quick database inspection script
# Shows common database information without entering MySQL shell

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📊 CheckMate Database Status"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

DB_CMD="mysql -h metro.proxy.rlwy.net -u root -p'pNJrfPcIGAVDDsVNuuUECXWTeFlxRrUq' --port 28784 railway"

echo "📋 All Tables:"
$DB_CMD -e "SHOW TABLES;" 2>/dev/null
echo ""

echo "👥 Users (Total: $($DB_CMD -sN -e 'SELECT COUNT(*) FROM users;' 2>/dev/null)):"
$DB_CMD -e "SELECT user_id, name, email, phone_number, balance, created_at FROM users;" 2>/dev/null
echo ""

echo "🔐 Active Sessions (Total: $($DB_CMD -sN -e 'SELECT COUNT(*) FROM sessions;' 2>/dev/null)):"
$DB_CMD -e "SELECT session_id, user_id, LEFT(token, 20) as token_preview, expires_at FROM sessions LIMIT 5;" 2>/dev/null
echo ""

echo "👫 Friendships (Total: $($DB_CMD -sN -e 'SELECT COUNT(*) FROM friendships;' 2>/dev/null)):"
$DB_CMD -e "SELECT * FROM friendships LIMIT 5;" 2>/dev/null
echo ""

echo "💰 Recent Balance Changes (Last 5):"
$DB_CMD -e "SELECT history_id, user_id, amount, balance_before, balance_after, transaction_type, created_at FROM balance_history ORDER BY created_at DESC LIMIT 5;" 2>/dev/null
echo ""

echo "💸 Recent Transactions (Last 5):"
$DB_CMD -e "SELECT transaction_id, from_user_id, to_user_id, amount, transaction_type, status, created_at FROM transactions ORDER BY created_at DESC LIMIT 5;" 2>/dev/null
echo ""

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Database check complete!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
