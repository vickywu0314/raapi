CREATE TABLE IF NOT EXISTS ra_ai_chat_audit (
 event_ref CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 trace_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
 doctor_id BIGINT NOT NULL,
 scope_kind VARCHAR(16) NOT NULL,
 engine VARCHAR(8) NOT NULL,
 outcome VARCHAR(32) NOT NULL,
 model_version VARCHAR(64) NOT NULL,
 prompt_version VARCHAR(64) NOT NULL,
 elapsed_ms BIGINT NOT NULL,
 created_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
