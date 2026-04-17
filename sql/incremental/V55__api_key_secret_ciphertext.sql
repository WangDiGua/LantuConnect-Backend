START TRANSACTION;

SET @exist := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_api_key'
      AND COLUMN_NAME = 'secret_ciphertext'
);

SET @sql := IF(
    @exist = 0,
    'ALTER TABLE t_api_key ADD COLUMN secret_ciphertext VARCHAR(1024) NULL COMMENT ''AES-GCM encrypted full plaintext API key for later detail reveal'' AFTER key_hash',
    'SELECT 1'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

COMMIT;
