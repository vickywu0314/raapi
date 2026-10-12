# 待办事项

## ❓ 待业务确认（确认后告诉开发，改完即删）

> 完整清单（13 个业务问题 + 101 个随访字段）见 **`docs/待确认清单-v1.xlsx`**，请找人在黄色列填写后发回。下表是其中影响已上线功能的几项。

| # | 问题 | 现在的做法 | 确认后改哪里 |
|---|---|---|---|
| 9 | research_type 5、9、999 各是什么病（6 = AS 已知） | 显示「研究类型 N」 | `PatientWriteServiceImpl.OTHER_RESEARCH` |
| 10 | 「转到自己名下」是转走还是两位医生共享 | 转走 | 新建患者接口 |
| 11 | 疾病分型口径：RF / 抗CCP 参考上限；取最近一次还是曾经阳性 | RF 20、抗CCP 25，取最近一次 | `ra.serology.*-uln`、`PatientServiceImpl.attachSerology` |
| 12 | ACR/EULAR 0 分（109 人）是真 0 分还是没填 | 按 0 分「暂不符合」 | `PatientServiceImpl` |
| 13 | `bblsj` 存的是什么（不良事件已确认存 `blsj`） | 原样显示，新增不写 | 字段字典 |
| 14 | 手部关节编号 1~20 对应哪个关节（对照在老系统前端） | 显示编号 | 访视详情 / 关节图 |
| 15 | 检验参考范围用哪套 | 不标红异常值 | 访视详情 |
| 16 | 导出申请接收邮箱 | 未配置 | `ra.export.apply-mail-to` |
| 17 | 脏器受累各类的明细选项（肺胸膜 / 心脏 / 肾脏 / 神经 / 血管炎 / 其他）PDF 没写 | 文本框，多个用「、」分隔 | `VisitFieldDict` 病情评估「脏器受累」 |
| 18 | 心脏检查 14 项的单位（E/A、FS、EF、LVD、BNP、CK-MB 等）PDF 没写 | 按常用单位显示（m/s、%、mm、pg/mL、U/L），存库只存数值 | `VisitFieldDict` 辅助检查「心脏检查」 |
| 19 | 标记脱落的原因选项 | 失访 / 撤回知情同意 / 不良事件 / 死亡 / 其他（其他须写说明） | `PatientWriteServiceImpl.WITHDRAW_REASONS` |

**新建患者 · 身份证号已存在时（2026-10-06 已确认）**
- 不在 RA 研究库（research_type 不在 0/1/2/3/4/7，如 6 = AS）：不新建患者行，复用该患者，新增一条 RA 医患关系挂到当前医生名下；原来的病种自动记入「其他病史」（`patient_comorbidity`）。
- 在 RA 研究库、不在我名下：提示「患者已存在，确认要把该患者转到自己名下吗」，点「是」把该 RA 医患关系的 `doctor_id` 改为当前医生（原医生不再看到），写修改记录。
- 已在我名下：提示后直接打开患者详情。

已确认（2026-10-06）：HAQ 第 2 题「梳头」，13、14 题顺序不影响计分、保持字典顺序；脏器受累明细保留，选「是」时展开；随访 2~7 组表单字段只按 PDF，PDF 没有的不要（cmHot、jrSt、wd/hd/xz/t_6、中医外治、药费合计等不放表单，老数据在访视详情「其他字段」显示）；各字段存储类型已查（见 docs/新增随访字段清单.md 末尾）。

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

