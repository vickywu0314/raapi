-- MySQL 5.7.44 研究编号回填；替代当前入口的 20261003_backfill_study_no.sql，不改历史文件。
-- 前提：先执行 20261003_patient_list.sql；patient_basic_info 为 InnoDB，uk_study_no 全列唯一索引存在。
-- 维护窗口暂停新建患者/其它编号写者，先备份并在同schema合成副本预演；不自动在启动时执行。
-- 与原规则一致：只补 NULL（不改空串/已有编号）；日期依次取 create_date、createDate、执行当天；同日按数值 id 排序从00001。
-- 原5位序号上限99999；超过时计划表唯一约束拒绝，禁止截号/绕过错误继续写。
-- 只用确定性相关 COUNT，不依赖用户变量的求值次序；@ra_backfill_day 仅固定本次日期。
-- 任一语句错误后停止客户端，ROLLBACK 并断开；修复冲突后可从头重跑。不要用mysql --force。
SET @ra_backfill_day = CURRENT_DATE();
DROP TEMPORARY TABLE IF EXISTS ra_study_no_plan;
CREATE TEMPORARY TABLE ra_study_no_plan (
  id BIGINT NOT NULL PRIMARY KEY,
  study_no VARCHAR(32) NOT NULL,
  UNIQUE KEY uk_plan_no (study_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
START TRANSACTION;
INSERT INTO ra_study_no_plan (id, study_no)
SELECT p.id, CONCAT('RA-', DATE_FORMAT(COALESCE(p.create_date,p.createDate,@ra_backfill_day),'%Y%m%d'), '-',
       LPAD((SELECT COUNT(*) FROM patient_basic_info q
             WHERE q.study_no IS NULL AND q.id <= p.id
               AND DATE(COALESCE(q.create_date,q.createDate,@ra_backfill_day)) = DATE(COALESCE(p.create_date,p.createDate,@ra_backfill_day))),5,'0'))
FROM patient_basic_info p WHERE p.study_no IS NULL;
-- 一个InnoDB UPDATE：与已有编号唯一冲突时整个语句回滚，不留下已更新的部分患者。
UPDATE patient_basic_info p JOIN ra_study_no_plan n ON n.id=p.id
SET p.study_no=n.study_no WHERE p.study_no IS NULL;
COMMIT;
DROP TEMPORARY TABLE ra_study_no_plan;
SELECT COUNT(*) AS no_study_no FROM patient_basic_info WHERE study_no IS NULL;
