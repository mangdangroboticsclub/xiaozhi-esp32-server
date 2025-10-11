-- Fix column names in user_premium_subscription table to match BaseEntity pattern
-- The table was created with created_date/updated_date but entity expects create_date/update_date

USE xiaozhi_esp32_server;

-- Check if we need to rename the columns
SET @old_created_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'user_premium_subscription' 
     AND COLUMN_NAME = 'created_date');

SET @old_updated_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'user_premium_subscription' 
     AND COLUMN_NAME = 'updated_date');

SET @new_create_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'user_premium_subscription' 
     AND COLUMN_NAME = 'create_date');

SET @new_update_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'user_premium_subscription' 
     AND COLUMN_NAME = 'update_date');

-- Rename created_date to create_date if needed
SET @s = (SELECT IF(
    @old_created_exists > 0 AND @new_create_exists = 0,
    "ALTER TABLE user_premium_subscription CHANGE COLUMN created_date create_date datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation date'",
    "SELECT 'created_date column rename not needed'"
));
PREPARE stmt FROM @s;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Rename updated_date to update_date if needed
SET @s = (SELECT IF(
    @old_updated_exists > 0 AND @new_update_exists = 0,
    "ALTER TABLE user_premium_subscription CHANGE COLUMN updated_date update_date datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Record update date'",
    "SELECT 'updated_date column rename not needed'"
));
PREPARE stmt FROM @s;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Verify the changes
SELECT 'Column names fixed to match BaseEntity pattern' as status;

-- Show current table structure
DESCRIBE user_premium_subscription;