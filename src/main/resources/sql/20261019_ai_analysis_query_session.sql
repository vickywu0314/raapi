-- MySQL 5.7兼容；仅新增会话表。旧ra_ai_analysis_run保留，应用不再读写。
-- query_json为规范筛选，最多128KiB，5000个合法ID不受TEXT 64KiB边界截断。
CREATE TABLE IF NOT EXISTS ra_ai_analysis_query_session (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 owner_doctor_id BIGINT NOT NULL,
 scope_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 sort_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 query_version INT NOT NULL,
 query_json MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 as_of_date CHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 policy_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 created_at_ms BIGINT NOT NULL,
 expires_at_ms BIGINT NOT NULL,
 PRIMARY KEY(id),KEY idx_analysis_query_expiry(expires_at_ms,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
