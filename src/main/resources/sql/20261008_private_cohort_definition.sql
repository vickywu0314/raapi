-- 仅新增私有定义与成功回执；生产目标版本和长期留存待接收。
CREATE TABLE ra_private_cohort_definition (
 id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 owner_doctor_id BIGINT NOT NULL,
 study_code VARCHAR(2) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 name VARCHAR(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 note VARCHAR(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL,
 version BIGINT NOT NULL,
 filters_version INT NOT NULL,
 filters_utf8 MEDIUMBLOB NOT NULL,
 filters_sha256 VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 created_at_ms BIGINT NOT NULL,
 updated_at_ms BIGINT NOT NULL,
 deleted_at_ms BIGINT NULL,
 active_name VARBINARY(120) GENERATED ALWAYS AS (CASE WHEN deleted_at_ms IS NULL THEN CAST(name AS BINARY) ELSE NULL END) STORED,
 UNIQUE KEY private_active_name(owner_doctor_id,study_code,active_name),
 KEY private_list(owner_doctor_id,study_code,deleted_at_ms,created_at_ms,id)
) ENGINE=InnoDB;
CREATE TABLE ra_private_cohort_command (
 owner_doctor_id BIGINT NOT NULL,
 action VARCHAR(6) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 protocol_version INT NOT NULL,
 cohort_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 result_version BIGINT NOT NULL,
 request_sha256 VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 trace_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 created_at_ms BIGINT NOT NULL,
 PRIMARY KEY(owner_doctor_id,action,request_id)
) ENGINE=InnoDB;
