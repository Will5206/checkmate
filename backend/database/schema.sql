-- CheckMate db schema
-- user accounts


-- -------
-- users table
CREATE TABLE IF NOT EXISTS users (
    user_id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    phone_number VARCHAR(20) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    balance DECIMAL(10, 2) DEFAULT 0.00,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- i'm creating indexes for faster lookups, but since we're not actually
-- going to have a lot of users it's just good practice
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_phone ON users(phone_number);


-- -----
--friends relationship table (for future sprints)
CREATE TABLE IF NOT EXISTS friendships (
    friendship_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id_1 VARCHAR(36) NOT NULL,
    user_id_2 VARCHAR(36) NOT NULL,
    status ENUM('pending', 'accepted', 'declined') DEFAULT 'pending',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id_1) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id_2) REFERENCES users(user_id) ON DELETE CASCADE,
    UNIQUE KEY unique_friendship (user_id_1, user_id_2)
);


--
--
--
--sessions table for login management
CREATE TABLE IF NOT EXISTS sessions (
    session_id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    token VARCHAR(255) UNIQUE NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);


CREATE INDEX idx_sessions_token ON sessions(token);
CREATE INDEX idx_sessions_user_id ON sessions(user_id);

-- -----
-- balance_history table for tracking balance changes over time
CREATE TABLE IF NOT EXISTS balance_history (
    history_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    balance_before DECIMAL(10, 2) NOT NULL,
    balance_after DECIMAL(10, 2) NOT NULL,
    transaction_type ENUM('payment_received', 'payment_sent', 'pot_contribution', 'pot_withdrawal', 'receipt_split', 'refund', 'adjustment', 'other') NOT NULL,
    description VARCHAR(500),
    reference_id VARCHAR(100),
    reference_type VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- indexes for faster lookups
CREATE INDEX idx_balance_history_user_id ON balance_history(user_id);
CREATE INDEX idx_balance_history_created_at ON balance_history(created_at);
CREATE INDEX idx_balance_history_type ON balance_history(transaction_type);
CREATE INDEX idx_balance_history_reference ON balance_history(reference_type, reference_id);