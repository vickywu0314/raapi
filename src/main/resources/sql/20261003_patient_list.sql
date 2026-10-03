-- 患者列表（/api/ra/patient/patientsList）依赖的表结构改动，wenwen 库执行一次。
-- 执行后再跑 20261003_backfill_study_no.sql 给老患者补研究编号。

-- 1. 患者表：研究编号、随访周期
--    follow_cycle 为 NOT NULL DEFAULT 12，老患者加字段时自动为 12，无需另行补数据。
ALTER TABLE `patient_basic_info`
  ADD COLUMN `study_no` varchar(32) DEFAULT NULL COMMENT '研究编号：病种字母-建档日期yyyyMMdd-5位序号，如 RA-20261003-00001；序号用完后由高位起以字母替换数字位',
  ADD COLUMN `follow_cycle` int NOT NULL DEFAULT 12 COMMENT '随访周期（月）：3 / 6 / 12 / 24，默认 12',
  ADD UNIQUE KEY `uk_study_no` (`study_no`);

-- 2. 患者-常见相关疾病 关联表（列表「其他病史」列读取此表）
--    数据来源：医生在新建患者 / 编辑档案中勾选（source=manual），
--    或按身份证号在其它病种表中匹配到同一患者（source=auto，见 docs/TODO.md）。
CREATE TABLE IF NOT EXISTS `patient_comorbidity` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `patient_id` bigint NOT NULL COMMENT '患者ID，patient_basic_info.id',
  `disease_code` varchar(20) NOT NULL COMMENT '病种编码：FM 纤维肌痛 / AS 强直性脊柱炎 / SS 系统性硬化症 …',
  `since_year` int DEFAULT NULL COMMENT '起病年份',
  `source` varchar(10) NOT NULL DEFAULT 'manual' COMMENT '来源：manual 医生勾选 / auto 按身份证号匹配',
  `linked_patient_id` bigint DEFAULT NULL COMMENT '该患者在对应病种表中的ID（auto 时填写）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_patient_disease` (`patient_id`, `disease_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='患者-常见相关疾病关联';
