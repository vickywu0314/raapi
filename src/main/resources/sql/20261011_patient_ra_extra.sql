-- 患者档案里 RA 研究库新增、老表没有的字段（wenwen 库执行一次）。
-- 来源：《RA 数据库字段》思维导图「基本信息」最后两项：诊断结果告知、患者知情同意书签订。
-- 老表 patient_basic_info 不改，按患者一行存；新建患者 / 编辑档案写入，患者详情读取（表不存在时详情页按未填写显示）。
CREATE TABLE IF NOT EXISTS `patient_ra_extra` (
  `patient_id` bigint NOT NULL COMMENT '患者ID，patient_basic_info.id',
  `diagnosis_informed` tinyint DEFAULT NULL COMMENT '诊断结果告知：1 已告知 / 0 未告知 / NULL 未填',
  `consent_signed` tinyint DEFAULT NULL COMMENT '患者知情同意书签订：1 已签订 / 0 未签订 / NULL 未填',
  `update_doctor_id` bigint DEFAULT NULL COMMENT '最后修改医生',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`patient_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='患者档案 RA 扩展字段（诊断结果告知、知情同意书）';
