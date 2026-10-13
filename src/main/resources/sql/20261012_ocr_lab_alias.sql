-- OCR 识别病历：检验项目「叫法 → 系统字段」对照表（wenwen 库执行一次）。
-- 各家医院检验单的项目名称不完全一样；医生在识别结果里手动对应过一次的叫法存在这里，下次自动对上。
-- 内置同义词在代码里（OcrResultParser.LAB），这张表只存医生补充的。新表，不动老表。
CREATE TABLE IF NOT EXISTS `ocr_lab_alias` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `alias` varchar(200) NOT NULL COMMENT '检验项目叫法（名称|缩写，去空格、括号、连字符，转大写）',
  `field` varchar(40) NOT NULL COMMENT '系统字段（辅助检查 fzjc 的字段名，如 cfydb）',
  `sample_name` varchar(200) DEFAULT NULL COMMENT '原始名称示例',
  `doctor_id` bigint DEFAULT NULL COMMENT '最后对照的医生',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_alias` (`alias`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OCR 检验项目叫法对照';
