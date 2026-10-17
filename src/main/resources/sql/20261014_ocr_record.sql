-- OCR 识别病历：每张上传的病历图片一条记录（wenwen 库执行一次）。新表，不动老表。
-- 上传时记下图片和医生；识别后存识别结果；用识别结果新建患者 / 保存随访后记下对应的患者、随访，
-- 访视详情里据此显示「OCR 识别原图」。图片文件本身在服务器 ra.upload.dir/ocr/ 目录下。
CREATE TABLE IF NOT EXISTS `ocr_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `file_id` varchar(64) NOT NULL COMMENT '图片编号：yyyyMMdd/随机名.ext（文件在 ra.upload.dir/ocr/ 下）',
  `doctor_id` bigint DEFAULT NULL COMMENT '上传的医生',
  `patient_id` bigint DEFAULT NULL COMMENT '用识别结果建档 / 随访的患者（未保存为空）',
  `visit_id` bigint DEFAULT NULL COMMENT '用识别结果保存的随访（patient_follow_up_history.id，未保存为空）',
  `result` mediumtext COMMENT '识别结果（JSON：基本信息、报告日期、检验项目及对应字段、原文）',
  `upload_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `recognize_time` datetime DEFAULT NULL,
  `link_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file` (`file_id`),
  KEY `idx_patient_visit` (`patient_id`, `visit_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OCR 识别病历图片记录';
