-- 患者修改记录（详情页「修改记录」读取此表），wenwen 库执行一次。
-- 老数据没有修改记录，详情页显示「暂无修改记录」；此后新建档案、修改档案、新增 / 编辑 / 删除随访、标记脱落等写操作
-- 在同一事务里调用 AuditLogService.record(...) 写入一条。
CREATE TABLE IF NOT EXISTS `patient_audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `patient_id` bigint NOT NULL COMMENT '患者ID，patient_basic_info.id',
  `visit_id` bigint DEFAULT NULL COMMENT '涉及的随访ID，patient_follow_up_history.id；档案类操作为空',
  `action` varchar(20) NOT NULL COMMENT '动作：新建档案 / 修改档案 / 新增随访 / 编辑随访 / 删除随访 / 标记脱落 / 质控处理',
  `detail` varchar(4000) DEFAULT NULL COMMENT '修改内容，页面直接显示；多项用「；」分隔，字段修改写成「字段：旧值 → 新值」',
  `changes` text COMMENT '结构化修改明细 JSON：[{"field":"mobile","label":"手机号","before":"…","after":"…"}]，供以后统计 / 回溯',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人（医生ID，user.id）',
  `operator_name` varchar(100) DEFAULT NULL COMMENT '操作人显示名，如「陈医生（研究者）」',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_patient_time` (`patient_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='患者修改记录';
