# 待办事项

## ❓ 待业务确认（确认后告诉开发，改完即删）

> 完整清单（13 个业务问题 + 101 个随访字段）见 **`docs/待确认清单-v1.xlsx`**，请找人在黄色列填写后发回。下表是其中影响已上线功能的几项。

| # | 问题 | 现在的做法 | 确认后改哪里 |
|---|---|---|---|
| 2 | 随访表 `patient_follow_up_history.bsbq`（病史病情）里的 `touchHot`（4068 条）与 `cmHot`（3114 条）选项相同，是什么关系？ | `touchHot` 当作「疼痛关节是否发热」 | 字段字典 |
| 3 | 随访表 `bsbq` 里的 `jrSt`（3105 条，无 / 偶尔 / 经常 / 几乎总是）是什么？ | 保留显示，新增表单不放 | 字段字典 |
| 4 | 随访表 `bqpg`（病情评估）里的 `wd_zc/yc`、`hd_zc/yc`、`xz_zc/yc`、`t_6`（各 7158 条，0~6 整数）是什么？ | 保留显示，新增表单不放 | 字段字典 |
| 6 | 中医外治、药费合计在 RA 数据里没有，是否从字典去掉 | 建议去掉 | 字段字典 |
| 7 | 字段核对 SQL 第③段结果（各字段存的类型） | 待开发跑 | 新增随访写库 |
| 8 | 新建患者写 `patient_relation_doctor.research_type` 填几 | 配置留空，提示未配置 | `ra.patient.research-type` |
| 9 | research_type 5、9、999 各是什么病（6 = AS 已知） | 显示「研究类型 N」 | `PatientWriteServiceImpl.OTHER_RESEARCH` |
| 10 | 「转到自己名下」是转走还是两位医生共享 | 转走 | 新建患者接口 |
| 11 | 疾病分型口径：RF / 抗CCP 参考上限；取最近一次还是曾经阳性 | RF 20、抗CCP 25，取最近一次 | `ra.serology.*-uln`、`PatientServiceImpl.attachSerology` |
| 12 | ACR/EULAR 0 分（109 人）是真 0 分还是没填 | 按 0 分「暂不符合」 | `PatientServiceImpl` |
| 13 | `bblsj` 存的是什么（不良事件已确认存 `blsj`） | 原样显示，新增不写 | 字段字典 |
| 14 | 手部关节编号 1~20 对应哪个关节（对照在老系统前端） | 显示编号 | 访视详情 / 关节图 |
| 15 | 检验参考范围用哪套 | 不标红异常值 | 访视详情 |
| 16 | 导出申请接收邮箱 | 未配置 | `ra.export.apply-mail-to` |

**新建患者 · 身份证号已存在时（2026-10-06 已确认）**
- 不在 RA 研究库（research_type 不在 0/1/2/3/4/7，如 6 = AS）：不新建患者行，复用该患者，新增一条 RA 医患关系挂到当前医生名下；原来的病种自动记入「其他病史」（`patient_comorbidity`）。
- 在 RA 研究库、不在我名下：提示「患者已存在，确认要把该患者转到自己名下吗」，点「是」把该 RA 医患关系的 `doctor_id` 改为当前医生（原医生不再看到），写修改记录。
- 已在我名下：提示后直接打开患者详情。

已确认（2026-10-06）：HAQ 第 2 题「梳头」，13、14 题顺序不影响计分、保持字典顺序；脏器受累明细保留，选「是」时展开。

已确认并完成：research_type 只算 0/1/2/3/4/7、删除随访（物理删除）、婚史编码、吸烟 smoke / smoke_stop、user.name、不良反应 blsj、随访字段含义（101 项，见 `docs/随访字段字典.md`）、HAQ 20 题题目、Wx = 未查、finish 仅供前端显示、图片可直接显示。

## 📋 数据现状调研（2026-10-05）

- **研究类型 `research_type`**：6 = AS（有 BASDAI / HLA-B27，无 DAS28），已在所有接口排除；0、1、2、3、4、7 为 RA（有 DAS28、关节评估）；5 只有 1 条且内容为空。各值是否为不同 RA 课题 / 中心待确认。
- **随访 7 个模块是老系统按拼音缩写存的结构化 JSON**（不是 `{record, date}`），例如：
  `fzjc`：bxb 白细胞、xhdb 血红蛋白、xxb 血小板、xc 血沉、cfydb C反应蛋白、lfsyz 类风湿因子、alt、ast、crea、xcgImgs 化验单图片……；
  `bqpg`：q1~q20 HAQ、zzgj 肿胀关节、ytgj 压痛关节、**result.crpScore = DAS28-CRP**、result.esrScore = DAS28-ESR、acr20/50/70；
  `zyzd`：zz 主证、zzzz / zzcz 主症 / 次症、ss 舌色、sx 舌形、tz 苔质、ts 苔色、mx 脉象；
  `zlfa`：xyList 西药、zcyList 中成药、cyList 中药饮片、zywzList 中医外治；每个模块有 finish 表示已完成。
