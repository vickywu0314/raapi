CREATE TABLE ra_ai_analysis_run (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 owner_doctor_id BIGINT NOT NULL, scope_fingerprint CHAR(64) CHARACTER SET ascii NOT NULL,
 sort_key VARCHAR(80) CHARACTER SET ascii NOT NULL, payload_version INT NOT NULL,
 payload MEDIUMBLOB NOT NULL, payload_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_at_ms BIGINT NOT NULL, expires_at_ms BIGINT NOT NULL,
 INDEX idx_analysis_expiry (expires_at_ms,id)
) ENGINE=InnoDB;
