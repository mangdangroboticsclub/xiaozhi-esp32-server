-- ============================================================================
-- Complete Database Setup for xiaozhi-esp32-server
-- This script applies ALL necessary database changes for the new features:
-- 1. Chat limit tracking and auto-disable
-- 2. Premium account management
-- 3. Shopify payment integration
-- ============================================================================

USE xiaozhi_esp32_server;

-- ============================================================================
-- PART 1: Chat Limit Feature
-- ============================================================================

-- Add auto_disabled_reason column for chat limit tracking
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
                   WHERE TABLE_SCHEMA = DATABASE() 
                   AND TABLE_NAME = 'sys_user' 
                   AND COLUMN_NAME = 'auto_disabled_reason');

SET @sql = IF(@col_exists = 0, 
    'ALTER TABLE sys_user ADD COLUMN auto_disabled_reason VARCHAR(100) NULL COMMENT ''自动禁用原因，用于区分手动禁用和自动禁用的账户''',
    'SELECT ''Column auto_disabled_reason already exists'' AS message');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add chat_count_month column for monthly chat tracking
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
                   WHERE TABLE_SCHEMA = DATABASE() 
                   AND TABLE_NAME = 'sys_user' 
                   AND COLUMN_NAME = 'chat_count_month');

SET @sql = IF(@col_exists = 0, 
    'ALTER TABLE sys_user ADD COLUMN chat_count_month INT DEFAULT 0 COMMENT ''当月聊天数量''',
    'SELECT ''Column chat_count_month already exists'' AS message');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add last_reset_month column for tracking monthly reset
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
                   WHERE TABLE_SCHEMA = DATABASE() 
                   AND TABLE_NAME = 'sys_user' 
                   AND COLUMN_NAME = 'last_reset_month');

SET @sql = IF(@col_exists = 0, 
    'ALTER TABLE sys_user ADD COLUMN last_reset_month VARCHAR(7) NULL COMMENT ''上次重置月份 (YYYY-MM)''',
    'SELECT ''Column last_reset_month already exists'' AS message');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Insert max_chat_count system parameter
INSERT INTO sys_params (id, param_code, param_value, param_type, remark, creator, create_date, updater, update_date)
SELECT 
    (SELECT COALESCE(MAX(id), 0) + 1 FROM sys_params), 
    'server.max_chat_count', 
    '1000', 
    0, 
    '每月最大聊天次数限制，超过此数量的非超级管理员账户将被自动禁用', 
    1, 
    NOW(), 
    1, 
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_params WHERE param_code = 'server.max_chat_count'
);

SELECT 'Step 1/3: Chat limit feature added successfully' AS status;

-- ============================================================================
-- PART 2: Premium Account Feature
-- ============================================================================

-- Add is_premium column
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'sys_user' 
     AND COLUMN_NAME = 'is_premium');

SET @sql = IF(@col_exists = 0,
    "ALTER TABLE sys_user ADD COLUMN is_premium tinyint(1) DEFAULT 0 COMMENT 'Premium status: 0=regular, 1=premium'",
    "SELECT 'is_premium column already exists'");

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add premium_expires_at column
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'sys_user' 
     AND COLUMN_NAME = 'premium_expires_at');

SET @sql = IF(@col_exists = 0,
    "ALTER TABLE sys_user ADD COLUMN premium_expires_at datetime NULL COMMENT 'Premium expiry date, NULL if not premium'",
    "SELECT 'premium_expires_at column already exists'");

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add premium_last_check column
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'sys_user' 
     AND COLUMN_NAME = 'premium_last_check');

SET @sql = IF(@col_exists = 0,
    "ALTER TABLE sys_user ADD COLUMN premium_last_check datetime NULL COMMENT 'Last premium status check timestamp'",
    "SELECT 'premium_last_check column already exists'");

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Create index for premium status queries (ignore error if exists)
SET @index_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
                     WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'sys_user'
                     AND INDEX_NAME = 'idx_premium_status');

SET @sql = IF(@index_exists = 0,
    "CREATE INDEX idx_premium_status ON sys_user(is_premium, premium_expires_at)",
    "SELECT 'Index idx_premium_status already exists'");

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT 'Step 2/3: Premium account columns added successfully' AS status;

-- ============================================================================
-- PART 3: Premium Subscription Table (with corrected column names)
-- ============================================================================

-- Create the premium subscription table with correct column names (create_date/update_date)
CREATE TABLE IF NOT EXISTS user_premium_subscription (
    id bigint NOT NULL AUTO_INCREMENT COMMENT 'Subscription ID',
    user_id bigint NOT NULL COMMENT 'User ID from sys_user table',
    subscription_type varchar(50) NOT NULL DEFAULT 'MONTHLY' COMMENT 'Subscription type: MONTHLY, YEARLY',
    payment_amount decimal(10,2) NOT NULL COMMENT 'Payment amount',
    payment_currency varchar(10) NOT NULL DEFAULT 'USD' COMMENT 'Payment currency',
    payment_method varchar(50) COMMENT 'Payment method: SHOPIFY',
    payment_transaction_id varchar(255) COMMENT 'External payment transaction ID',
    subscription_start_date datetime NOT NULL COMMENT 'Subscription start date',
    subscription_end_date datetime NOT NULL COMMENT 'Subscription end date',
    status varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status: ACTIVE, EXPIRED, CANCELLED, REFUNDED',
    auto_renew tinyint(1) DEFAULT 0 COMMENT 'Auto renewal enabled: 0=no, 1=yes',
    creator bigint COMMENT 'Creator user ID',
    create_date datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation date',
    updater bigint COMMENT 'Updater user ID',
    update_date datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Record update date',
    PRIMARY KEY (id),
    INDEX idx_user_id (user_id),
    INDEX idx_subscription_dates (subscription_start_date, subscription_end_date),
    INDEX idx_status (status),
    INDEX idx_payment_transaction (payment_transaction_id),
    CONSTRAINT fk_premium_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User premium subscription records';

-- Fix column names if table was created with wrong names
SET @old_created_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'user_premium_subscription' 
     AND COLUMN_NAME = 'created_date');

SET @sql = IF(@old_created_exists > 0,
    "ALTER TABLE user_premium_subscription CHANGE COLUMN created_date create_date datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation date'",
    "SELECT 'Column name already correct (create_date)'");

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @old_updated_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'user_premium_subscription' 
     AND COLUMN_NAME = 'updated_date');

SET @sql = IF(@old_updated_exists > 0,
    "ALTER TABLE user_premium_subscription CHANGE COLUMN updated_date update_date datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Record update date'",
    "SELECT 'Column name already correct (update_date)'");

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT 'Step 3/3: Premium subscription table created successfully' AS status;

-- ============================================================================
-- Verification
-- ============================================================================

SELECT '========================================' AS divider;
SELECT 'DATABASE SETUP COMPLETED SUCCESSFULLY!' AS status;
SELECT '========================================' AS divider;

-- Show sys_user table structure
SELECT 'Verifying sys_user table columns:' AS info;
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 'sys_user'
  AND COLUMN_NAME IN ('auto_disabled_reason', 'chat_count_month', 'last_reset_month', 
                      'is_premium', 'premium_expires_at', 'premium_last_check')
ORDER BY ORDINAL_POSITION;

-- Show user_premium_subscription table structure
SELECT '' AS divider;
SELECT 'Verifying user_premium_subscription table:' AS info;
DESCRIBE user_premium_subscription;

-- Show system parameters
SELECT '' AS divider;
SELECT 'Verifying system parameters:' AS info;
SELECT param_code, param_value, remark 
FROM sys_params 
WHERE param_code = 'server.max_chat_count';
