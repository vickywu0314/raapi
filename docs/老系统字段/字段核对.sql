-- 随访字段核对（只读查询，不改数据）。按《RA 数据库字段》思维导图调整随访字段前，查老数据里实际有哪些字段、存了哪些选项。
-- 需要 MySQL 8.0+（JSON_TABLE）。research_type 白名单同 ProjectMapper.xml 的 raResearchTypes。
-- 用法：两段查询分别执行，结果导出为 CSV 或复制文本发给开发。

-- ① 每个模块实际出现过的字段名及出现次数（找 PDF 新增字段是否已有字段名）
SELECT 'bsbq' AS module, jt.k AS field, COUNT(*) AS c
FROM patient_follow_up_history f,
     JSON_TABLE(JSON_KEYS(f.bsbq), '$[*]' COLUMNS (k VARCHAR(100) PATH '$')) jt
WHERE f.research_type IN (0, 1, 2, 3, 4, 7) AND JSON_VALID(f.bsbq)
GROUP BY jt.k
UNION ALL
SELECT 'fzjc' AS module, jt.k AS field, COUNT(*) AS c
FROM patient_follow_up_history f,
     JSON_TABLE(JSON_KEYS(f.fzjc), '$[*]' COLUMNS (k VARCHAR(100) PATH '$')) jt
WHERE f.research_type IN (0, 1, 2, 3, 4, 7) AND JSON_VALID(f.fzjc)
GROUP BY jt.k
UNION ALL
SELECT 'bqpg' AS module, jt.k AS field, COUNT(*) AS c
FROM patient_follow_up_history f,
     JSON_TABLE(JSON_KEYS(f.bqpg), '$[*]' COLUMNS (k VARCHAR(100) PATH '$')) jt
WHERE f.research_type IN (0, 1, 2, 3, 4, 7) AND JSON_VALID(f.bqpg)
GROUP BY jt.k
UNION ALL
SELECT 'zyzd' AS module, jt.k AS field, COUNT(*) AS c
FROM patient_follow_up_history f,
     JSON_TABLE(JSON_KEYS(f.zyzd), '$[*]' COLUMNS (k VARCHAR(100) PATH '$')) jt
WHERE f.research_type IN (0, 1, 2, 3, 4, 7) AND JSON_VALID(f.zyzd)
GROUP BY jt.k
UNION ALL
SELECT 'zlfa' AS module, jt.k AS field, COUNT(*) AS c
FROM patient_follow_up_history f,
     JSON_TABLE(JSON_KEYS(f.zlfa), '$[*]' COLUMNS (k VARCHAR(100) PATH '$')) jt
WHERE f.research_type IN (0, 1, 2, 3, 4, 7) AND JSON_VALID(f.zlfa)
GROUP BY jt.k
UNION ALL
SELECT 'blsj' AS module, jt.k AS field, COUNT(*) AS c
FROM patient_follow_up_history f,
     JSON_TABLE(JSON_KEYS(f.blsj), '$[*]' COLUMNS (k VARCHAR(100) PATH '$')) jt
WHERE f.research_type IN (0, 1, 2, 3, 4, 7) AND JSON_VALID(f.blsj)
GROUP BY jt.k
ORDER BY module, c DESC;

-- ② 选项类字段实际存的值（单选 / 多选；不同取值不超过 40 种的字段才列出，化验数值不会出现）
WITH kv AS (
  SELECT 'bsbq' AS module, jt.k, JSON_EXTRACT(f.bsbq, CONCAT('$."', jt.k, '"')) AS val
  FROM patient_follow_up_history f,
       JSON_TABLE(JSON_KEYS(f.bsbq), '$[*]' COLUMNS (k VARCHAR(100) PATH '$')) jt
  WHERE f.research_type IN (0, 1, 2, 3, 4, 7) AND JSON_VALID(f.bsbq)
  UNION ALL
  SELECT 'fzjc' AS module, jt.k, JSON_EXTRACT(f.fzjc, CONCAT('$."', jt.k, '"')) AS val
  FROM patient_follow_up_history f,
       JSON_TABLE(JSON_KEYS(f.fzjc), '$[*]' COLUMNS (k VARCHAR(100) PATH '$')) jt
  WHERE f.research_type IN (0, 1, 2, 3, 4, 7) AND JSON_VALID(f.fzjc)
  UNION ALL
  SELECT 'bqpg' AS module, jt.k, JSON_EXTRACT(f.bqpg, CONCAT('$."', jt.k, '"')) AS val
  FROM patient_follow_up_history f,
       JSON_TABLE(JSON_KEYS(f.bqpg), '$[*]' COLUMNS (k VARCHAR(100) PATH '$')) jt
  WHERE f.research_type IN (0, 1, 2, 3, 4, 7) AND JSON_VALID(f.bqpg)
  UNION ALL
  SELECT 'zyzd' AS module, jt.k, JSON_EXTRACT(f.zyzd, CONCAT('$."', jt.k, '"')) AS val
  FROM patient_follow_up_history f,
       JSON_TABLE(JSON_KEYS(f.zyzd), '$[*]' COLUMNS (k VARCHAR(100) PATH '$')) jt
  WHERE f.research_type IN (0, 1, 2, 3, 4, 7) AND JSON_VALID(f.zyzd)
  UNION ALL
  SELECT 'zlfa' AS module, jt.k, JSON_EXTRACT(f.zlfa, CONCAT('$."', jt.k, '"')) AS val
  FROM patient_follow_up_history f,
       JSON_TABLE(JSON_KEYS(f.zlfa), '$[*]' COLUMNS (k VARCHAR(100) PATH '$')) jt
  WHERE f.research_type IN (0, 1, 2, 3, 4, 7) AND JSON_VALID(f.zlfa)
  UNION ALL
  SELECT 'blsj' AS module, jt.k, JSON_EXTRACT(f.blsj, CONCAT('$."', jt.k, '"')) AS val
  FROM patient_follow_up_history f,
       JSON_TABLE(JSON_KEYS(f.blsj), '$[*]' COLUMNS (k VARCHAR(100) PATH '$')) jt
  WHERE f.research_type IN (0, 1, 2, 3, 4, 7) AND JSON_VALID(f.blsj)
),
vals AS (
  SELECT module, k, JSON_UNQUOTE(val) AS v FROM kv WHERE JSON_TYPE(val) IN ('STRING', 'INTEGER', 'BOOLEAN')
  UNION ALL
  SELECT kv.module, kv.k, a.v FROM kv,
         JSON_TABLE(kv.val, '$[*]' COLUMNS (v VARCHAR(200) PATH '$' NULL ON ERROR)) a
  WHERE JSON_TYPE(kv.val) = 'ARRAY' AND a.v IS NOT NULL
),
small AS (SELECT module, k FROM vals GROUP BY module, k HAVING COUNT(DISTINCT v) <= 40)
SELECT vals.module, vals.k AS field, vals.v AS value, COUNT(*) AS c
FROM vals JOIN small ON small.module = vals.module AND small.k = vals.k
GROUP BY vals.module, vals.k, vals.v
ORDER BY vals.module, vals.k, c DESC;


