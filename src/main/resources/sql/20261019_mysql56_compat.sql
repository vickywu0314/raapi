-- MySQL 5.6 兼容：只给「之前已经执行过旧版本建表脚本」的库用；新库直接执行新版 20261008 / 20261012 即可，不需要本文件。
-- 可重复执行：表结构已是新版时这两句等于不改。
-- 1. AI 保存条件：active_name 由生成列（5.7 才支持）转成普通列，保留现有值；之后由应用在新增 / 改名 / 删除时写入。
ALTER TABLE ra_private_cohort_definition MODIFY active_name VARBINARY(120) NULL;
-- 2. OCR 叫法对照：旧版 alias 为 varchar(200) 时不用改（5.7 上可用；应用只写 191 字以内）。
