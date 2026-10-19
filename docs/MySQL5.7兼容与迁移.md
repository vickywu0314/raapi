# MySQL 5.7 兼容与迁移

Date: 2026-10-19
Status: Current
Owner: 后端维护者；目标库与数据迁移由 DBA/运维接收
Scope: 当前 RA 的 MySQL 5.7.44 开发/集成测试基线及现行迁移入口；不授权或证明生产数据降级、部署与浏览器验收

## 1. 版本与边界

当前服务器基线为 **MySQL Community Server 5.7.44 / InnoDB**，Java 8不变。POM继续由 Spring Boot 2.2.0.RELEASE 管理 `mysql-connector-java:8.0.18`，驱动类保留 `com.mysql.cj.jdbc.Driver`。服务器5.7不等于驱动改成“5.7”；该驱动的[精确版本官方说明](https://dev.mysql.com/doc/relnotes/connector-j/en/news-8-0-18.html)明确支持 MySQL 5.7。不同服务器/驱动组合不能沿用本次实证。

MySQL5.7已于2023-10-25进入 Oracle Sustaining Support，见[官方生命周期公告](https://www.mysql.com/support/eol-notice.html)。采用5.7需要运维接收维护和安全风险，本次兼容调整不表示这是长期推荐版本。

本批只改源码、执行入口和合成验证；没有连接生产、改老患者或运行历史迁移。**不得把MySQL8 datadir/物理备份直接挂给5.7**。若目标当前是8，另建5.7实例，以经批准的逻辑导出、DDL转换、数据校验及切换/回退方案迁移；该操作仍待独立授权与目标接收。

## 2. 不兼容项与当前处理

| 消费者 | 8→5.7差异 | 当前处理 |
|---|---|---|
| 研究编号回填 | `ROW_NUMBER()`窗口函数不可用 | 当前只用[5.7日期化回填](../src/main/resources/sql/20261018_backfill_study_no_mysql57.sql)，相关COUNT确定性排名；历史8脚本保留 |
| 随访字段核对 | `JSON_TABLE`与`WITH` CTE不可用 | 当前用[只读核对工具](../../scripts/audit_legacy_fields.py)完整遍历JSON，旧SQL仅8历史参考 |
| 随访编辑fixture | `utf8mb4_0900_ai_ci`不可用 | 换`utf8mb4_unicode_ci`；原字节/NULL安全CAS及大小写/空格差异判据保留 |
| 审计拒写故障注入 | 5.7接受但忽略`CHECK` | 使用真实`BEFORE INSERT` trigger `SIGNAL`，仍验证已执行UPDATE、审计失败及全部回滚 |
| OCR alias唯一键 | utf8mb4 `VARCHAR(200)`完整800字节索引超过旧767字节前提 | DDL显式`ROW_FORMAT=DYNAMIC`，要求Barracuda/large_prefix；保完整唯一性，不改191前缀 |
| 保留载荷/查询会话 | 5.7默认packet4MiB，比较聚合独立默认cap8MiB；规范条件可能超过TEXT 64KiB | 服务器/测试明确`max_allowed_packet=64M`，Comparison Mapper保8MiB往返；分析只存MEDIUMTEXT规范query（应用≤128KiB），不再保留全结果blob |
| 患者搜索LIKE | 5.7中原隐式反斜杠转义对纯反斜杠关键词未匹配 | 绑定原文中不存在的单字符`ESCAPE`并转义`%/_`，避免转义符自身翻倍，保反斜杠与原LIKE/collation语义；实际HTTP回归特殊字符及200码点边界 |
| 私有定义状态测试观察器 | 5.7的CONCAT不能直接混合HEX/ASCII/连接排序规则 | 改为三列独立读取，保完整载荷HEX、hash和version逐值不变断言 |
| 测试runtime/native owner | 固定8.4包、`mysqlx=OFF`及无版本断言 | 固定官方5.7.44包与SHA256，删除8专用参数；所有独立JDBC fixture及Node owner在建表前拒绝错误版本 |
| 只读结构快照 | `wenwen.sql`来自8.0.43，含8专用collation与导出语法 | 保留历史；不作为5.7初始化/迁移入口 |

MySQL的[84118](https://bugs.mysql.com/bug.php?id=84118)与[39808](https://bugs.mysql.com/bug.php?id=39808)记录了unicode_ci下LIKE重复转义符的异常。本次保留LIKE与原collation，以原文codepoint集合从固定白名单选择不存在的单字符escape，使用绑定参数，不动态拼SQL。白名单只有ASCII可打印字符（不含空格，排除%/_）和BMP私用区E000–F8FF，共6492候选，不选控制符、代理或补充平面；这是一个业务兼容用例里的6492次绑定检查，不是6492个业务测试。普通listPatients/listPatientIds没有新增200码点上限，368码点关键词实际SQL正常空结果；若病态输入穷尽白名单则明确拒绝并保持零SQL，不截断、不清空keyword或略过WHERE。

MySQL5.7.44保留 `@@transaction_isolation`（自5.7.20支持）、JSON函数、二进制比较、stored生成列/索引和当前新表事务。没有为消除历史字符串而批量改写旧计划、证据或调研快照。当前Java行为与SQL实跑范围见[测试指南](测试运行指南.md#mysql57-compat当前基线)。

## 3. 实例准入

开发/测试与目标实例逐项核查，客户端packet也应允许64MiB：

```ini
[mysqld]
transaction-isolation=REPEATABLE-READ
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci
max-allowed-packet=64M
innodb-file-per-table=ON
innodb-file-format=Barracuda
innodb-large-prefix=ON
innodb-default-row-format=DYNAMIC
```

```sql
SELECT VERSION(),@@transaction_isolation,@@max_allowed_packet,
       @@innodb_large_prefix,@@innodb_file_per_table,
       @@innodb_file_format,@@innodb_default_row_format,@@sql_mode;
```

保留严格模式及`ONLY_FULL_GROUP_BY`，不以关闭模式掩盖兼容问题。应用URL、库名、账号与权限须显式配置；5.7默认认证与Connector/J8兼容，无需使用8的认证插件。`useSSL=false`仅限loopback合成测试，真实数据库TLS按目标安全配置接收。连接到8.x、MariaDB或H2的测试必须失败，不能报告5.7通过。

CI准备脚本使用官方[5.7.44归档](https://cdn.mysql.com/archives/mysql-5.7/mysql-5.7.44-linux-glibc2.12-x86_64.tar.gz)，SHA256 `798ff0a14cdc1ab259f8a3b6a7f683403e9bbaa85957d82e5370f82b27ba6805`，Ubuntu22.04依赖`libaio1 libncurses5 libtinfo5 libnuma1`，保留glibc/libcrypt/libstdc++运行库。新datadir独占、loopback绑定、原owner释放边界不变。Actions费用暂停期间本批仅本地验证脚本，不触发/重试远端CI。

## 4. 研究编号回填顺序

1. 核对实际schema、InnoDB及备份，在合成副本预演；维护窗口暂停新建患者/编号写者，使用没有未提交事务的新连接。
2. 尚未执行时先按原上线清单执行[患者列表结构变更](../src/main/resources/sql/20261003_patient_list.sql)，已有字段/索引不重复ALTER。
3. 只选择[20261018的5.7回填](../src/main/resources/sql/20261018_backfill_study_no_mysql57.sql)，不要再运行8窗口函数版本。只补NULL，已有编号和空串不动，日期顺序与同日数值id顺序保持原规则，单日仍上限99999。
4. 任一错误立即停止客户端，不用`mysql --force`；执行`ROLLBACK`并断开连接，解决唯一冲突后可从头重跑。临时计划表不会写老表；正式写入只有一个InnoDB UPDATE，唯一冲突整体拒绝。重跑先清理本连接同名临时表，成功后临时表退出。
5. 核对`no_study_no=0`、唯一性、固定日期/id的手工样本和受影响行；新建患者后续沿原StudyNoUtil。这里只验证合成行为，不代表真实回填已执行，也没有对大规模真实库作性能测量。

## 5. 随访字段只读核对

旧[字段核对.sql](老系统字段/字段核对.sql)保留MySQL8历史说明，5.7不要执行。Python3标准库工具只SELECT六模块，保RA research_type白名单；先由服务器JSON_VALID判断、JSON_EXTRACT规范化再取HEX，不写数据、不创建数据库函数/过程或临时业务表，无固定数组长度上限：

```sh
python3 scripts/audit_legacy_fields.py \
  --mysql /absolute/path/to/mysql \
  --defaults-extra-file /absolute/private/mysql-client.cnf \
  --database <AUTHORIZED_DATABASE> \
  --output-dir /absolute/private/new-field-audit
```

命令从仓库根运行。私有客户端配置仅账号所需只读权限；输出目录必须尚不存在，目录700、文件600。只输出完成/无效JSON计数，不在控制台记录原行、凭据或mysql错误原文。输出是三个UTF-8 JSON记录数组：`fields.json`（module/field/count）、`types.json`（另含type）、`options.json`（另含value，不同值≤40）。不生成CSV，防止不可信键/选项被表格软件当公式执行；包括`=`、`+`、`-`、`@`及制表/换行前缀的字符串均由JSON编码原样保存，不加前缀或截断。请用私有文本/JSON查看器打开，不转换为可执行表格单元格。

SQL NULL与JSON_VALID=0的模块跳过；NaN、Infinity、-Infinity、1e309不被Python宽松解析当有效数据。重复键沿5.7规范化取首值；INTEGER/UNSIGNED INTEGER/DOUBLE按服务器规范化数值及有符号上限区分。STRING/INTEGER/BOOLEAN及数组内标量参与选项，独立UNSIGNED INTEGER/DOUBLE/OBJECT/NULL不参与，保旧SQL类型白名单。100层嵌套等使服务器JSON_VALID报错时整体退出1，不产生部分报告或成功计数（可留下空私有目录；重试使用新目录）。数组不截长，键和值不按旧VARCHAR100/200截断；客户端按Unicode精确键/值分组，可能比旧数据库大小写/重音不敏感分组更细。输出可能包含敏感选项，只在已授权私有位置查看，不上传或外发。

## 6. 验证与仍未接收项

真实5.7基线证实旧0900建表失败，修collation后旧CHECK故障注入未拒写。新兼容用例验证窗口/CTE/JSON_TABLE不可用、日期/id/NULL/已有编号、冲突无部分写入及重跑、200字符四字节alias完整唯一性、当前比较Mapper8MiB回读及分析查询会话5000大long IDs/>64KiB MEDIUMTEXT往返（2026-10-19替代旧analysis blob Mapper路径）；原随访编辑、OCR和普通后端测试保原断言。Python覆盖10001项长数组、转义键、类型/≤40选项和坏JSON，另实跑5.7只读CLI链。

准确fresh报告数量与源码由本批实现交接/独立review接收，不把编译或exit0算测试成功。历史MySQL8证据仍只证明当时版本；当前合成SQL不代替目标数据库预演、真实身份/数据迁移、浏览器、医学或部署接收。
