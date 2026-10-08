CREATE TABLE ra_ai_parse_audit (
 event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 question_ref CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 trace_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 actor_doctor_id BIGINT NOT NULL,
 action VARCHAR(8) NOT NULL,
 condition_summary VARCHAR(1024) NOT NULL,
 engine VARCHAR(8) NOT NULL,
 parse_status VARCHAR(16) NOT NULL,
 applied BOOLEAN NOT NULL,
 fallback_reason VARCHAR(32) NULL,
 duration_ms BIGINT NOT NULL,
 schema_version VARCHAR(64) NOT NULL,
 rule_version VARCHAR(64) NOT NULL,
 model_version VARCHAR(128) NULL,
 prompt_version VARCHAR(128) NULL,
 created_at_ms BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
