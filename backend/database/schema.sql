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
-- friends relationship table (for future sprints)
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
-- sessions table for login management
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

-- -----
-- transactions table for tracking all financial transactions between users
CREATE TABLE IF NOT EXISTS transactions (
    transaction_id INT AUTO_INCREMENT PRIMARY KEY,
    from_user_id VARCHAR(36) NOT NULL,
    to_user_id VARCHAR(36),
    amount DECIMAL(10, 2) NOT NULL,
    transaction_type ENUM('receipt_payment', 'pot_contribution', 'pot_withdrawal', 'peer_to_peer', 'refund', 'other') NOT NULL,
    description VARCHAR(500),
    status ENUM('pending', 'completed', 'failed', 'cancelled') DEFAULT 'pending',
    related_entity_id VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (from_user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (to_user_id) REFERENCES users(user_id) ON DELETE SET NULL,
    CHECK (amount > 0)
);

-- indexes for faster lookups
CREATE INDEX idx_transactions_from_user ON transactions(from_user_id);
CREATE INDEX idx_transactions_to_user ON transactions(to_user_id);
CREATE INDEX idx_transactions_status ON transactions(status);
CREATE INDEX idx_transactions_type ON transactions(transaction_type);
CREATE INDEX idx_transactions_created_at ON transactions(created_at);
CREATE INDEX idx_transactions_related_entity ON transactions(related_entity_id);

-- -----
-- receipts table for storing receipt information
CREATE TABLE IF NOT EXISTS receipts (
    receipt_id INT AUTO_INCREMENT PRIMARY KEY,
    uploaded_by VARCHAR(36) NOT NULL,
    merchant_name VARCHAR(255),
    date TIMESTAMP,
    total_amount DECIMAL(10, 2) NOT NULL,
    tip_amount DECIMAL(10, 2) DEFAULT 0.00,
    tax_amount DECIMAL(10, 2) DEFAULT 0.00,
    image_url VARCHAR(500),
    status ENUM('pending', 'accepted', 'declined', 'completed') DEFAULT 'pending',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (uploaded_by) REFERENCES users(user_id) ON DELETE CASCADE
);

-- indexes for receipts
CREATE INDEX idx_receipts_uploaded_by ON receipts(uploaded_by);
CREATE INDEX idx_receipts_status ON receipts(status);
CREATE INDEX idx_receipts_created_at ON receipts(created_at);

-- -----
-- receipt_items table for storing individual items on a receipt
CREATE TABLE IF NOT EXISTS receipt_items (
    item_id INT AUTO_INCREMENT PRIMARY KEY,
    receipt_id INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    quantity INT DEFAULT 1,
    category VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (receipt_id) REFERENCES receipts(receipt_id) ON DELETE CASCADE
);

-- indexes for receipt_items
CREATE INDEX idx_receipt_items_receipt_id ON receipt_items(receipt_id);

-- -----
-- receipt_participants table for tracking who a receipt was sent to and their status
CREATE TABLE IF NOT EXISTS receipt_participants (
    participant_id INT AUTO_INCREMENT PRIMARY KEY,
    receipt_id INT NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    status ENUM('pending', 'accepted', 'declined') DEFAULT 'pending',
    paid_amount DECIMAL(10, 2) DEFAULT 0.00,
    paid_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (receipt_id) REFERENCES receipts(receipt_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    UNIQUE KEY unique_receipt_participant (receipt_id, user_id)
);

-- indexes for receipt_participants
CREATE INDEX idx_receipt_participants_receipt_id ON receipt_participants(receipt_id);
CREATE INDEX idx_receipt_participants_user_id ON receipt_participants(user_id);
CREATE INDEX idx_receipt_participants_status ON receipt_participants(status);

-- -----
-- item_assignments table for tracking which items belong to which users
CREATE TABLE IF NOT EXISTS item_assignments (
    assignment_id INT AUTO_INCREMENT PRIMARY KEY,
    receipt_id INT NOT NULL,
    item_id INT NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    quantity INT DEFAULT 1,
    paid_by VARCHAR(36) NULL,
    paid_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (receipt_id) REFERENCES receipts(receipt_id) ON DELETE CASCADE,
    FOREIGN KEY (item_id) REFERENCES receipt_items(item_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (paid_by) REFERENCES users(user_id) ON DELETE SET NULL,
    UNIQUE KEY unique_item_user (item_id, user_id)
);
CREATE INDEX idx_item_assignments_receipt_id ON item_assignments(receipt_id);
CREATE INDEX idx_item_assignments_item_id ON item_assignments(item_id);
CREATE INDEX idx_item_assignments_user_id ON item_assignments(user_id);