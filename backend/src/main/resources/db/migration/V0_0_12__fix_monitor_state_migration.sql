-- Fix monitor state migration for cases where the previous migration was partially applied
-- This migration handles the case where the migration was started but checksum mismatch occurred

-- Check if state column exists, if not add it
SET @column_exists = (SELECT COUNT(*)
                      FROM INFORMATION_SCHEMA.COLUMNS
                      WHERE TABLE_NAME = 'monitors'
                      AND COLUMN_NAME = 'state'
                      AND TABLE_SCHEMA = DATABASE());

-- Add the state column if it doesn't exist
SET @sql = IF(@column_exists = 0,
    'ALTER TABLE monitors ADD COLUMN state ENUM(''ACTIVE'', ''SILENT'', ''INACTIVE'') NOT NULL DEFAULT ''ACTIVE''',
    'SELECT "state column already exists"');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Check if is_active column still exists, if so migrate data and drop it
SET @old_column_exists = (SELECT COUNT(*)
                          FROM INFORMATION_SCHEMA.COLUMNS
                          WHERE TABLE_NAME = 'monitors'
                          AND COLUMN_NAME = 'is_active'
                          AND TABLE_SCHEMA = DATABASE());

-- Migrate data if is_active column still exists
SET @migrate_sql = IF(@old_column_exists > 0,
    'UPDATE monitors SET state = CASE WHEN is_active = 1 THEN ''ACTIVE'' ELSE ''INACTIVE'' END',
    'SELECT "is_active column already removed"');

PREPARE stmt2 FROM @migrate_sql;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

-- Drop is_active column if it still exists
SET @drop_sql = IF(@old_column_exists > 0,
    'ALTER TABLE monitors DROP COLUMN is_active',
    'SELECT "is_active column already removed"');

PREPARE stmt3 FROM @drop_sql;
EXECUTE stmt3;
DEALLOCATE PREPARE stmt3;

-- Check if the new index exists, if not add it
SET @new_index_exists = (SELECT COUNT(*)
                         FROM INFORMATION_SCHEMA.STATISTICS
                         WHERE TABLE_NAME = 'monitors'
                         AND INDEX_NAME = 'idx_monitors_state'
                         AND TABLE_SCHEMA = DATABASE());

-- Add index on state column if it doesn't exist
SET @index_sql = IF(@new_index_exists = 0,
    'ALTER TABLE monitors ADD INDEX idx_monitors_state (state)',
    'SELECT "state index already exists"');

PREPARE stmt4 FROM @index_sql;
EXECUTE stmt4;
DEALLOCATE PREPARE stmt4;

-- Check if the old index still exists, if so drop it
SET @old_index_exists = (SELECT COUNT(*)
                         FROM INFORMATION_SCHEMA.STATISTICS
                         WHERE TABLE_NAME = 'monitors'
                         AND INDEX_NAME = 'idx_monitors_active'
                         AND TABLE_SCHEMA = DATABASE());

-- Drop old index if it still exists
SET @drop_index_sql = IF(@old_index_exists > 0,
    'ALTER TABLE monitors DROP INDEX idx_monitors_active',
    'SELECT "old index already removed"');

PREPARE stmt5 FROM @drop_index_sql;
EXECUTE stmt5;
DEALLOCATE PREPARE stmt5;