- 库里的字典表（c_dict、my_dict、x_dictionary）只有性别、药品类别，**没有字段中文名**。
- **老系统仍在使用（已确认）**：老表只读；写老表时只改用户改过的字段、保持老 JSON 结构；老表只新增可空 / 有默认值的字段。
- **已完成**：列表 / 详情 DAS28-CRP 取 bqpg.result.crpScore；质控「缺 DAS28」改为检查 crpScore / esrScore（原来查文字 DAS28，几乎所有患者都被误判为缺）。
- **已完成**：字段字典 `VisitFieldDict`（文档 `docs/随访字段字典.md`），访视详情按它结构化显示。编辑随访（visit-edit.html）也按同一字典读写。
- **待提供**：老系统源码或字段说明（字段中文名最可靠的来源）；老系统是否还在写这些表。

## 上线前

- [ ] **执行建表 / 改表 SQL**（按文件名日期顺序）
  - `src/main/resources/sql/20261003_patient_list.sql`：患者表加 `study_no`、`follow_cycle`；新建 `patient_comorbidity`
  - `src/main/resources/sql/20261003_backfill_study_no.sql`：老患者补研究编号
  - `src/main/resources/sql/20261005_patient_audit_log.sql`：修改记录表
  - `src/main/resources/sql/20261006_export_application.sql`：数据导出申请表
  - `src/main/resources/sql/20261006_patient_acr_eular.sql`：ACR/EULAR 2010 评估记录表（新建患者用）
- [ ] **配置导出申请接收邮箱**：`application.properties` 的 `ra.export.apply-mail-to`
- [ ] **配置新建患者的研究类型**：`application.properties` 的 `ra.patient.research-type`（待确认 RA 研究库对应的值；留空时新建患者提示未配置、不写库）

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

## 🔍 数据核查规则（候选，做「数据核查」报告时用；只读，不改数据）

- [ ] DAS28-CRP / ESR、HAQ 按原始数据复算，与库里存的不一致
- [ ] 化验值超出合理范围、数值字段填了文字
- [ ] 随访日期在未来、早于确诊日期
- [ ] 同一患者同一天多条随访（如患者 4564 的 58254、58255，均为 2026-08-17）
- [ ] 血压收缩压 < 舒张压，疑似填反（patient_basic_info 有 xy_h=80、xy_l=120 等 17 条以上）

## 后续接口

- [ ] 导出申请发邮件：提交后发到 `ra.export.apply-mail-to`（申请已记录在 export_application，mail_sent 标记是否已发）；审批流程待定
- [ ] OCR识别录入（患者列表、患者详情、访视详情的按钮，目前提示开发中）：百度医疗 OCR

- [ ] 新建患者 / 编辑档案：写入 `study_no`（`StudyNoUtil` 生成）、`follow_cycle`、`patient_comorbidity`（医生勾选）
- [ ] 疾病分型手动修改（如外院化验没录进系统）：如需要，新建表存医生手动填的分型，手动值优先于按 RF / 抗CCP 算出的值
- [ ] 其他病史弹窗的当前情况、核心指标、治疗：目前无数据来源，返回 null
- [ ] 所有写接口在同一事务里调用 `AuditLogService.record(...)` 写修改记录（见 docs/API.md 3.3）
- [ ] 患者详情 / 访视详情页按钮（下载病历、问问AI、标记脱落、AI 拍照导入）、编辑档案、新增随访：接口未做，前端暂时提示「开发中」或仍为演示页（删除随访、编辑随访已完成）
- [ ] 编辑随访：ACR20/50/70 未重算（需对比上次随访）；图片暂不能上传 / 删除；多选项暂为文本框（「、」分隔），选项清单确认后可改为勾选
- [ ] 删除随访（逻辑删除）方案待定：随访表加删除标记 + 删除原因；项目总览、患者列表、详情、质控等所有查询都要排除已删除的随访；写修改记录；基线访视后还有其他随访时不能删
