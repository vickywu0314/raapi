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

- [ ] **接收MySQL5.7.44环境与迁移**：按[兼容与迁移](MySQL5.7兼容与迁移.md)核对64MiB packet、InnoDB/Barracuda/large_prefix/DYNAMIC、严格模式/RR/TLS/账号权限；备份并在目标副本预演全部相关DDL与研究编号回填，保当前独立比较8MiB容量、规范查询128KiB存储边界及全列唯一性。不得物理降级8 datadir；生产数据逻辑迁移、切换/回退与生命周期风险由DBA/运维接收。本批不执行生产操作。

- P05 chat上线前执行 `src/main/resources/sql/20261012_ai_chat_audit.sql` 新append审计DDL；确认生产typed身份、权威源、独立aggregate/clinical出站批准及真实原文处理边界。`ra.ai.chat.enabled` 与两类批准默认关闭，parse许可不足；合成DeepSeek/SQL/Node技术接收不批准真实患者或部署。保留固定源 `chat:cohort-states` / `chat:cohort-loopback` 与 `chat:patient-states` / `chat:patient-loopback` 浏览器补验；患者全程/本次技术实现不批准真实临床出站。

- [ ] **接收当前启动准入与运行材料（历史P07c起点）**：[发布与回滚手册](AI队列发布与回滚手册.md)拥有两键关闭/重启生效、当前准确23路身份优先/503/零副作用、七份DDL共8新表逐文件hash及旧申请兼容前置、目标只读核查/逐statement预演与部分成功续作入口。目标host/账号/受控认证、MySQL/InnoDB/短RR/时区/索引、访问留存及已批准当前/回退工件hash/配置版本/真实部署渠道仍须AQC-EXT-04接收；此待办不执行目标SQL或生产发布。
- [ ] **接收P07c整组回退与恢复前提**：先关闭新增API/普通XLSX并复用LLM/SMTP原开关，经运维真实渠道生效，再按原进程关闭语义及已核整组工件回退；CRP阈值与全部共享消费者不能局部回退。保留旧患者/访视、新表、定义/回执/有期payload/审计/申请及attempt/from/to，不倒写历史CRP、不drop/recreate或盲重放DDL、UNKNOWN/SENT不开门重发。恢复仍需原TTL/U/权限/医学与外发政策；[API §7.9](API.md#79-启动配置准入与关闭边界)的默认true不是发布批准。八项接收进度只见[唯一登记](../../docs/plans/ai-cohort/review-register.md#接入与发布接收义务)。

下方原业务/旧迁移待办保持原有归属；历史P07c片只核六份当时的新DDL及旧申请兼容，当前准入清单为手册七份DDL共8新表。目标SQL仍须DBA逐项批准，不把已有旧迁移/回填列表变成本Flow自动执行清单。

- [ ] **接收P06b申请companion DDL与状态留存**：[20261008_ai_export_request.sql](../src/main/resources/sql/20261008_ai_export_request.sql)仅新增InnoDB/utf8mb4专表，无患者/旧表FK或后台恢复。与现存export_application同短事务创建，主键owner/requestId与applicationId唯一；仅本片旧行mail_to/mail_sent可更新，旧status/CSV/apply不变。DBA接收目标引擎/索引/权限/摘要与自由reason访问留存，再安装表/启用新入口；回滚先关新applyAnalysis/retryNotification和SMTP，不删除旧申请/companion或把UNKNOWN改FAILED。AQC-EXT-04仍OPEN。
- [ ] **接收P06b真实SMTP和管理员收件**：ra.export.smtp.enabled默认false，生产仅强制STARTTLS/默认信任与hostname；host/port/from与唯一ra.export.apply-mail-to、安全凭据渠道、有限模板由SK/管理员/运维批准。凭据不入仓库/消息/日志；测试PKCS12及LOOPBACK_PLAIN不用于生产。首次绑定to/from后改地址会RECIPIENT_CHANGED。真实无患者通知的SMTP受理与管理员收件分别确认，不能以loopback或SENT自闭AQC-EXT-06；文件字段/日期发布AQC-REL-02仍OPEN。
- [ ] **接收P06b不可逆/退出边界**：申请/claim明确提交后当前caller最多一次SMTP，UNKNOWN无普通重发或后台复活。9500/10000ms仅单网络调用预算；socket主动close及真实afterExecute在≤500ms窗口退出，native DNS未退保原容量/BUSY。不保证真实TCP写永久阻塞试验、强杀native DNS或浏览器断连；邮件SENT不证明送达、审批或文件权利。

- [ ] **接收P06a普通XLSX审计DDL与对外字段/日期政策**：[20261008_ai_export_audit.sql](../src/main/resources/sql/20261008_ai_export_audit.sql)只新增ra_ai_export_audit（25列/InnoDB/无旧表外键），记录PREPARING/NEED_APPLY/READY生成事实与NOT_STARTED/REJECTED/STARTED/WRITTEN/FAILED传输事实，不是下载许可或送达证明。DBA接收目标版本/权限、索引、访问/留存，再安装新表与入口；回滚先停新xlsx入口，保留已提交审计，不自动删表/事件。AQC-EXT-04保持OPEN。
- [ ] **接收P06a真实对外发布**：业务/数据负责人确认实际默认15字段/比较12指标、ID/静态字段无日不豁免、显式日期子集、上海月首减6自然月及比较窗外affectedN=整个validN（非实际越窗人数）、metadata/精度/隐私与网络UNKNOWN边界；AQC-REL-02仍OPEN。缺省/绑定6可用，其他可绑定整数仅新接口503；坏文本保持真实bean装配失败。当前文件rows/cells/bytes与比较聚合容量独立配置，不再共享旧analysis maxBytes；生产旧CSV/申请流程不因开发证据获得新权限；无本片SMTP/后台文件/下载重试。

- [ ] **接收规则解析专用追加审计DDL与访问/留存政策**：[20261008_ai_parse_audit.sql](../src/main/resources/sql/20261008_ai_parse_audit.sql)只新增ra_ai_parse_audit（InnoDB、无旧表外键），保存可信actor/action、白名单普通条件摘要/状态/版本/trace，禁止原问句、其hash、ids/其hash、病例或notice。部署先由DBA核目标版本/权限/留存，再安装表和两路API；缺表时parse/apply失败503、不回部分候选。回滚先停止两路入口，保留已提交事件供审计接收，不自动删表/丢事件；后续受控移除须另行批准留存方案。AQC-EXT-04保持OPEN；模型网关默认禁出站；原合成loopback协议之外，ai-analysis-real-data/P04及P05四次固定源合成DeepSeek真实服务调用已作限定技术接收，真实临床问题/字段、原文外发和部署政策仍由AQC-EXT-05接收，技术验证不关闭生产义务。

- [ ] **接收P05b真实AI启用与外发政策**：默认ra.ai.gateway.enabled=false/outbound-approved=false；真实启用前由GY/数据安全负责人在安全渠道接收固定endpoint/model/额度与凭据，以及批准的文本范围、部署、原文访问/留存（可明确不存）决定。受控p05b-synthetic-model/p05b-loopback-key只用于字面127.0.0.1，不配置真实key或以合成测试关闭AQC-EXT-05。凭据仅服务器安全注入，禁止仓库/消息/审计/异常/日志保存；不得扩大到自由中文、患者上下文或任意URL。
- [ ] **接收P05b部署生命周期边界**：正常/可终止socket先真实owner退出再短审计，caller中断503且保flag/无成功事件；最多2个daemon、无队列/重试。5秒是单次网关预算，不是入口/审计/写回SLA。native DNS不可强杀，未退者保真实容量和未terminated状态；不得以RULE/TIMEOUT事件或容器关闭返回误称所有平台资源已退。现成DDL仅版本列实填，无新增表；模型已外发后的审计失败不能撤回HTTP或自动重发。真实身份/目标DB/医学统计/通知与发布义务继续由对应接收owner关闭。


- [ ] **执行建表 / 改表 SQL**（按文件名日期顺序）
  - `src/main/resources/sql/20261003_patient_list.sql`：患者表加 `study_no`、`follow_cycle`；新建 `patient_comorbidity`
  - `src/main/resources/sql/20261018_backfill_study_no_mysql57.sql`：MySQL5.7老患者补研究编号，替代当前入口的8窗口函数版本；不重复执行历史版本
  - `src/main/resources/sql/20261005_patient_audit_log.sql`：修改记录表
  - `src/main/resources/sql/20261006_export_application.sql`：数据导出申请表
  - `src/main/resources/sql/20261006_patient_acr_eular.sql`：ACR/EULAR 2010 评估记录表（新建患者用）
  - `src/main/resources/sql/20261019_ai_analysis_query_session.sql`：当前AI分析小型查询会话表；先建表及核权限，再升级应用。旧ra_ai_analysis_run保留供回退，不再作为新分析读写前置
  - `src/main/resources/sql/20261011_patient_ra_extra.sql`：诊断结果告知、知情同意书（新建患者用）
  - `src/main/resources/sql/20261012_qc_issue_resolution.sql`：数据质控「标记已处理」记录（`qc_issue_resolution`）
  - `src/main/resources/sql/20261012_ocr_lab_alias.sql`：OCR 检验项目叫法对照（`ocr_lab_alias`，医生手动对应过的叫法）
  - `src/main/resources/sql/20261014_ocr_record.sql`：OCR 病历图片记录（`ocr_record`，图片 ↔ 患者 / 随访、识别结果）
  - `src/main/resources/sql/20261008_private_cohort_definition.sql`：AI 私有条件定义（`ra_private_cohort_definition`、`ra_private_cohort_command`）
  - `src/main/resources/sql/20261008_ai_compare_run.sql`：AI 两队列比较结果（`ra_ai_compare_run`）
  - `src/main/resources/sql/20261008_ai_parse_audit.sql`：AI 问题解析审计（`ra_ai_parse_audit`）
  - `src/main/resources/sql/20261008_ai_export_audit.sql`：AI 普通 XLSX 导出审计（`ra_ai_export_audit`）
  - `src/main/resources/sql/20261008_ai_export_request.sql`：AI 导出申请（`ra_ai_export_request`）
  - 历史`src/main/resources/sql/20261007_ai_analysis_run.sql`只作旧应用回退资料；不自动DROP/清理既有表。本轮新会话DDL的生产执行仍由DBA另行接收（AQC-EXT-04保持OPEN）
- [ ] **接收比较专表与独立容量**：ra_ai_compare_run继续保存小聚合；ra.ai.comparison.result-max-bytes默认8388608、合法1..16777215，TTL/key仍沿analysis配置。旧analysis.result-max-bytes不再作为比较/分析/导出限制。保持短事务、owner/expiry/完整U复验与原生产DDL接收；不把新query-session实时语义混入已保留比较聚合。
- [ ] **接收私有定义生产DDL与长期留存**：`src/main/resources/sql/20261008_private_cohort_definition.sql`新增`ra_private_cohort_definition`和`ra_private_cohort_command`，无旧表写入或外键。隔离MySQL8.4.11已执行utf8mb4/二进制stored生成列活动名唯一索引及双表事务；生产目标MySQL版本/索引、InnoDB/短RR和长期定义/成功回执留存须DBA/运维确认。逻辑删释放名字，定义和成功回执仍长期保留，没有TTL或后台清理。AQC-EXT-04保持OPEN，未执行生产DDL或部署
- [ ] **部署查询会话与独立导出限制**：先执行20261019_ai_analysis_query_session.sql并核SELECT/INSERT/DELETE权限；分析仅存规范query/owner/asOf/规则指纹/TTL。保安全注入cursor-key-base64及result-ttl-seconds默认900，废止analysis.result-max-bytes的生产消费。文件独立ra.export.xlsx.max-bytes默认16777216、max-rows默认100000、max-cells默认1000000；超导出限制不影响正常分析。旧analysisId明确重新分析，跨页随源更新可漏重；原blob表不drop。当前/回退应用与新旧ID行为、目标库权限/DDL和保留政策由DBA/运维接收，不能以开发5.7合成验证替代AQC-EXT-01/04。
- [ ] **配置导出申请接收邮箱**：`application.properties` 的 `ra.export.apply-mail-to`
- [ ] **前端 nginx：同源反向代理 `/api/` 到后端，并放开上传大小**
  - 页面和接口必须同一个域名 / 端口（登录靠 session Cookie）：`location /api/ { proxy_pass http://127.0.0.1:8065; }`，不要改写 Cookie 的 path
  - `client_max_body_size 20m;`：nginx 默认只允许 1MB，手机拍的病历照片（2～5MB）OCR 上传会 413 失败；后端已允许 15MB
  - `proxy_read_timeout 60s;`：OCR 识别、AI 分析可能要十几秒
  - 前端只发布页面文件（*.html、css/、js/、img/、guides/），不要发布 node_modules/、tests/、mock/、scripts/、docs/、_release/、Claude outputs/
- [ ] **配置上传图片目录 `ra.upload.dir`（OCR 病历原图存这里，丢了访视详情里的原图就打不开）**
  - 服务器配置写绝对路径，如 `ra.upload.dir=/data/raapi/upload`；默认值 `upload` 是相对后端启动目录的，换启动目录 / 重新部署时图片会"找不到"
  - 目录要提前建好，后端运行账号有读写权限；不要放在会被部署覆盖或清空的目录（如 jar 所在的发布目录、/tmp）
  - 纳入服务器备份；病历图片含患者隐私，只给后端运行账号权限，不对外开 nginx 静态访问（只能通过 `/api/ra/files/ocr/...` 登录后查看）
  - 多台后端时要用共享存储（NAS 等），否则在 A 机上传、B 机查看会 404
  - 清理规则（如未关联患者的图片保留多久）待业务定
- [ ] **配置百度 OCR**：服务器配置 `ra.ocr.baidu.api-key`、`ra.ocr.baidu.secret-key`（不进 git），百度控制台开通「医疗检验报告单识别」「通用文字识别（高精度版）」；服务器能访问 `aip.baidubce.com:443`。病历图片外发百度需经医院 / 数据安全负责人同意
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
- [ ] OCR识别录入：已完成（2026-10-12，见 API.md 十一），只用模拟的百度返回测过；拿到真实密钥后用几家医院的真实报告单核对识别和对应效果，内置同义词按实际叫法补充（`OcrResultParser.LAB`）；病历原图记在新表 `ocr_record`，访视详情显示「OCR 识别原图」（2026-10-14），不写老表的「检验报告」图片字段（xcgImgs，老系统图片格式未知）

- [ ] 新建患者 / 编辑档案：写入 `study_no`（`StudyNoUtil` 生成）、`follow_cycle`、`patient_comorbidity`（医生勾选）
- [ ] 疾病分型手动修改（如外院化验没录进系统）：如需要，新建表存医生手动填的分型，手动值优先于按 RF / 抗CCP 算出的值
- [ ] 其他病史弹窗的当前情况、核心指标、治疗：目前无数据来源，返回 null
- [ ] 所有写接口在同一事务里调用 `AuditLogService.record(...)` 写修改记录（见 docs/API.md 3.3）
- [x] 下载病历（PDF）、标记脱落 / 恢复随访、编辑档案、新增随访、随访管理已完成（2026-10-11）；OCR识别录入于2026-10-12接入。当前可信 AI 来源提供只读档案/访视/实测病程及本次/全程问答，ALL/COHORT/PATIENT 问答带后台事实/来源；默认出站关闭，真实临床与原文许可仍由 AQC-EXT-05 接收，不新增回答转条件或审批。
- [ ] 新增 / 编辑随访：ACR20/50/70 未计算（需对比上次随访）；图片暂不能上传 / 删除；手部关节编号、脏器受累明细仍为文本框（选项待确认）；单选 / 多选已按定稿清单改为勾选（2026-10-11）
- [ ] 删除随访（逻辑删除）方案待定：随访表加删除标记 + 删除原因；项目总览、患者列表、详情、质控等所有查询都要排除已删除的随访；写修改记录；基线访视后还有其他随访时不能删

- P06a 当前 typed 身份、生产药品/FM 配置 → 保留引用 → 原生 XLSX/唯一申请已接入合成组合验证；真实身份/权威内容、实际对外字段和来源日期窗口批准、真实 SMTP 管理员收件继续由原 EXT-06/REL-02 等唯一 owner 接收。`SENT` 不表示收到、申请不表示审批或下载权。六 pure 固定源外部接收及 `cohort:current-export-loopback` 保留 BROWSER_PENDING，Actions 暂停；不据合成技术结果启用生产通知或对外文件。

## ENTRY-20261019 录入规则待业务确认

- [ ] 提供辅助检查所需指标集合及适用条件，不能以“全部必填”代替清单。
- [ ] 确认字段单位、参考/合理范围、必填项允许未查豁免的条件；可选 `ra.visit.validation.rules` 默认 `{}`，仅支持 fzjc 字典现有无 showIf/notBaseline 依赖的 laboratory 字段；ANA 等非 laboratory radioList 暂不支持配置，非支持字段 fail-closed，不提供全表单条件必填/基线重排。没有临床阈值或新必填默认。格式/有限数值及配置下逐项核对已实现，尚不能称附件第1/2项全完成。
- [ ] 裁决不良事件原文“是之前/之后”的门控；现保持 `event=有`，旧 `bblsj` 只读归组且原数据保留。
- 本轮随访表单/保存身份已改取登录会话；其余上方既存接口身份待办不据此全部勾销。附件第5条按用户后续明确指令保持原样。TCM 独立 OCR 另批处理。
