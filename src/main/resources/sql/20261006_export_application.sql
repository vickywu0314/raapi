-- 数据导出申请（超过半年的数据需提交申请），wenwen 库执行一次。
-- 申请提交后发到指定邮箱（application.properties 的 ra.export.apply-mail-to），发邮件功能后续实现，先只记录。
CREATE TABLE IF NOT EXISTS `export_application` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `doctor_id` bigint NOT NULL COMMENT '申请人（医生ID，user.id）',
  `doctor_name` varchar(100) DEFAULT NULL COMMENT '申请人姓名',
  `start_date` date NOT NULL COMMENT '申请导出的随访日期起',
  `end_date` date NOT NULL COMMENT '申请导出的随访日期止',
  `scope` varchar(4000) DEFAULT NULL COMMENT '导出范围 JSON：筛选条件（keyword / followStatus / completeness）或勾选的患者ID',
  `reason` varchar(1000) NOT NULL COMMENT '申请原因 / 用途',
  `mail_to` varchar(255) DEFAULT NULL COMMENT '发送到的邮箱',
  `mail_sent` tinyint NOT NULL DEFAULT 0 COMMENT '邮件是否已发送：0 未发送（发邮件功能待实现）/ 1 已发送',
  `status` varchar(20) NOT NULL DEFAULT 'pending' COMMENT '状态：pending 待审批 / approved 已通过 / rejected 已拒绝',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  PRIMARY KEY (`id`),
  KEY `idx_doctor` (`doctor_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据导出申请';
