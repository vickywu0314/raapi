-- 新建患者 / 编辑档案：ACR/EULAR 2010 类风湿关节炎分类标准的各部分选择（wenwen 库执行一次）。
-- 老系统只在 patient_basic_info.acr_eular_score 存总分（acr_eular_info 从未写过）；
-- 新系统评估时总分仍写 acr_eular_score（老系统能看到），各部分得分存本表，每评估一次插一行，最新一行为准。
CREATE TABLE IF NOT EXISTS `patient_acr_eular` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `patient_id` bigint NOT NULL COMMENT '患者ID，patient_basic_info.id',
  `joint_score` int NOT NULL COMMENT '受累关节数量：0 1个中大关节 / 1 2-10个中大关节 / 2 1-3个小关节 / 3 4-10个小关节 / 5 >10个小关节',
  `serology_score` int NOT NULL COMMENT '血清学抗体检测：0 RF或抗CCP均阴性 / 2 至少一项低滴度阳性 / 3 至少一项高滴度阳性',
  `duration_score` int NOT NULL COMMENT '滑膜炎持续时间：0 <6周 / 1 ≥6周',
  `acute_score` int NOT NULL COMMENT '急性时相反应物：0 CRP或ESR均正常 / 1 CRP或ESR增高',
  `total_score` int NOT NULL COMMENT '总分 0~10，≥6 分可分类为 RA',
  `doctor_id` bigint DEFAULT NULL COMMENT '评估医生',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '评估时间',
  PRIMARY KEY (`id`),
  KEY `idx_patient` (`patient_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ACR/EULAR 2010 分类标准评估记录';
