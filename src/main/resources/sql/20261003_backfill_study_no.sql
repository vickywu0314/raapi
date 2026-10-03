-- 给老患者补研究编号（需 MySQL 8.0+，用到 ROW_NUMBER）。
-- 规则：RA-建档日期yyyyMMdd-当天序号（5位，按 id 顺序从 00001 起），如 RA-20250407-00001。
-- 建档日期取 create_date，为空取 createDate，都为空取执行当天。
-- 只补 study_no 为空的患者；请在「新建患者」功能上线前执行一次，避免与新编号的当天序号冲突（冲突时唯一索引会报错，不会写入重复编号）。
-- 老数据单日不会超过 99999 人，这里不处理字母进位；新建患者的编号由 com.wenwen.util.StudyNoUtil 生成。
UPDATE `patient_basic_info` p
JOIN (
  SELECT t.id,
         CONCAT('RA-', DATE_FORMAT(t.d, '%Y%m%d'), '-',
                LPAD(ROW_NUMBER() OVER (PARTITION BY DATE(t.d) ORDER BY t.id), 5, '0')) AS no
  FROM (SELECT id, COALESCE(create_date, createDate, NOW()) AS d
        FROM `patient_basic_info` WHERE study_no IS NULL) t
) n ON n.id = p.id
SET p.study_no = n.no;

-- 检查：应返回 0
SELECT COUNT(*) AS no_study_no FROM `patient_basic_info` WHERE study_no IS NULL;
