CREATE TABLE ra_ai_compare_run (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 owner_doctor_id BIGINT NOT NULL, scope_fingerprint CHAR(64) CHARACTER SET ascii NOT NULL,
 payload_version INT NOT NULL DEFAULT 1,
 payload MEDIUMBLOB NOT NULL, payload_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_at_ms BIGINT NOT NULL, expires_at_ms BIGINT NOT NULL,
 INDEX idx_compare_expiry (expires_at_ms,id)
) ENGINE=InnoDB;
