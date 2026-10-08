# AI 队列发布与回滚手册

Date: 2026-10-08
Status: Current
Owner: 后端开发者；目标环境接收由 DBA/运维负责
Scope: 现存15条新增HTTP入口的启动准入、六份新DDL核查、原状态留存及整组应用回退；技术准备资料，不是发布批准，不执行生产操作

本手册用于维护者准备并核查关闭、SQL预演、应用回退与恢复材料。新开关只决定当前实例是否接收新请求；已有定义、保留结果、审计和申请状态不能被开关重写。真实目标主机、当前/回退工件和部署渠道尚未交付，生产SQL、发布或回退均未执行。

接口合同见 [API §7.9](API.md#79-启动配置准入与关闭边界)，合成本地验证入口见 [测试指南 P07c](测试运行指南.md#p07c启动准入关闭与原状态保真)。进度、外部接收owner、触发与关闭证据只由 [唯一登记](../../docs/plans/ai-cohort/review-register.md#接入与发布接收义务) 维护；本页不建立第二份状态台账。

## 1. 工件与环境准入

在任何目标操作前，由运维/DBA接收并核对：

- 固定当前代码commit/tree、对应实现与独立评审/Gate证据。本片冻结前基线commit为 `aa52476b0b62f9138501978965ae94a918932608`、tree为 `d0e78482815c8d8817a59805ce0fd1e201172ebe`；冻结提交为 `ad4eda63b9104e889a2fe429347f8562c3c85c60`、tree为 `56a67934670f97b6b3cb330be6ba38e6560c1749`，均不是完成实现/部署证据。P07c冻结输入为 [P07c Prompt](../../docs/plans/ai-cohort/tasks/P07c.md)，前序技术证据由 [P07b登记](../../docs/plans/ai-cohort/review-register.md#task-投影) 和 [P06技术Gate](../../docs/plans/ai-cohort/review-register.md#p06-gate) 拥有。
- 构建/运行使用Java 8；仓库 [pom.xml](../pom.xml) 的Spring Boot parent为 `2.2.0.RELEASE`、Java目标为 `1.8`。不以本地编译成功推断目标JVM、依赖或部署工件已核实。
- 经批准的当前工件与回退工件的文件名、来源、SHA256、代码commit/tree；各自配置版本/内容hash、兼容schema和秘密注入方式。工件hash与Git tree不是同一个值，必须分别记录。
- 目标MySQL版本、库身份、所有相关表InnoDB、实际JDBC连接的短REPEATABLE_READ事务与时区配置；生成列/utf8mb4/二进制索引及索引长度兼容。开发隔离MySQL8.4证据不能代替目标兼容性。
- 真实Principal映射、患者/访视/字典适配、访问权限、留存政策、医学/统计及字段/日期外发批准，按各自原触发接收，不用启动配置替代批准。

**操作待交付（AQC-EXT-04）**：目标host/库/账号、受控认证文件、工件存放与启动方式、流量切换/进程关闭渠道、当前及回退工件hash和配置版本、目标SQL预演/部署/回退证据均须原owner交付。本手册不猜测systemd/Docker命令，不启动 `RaApplication`，不访问生产凭据或目标数据库。

仓库根的只读工件核对命令形状：

```sh
git rev-parse HEAD HEAD^{tree}
git status --short
# 提交形成后只读检索本片实现对象；核对trailer/写集并以登记接收的固定对象为准。
git log --all --format='%H %T %s' \
 --grep='Workflow-Op: execute ai-cohort/P07c' -- \
 raapi/src/main/java/com/wenwen/ai/scope/AiFeatureAvailability.java
# 以下变量由运维填写并核实实际文件，未填写时不执行。
sha256sum "${RA_CURRENT_ARTIFACT:?需接收当前工件绝对路径}" \
  "${RA_ROLLBACK_ARTIFACT:?需接收回退工件绝对路径}"
```

配置内容hash在安全渠道核对，不将含凭据的配置复制到仓库、聊天、工件摘要或公开日志。

## 2. 启动配置矩阵

所有这些配置均在构造相应bean/context时接收；修改文件/PropertySource必须重新创建context或重启实例才生效。下表只列可公开键与约束，不复制 `application.properties`。

| 键 | 缺省与合法值 | 影响与核查 |
|---|---|---|
| `ra.ai.api.enabled` | 缺省字符串 `true`；只有精确 `true` 放行 | `false` 或其他已解析字符串关闭全部15条新增路由；匿名仍先401。无每请求热读取 |
| `ra.export.xlsx.enabled` | 缺省字符串 `true`；只有精确 `true` 放行 | global开启时仅关闭新 `/api/ra/export/xlsx`；申请/重试、规则/AI和旧CSV不受此键影响。global关闭优先返回整体原因 |
| `ra.ai.gateway.enabled` / `ra.ai.gateway.outbound-approved` | 均默认 `false`，仅规范 `true` / `false` | 任一关闭则不出站；非法值按原网关规则降级 `AI_CONFIG_UNAVAILABLE`。global开启而LLM关闭时parse仍可200/RULE、显式apply留安全审计 |
| `ra.ai.gateway.endpoint` / `model` | 生产endpoint固定 `https://api.deepseek.com/chat/completions`；model为非空 `[A-Za-z0-9._-]{1,64}` | 由GY/数据安全负责人接收实际配置/额度与外发范围；不开任意URL或新增词出站 |
| `ra.ai.gateway.api-key` / `RA_AI_API_KEY` | 无可用缺省key；服务器安全注入 | key只进入受控Authorization。凭据/原文访问留存方案由原owner接收，不写入仓库、消息、审计或日志 |
| `ra.export.smtp.enabled` | 默认 `false`；规范 `true` 才可能发送 | 关闭仍可记录申请，原通知协议返回明确FAILED/NOT_CONFIGURED；不是整体API关闭。非法字符串返回原有限配置错误 |
| `ra.export.smtp.host` / `port` / `from`、`ra.export.apply-mail-to` | 默认空；port 1..65535、唯一合法from/to | SK/管理员/运维接收真实地址与host/port。首次绑定from/to不得因改配置静默换收件人 |
| `ra.export.smtp.username` / `password` / `mode` | 凭据同时有或同时无；mode默认 `STARTTLS` | 生产强制STARTTLS、默认JVM信任链与hostname验证；测试 `LOOPBACK_PLAIN`、临时CA/PKCS12不用于生产。凭据经安全渠道注入 |
| `ra.ai.analysis.result-ttl-seconds` | 默认 `900`；正数且时间乘法/加法不溢出 | 分析/比较共享TTL；重新开启不延长旧ID有效期，无后台定时清理 |
| `ra.ai.analysis.result-max-bytes` | 默认 `8388608`；正数且≤16777215 | 保留结果/普通文件共享容量；超限真实拒绝，不截行；不把缩容解释为重新计算旧run |
| `ra.ai.analysis.cursor-key-base64` | 默认空；标准Base64解码至少32字节 | 生产key仅安全注入，不生成/记录；缺/错key在相关分析SQL前503，轮换使旧cursor失效 |
| `ra.export.free-months` | 默认int `6`，新功能仅允许已绑定值 `6` | 不用非法值充当关闭门；其他已绑定整数让新导出/首次申请拒绝503，坏文本保持原Spring绑定失败，还会影响旧ExportService |

两个新门不使用primitive boolean宽松绑定。已解析空串、`TRUE`、`False`、` true `、`1`、`yes`均关闭，且不会仅因这些值让新bean/context启动失败；此保证不覆盖其他Spring配置或外部PropertySource故障。

## 3. 关闭新请求

1. 运维先准备并接收下列无秘密目标启动片段，保留其他经核验的配置，尤其 `free-months=6`、TTL/cap与原cursor key；不为关闭轮换key或改持久状态。

```properties
ra.ai.api.enabled=false
ra.export.xlsx.enabled=false
ra.ai.gateway.enabled=false
ra.ai.gateway.outbound-approved=false
ra.export.smtp.enabled=false
```

2. 经运维核验，用真实部署渠道启动接收此配置的新实例并切换新请求。**目标启动/流量切换操作待AQC-EXT-04交付**。本页片段不是已执行的关闭记录。未重新装配的旧实例继续使用其原构造值；滚动期间不能承诺所有实例原子关闭。
3. 对下列准确15条POST，在有效可信身份下验证global关闭时HTTP503、`success=false`、`code=SERVICE_UNAVAILABLE`、`data=null`、message=`队列功能暂不可用`及自身 `X-Trace-Id`。合法已存在资源body和畸形body `{` 均应在body前拒绝。匿名仍HTTP401/UNAUTHENTICATED，不被503掩盖。
4. 核查该次HTTP结束后、清理前body读取bytes=0、数据库借用/查询/写入=0、无新定义/回执/run/审计/申请、POI/文件生成=0、AI/SMTP新增请求=0。JSON错误响应自己的字节不等于文件字节；仅看到503不能单独证明零副作用，目标观测方式及无患者验证输入由运维接收。
5. 只关文件时，global=`true`、xlsx=`false`，仅xlsx返回503/null/message=`文件导出暂不可用`。其余路由继续原权限、输入、状态机；申请与retry不被文件门阻断。两个键均false时整体原因优先。

| Controller / 门 | 完整POST路由 |
|---|---|
| AiCohortController / requireApi | `/api/ra/ai/cohort`、`/api/ra/ai/cohortPatients`、`/api/ra/ai/resolveEntry` |
| AiParseController / requireApi | `/api/ra/ai/parse`、`/api/ra/ai/applyParse` |
| PrivateCohortController / requireApi | `/api/ra/cohort/save`、`/api/ra/cohort/list`、`/api/ra/cohort/open`、`/api/ra/cohort/rename`、`/api/ra/cohort/delete` |
| ComparisonController / requireApi | `/api/ra/ai/compare`、`/api/ra/ai/compareResult` |
| AiExportController / requireXlsx（先global后file） | `/api/ra/export/xlsx` |
| AiExportApplicationController / requireApi | `/api/ra/export/applyAnalysis`、`/api/ra/export/retryNotification` |

每个入口顺序为原 `principal.requireCurrent()` → 新门 → 原Clock/body/Service；不添加第二次鉴权，不在Service之后关闭。旧 `/api/ra/export/range`、`/api/ra/export/visitsCsv`、`/api/ra/export/apply` 及其他旧接口不受两键控制。本片旧兼容范围只包括range成功与visitsCsv/apply非法日期仍HTTP200业务code=400，不宣称重新验收完整旧CSV/真实鉴权。

已通过检查的调用继续原Service/事务/网络终点。开关不强杀在途、撤回已发字节、退款或回滚数据。真正关闭旧进程/context仍可能触发原网关/SMTP destroy语义；不能把启动门说成能保证被销毁context继续成功或强杀native DNS。

## 4. SQL清单与留存

六文件共 **7个新表**，全部InnoDB，无旧患者外键；以下SHA256是完整文件内容hash，不是执行结果。生产脚本目录（仓库根相对）为 `raapi/src/main/resources/sql/`。

| 文件 | 新表、目的与索引 | SHA256 |
|---|---|---|
| [20261007_ai_analysis_run.sql](../src/main/resources/sql/20261007_ai_analysis_run.sql) | `ra_ai_analysis_run`：有期完整分析数值/全U指纹/v1 payload/hash；主键id、`idx_analysis_expiry(expires_at_ms,id)` | `bde67e3e9a11eb1e6815c81f2d2152a31f0fb1e841c7332462bcb39078d37146` |
| [20261008_private_cohort_definition.sql](../src/main/resources/sql/20261008_private_cohort_definition.sql) | `ra_private_cohort_definition` 与 `ra_private_cohort_command`：长期不可变条件/成功回执；定义主键id、stored二进制active_name及 `private_active_name(owner_doctor_id,study_code,active_name)` 唯一、`private_list`；回执主键(owner_doctor_id,action,request_id) | `835990379ef696294499ec5a805715f87d6623e3aa98d3317cd7692b012a8800` |
| [20261008_ai_compare_run.sql](../src/main/resources/sql/20261008_ai_compare_run.sql) | `ra_ai_compare_run`：独立有期比较payload/hash/全U指纹；主键id、`idx_compare_expiry(expires_at_ms,id)` | `73fb256b9b80570dadee39595a0dbfbffc492b566d2ad2d491701dd3c47b87c6` |
| [20261008_ai_parse_audit.sql](../src/main/resources/sql/20261008_ai_parse_audit.sql) | `ra_ai_parse_audit`：仅安全解析/应用事件；主键event_id，无额外二级索引，utf8mb4 | `a8aeb6c12c29089bb102d79554deb0f4ea05e95ff353ba26581b1cd562c2bbd0` |
| [20261008_ai_export_audit.sql](../src/main/resources/sql/20261008_ai_export_audit.sql) | `ra_ai_export_audit`：25列生成/传输事实；主键id、`idx_export_actor_time(actor_doctor_id,created_at_ms)`，utf8mb4 | `69e5319c90a3515986106e031f27d232e0b7ddb1fd4811fca522bee7623e67e3` |
| [20261008_ai_export_request.sql](../src/main/resources/sql/20261008_ai_export_request.sql) | `ra_ai_export_request`：旧申请companion与attempt；主键(owner_doctor_id,request_id)、application_id唯一、`idx_owner_created`，utf8mb4_unicode_ci | `5373a68ab74fa6c5be9de77c835cdaffc367fde5b148e75ce1198f3f30b03349` |

旧 [20261006_export_application.sql](../src/main/resources/sql/20261006_export_application.sql) 是兼容前置表，不计入7个新表；文件SHA256为 `f3e89d5688c7267f5006f32cdc11d4cbec7f62f8b00948ad6df66249acf29a52`。先核目标已有 `export_application` 的列/引擎/默认值/主键及 `idx_doctor(doctor_id,create_time)`，不能因其脚本 `IF NOT EXISTS` 返回成功推断结构一致。若缺表，由DBA在原接收/授权范围内另行安排；不自动执行TODO里的其他旧迁移或研究编号/疾病回填。

访问/留存要求：

- 定义与成功回执长期保留；逻辑删除释放活动名称，不物理删回执，不恢复被删定义。
- 分析/比较按原共享TTL，创建时仅本表限清1000条过期行；无请求不承诺整点物理清除，已清缺行404、到期仍按原410。重开不延寿或重建旧ID。
- 解析审计、导出审计、旧申请/companion的访问权限、自由reason与摘要留存由外部owner接收；开关不增加查询/恢复管理API，不自动删除审计或申请。
- DDL部署账号与运行账号分离，按目标环境审批最小权限。运行新表分析/比较需SELECT/INSERT/DELETE，定义需SELECT/INSERT/UPDATE、成功回执SELECT/INSERT、解析审计INSERT、导出审计INSERT/UPDATE、companion SELECT/INSERT/UPDATE。旧源保持SELECT；申请只INSERT旧表并更新本片创建行的mail_to/mail_sent，不能获准批改旧status或旧患者/访视。

## 5. DBA只读核查与受控预演

先将账号、库与受控认证文件经安全渠道填写并核实，再执行；认证文件不得放仓库或公开输出。下列命令是形状，未提供目标值，不代表已连接或执行。`--defaults-extra-file` 必须作为mysql的第一个选项，不把密码放命令行或环境示例。

```sh
# 从仓库根核对六个新文件及旧前置；不能用目录通配执行所有历史SQL。
sha256sum \
 raapi/src/main/resources/sql/20261007_ai_analysis_run.sql \
 raapi/src/main/resources/sql/20261008_private_cohort_definition.sql \
 raapi/src/main/resources/sql/20261008_ai_compare_run.sql \
 raapi/src/main/resources/sql/20261008_ai_parse_audit.sql \
 raapi/src/main/resources/sql/20261008_ai_export_audit.sql \
 raapi/src/main/resources/sql/20261008_ai_export_request.sql \
 raapi/src/main/resources/sql/20261006_export_application.sql

# DBA核实AUTH_FILE、TARGET_DB、目标连接及权限后，在批准的受控渠道保存结果。
mysql --defaults-extra-file="${AUTH_FILE:?需接收受控认证文件}" \
 --database="${TARGET_DB:?需确认目标库}" --batch --raw \
 --execute='SELECT DATABASE(), VERSION(), @@session.transaction_isolation,
 @@session.time_zone, @@global.time_zone, @@system_time_zone;
 SHOW GRANTS FOR CURRENT_USER;'
```

若目标旧MySQL不支持 `transaction_isolation`，停止并由DBA明确该版本的等价 `tx_isolation` 核查语句，不以未知变量错误或全局值推断真实JDBC短RR。还须保存应用真实连接及事务隔离证据。上海政策日期由注入Clock/Asia/Shanghai计算，epoch毫秒与旧DATETIME `create_time` 分别核对，不盲改目标全局时区。

以下只读SQL用于列存在性/引擎，再对已存在表执行SHOW；缺表属于待执行项，不能让SHOW报错后照常启用：

```sql
SELECT TABLE_NAME, ENGINE, TABLE_COLLATION
FROM information_schema.TABLES
WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN (
 'export_application','ra_ai_analysis_run','ra_private_cohort_definition',
 'ra_private_cohort_command','ra_ai_compare_run','ra_ai_parse_audit',
 'ra_ai_export_audit','ra_ai_export_request')
ORDER BY TABLE_NAME;
SHOW CREATE TABLE export_application;
SHOW INDEX FROM export_application;
SHOW CREATE TABLE ra_ai_analysis_run;
SHOW INDEX FROM ra_ai_analysis_run;
SHOW CREATE TABLE ra_private_cohort_definition;
SHOW INDEX FROM ra_private_cohort_definition;
SHOW CREATE TABLE ra_private_cohort_command;
SHOW INDEX FROM ra_private_cohort_command;
SHOW CREATE TABLE ra_ai_compare_run;
SHOW INDEX FROM ra_ai_compare_run;
SHOW CREATE TABLE ra_ai_parse_audit;
SHOW INDEX FROM ra_ai_parse_audit;
SHOW CREATE TABLE ra_ai_export_audit;
SHOW INDEX FROM ra_ai_export_audit;
SHOW CREATE TABLE ra_ai_export_request;
SHOW INDEX FROM ra_ai_export_request;
```

DBA逐列核长度、类型、NULL/default、字符集/排序规则、主键/唯一键/索引次序、stored生成列表达式与实际引擎；不要将SHOW CREATE输出的格式差异误当语义相同或缺陷。权限核查结果/账号与目标连接资料只在受控渠道保存，不公开凭据。

受控预演/真实DDL执行顺序：

1. 保持新API、文件、LLM、SMTP关闭，先完成目标兼容/权限/留存及旧申请表核查。准备可恢复的环境备份和操作记录，由DBA确认，不在本Flow自动创建备份/迁移平台。
2. 在DBA批准的目标兼容预演库，按上表准确顺序逐文件执行并逐次保存UTC、命令、exit、完整错误与执行后schema；private文件含两条CREATE，必须逐statement确认。分析→私有两表→比较→解析审计→导出审计→申请companion共7表，旧申请表必须在新companion业务启用前存在并兼容。
3. 仅当DBA已核某文件全部目标表均缺失且获准执行，才使用下列命令形状。每次选择一个准确文件，不使用 `--force` 或一键目录循环；本页不给已填的生产命令。

```sh
# SQL_FILE仅可取上表准确DDL，由DBA核实“整个文件仍未执行”后填写。
mysql --defaults-extra-file="${AUTH_FILE:?需接收受控认证文件}" \
 --database="${TARGET_DB:?需确认目标库}" --batch --raw \
 < "${SQL_FILE:?需选准确未执行DDL}"
```

4. **部分成功时不盲重放**。六份新DDL都是原始CREATE，没有可重复迁移语义；MySQL DDL不能靠普通事务把整份/六文件整体回滚。遇错误保持入口关闭，依据完整错误与SHOW CREATE/INDEX确定哪些statement已落地、既存结构是否逐项匹配；由DBA从原文件提取仅缺失且已核实的statement续作，并再次核查。尤其private定义表已建、command未建时，不能整份重跑，也不能加IF NOT EXISTS掩盖漂移。
5. 不自动DROP/recreate，不改旧患者/访视，不回填历史CRP，不以“部署失败”删除已成功新表或申请。schema漂移/兼容不明确时停在EXT-04接收，先保留事实，再由有权owner决定修复。

## 6. 合成DDL证据与本地复验

真实fixture装配边界（源码静态核查）：

- `ExportApplicationHttpFixture → AiExportHttpFixture → ComparisonHttpFixture → PrivateCohortHttpFixture → CohortHttpFixture` 执行生产private/compare/export audit/request四文件，以及旧申请DDL。analysis使用测试 `ai-cohort/p03c/schema.sql`；该文件与生产analysis DDL逐字相同，SHA256均为 `bde67e3e9a11eb1e6815c81f2d2152a31f0fb1e841c7332462bcb39078d37146`。
- `AiGatewayHttpFixture → ParseCohortHttpFixture` 执行生产parse audit DDL并保留真实网关bean及字面loopback成功peer。
- 旧指南/登记拥有已实际运行的Java8/隔离MySQL8.4/HTTP/POI/loopback证据。上面静态装配与同字节核查只说明本片可复验输入，不单独证明P07c运行成功，更不证明目标库预演或全量历史数据质量。

本片准确命令由 [测试指南 P07c](测试运行指南.md#p07c启动准入关闭与原状态保真) 拥有；包含首最高损失delete、focused与完整普通选择，保持旧深结构方法排除、同datadir串行、clean与两个非零测试发现guard。本地context重建不是生产滚动部署。

本轮实际fresh clean验收：两新类18/0/0/0（2026-10-08T19:05:17.507656534Z～2026-10-08T19:06:05.218057219Z）；冻结focused选择10类168/0/0/0（2026-10-08T19:06:46.023839629Z～2026-10-08T19:08:43.707726414Z）；冻结完整普通选择48类457/0/0/0（2026-10-08T19:09:28.776111758Z～2026-10-08T19:12:11.598003594Z），三轮均exit0。focused恰原150方法+新增18，不能沿用P07b另一focused选择的151计数；完整普通恰保留原46类439唯一方法+本片两类18新增，另1旧P06a方法仅历史复用，未运行/改写/重放，源码458不是458 fresh。

三轮294项生产/测试/resources/POM/工具链输入逐项相同且与当前源码一致，inputs.json SHA256=`1d916145167c16041ac0ad42edf36d7d353bc8db49e7ba6b2e6616fbd3299354`。focused方法集合SHA256=`2acda272434582db607b0e56faa643f80366a25c69bc8eab9b51d38aa2f6d21b`；完整普通方法集合SHA256=`63e6d52f92b820878c4df1812e77f06c3ea528c5901b2982feedda843b74b3ac`。原XML SHA及纳秒mtime均在各自UTC窗口，归档copy2与最终module原报告逐字/mtime一致；focused XML元数据索引SHA256=`839a2659f752ce568c14f404e58456d90f30865262b5fdf2f6c285071d9265a4`，普通索引SHA256=`dbd70db9e0e5d14db643aadab2ce46bc4064223e92c5143135af09d89d0c262c`。原274个未获准修改的tracked生产/旧测试/resources/POM blob仍与freeze逐字相同；Controller仅新依赖/门调用、配置仅新bean，Service/Store/Mapper/DDL/旧fixture/POM未变。可复核临时源报告位于仓库根相对 `.tmp/ai-cohort-p07c/33-fresh-focused/`、`34-fresh-ordinary/`，固定实现与独立评审身份随后由唯一登记接收，临时报告不是生产预演或唯一持久结论。

本轮上述真实fixture在隔离库实际执行对应生产private/compare/parse/export audit/request与旧申请DDL，analysis测试schema与生产文件逐字相同；组合验证覆盖本节六DDL七表的合成预演输入。六组行为RED→GREEN、两门反事实目标失败/逐字恢复及关开状态/实例边界的实际观测见测试指南P07c；不把context重建算成生产滚动部署、目标库兼容或真实回滚接收。

## 7. 保留状态与通知人工处理

关闭前后用相同identity核查原定义D/version、filters bytes/hash、成功命令回执、有期分析R/比较K的payload bytes/hash及具体审计行。重新开启后D可动态open产生新分析，原R/K仅在原TTL、owner与完整U满足时重读；到期/清理不因重启恢复。文件门不把已有PREPARING/READY/WRITTEN等事实反向改写。

通知必须保存原applicationId/requestId/requestHash、完整持久字段、attemptId/attemptNo、原from/to、旧mail_to/mail_sent与已发生SMTP计数。真实已受理后settle回滚可留UNKNOWN/mail_sent=0；不把UNKNOWN改FAILED，也不把未发标SENT。

- `UNKNOWN`：人工在受控渠道核实原attempt、服务端受理/收件线索与原通知摘要，不能仅凭mail_sent=0判断未发。当前系统无自动重发、后台恢复或人工审批/grant入口；普通retry/同参apply只读现态。人工核实不授权改库状态、另发邮件或新建重复申请，任何范围外处置须另行批准。
- `SENT`：只证明最终SMTP250受理及本地SENT/mail_sent同事务已确认，不能证明管理员收件/送达、审批或文件许可；retry不再发。
- `FAILED`：只有明确可重试状态，显式retry经原claim后才同申请新attempt/attemptNo+1；保留原收发绑定，`RECIPIENT_CHANGED`不绕过。不是开门时自动重试。
- 关闭时新apply和U/S/F各retry都应503/零body/SQL/SMTP，逐字段不变；重开后同参apply仍只读回执，UNKNOWN/SENT不重发，FAILED仅显式retry。原free字段日期政策仍可NEED_APPLY，申请不解锁下载。

导出 `WRITTEN` 只说明本地servlet write/flush返回，delivery仍UNKNOWN；审计不是下载grant。各状态人工处理与留存仍按EXT-04/06及REL-02接收，不以合成peer或开关解除。

## 8. 整组应用回退与恢复

1. 先按§3拒绝新增请求；运维核各实际实例/流量边界，允许已准入调用按原Service完成，并按既有进程关闭语义收尾。新门没有强停或跨进程原子关闭保证。
2. 由运维核对已批准回退工件SHA256、配置版本、schema/数据格式兼容，再经真实部署渠道切换。**当前回退工件、工件hash与渠道尚未交付，待AQC-EXT-04**，不能把模板写成已回滚。若回退版本不认识本片两键，不能假定配置可控制它；先接收该版本实际准入/路由策略。
3. CRP阈值及全部共享消费者必须作为整组工件回退；不能仅回一个Controller/util形成2.3/2.7/4.1与其他消费者分叉。旧患者/访视、新表、既有payload/定义/审计/申请保留，不回写历史CRP、不恢复未核实复算，不删除新表“回滚”。
4. 回退中DDL/二进制替换或配置失败时继续关闭新增请求、保留已成事实、核清实际运行实例和结构，由运维续作；不自动重发UNKNOWN、退款、反标文件/邮件状态或重建旧ID。
5. 恢复前重新核schema/引擎/短RR/权限、当前工件与配置版本、可信身份/医学/外发政策及原cursor key策略。只在相应材料接收且获准后，按需要重启开启global与普通文件；LLM和SMTP独立接收、独立启用。开门本身不是批准。
6. 恢复后按原业务合同核有效定义/TTL内分页/比较与合法显式日期文件；原过期410/已清404、U变409和字段NEED_APPLY仍真实成立，UNKNOWN/SENT零新增SMTP。本地验证不能替代目标恢复/回退证据。

## 9. 外部验收交接材料

以下仅列所需材料，不复制可变进度/owner/关闭表。八项的当前状态、原触发与唯一接收操作见 [登记接入与发布接收义务](../../docs/plans/ai-cohort/review-register.md#接入与发布接收义务)，任何一项不能在本手册自行关闭。

- AQC-EXT-01：真实凭证/id映射、可信Principal适配固定实现与真实联调；合成provider不替代。
- AQC-EXT-02：真实源映射、历史质量、疼痛尺度与医学接收；未核实组成项复算继续禁用。
- AQC-EXT-03：权威旧字典表/主键/alias/维护路径及医学批准；合成DevelopmentDrugDictionary不替代。
- AQC-EXT-04：目标版本/引擎/隔离、六DDL七表与旧申请核查、权限/访问留存、配置版本、已批准当前/回退工件hash、真实预演/部署/关闭/回退证据。
- AQC-EXT-05：安全渠道key、endpoint/model/额度、无患者合成真实成功/受控失败与正常提示、外发/部署/原文访问留存（可明确不存）批准；loopback不替代。
- AQC-EXT-06：host/port/TLS、唯一from/to、安全凭据/模板、真实无患者SMTP受理与管理员收件分别确认；SENT不替代收件。
- AQC-REL-01：固定输入/政策参数、独立统计基准/差异、容差与医学/统计/业务批准，技术对拍不替代。
- AQC-REL-02：实际字段/来源日期目录、默认NEED_APPLY与显式子集、聚合覆盖/元数据/精度/隐私/传输边界及对外发布批准，合成文件不替代。

本片只准备运行材料，不联系第三方、不部署、不生成生产密钥、不操作目标SQL。Q27历史文件交付/Q28特殊格式、审批/grant、全量回填、热切换、管理API、性能benchmark均不在本片范围；P07最终出口按必要接收材料独立判断。