- [ ] **接收P07c启动准入与运行材料**：[发布与回滚手册](AI队列发布与回滚手册.md)拥有两键关闭/重启生效、准确15路身份优先/503/零副作用、六份DDL共7新表逐文件hash及旧申请兼容前置、目标只读核查/逐statement预演与部分成功续作入口。目标host/账号/受控认证、MySQL/InnoDB/短RR/时区/索引、访问留存及已批准当前/回退工件hash/配置版本/真实部署渠道仍须AQC-EXT-04接收；本片不执行目标SQL或生产发布。
- [ ] **接收P07c整组回退与恢复前提**：先关闭新增API/普通XLSX并复用LLM/SMTP原开关，经运维真实渠道生效，再按原进程关闭语义及已核整组工件回退；CRP阈值与全部共享消费者不能局部回退。保留旧患者/访视、新表、定义/回执/有期payload/审计/申请及attempt/from/to，不倒写历史CRP、不drop/recreate或盲重放DDL、UNKNOWN/SENT不开门重发。恢复仍需原TTL/U/权限/医学与外发政策；[API §7.9](API.md#79-启动配置准入与关闭边界)的默认true不是发布批准。八项接收进度只见[唯一登记](../../docs/plans/ai-cohort/review-register.md#接入与发布接收义务)。

下方原业务/旧迁移待办保持原有归属；P07c的SQL清单只选择六份新DDL及旧申请兼容核查，不把已有旧迁移/回填列表变成本Flow自动执行清单。

- [ ] **接收P06b申请companion DDL与状态留存**：[20261008_ai_export_request.sql](../src/main/resources/sql/20261008_ai_export_request.sql)仅新增InnoDB/utf8mb4专表，无患者/旧表FK或后台恢复。与现存export_application同短事务创建，主键owner/requestId与applicationId唯一；仅本片旧行mail_to/mail_sent可更新，旧status/CSV/apply不变。DBA接收目标引擎/索引/权限/摘要与自由reason访问留存，再安装表/启用新入口；回滚先关新applyAnalysis/retryNotification和SMTP，不删除旧申请/companion或把UNKNOWN改FAILED。AQC-EXT-04仍OPEN。
- [ ] **接收P06b真实SMTP和管理员收件**：ra.export.smtp.enabled默认false，生产仅强制STARTTLS/默认信任与hostname；host/port/from与唯一ra.export.apply-mail-to、安全凭据渠道、有限模板由SK/管理员/运维批准。凭据不入仓库/消息/日志；测试PKCS12及LOOPBACK_PLAIN不用于生产。首次绑定to/from后改地址会RECIPIENT_CHANGED。真实无患者通知的SMTP受理与管理员收件分别确认，不能以loopback或SENT自闭AQC-EXT-06；文件字段/日期发布AQC-REL-02仍OPEN。
- [ ] **接收P06b不可逆/退出边界**：申请/claim明确提交后当前caller最多一次SMTP，UNKNOWN无普通重发或后台复活。9500/10000ms仅单网络调用预算；socket主动close及真实afterExecute在≤500ms窗口退出，native DNS未退保原容量/BUSY。不保证真实TCP写永久阻塞试验、强杀native DNS或浏览器断连；邮件SENT不证明送达、审批或文件权利。

- [ ] **接收P06a普通XLSX审计DDL与对外字段/日期政策**：[20261008_ai_export_audit.sql](../src/main/resources/sql/20261008_ai_export_audit.sql)只新增ra_ai_export_audit（25列/InnoDB/无旧表外键），记录PREPARING/NEED_APPLY/READY生成事实与NOT_STARTED/REJECTED/STARTED/WRITTEN/FAILED传输事实，不是下载许可或送达证明。DBA接收目标版本/权限、索引、访问/留存，再安装新表与入口；回滚先停新xlsx入口，保留已提交审计，不自动删表/事件。AQC-EXT-04保持OPEN。
- [ ] **接收P06a真实对外发布**：业务/数据负责人确认实际默认15字段/比较12指标、ID/静态字段无日不豁免、显式日期子集、上海月首减6自然月及比较窗外affectedN=整个validN（非实际越窗人数）、metadata/精度/隐私与网络UNKNOWN边界；AQC-REL-02仍OPEN。缺省/绑定6可用，其他可绑定整数仅新接口503；坏文本保持真实bean装配失败。仅共享maxBytes容量，生产旧CSV/申请流程不因开发证据获得新权限；无本片SMTP/后台文件/下载重试。

- [ ] **接收规则解析专用追加审计DDL与访问/留存政策**：[20261008_ai_parse_audit.sql](../src/main/resources/sql/20261008_ai_parse_audit.sql)只新增ra_ai_parse_audit（InnoDB、无旧表外键），保存可信actor/action、白名单普通条件摘要/状态/版本/trace，禁止原问句、其hash、ids/其hash、病例或notice。部署先由DBA核目标版本/权限/留存，再安装表和两路API；缺表时parse/apply失败503、不回部分候选。回滚先停止两路入口，保留已提交事件供审计接收，不自动删表/丢事件；后续受控移除须另行批准留存方案。AQC-EXT-04保持OPEN；模型网关默认禁出站，仅合成loopback协议已验证；真实AI/原文政策仍由AQC-EXT-05接收，技术验证不关闭生产义务。

- [ ] **接收P05b真实AI启用与外发政策**：默认ra.ai.gateway.enabled=false/outbound-approved=false；真实启用前由GY/数据安全负责人在安全渠道接收固定endpoint/model/额度与凭据，以及批准的文本范围、部署、原文访问/留存（可明确不存）决定。受控p05b-synthetic-model/p05b-loopback-key只用于字面127.0.0.1，不配置真实key或以合成测试关闭AQC-EXT-05。凭据仅服务器安全注入，禁止仓库/消息/审计/异常/日志保存；不得扩大到自由中文、患者上下文或任意URL。
- [ ] **接收P05b部署生命周期边界**：正常/可终止socket先真实owner退出再短审计，caller中断503且保flag/无成功事件；最多2个daemon、无队列/重试。5秒是单次网关预算，不是入口/审计/写回SLA。native DNS不可强杀，未退者保真实容量和未terminated状态；不得以RULE/TIMEOUT事件或容器关闭返回误称所有平台资源已退。现成DDL仅版本列实填，无新增表；模型已外发后的审计失败不能撤回HTTP或自动重发。真实身份/目标DB/医学统计/通知与发布义务继续由对应接收owner关闭。


- [ ] **执行建表 / 改表 SQL**（按文件名日期顺序）
  - `src/main/resources/sql/20261003_patient_list.sql`：患者表加 `study_no`、`follow_cycle`；新建 `patient_comorbidity`
  - `src/main/resources/sql/20261003_backfill_study_no.sql`：老患者补研究编号
  - `src/main/resources/sql/20261005_patient_audit_log.sql`：修改记录表
  - `src/main/resources/sql/20261006_export_application.sql`：数据导出申请表
  - `src/main/resources/sql/20261006_patient_acr_eular.sql`：ACR/EULAR 2010 评估记录表（新建患者用）
  - `src/main/resources/sql/20261007_ai_analysis_run.sql`：AI 队列分析结果表
  - `src/main/resources/sql/20261011_patient_ra_extra.sql`：诊断结果告知、知情同意书（新建患者用）
  - `src/main/resources/sql/20261008_private_cohort_definition.sql`：AI 私有条件定义（`ra_private_cohort_definition`、`ra_private_cohort_command`）
  - `src/main/resources/sql/20261008_ai_compare_run.sql`：AI 两队列比较结果（`ra_ai_compare_run`）
  - `src/main/resources/sql/20261008_ai_parse_audit.sql`：AI 问题解析审计（`ra_ai_parse_audit`）
  - `src/main/resources/sql/20261008_ai_export_audit.sql`：AI 普通 XLSX 导出审计（`ra_ai_export_audit`）
  - `src/main/resources/sql/20261008_ai_export_request.sql`：AI 导出申请（`ra_ai_export_request`）
  - `src/main/resources/sql/20261007_ai_analysis_run.sql`：独立有期分析结果表，无旧患者外键；仅开发合成库已验证，生产执行由DBA另行接收（AQC-EXT-04仍OPEN）
- [ ] **接收比较专表与共享保留配置**：`src/main/resources/sql/20261008_ai_compare_run.sql`新增独立`ra_ai_compare_run`，不改单队列v1/旧表或建旧外键。共享TTL/cap/key门槛、每创建稳定限清1000过期比较行、cleanup+INSERT同短事务、owner/expiry/当前完整U复验仅在隔离合成MySQL8.4.11验证。DBA/运维接收目标版本、InnoDB/短RR、保留策略和生产DDL；无后台清理，已清缺行404，commit后传输失败可留TTL孤立行。AQC-EXT-04仍OPEN；来源日期摘要未代替P06导出字段/窗口与AQC-REL-02接收
- [ ] **接收私有定义生产DDL与长期留存**：`src/main/resources/sql/20261008_private_cohort_definition.sql`新增`ra_private_cohort_definition`和`ra_private_cohort_command`，无旧表写入或外键。隔离MySQL8.4.11已执行utf8mb4/二进制stored生成列活动名唯一索引及双表事务；生产目标MySQL版本/索引、InnoDB/短RR和长期定义/成功回执留存须DBA/运维确认。逻辑删释放名字，定义和成功回执仍长期保留，没有TTL或后台清理。AQC-EXT-04保持OPEN，未执行生产DDL或部署
- [ ] **部署有期分析配置与保留政策**：注入`ra.ai.analysis.cursor-key-base64`（标准Base64解码至少32字节；不提交/记录生产key）、`result-ttl-seconds`默认900及`result-max-bytes`默认8388608（均为`ra.ai.analysis.`前缀）。缺/错配置仅本功能503且分析SQL前拒绝，不阻其它bean启动；key轮换会使旧cursor无效。确认目标InnoDB/短RR、当前医生映射、8MiB单结果容量及每创建限删1000过期行；无请求不保证物理整点清除，过期禁止读取。commit后传输失败可能有完整TTL孤立行。此处不关闭真实身份/生产部署/保留政策AQC-EXT-01/04
- [ ] **配置导出申请接收邮箱**：`application.properties` 的 `ra.export.apply-mail-to`
- [x] 新建患者的研究类型：`ra.patient.research-type=0`（2026-10-11 确认，已配置）

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

- [ ] 登录：现为测试账号（`ra.auth.accounts`），正式账号从哪里来（user 表账号密码 / 单点登录，Q-24 账号找贵玉）待定；患者、随访、导出等接口仍按前端传的 `doctorId`，正式上线前要改为取登录会话里的医生（AI 队列分析已改）
- [ ] 导出申请真实SMTP/管理员收件接收：P06b两入口及受控单次通知/安全FAILED重试已实现，实际服务器/TLS/身份/配置及受理、收件分别由AQC-EXT-06接收。UNKNOWN不可盲重发；本Flow不含审批或历史文件交付
- [ ] OCR识别录入（患者列表、患者详情、访视详情的按钮，目前提示开发中）：百度医疗 OCR

- [ ] 新建患者 / 编辑档案：写入 `study_no`（`StudyNoUtil` 生成）、`follow_cycle`、`patient_comorbidity`（医生勾选）
- [ ] 疾病分型手动修改（如外院化验没录进系统）：如需要，新建表存医生手动填的分型，手动值优先于按 RF / 抗CCP 算出的值
- [ ] 其他病史弹窗的当前情况、核心指标、治疗：目前无数据来源，返回 null
- [ ] 所有写接口在同一事务里调用 `AuditLogService.record(...)` 写修改记录（见 docs/API.md 3.3）
- [x] 下载病历（PDF）、标记脱落 / 恢复随访、编辑档案、新增随访、随访管理已完成（2026-10-11）；问问AI、OCR识别录入仍提示开发中
- [ ] 新增 / 编辑随访：ACR20/50/70 未计算（需对比上次随访）；图片暂不能上传 / 删除；手部关节编号、脏器受累明细仍为文本框（选项待确认）；单选 / 多选已按定稿清单改为勾选（2026-10-11）
- [ ] 删除随访（逻辑删除）方案待定：随访表加删除标记 + 删除原因；项目总览、患者列表、详情、质控等所有查询都要排除已删除的随访；写修改记录；基线访视后还有其他随访时不能删
