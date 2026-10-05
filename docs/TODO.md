# 待办事项

## ❓ 待业务确认（确认后告诉开发，改完即删）

| # | 问题 | 现在的做法 | 确认后改哪里 |
|---|---|---|---|
| 1 | 婚史 `patient_basic_info.marry`（int）每个数字代表什么？可先查：`SELECT marry, COUNT(*) FROM patient_basic_info GROUP BY marry;` | 详情页显示「代码 N」 | `PatientServiceImpl` 补编码 → 中文 |
| 2 | 吸烟 `patient_basic_info.smoke`（int）取值含义？是否 0 = 不吸烟、其它 = 吸烟？`smoke_stop` 是否表示已戒烟？ | 0 显示「不吸烟」，其它显示「吸烟 N 年 · 每日 N 支」 | `PatientServiceImpl.smokingText` |

## 上线前

- [ ] **执行建表 / 改表 SQL**（按文件名日期顺序）
  - `src/main/resources/sql/20261003_patient_list.sql`：患者表加 `study_no`、`follow_cycle`；新建 `patient_comorbidity`
  - `src/main/resources/sql/20261003_backfill_study_no.sql`：老患者补研究编号
  - `src/main/resources/sql/20261005_patient_audit_log.sql`：修改记录表

- [ ] **整理老数据：按身份证号把患者的相关疾病写入中间表 `patient_comorbidity`**
  - 规则：RA 患者（`patient_basic_info.card_no`）与其它病种表中的患者**身份证号相同**，即认为该患者也患有该病，插入一条关联：
    `patient_id` = RA 患者 id，`disease_code` = 病种编码（FM / AS / SS …），`source` = `auto`，`linked_patient_id` = 对应病种表中的患者 id。
  - 身份证号为空的患者不匹配；已存在的关联（`uk_patient_disease`）跳过。
  - 待补：各病种表的表名、身份证号字段名、起病年份字段（用于 `since_year`）。拿到后写成脚本放到 `src/main/resources/sql/`，形如：
    ```sql
    INSERT IGNORE INTO patient_comorbidity (patient_id, disease_code, source, linked_patient_id)
    SELECT p.id, 'AS', 'auto', a.id
    FROM patient_basic_info p
    JOIN <AS病种患者表> a ON a.<身份证号字段> = p.card_no
    WHERE p.card_no IS NOT NULL AND p.card_no <> '';
    ```
  - 新建患者时也要按同一规则自动匹配一次（新建患者接口实现时做）。

## 后续接口

- [ ] 新建患者 / 编辑档案：写入 `study_no`（`StudyNoUtil` 生成）、`follow_cycle`、`patient_comorbidity`（医生勾选）
- [ ] 患者列表「疾病分型」「DAS28」：目前返回 null，等随访表 `bsbq` / `bqpg` 有结构化数据后再取
- [ ] 其他病史弹窗的当前情况、核心指标、治疗：目前无数据来源，返回 null
- [ ] 所有写接口在同一事务里调用 `AuditLogService.record(...)` 写修改记录（见 docs/API.md 3.3）
- [ ] 患者详情页按钮（下载病历、问问AI、标记脱落、AI 拍照导入）、访视详情、编辑档案、新增随访：接口未做，前端暂时提示「开发中」或仍为演示页
