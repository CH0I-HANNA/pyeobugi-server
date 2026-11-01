CREATE TABLE device_credentials (
  device_id   CHAR(36) NOT NULL PRIMARY KEY,
  secret_hash CHAR(64) NOT NULL COMMENT 'SHA-256(deviceSecret) 16진수',
  created_at  DATETIME NOT NULL COMMENT 'KST 기준 등록 시각'
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
