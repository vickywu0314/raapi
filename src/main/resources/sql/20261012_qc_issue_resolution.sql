-- 数据质控：医生把某个质控问题「标记已处理」的记录（wenwen 库执行一次）。
-- 质控问题本身是按当前数据实时算出来的，不落库；这里只存「已处理」，数据改好后问题自动消失，记录保留备查。
-- 新表，不动老表。
CREATE TABLE IF NOT EXISTS `qc_issue_resolution` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `patient_id` bigint NOT NULL COMMENT '患者ID，patient_basic_info.id',
  `issue_key` varchar(120) NOT NULL COMMENT '问题编码，如 missing:M_DAS28、lab:12345:cfydb、conflict:visit-future:12345',
  `doctor_id` bigint NOT NULL COMMENT '处理医生',
  `note` varchar(255) DEFAULT NULL COMMENT '处理说明',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '处理时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_patient_issue` (`patient_id`, `issue_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据质控问题处理记录';
