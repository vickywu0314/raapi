-- 本片通知回执；无旧表/患者外键，无后台恢复。
CREATE TABLE ra_ai_export_request (
 owner_doctor_id BIGINT NOT NULL,
 request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 application_id BIGINT NULL,
 source VARCHAR(7) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 reference_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 purpose_code VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 verified_summary TEXT NOT NULL,
 created_at_ms BIGINT NOT NULL,
 mail_state VARCHAR(7) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 attempt_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 attempt_no INT NOT NULL,
 attempt_started_at_ms BIGINT NULL,
 decided_at_ms BIGINT NULL,
 mail_from VARCHAR(255) NULL,
 last_error VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
 PRIMARY KEY(owner_doctor_id,request_id), UNIQUE(application_id),
 INDEX idx_owner_created(owner_doctor_id,created_at_ms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
