-- 质控问题处理记录表（projectsData 接口依赖此表，部署前需先在 wenwen 库执行）
-- 某患者的某条规则被标记“已处理”后，该问题不再计入待处理。
-- 数据补齐后规则自然不再命中，即自动关闭，无需写入此表。
CREATE TABLE IF NOT EXISTS `ra_qc_issue_handle` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `patient_id` bigint NOT NULL COMMENT '患者ID，patient_basic_info.id',
  `rule_code` varchar(50) NOT NULL COMMENT '规则编码：M_DAS28/M_BASELINE_LAB/M_COMORBIDITY/M_MEDICATION/L_DATE_ORDER/L_ASSESSMENT',
  `handle_status` tinyint NOT NULL DEFAULT '1' COMMENT '1-已处理 0-撤销处理（重新待处理）',
  `handler_id` bigint DEFAULT NULL COMMENT '处理人（医生ID）',
  `handle_note` varchar(500) DEFAULT NULL COMMENT '处理说明',
  `handle_time` datetime DEFAULT NULL COMMENT '处理时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_patient_rule` (`patient_id`,`rule_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RA 质控问题处理记录';