-- ③ 每个字段存的类型（STRING 文字 / ARRAY 数组 / INTEGER、DOUBLE 数字 / BOOLEAN / OBJECT），新增随访按老数据的类型写
SELECT 'bsbq' AS module, jt.k AS field, JSON_TYPE(JSON_EXTRACT(f.bsbq, CONCAT('$."', jt.k, '"'))) AS type, COUNT(*) AS c
FROM patient_follow_up_history f,
     JSON_TABLE(JSON_KEYS(f.bsbq), '$[*]' COLUMNS (k VARCHAR(100) PATH '$')) jt
WHERE f.research_type IN (0, 1, 2, 3, 4, 7) AND JSON_VALID(f.bsbq)
GROUP BY jt.k, type
UNION ALL
SELECT 'fzjc' AS module, jt.k AS field, JSON_TYPE(JSON_EXTRACT(f.fzjc, CONCAT('$."', jt.k, '"'))) AS type, COUNT(*) AS c
FROM patient_follow_up_history f,
     JSON_TABLE(JSON_KEYS(f.fzjc), '$[*]' COLUMNS (k VARCHAR(100) PATH '$')) jt
WHERE f.research_type IN (0, 1, 2, 3, 4, 7) AND JSON_VALID(f.fzjc)
GROUP BY jt.k, type
UNION ALL
SELECT 'bqpg' AS module, jt.k AS field, JSON_TYPE(JSON_EXTRACT(f.bqpg, CONCAT('$."', jt.k, '"'))) AS type, COUNT(*) AS c
FROM patient_follow_up_history f,
     JSON_TABLE(JSON_KEYS(f.bqpg), '$[*]' COLUMNS (k VARCHAR(100) PATH '$')) jt
WHERE f.research_type IN (0, 1, 2, 3, 4, 7) AND JSON_VALID(f.bqpg)
GROUP BY jt.k, type
UNION ALL
SELECT 'zyzd' AS module, jt.k AS field, JSON_TYPE(JSON_EXTRACT(f.zyzd, CONCAT('$."', jt.k, '"'))) AS type, COUNT(*) AS c
FROM patient_follow_up_history f,
     JSON_TABLE(JSON_KEYS(f.zyzd), '$[*]' COLUMNS (k VARCHAR(100) PATH '$')) jt
WHERE f.research_type IN (0, 1, 2, 3, 4, 7) AND JSON_VALID(f.zyzd)
GROUP BY jt.k, type
UNION ALL
SELECT 'zlfa' AS module, jt.k AS field, JSON_TYPE(JSON_EXTRACT(f.zlfa, CONCAT('$."', jt.k, '"'))) AS type, COUNT(*) AS c
FROM patient_follow_up_history f,
     JSON_TABLE(JSON_KEYS(f.zlfa), '$[*]' COLUMNS (k VARCHAR(100) PATH '$')) jt
WHERE f.research_type IN (0, 1, 2, 3, 4, 7) AND JSON_VALID(f.zlfa)
GROUP BY jt.k, type
UNION ALL
SELECT 'blsj' AS module, jt.k AS field, JSON_TYPE(JSON_EXTRACT(f.blsj, CONCAT('$."', jt.k, '"'))) AS type, COUNT(*) AS c
FROM patient_follow_up_history f,
     JSON_TABLE(JSON_KEYS(f.blsj), '$[*]' COLUMNS (k VARCHAR(100) PATH '$')) jt
WHERE f.research_type IN (0, 1, 2, 3, 4, 7) AND JSON_VALID(f.blsj)
GROUP BY jt.k, type
ORDER BY module, field, c DESC;
