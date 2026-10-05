# 待办事项

## ❓ 待业务确认（确认后告诉开发，改完即删）

| # | 问题 | 现在的做法 | 确认后改哪里 |
|---|---|---|---|
| 1 | 随访表 `bblsj` 是不是「随诊病例」？存的是什么格式？（`blsj` = 不良反应已确认） | 访视详情按「随诊病例」显示 | `VisitServiceImpl.MODULES` |
| 2 | 吸烟：`smoke` 0 = 不吸烟（**已确认**）。还剩：`smoke_stop` 是否表示已戒烟？取值含义？ | 0 显示「不吸烟」，其它显示「吸烟 N 年 · 每日 N 支」，未显示是否戒烟 | `PatientServiceImpl.smokingText` |
| 3 | **删除本次随访**的逻辑（是否逻辑删除、删除后统计怎么算、基线访视能否删） | 按钮提示「开发中」 | 见下方「后续接口」 |
| 4 | **编辑本次随访**怎么保存：编辑页是 7 个模块、120 个结构化字段的表单，而随访表每个模块存一段 JSON（多为 `{record, date}`）。结构化表单的数据存到哪里、老数据怎么回填到表单？ | 编辑页仍为演示页；基本信息只读（已确认），其它随访数据可改（已确认） | 新增 / 编辑随访接口 |

## 📋 数据现状调研（2026-10-05）

- **研究类型 `research_type`**：6 = AS（有 BASDAI / HLA-B27，无 DAS28），已在所有接口排除；0、1、2、3、4、7 为 RA（有 DAS28、关节评估）；5 只有 1 条且内容为空。各值是否为不同 RA 课题 / 中心待确认。
- **随访 7 个模块是老系统按拼音缩写存的结构化 JSON**（不是 `{record, date}`），例如：
  `fzjc`：bxb 白细胞、xhdb 血红蛋白、xxb 血小板、xc 血沉、cfydb C反应蛋白、lfsyz 类风湿因子、alt、ast、crea、xcgImgs 化验单图片……；
  `bqpg`：q1~q20 HAQ、zzgj 肿胀关节、ytgj 压痛关节、**result.crpScore = DAS28-CRP**、result.esrScore = DAS28-ESR、acr20/50/70；
  `zyzd`：zz 主证、zzzz / zzcz 主症 / 次症、ss 舌色、sx 舌形、tz 苔质、ts 苔色、mx 脉象；
  `zlfa`：xyList 西药、zcyList 中成药、cyList 中药饮片、zywzList 中医外治；每个模块有 finish 表示已完成。
- 库里的字典表（c_dict、my_dict、x_dictionary）只有性别、药品类别，**没有字段中文名**。
- **下一步方案**：沿用老系统格式存（不新建表、不转换），后端建一份「字段 → 中文名 / 单位 / 参考范围」字典，用于访视详情结构化显示和编辑随访；列表 DAS28 改为取 bqpg.result.crpScore。
- **待提供**：老系统源码或字段说明（字段中文名最可靠的来源）；老系统是否还在写这些表。

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
- [ ] 患者详情 / 访视详情页按钮（下载病历、问问AI、标记脱落、AI 拍照导入、删除本次随访）、编辑档案、新增 / 编辑随访：接口未做，前端暂时提示「开发中」或仍为演示页
- [ ] 删除随访（逻辑删除）方案待定：随访表加删除标记 + 删除原因；项目总览、患者列表、详情、质控等所有查询都要排除已删除的随访；写修改记录；基线访视后还有其他随访时不能删
