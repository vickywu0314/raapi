# RA 类风湿研究平台 接口说明

## 通用说明

| 项 | 值 |
|---|---|
| 本地地址 | `http://localhost:8065`（端口 `server.port`） |
| 路径规则 | `/api/<病种>/<业务>/<接口名>`，如 `/api/ra/project/projectsData` |
| 请求方式 | 所有接口均为 `POST`，参数格式按各接口说明；AI 队列示踪入口使用 JSON 对象 |
| 在线文档 | 启动后访问 `http://localhost:8065/swagger-ui.html`，可直接在页面上调接口 |

### 统计范围：只含 RA

所有接口只统计 / 返回 RA 患者和 RA 随访：`research_type`（医患关系表、随访表都有）为 **0、1、2、3、4、7**。

| 值 | 含义 | 是否算 RA |
|---|---|---|
| 0 | 类风湿关节炎研究平台 | 是 |
| 1 | 益赛普研究系统 | 是 |
| 2 | 瘀血痹胶囊真实世界研究 | 是 |
| 3 | 尪痹胶囊 | 是 |
| 4 | 痹祺胶囊 | 是 |
| 7 | 疗效可视化研究 | 是 |
| 6 | 强直性数据库（AS） | 否 |
| 5、9、999 | 含义不明 | 否 |

包含哪些类型只在 `ProjectMapper.xml` 的 `raResearchTypes` 一处配置。

### 与老系统共用数据库的约定

老系统仍在使用这些表，新平台**不能影响老系统**：
- 老表只读；需要写老表时，只改用户实际修改的字段，JSON 内容保持老系统的字段名和结构，不认识的字段原样保留。
- 老表只允许**新增可为空或有默认值的字段**（如 `study_no`、`follow_cycle`），不改、不删老字段。
- 新功能的数据放新表（如 `patient_comorbidity`、`patient_audit_log`）。

### 统一返回结构 `DataResult`

JSON响应使用下面这个外层结构，业务数据在 `data` 里；文件成功响应按对应接口说明直接返回bytes。

| 字段 | 类型 | 中文含义 |
|---|---|---|
| `success` | Boolean | 是否成功：`true` 成功，`false` 失败 |
| `code` | String | 状态码：`"200"` 成功，`"400"` 参数错误，`"500"` 服务端错误 |
| `message` | String | 提示信息，失败时是原因，前端可直接展示 |
| `data` | Object | 业务数据（单个对象），各接口不同，见下文 |
| `dataList` | Array | 业务数据（列表），列表类接口使用；不用时为 `null` |
| `page` | Object | 分页信息，分页接口使用；不用时为 `null` |
| `params` | Object | 预留，目前为 `null` |
| `dataMap` | Object | 预留，目前为 `null` |

---

## 〇、登录（服务端会话）

| 接口 | 说明 |
|---|---|
| `POST /api/ra/auth/login` | 入参 `username`、`password`、`remember`（true = 会话 30 天，否则 12 小时无操作失效）。成功后建立会话（Cookie `JSESSIONID`），返回 `{username, doctorId, name, role}`；`name` 取 `user.name`。失败 `code=401`「用户名或密码错误」 |
| `POST /api/ra/auth/me` | 当前登录用户；没登录 / 会话过期 `success=false, code=401`。前端每个页面打开时调一次，401 回登录页 |
| `POST /api/ra/auth/logout` | 结束会话 |

账号（测试期）：`application.properties` 的 `ra.auth.accounts`，格式「用户名:密码:医生ID」，多个用逗号分隔；现配 `yanwenjing:ra2026:82394`（言文静）。正式账号体系确定后替换 `AuthService.check()`。
会话在后端内存里，后端重启后需重新登录。AI 队列分析（第七节）按会话里的医生鉴权，**不信任请求里传的 doctorId**；其他接口暂时仍按入参 `doctorId`（见 TODO）。

---

## 一、项目总览页

### 1.1 项目总览数据

页面顶部 6 个指标卡 + “研究执行概览”区块，一个接口全部返回。

| 项 | 值 |
|---|---|
| 页面 | 项目总览 |
| 地址 | `POST /api/ra/project/projectsData` |
| 代码 | `ProjectController.projectsData` → `ProjectServiceImpl.getProjectsData` → `ProjectMapper.xml` |

#### 入参

| 参数 | 类型 | 必填 | 中文含义 | 示例 |
|---|---|---|---|---|
| `doctorId` | Long | 是 | 当前登录医生 ID。总览是项目级统计，**目前不按医生过滤**，仅作接收 | `5065` |

#### 调用示例

```bash
curl -X POST "http://localhost:8065/api/ra/project/projectsData?doctorId=5065"
```

#### 出参（`data` 字段）

**顶部指标卡**

| 字段 | 类型 | 页面位置 / 中文含义 | 计算口径 |
|---|---|---|---|
| `enrolledPatients` | int | 已入组患者 | 全局 RA 患者（至少一条 RA 医患关系，含已脱落；不按 doctorId 限定） |
| `followUpCompletionRate` | number | 计划随访完成率（%），1 位小数 | `followUpDoneCount ÷ followUpDueCount × 100`；分母为 0 时返回 0.0 |
| `followUpDueCount` | int | 应随访人数（完成率的分母） | 未脱落、且入组已超过一个随访周期（90 天）的患者 |
| `followUpDoneCount` | int | 已随访人数（完成率的分子） | 上述患者中，最近 90 天内有随访记录的人数 |
| `dataQualityRate` | number | 整体数据质量（%），1 位小数 | 没有任何待处理质控问题的患者 ÷ 全部患者 × 100 |
| `centerCount` | int | 参与研究中心数 | 一期固定为 1 |

**研究执行概览**

| 字段 | 类型 | 页面位置 / 中文含义 | 计算口径 |
|---|---|---|---|
| `trackingPatients` | int | 跟踪患者 - 有效人数 | 未脱落的患者（`patient_relation_doctor.miss` 没有为 1 的记录） |
| `totalPatients` | int | 跟踪患者 - 总人数 | 同 `enrolledPatients` |
| `trackingRate` | number | 跟踪患者占比（%），1 位小数 | `trackingPatients ÷ totalPatients × 100` |
| `pendingFollowUpPatients` | int | 待随访患者 | 未脱落，且最后一次随访（无随访取入组日期）距今超过 90 天 |
| `pendingQcIssues` | int | 待处理质控问题（条数） | 每个患者命中一条质控规则算 1 条，规则见下表 |
| `qcIssuePatients` | int | 有待处理质控问题的患者数 | 至少命中一条规则的患者，去重 |
| `researchUsableRecords` | int | 研究级可用记录 | 随访日期、病情评估、辅助检查、治疗方案四项都不为空的随访记录条数 |

**质控规则（一期）**

| 规则编码 | 类型 | 中文含义 |
|---|---|---|
| `M_DAS28` | 缺失 | 所有 RA 随访中都没有共同严格原始标量读取与 canonicalCrp 认可的非负 CRP 存值；ESR不能替代，0有效，未来/无日期合法存值也满足存在性 |
| `M_BASELINE_LAB` | 缺失 | 最早完整 COALESCE 双日期时间的 RA 随访中无非空辅助检查；并列首访任一有值即可，无日期/无访视也缺 |
| `M_COMORBIDITY` | 缺失 | 既往史 p.jws 为空 |
| `M_MEDICATION` | 缺失 | 所有 RA 随访的 zlfa 都为空 |
| `L_DATE_ORDER` | 逻辑冲突 | 发病晚于确诊；随访早于发病；随访日期在未来 |
| `L_ASSESSMENT` | 逻辑冲突 | 不吸烟却填了吸烟年数/支数；不饮酒却填了饮酒年数/量；ACR/EULAR 评分不在 0~10 |

四类缺失集中使用开发政策 dev-missing-v04。原三M的空值仍为 SQL IFNULL(TRIM(value), '') IN ('','{}','[]','null')；普通非空文本只证明记录存在，不证明医学结构完整。两L保留原字段、双日期、NOW()和RA范围，不属于资料缺失。

总览的全局RA总数、四M加两L命中条数和问题患者数取同一次短REPEATABLE_READ源视图，退出连接后解析CRP及计算。仅L的患者可资料完整但降低dataQualityRate；百分比仍一位HALF_UP。其余总览指标保持原定义。读源/计算失败传播服务失败，不返回空COMPLETE或上次结果；未建设QC问题表、人工关闭或缓存。

随访周期 90 天可在 `application.properties` 里通过 `ra.followup.cycle-days` 修改。

#### 返回示例

```json
{
    "success": true,
    "code": "200",
    "message": "成功",
    "data": {
        "enrolledPatients": 20,
        "followUpCompletionRate": 0.0,
        "followUpDoneCount": 0,
        "followUpDueCount": 15,
        "dataQualityRate": 0.0,
        "centerCount": 1,
        "trackingPatients": 15,
        "totalPatients": 20,
        "trackingRate": 75.0,
        "pendingFollowUpPatients": 15,
        "pendingQcIssues": 47,
        "qcIssuePatients": 20,
        "researchUsableRecords": 20
    },
    "page": null,
    "dataList": null,
    "params": null,
    "dataMap": null
}
```

#### 失败返回

| 场景 | `code` | `message` |
|---|---|---|
| 数据库查询出错 | `500` | 获取项目总览数据失败（详细错误看后台日志） |

---

## 二、患者列表页

### 2.1 患者列表

列表、模糊查询、筛选、分页、页头汇总是同一个接口。**只返回该医生名下的患者**（`patient_relation_doctor.doctor_id`）。

| 项 | 值 |
|---|---|
| 页面 | 患者管理 → 患者列表（`patients.html`） |
| 地址 | `POST /api/ra/patient/patientsList` |
| 代码 | `PatientController.patientsList` → `PatientServiceImpl.listPatients` → `PatientMapper.xml` |
| 依赖 | 先执行 `sql/20261003_patient_list.sql`、`sql/20261003_backfill_study_no.sql` |

#### 入参

| 参数 | 类型 | 必填 | 中文含义 | 示例 |
|---|---|---|---|---|
| `doctorId` | Long | 是 | 当前登录医生 ID，只返回其名下患者 | `5065` |
| `keyword` | String | 否 | 模糊查询：姓名 / ID号 / 研究编号，包含匹配，不区分大小写 | `林` |
| `followStatus` | String | 否 | 随访状态：`active` 随访中 / `soon` 近期需随访 / `overdue` 随访逾期 / `pending_first` 待首次随访 / `withdrawn` 已脱落；不传 = 全部 | `soon` |
| `completeness` | String | 否 | 数据完整性：`complete` 数据完整 / `missing` 数据缺失；不传 = 全部 | `missing` |
| `page` | int | 否 | 页码，从 1 开始，默认 1 | `1` |
| `size` | int | 否 | 每页条数，默认 20，最大 200；页面下拉 8 / 16 / 32 | `8` |

排序：建档时间（create_date，空则 createDate）倒序，最后新建的在最上面；无建档时间的老数据排最后；同时间按 patientId 倒序。keyword的LIKE转义、collation及followStatus保持原SQL语义。

同一次短REPEATABLE_READ取得未筛选医生RA范围、原三M/两L、原始bqpg和经keyword/followStatus筛选的完整排序候选；退出后用共享四M政策过滤、计数，再分页。totalPatients/incompleteCount始终取未受keyword/followStatus/completeness影响的医生U；超出末页返回空items并保留total。详情的incomplete/missingItems同政策，DAS复用该快照解析值；详情其他基本字段和时间线不声称获得这一新快照保证。listPatientIds不截首屏，原ExportService只接收该完整筛选ID范围，原始导出内容不变。

#### 调用示例

```bash
curl -X POST "http://localhost:8065/api/ra/patient/patientsList" \
  --data-urlencode doctorId=5065 --data-urlencode keyword=林 --data-urlencode page=1 --data-urlencode size=8
```

#### 出参（`data` 字段）

**页头 / 分页**

| 字段 | 类型 | 页面位置 / 中文含义 | 计算口径 |
|---|---|---|---|
| `totalPatients` | int | 页头「共 N 位患者已建档」 | 该医生名下患者数（含已脱落），不受筛选影响 |
| `incompleteCount` | int | 页头「N 位资料待补全」 | `incomplete = true` 的人数，不受筛选影响；点击后按 `completeness=missing` 筛选 |
| `total` | int | 「共 N 条」与分页 | 符合当前筛选条件的条数 |
| `page` / `size` | int | 当前页码 / 每页条数 | — |
| `items` | Array | 当前页患者，字段见下表 | — |

**`items[]` 每一行**

| 字段 | 类型 | 页面列 / 中文含义 | 计算口径 |
|---|---|---|---|
| `patientId` | Long | 患者信息 · ID号 | `patient_basic_info.id` |
| `name` | String | 患者信息 · 姓名 | `name` |
| `gender` | Integer | 性别编码 | `gender`：1 男 / 2 女 |
| `sex` | String | 患者信息 · 性别 | 男 / 女；其它值为 `null` |
| `birthYear` | Integer | 患者信息 · 出生年份 | 截至本次 Clock 上海日期，由合法 15/18 位格式及日历提取生日（不校验真实性）；无可靠生日为 `null`，不以 legacy age/createYear 估算 |
| `age` | Integer | 患者信息 · xx 岁（页面显示「1964 年 · 62 岁」） | 截至本次上海日期按有效生日算整周岁；无可靠生日为 `null` |
| `studyNo` | String | 研究信息 · 研究编号 | `study_no`，如 `RA-20261003-00001` |
| `visitCount` | int | 研究信息 · 已随访 N 次；最近随访 · 累计 N 次 | 该患者随访记录条数 |
| `followCycle` | int | 研究信息 · 每 N 个月 | `follow_cycle`：3 / 6 / 12 / 24，默认 12 |
| `subtype` | String | 疾病资料 · 分型 | 截至本次日期，有日期的有效 RF/CCP 任一曾经阳性 → `血清阳性`；无阳性且有明确阴性 → `血清阴性`；无可分类证据 → `null` |
| `rf` / `ccp` | Object | 疾病资料 · 分型下方的 RF / CCP 结果 | 随访辅助检查 `fzjc.lfsyz`（类风湿因子 RF）/ `fzjc.kccpkt`（抗CCP抗体），各取截至本次日期**最新可分类且有日期**的随访；最新阴性可能与曾经阳性的 subtype 不同 |
| `rf.status` / `statusLabel` | String | 状态 | `negative` 阴性 / `low_positive` 低滴度阳性 / `high_positive` 高滴度阳性 / `positive` 阳性（滴度未定） / `untested` 无可分类的有日期结果；边界文本见 §7.1 共享政策 |
| `rf.value` / `uln` / `visitDate` | — | 化验原值 / 判定用的参考上限 / 取自哪次随访 | ULN 老数据没存，统一用配置 `ra.serology.rf-uln`（默认 20 IU/mL）、`ra.serology.ccp-uln`（默认 25 U/mL），待业务确认 |
| `latestDas28` | number | 疾病资料 · DAS28-CRP | 按本次注入Clock的上海asOfDate，取有日期且≤asOf的最新LocalDate有效 `bqpg.result.crpScore`，同日最大visitId（不按时分秒或SQL输入顺序）；数字/字符串等价，缺组成项仍接纳，0 有效，无医学上限。原值须非负，再两位 HALF_UP canonical；null/空白/未查/非法/负值继续历史查找，ESR 不补 CRP；均无有效值为 `null`（显示「DAS28-CRP 未提供」） |
| `das28Activity` / `das28ActivityLabel` | String | 疾病资料 · DAS28-CRP 后的活动度标签 | 按同一两位 canonical CRP 分级（当前开发默认，医学发布接收尚待确认）：`remission` 临床缓解 < 2.3；`low` 低疾病活动度 2.3 ~ 2.7；`moderate` 中疾病活动度 > 2.7 且 ≤ 4.1；`high` 高疾病活动度 > 4.1；无有效分值为 `null` |
| `comorbidities[]` | Array | 其他病史 | 保留 `patient_comorbidity` 原记录；空数组仅代表没有记录，不证明阴性 |
| `fmState` / `asState` | String | FM / AS 三态 | `TRUE` / `FALSE` / `UNKNOWN`；当前正向源缺行只能 UNKNOWN，未来 since_year 不作本次阳性 |
| `comorbidities[].code` / `name` | String | 病种编码 / 病名 | 如 `FM` / 纤维肌痛 |
| `comorbidities[].sinceYear` | Integer | 起病年份（弹窗用） | `since_year` |
| `comorbidities[].status` / `coreItems` / `treatment` | — | 弹窗：当前情况 / 核心指标 / 治疗 | 一期无数据来源，`null` |
| `comorbidities[].linkedStudyReady` | boolean | 对应病种库是否已接入 | 一期均为 `false` |
| `lastVisitDate` | String | 最近随访 · 日期 | 随访记录最大 `follow_up_date`，`yyyy-MM-dd`；无则 `null`（显示「暂无访视」） |
| `nextDueDate` | String | 下次应随访日期 | `lastVisitDate` + `followCycle` 个月；已脱落或无随访为 `null` |
| `followStatus` | String | 随访状态编码 | 按优先级命中即止：① 已脱落（本医生名下的 RA 医患关系 `miss=1`）→ `withdrawn`；② 无随访 → `pending_first`；③ `nextDueDate` 早于今天 → `overdue`；④ 14 天内到期 → `soon`；⑤ 其余 → `active` |
| `followStatusLabel` | String | 随访状态 · 标签文字 | 随访中 / 近期需随访 / 随访逾期 / 待首次随访 / 已脱落 |
| `incomplete` | boolean | 患者信息 ·「待补全」标签 | 命中任一「缺失」类质控规则（`M_DAS28` / `M_BASELINE_LAB` / `M_COMORBIDITY` / `M_MEDICATION`，与项目总览同一套） |
| `missingItems` | String[] | 缺失项 | 如 `["缺 DAS28 评分", "缺用药史"]` |

#### 返回示例

```json
{
    "success": true,
    "code": "200",
    "message": "成功",
    "data": {
        "totalPatients": 5,
        "incompleteCount": 2,
        "total": 5,
        "page": 1,
        "size": 8,
        "items": [
            {
                "patientId": 1,
                "studyNo": "RA-20240301-00001",
                "name": "林书豪",
                "gender": 1,
                "sex": "男",
                "birthYear": 1964,
                "age": 62,
                "visitCount": 1,
                "followCycle": 12,
                "subtype": "血清阳性",
                "rf": { "status": "high_positive", "statusLabel": "高滴度阳性", "value": "197", "uln": 20.0, "visitDate": "2026-09-03" },
                "ccp": { "status": "untested", "statusLabel": "未检测", "value": null, "uln": 25.0, "visitDate": null },
                "latestDas28": 6.68,
                "das28Activity": "high",
                "das28ActivityLabel": "高疾病活动度",
                "lastVisitDate": "2026-09-03",
                "nextDueDate": "2027-09-03",
                "followStatus": "active",
                "followStatusLabel": "随访中",
                "incomplete": false,
                "missingItems": [],
                "comorbidities": [
                    { "code": "FM", "name": "纤维肌痛", "sinceYear": 2019, "status": null, "coreItems": null, "treatment": null, "linkedStudyReady": false }
                ]
            }
        ]
    }
}
```

#### 失败返回

| 场景 | `code` | `message` |
|---|---|---|
| `followStatus` / `completeness` 取值不对 | `400` | 随访状态不正确：xxx / 数据完整性不正确：xxx |
| `page` < 1 或 `size` 不在 1~200 | `400` | 页码从 1 开始 / 每页条数应为 1~200 |
| 数据库查询出错 | `500` | 获取患者列表失败（详细错误看后台日志） |

---

## 三、患者详情页

页面：患者管理 → 点列表中的患者（`patient-visits.html?id=患者ID`）。三个接口都**只能查看自己名下的患者**，不在名下返回 `403`「患者不存在，或不在您名下」。

### 3.1 患者详情（基本信息 + 随访时间线）

| 项 | 值 |
|---|---|
| 地址 | `POST /api/ra/patient/patientDetail` |
| 代码 | `PatientController.patientDetail` → `PatientServiceImpl.getPatientDetail` → `PatientMapper.xml`（`getPatientBasic`、`listPatients`、`listVisits`） |

#### 入参

| 参数 | 类型 | 必填 | 中文含义 |
|---|---|---|---|
| `doctorId` | Long | 是 | 当前登录医生 ID |
| `patientId` | Long | 是 | 患者 ID |

#### 出参（`data` 字段）

| 字段 | 页面位置 / 中文含义 | 取法 |
|---|---|---|
| `patientId` / `studyNo` / `name` / `sex` / `birthYear` / `age` | 标题与标签 | 同患者列表 |
| `followStatus` / `followStatusLabel` | 姓名旁的随访状态 | 同患者列表 |
| `withdrawReason` | 脱落原因（已脱落时） | `patient_relation_doctor`（`miss=1`）的 `reason`、`other_miss_reason`、`note`，用「；」连接 |
| `subtype` / `rf` / `ccp` | 疾病分型 / RF / 抗CCP | 同患者列表；`subtype` 为 `null` 时显示「待补充」 |
| `mobile` | 患者手机号 | `mobile` |
| `cardNoMasked` / `hasCardNo` | 患者身份证号（后 4 位打码）/ 是否显示「眼睛」按钮 | `card_no` |
| `nation` | 民族 | `nation` |
| `marry` / `marryLabel` | 婚史 | `marry`：0 未婚 / 1 已婚 / 2 离异 / 3 丧偶；其它编码 `marryLabel` 为 `null`，页面显示「代码 N」 |
| `createDate` | 建档日期 | `create_date` |
| `followStartDate` | 随访观察起始 | 基线访视（最早一次随访）日期；无随访取建档日期 |
| `confirmDate` / `happenDate` | 确诊日期 / 发病时间 | `confirm_date` / `happen_date` |
| `followCycle` / `nextDueDate` / `nextDueDays` | 随访周期 · 下次随访 · 已逾期 N 天 / N 天后 | `follow_cycle`；下次 = 最近随访 + 周期；`nextDueDays` 负数为已逾期 |
| `latestDas28` / `das28Activity` / `das28ActivityLabel` | DAS28-CRP / 疾病活动度 | 同患者列表的存值接纳、两位 HALF_UP canonical 与 CRP 分层；不改 ESR |
| `height` / `weight` / `bmi` | 身高 / 体重 / BMI | `height`、`weight`；BMI = 体重 ÷ 身高(m)²，1 位小数 |
| `acrEularScore` / `acrEularLabel` | ACR/EULAR 2010 分类标准总分 / 结论 | `acr_eular_score`（空则 `acrEularScore`）；≥6 分「符合 RA 分类」，<6 分「暂不符合 RA 分类」，未评估为 `null`。老系统没存各部分选项（`acr_eular_info` 全空） |
| `diagnosisInformed` / `consentSigned` | 诊断结果告知 / 患者知情同意书 | 新表 `patient_ra_extra`：1 已告知 / 已签订，0 未告知 / 未签订，`null` 未填写（表未建时也为 `null`） |
| `waistline` / `heartRate` | 腰围（cm）/ 心率（次/分） | `waistline`、`xl` |
| `systolic` / `diastolic` | 血压：收缩压 / 舒张压（mmHg） | `xy_h`、`xy_l`；`xy` 是老系统另存的一份收缩压（与 `xy_h` 相同），`xy_h` 为空时用 `xy`。原样显示，不纠正（老数据有收缩压 < 舒张压、疑似填反的） |
| `smoking` | 吸烟史 | `smoke=0` →「不吸烟」；其它 →「吸烟 N 年 · 每日 N 支」 |
| `allergy` | 过敏史 | `gms`；为空且 `allergy=0` →「无」 |
| `familyHistory` | 家族史 | `jzs` |
| `pastHistory` | 其他病史 | `jws`（既往史） |
| `incomplete` / `missingItems` | 其他病史下的「资料待补全」 | 同患者列表 |
| `comorbidities[]` / `fmState` / `asState` | 常见相关疾病及三态 | 同患者列表；无关联记录不证明阴性 |
| `visitCount` | N 次访视 | 随访记录条数 |
| `visits[]` | 随访时间线（最近的在前） | `patient_follow_up_history` |
| `visits[].visitId` / `visitDate` | 随访 ID / 日期 | `id` / `follow_up_date` |
| `visits[].visitType` / `baseline` | 访视类型 | **时间最早的一次为「基线访视」**，其余为「常规随访」 |
| `visits[].doctorId` / `doctorName` | 记录医生 ID / 姓名 | `doctor_id`；姓名取 `user.name`，无则 `null`（页面显示 ID） |

### 3.2 查看身份证号明文

| 项 | 值 |
|---|---|
| 地址 | `POST /api/ra/patient/patientSensitive` |
| 入参 | `doctorId`、`patientId`（同 3.1） |
| 出参 | `data` 为身份证号明文字符串；无则 `null` |
| 页面 | 身份证号旁「眼睛」按钮，第一次点击时请求 |

### 3.3 修改记录

| 项 | 值 |
|---|---|
| 地址 | `POST /api/ra/patient/auditLogs` |
| 入参 | `doctorId`、`patientId`（同 3.1） |
| 依赖 | 先执行 `sql/20261005_patient_audit_log.sql` |
| 页面 | 「修改记录」默认收起，**展开时才请求**；老数据没有记录，返回空数组，页面显示「暂无修改记录」 |

出参 `data` 为数组，最新的在前：

| 字段 | 中文含义 |
|---|---|
| `time` | 操作时间 `yyyy-MM-dd HH:mm` |
| `action` | 动作：新建档案 / 修改档案 / 新增随访 / 编辑随访 / 删除随访 / 标记脱落 / 恢复随访 / 质控处理（决定标签颜色） |
| `visitId` | 涉及的随访 ID，档案类操作为 `null` |
| `detail` | 修改内容；多项用「；」分隔，字段修改写成「字段：旧值 → 新值」，页面把旧值标红删除线、新值标绿 |
| `operator` | 操作人，如「陈医生（研究者）」 |

**怎么记录**：表 `patient_audit_log`。以后的写接口（新建患者、编辑档案、新增 / 编辑 / 删除随访、标记脱落）在业务更新的**同一事务**里调用：

```java
List<FieldChange> changes = auditLogService.diff(修改前Map, 修改后Map, 字段中文名Map);
auditLogService.record(patientId, visitId, "修改档案", changes, 附加说明, doctorId, "陈医生（研究者）");
```

`diff` 只比较列出的字段、只记录有变化的；`record` 把变化拼成页面显示的 `detail`，同时把结构化明细存成 JSON（`changes` 列），以后可按字段统计和回溯。

### 3.4 标记脱落

| 项 | 值 |
|---|---|
| 地址 | `POST /api/ra/patient/withdraw` |
| 入参 | `doctorId`、`patientId`；`reason` 脱落原因（必填）：失访 / 撤回知情同意 / 不良事件 / 死亡 / 其他；`remark` 补充说明（选「其他」时必填，≤200 字） |
| 写库 | 老表 `patient_relation_doctor`：只改**本医生名下该患者的 RA 关系**的 `miss=1`、`reason`（选项）、`other_miss_reason`（说明）、`update_date`；其他医生 / 其他病种的关系不动 |
| 返回码 | 400 原因没选 / 404 不在名下 / 409 已是脱落状态 |
| 留痕 | 修改记录「标记脱落」：随访状态 随访中 → 已脱落；脱落原因 |
| 页面 | 患者详情「标记脱落」→ 弹窗选原因；成功后状态变「已脱落」，按钮变「恢复随访」 |

### 3.5 恢复随访

| 项 | 值 |
|---|---|
| 地址 | `POST /api/ra/patient/reactivate` |
| 入参 | `doctorId`、`patientId`；`remark` 说明（选填，≤200 字） |
| 写库 | 同上关系行 `miss=0`、`update_date`；**原脱落原因保留不清**（老数据、可追溯） |
| 返回码 | 404 不在名下 / 409 不是脱落状态 |
| 留痕 | 修改记录「恢复随访」：随访状态 已脱落 → 随访中；附原脱落原因、说明 |

---

## 四、访视详情页

页面：患者详情 → 点随访时间线中的一次随访（`visit-detail.html?id=患者ID&visit=随访ID`）。
页面同时请求两个接口：顶部患者信息用 3.1 `patientDetail`，下面 7 个病历模块用本接口。

### 4.1 访视详情（7 个病历模块）

| 项 | 值 |
|---|---|
| 地址 | `POST /api/ra/visit/visitDetail` |
| 代码 | `VisitController.visitDetail` → `VisitServiceImpl.getVisitDetail` → `VisitMapper.xml` |
| 权限 | 只能查看自己名下患者的随访，否则返回 `403`「随访记录不存在，或患者不在您名下」 |

#### 入参

| 参数 | 类型 | 必填 | 中文含义 |
|---|---|---|---|
| `doctorId` | Long | 是 | 当前登录医生 ID |
| `visitId` | Long | 是 | 随访 ID（`patient_follow_up_history.id`） |

#### 出参（`data` 字段）

| 字段 | 中文含义 | 取法 |
|---|---|---|
| `visitId` / `patientId` | 随访 ID / 患者 ID | 前端核对患者 ID 与地址栏一致 |
| `visitDate` | 随访日期 | `follow_up_date` |
| `visitType` / `baseline` | 访视类型 | 该患者时间最早的一次为「基线访视」，其余「常规随访」（与时间线同一规则） |
| `doctorId` / `doctorName` | 记录医生 | `doctor_id`；姓名 `user.name` |
| `filledCount` | 有内容的模块数（共 7） | — |
| `modules[]` | 7 个病历模块，按页面顺序 | 见下表 |

**模块与随访表字段对应**（字段含义见 `docs/随访字段字典.md`，业务已确认）

| `key` | 页面模块 | 随访表字段 |
|---|---|---|
| `history` | 病史病情 | `bsbq` |
| `exam` | 辅助检查 | `fzjc` |
| `assessment` | 病情评估 | `bqpg` |
| `tcm` | 中医诊断 | `zyzd` |
| `treatment` | 治疗方案 | `zlfa` |
| `adverse` | 不良反应 | `blsj` |
| `adverseEvent` | 不良事件 | `bblsj`（格式待确认，按原字段名显示） |

**每个模块 `modules[]`**

| 字段 | 中文含义 |
|---|---|
| `key` / `title` / `column` | 模块编码 / 中文名 / 随访表字段 |
| `filled` | 是否有内容；空串、`{}`、`[]`、`null` 视为未记录，页面显示「本次未记录」 |
| `groups[]` | 按字段字典分好的组：`title` 组名（如 血常规）、`items[]` 字段；字典外的字段在最后的「其他字段」组，按原字段名显示 |
| `groups[].items[]` | `key` 老系统字段名、`label` 中文名、`value` 显示值（多选用「、」连接）、`unit` 单位、`notChecked` 是否未查（`Wx` 标记为 true，此时 `value` 为「未查」） |
| `tables[]` | 清单（西药、中成药、中药饮片、中医外治）：`title`、`columns` 列名（只保留有内容的列）、`rows` 每行的值 |
| `images[]` | 图片：`title` 类别（化验检查报告、双手正位片、心电图、面部 / 舌面 / 舌底）、`urls` 地址 |
| `record` / `recordDate` | 字段存的是普通文字或 `{record, date}` 时的内容和日期；结构化 JSON 时为 `null` |

只读解析，不改老数据。

### 4.2 删除随访

| 项 | 值 |
|---|---|
| 页面 | 访视详情「删除本次随访」（弹窗填写删除原因） |
| 地址 | `POST /api/ra/visit/deleteVisit` |
| 代码 | `VisitController.deleteVisit` → `VisitServiceImpl.deleteVisit`（`@Transactional`，任何一步失败全部撤销） |
| 权限 | 只能删自己名下 RA 患者的随访，否则 `403` |

**入参**：`doctorId`、`visitId`、`reason`（删除原因，必填，≤ 500 字）

**出参**：`data` 为患者 ID（前端删除后回到该患者的随访记录）

**处理逻辑**（业务确认 2026-10-05）：
1. **物理删除**，不备份，不可恢复；老系统中也一并删除。基线访视也可以删，删除后日期最早的下一次随访自动成为基线访视。
2. 删除前，把后一次随访的 `last_follow_up_id` 改为指向被删这次的上一次（该字段有外键，链条接好才能删）。
3. 删除后，按剩下的随访重新计算老系统维护的字段：
   - `patient_basic_info`：`follow_up_count`、`last_follow_up_date` / `lastFollowUpDate`；
   - `patient_relation_doctor`（该患者、该随访的医生、该研究类型）：`follow_count`、`first_follow_up_date`、`last_follow_up_date`。
4. 写一条修改记录：动作「删除随访」，内容「基线访视 / 常规随访 日期；删除原因：…」。
5. 其它模块里引用这次随访的数据（如下一次随访病情评估里的「上次 DAS28」）保持原样，不重算。

**失败返回**：`400` 未填原因；`403` 随访不存在或不在名下；`409` 这次随访还被其他表的数据引用（外键），无法删除，已整体撤销。

### 4.3 编辑随访表单

| 项 | 值 |
|---|---|
| 页面 | 访视详情「编辑本次随访」→ `visit-edit.html?id=患者ID&visit=随访ID` |
| 地址 | `POST /api/ra/visit/visitEditForm`，入参 `doctorId`、`visitId` |
| 说明 | 基本信息只读（页面另调 3.1 `patientDetail`）；随访日期和 7 个病历模块可改 |

出参 `data`：`visitDate`、`visitType`、`version`（数据版本，保存时原样传回）、`modules[]`：

| 字段 | 中文含义 |
|---|---|
| `editable` / `record` | 模块是否可编辑；存的是普通文字等非结构化内容时为 `false`，只读显示 `record` |
| `groups[].fields[]` | `key`、`label`、`unit`、`type`、`value`（list 为字符串数组）、`wx`（未查勾选，无未查标记为 `null`） |
| `type` | `text` 文本 / `date` 日期 / `list` 多选（「、」分隔）/ `number` 数字 / `haq` HAQ 选项（无困难、稍有困难、很困难、不能进行）/ `computed` 保存时自动计算（只读）/ `readonly` 只读 / `wxonly` 只有未查勾选 |
| `tables[]` | 清单：`columns`（可编辑列）、`rows`（每行 `_row` 为原数组位置，保存时原样传回） |
| `images[]` / `others[]` | 图片、字典外字段：只读，保存时原样保留 |

### 4.4 保存编辑

| 项 | 值 |
|---|---|
| 地址 | `POST /api/ra/visit/updateVisit`，**请求体为 JSON**（`Content-Type: application/json`） |
| 代码 | `VisitServiceImpl.updateVisit`（`@Transactional`）+ `VisitEditor`（合并与重算规则） |

请求体：

```json
{
  "doctorId": 82394, "visitId": 18957, "version": "打开表单时拿到的 version", "visitDate": "2021-07-16",
  "modules": {
    "fzjc": { "fields": { "cfydb": "10" }, "wx": { "cmcfydb": false }, "tables": {} },
    "zlfa": { "fields": {}, "wx": {}, "tables": { "xyList": [ { "_row": 1, "drugName": "甲氨蝶呤", "dosis": "15" }, { "drugName": "叶酸", "dosis": "5" } ] } }
  }
}
```

**保存规则（老系统仍在使用，不能影响老数据）**：
1. 和数据库现值逐项比对，**只写有变化的字段**；没变化不写库（返回 `changedCount = 0`）。
2. 保持原值类型：原来是数字写数字、是字符串写字符串、是数组写数组；字典外字段、清单行里未显示的字段（如 `id`、`haveBadCost`）原样保留；字段顺序不变。
3. 清空字段写空串 / 空数组，不删除字段。清单：带 `_row` 的行在原行上修改；不带的是新增行（自动生成 `id`）；没传回的原有行即删除。
4. **病情评估计算项关联重评**：沿用既有肿胀 / 压痛关节计数和DAS28/HAQ公式，只在相关输入实际变化时重算或失效，不修复未编辑的历史分数。CRP清空、负值或标记未查只失效CRP评分；血沉清空、≤0或标记未查只失效ESR评分，另一评分的原值/类型不变。关节或患者总体评分（仅`ztScoreByPatient`，不以HAQ替代）变化时两项各自重评；明确空关节选择为0，零CRP/总体评分仍是有效输入。HAQ沿用八维各取已回答最大值后平均，某题清空但同维仍有答案可计算；任一维全缺使主`hqaScore`及既存`result.hqaScore`同时失效，别名不重复记修改条目。派生失效保留既存键：旧字符串置空串，其他类型置JSON null，不创建不存在的派生键。ACR20/50/70不重算。
5. 改随访日期：同时改 `follow_up_date` / `followUpDate`，病史病情里有 `followDate` 的同步修改；老系统的随访次数、首次 / 最近随访日期重算。
6. 打开表单后version不匹配在写前返回`409`。读取后保存窗口使用原文乐观比较：比较所有将替换模块；本次DAS重评且病情评估实际写回时，还比较病情评估与辅助检查的读取原文（含SQL NULL、空串、缺字段/只读旧模块及重评分数舍入不变）。仅HAQ/普通病情评估编辑不额外比较辅助检查；非本次写入或派生依赖的模块可独立并发修改且原文保留。JSON比较为null-safe、区分大小写的精确文本，日期编辑比较两套实际原日期而非显示日期；0行更新（含并发物理删除）返回`409`，不重试旧请求、不插入审计。
7. 写一条修改记录：动作「编辑随访」，内容逐项「模块·字段：旧值 → 新值」，自动计算项标「（自动计算）」。模块、日期/计数及审计在同一事务中提交；校验/SQL/审计失败向外返回失败并回滚，无变化零UPDATE、零审计。

出参 `data`：`changedCount` 修改项数、`changes[]` 修改内容。失败：`400` 格式错误（如数字、日期）、`403` 无权限、`409` 数据已被修改。

### 4.5 新增随访表单

| 项 | 值 |
|---|---|
| 地址 | `POST /api/ra/visit/visitCreateForm` |
| 入参 | `doctorId`、`patientId`（须在本医生名下、未脱落；脱落返回 409，先「恢复随访」） |
| 出参 | 结构同 4.3 编辑随访表单（模块 → 分组 → 字段 / 清单），另有 `visitNo` 第几次随访（1 = 基线）、`visitDate` 默认今天、`optionSets` 联动选项（主症 / 次症随证型，方剂随证型） |
| 自动带入 | 病史病情：发病时间、确诊时间取患者档案（与基本信息同一取法）；随访时间 = 随访日期；首诊时间 = 最早一次随访里填的首诊时间，没有则取最早一次随访日期（基线访视取今天）；男性患者「月经」默认选「男性」。医生都可以改 |

字段（4.3 / 4.5 共用）新增属性：`options` 选项（「存库值\|显示文字」时页面显示后者，存库用老数据写法）、`showIf`「字段:值」满足才显示（隐藏的字段不提交）、`optionsFrom` / `optionSet` 选项随上级字段变化、`notBaseline` 只在第 2 次及以后的随访显示。
`type`：text / textarea / date / number / list 多选（有 `options` 为勾选，否则文本框「、」分隔）/ radio 单选（存文字）/ radioList 单选（老数据存数组，如 `["阴性"]`）/ haq / computed / readonly。字段清单见 `docs/新增随访字段清单.md`。

### 4.6 保存新增随访

| 项 | 值 |
|---|---|
| 地址 | `POST /api/ra/visit/createVisit`（JSON） |
| 入参 | `doctorId`、`patientId`、`visitDate`（必填，不能晚于今天）、`modules`：随访表字段名 → `{fields, wx, tables}`（同 4.4） |
| 写库 | 老表 `patient_follow_up_history` 插一行，按老系统写法：`follow_up_date` = 随访日期、`followUpDate` = NULL、`status` = 1、`research_type` = 本医生该患者的 RA 关系、`last_follow_up_id` = 该患者时间最晚的一次随访、`doctor_id` = 当前医生；**没填的字段不写 key、没填的模块存 NULL**，填了的模块加 `finish: true`；`bblsj` 不写 |
| 计算项 | 肿胀 / 压痛关节数、DAS28-CRP / ESR、HAQ 按编辑随访同一算法算；`result.lastCrpScore` / `lastEsrScore` 取上一次随访的结果 |
| 同步 | 按编辑 / 删除随访同一规则重算患者表、医患关系表的随访次数和首次 / 最近随访日期 |
| 长度 | 模块 JSON 超过老表字段长度（bsbq 等 2000、bqpg 4000）返回 400 |
| 出参 | `visitId`、`patientId`、`filledCount` 填写了几个模块 |
| 留痕 | 修改记录「新增随访」：访视类型、日期、填写模块数 |

---

## 五、数据导出（患者列表「数据导出」按钮）

规则（业务确认 2026-10-06）：**近半年内**（当前月份往前 6 个月的 1 号至今，如 10 月为 4 月 1 日至今；`application.properties` 的 `ra.export.free-months`）的随访数据可直接下载；**更早的**需填写原因提交申请，申请发送到指定邮箱（`ra.export.apply-mail-to`，发邮件功能待实现，先记录到 `export_application` 表）。范围规则在后端校验。

导出范围：勾选了患者则只导出勾选的（只保留该医生名下的）；没勾选则导出当前查询条件下的全部患者（不分页）。
导出内容：所选时间段内的 RA 随访，每次随访一行；**姓名脱敏**（保留第一个字），不含身份证号、手机号。
列：患者ID、研究编号、姓名（脱敏）、性别、出生年份、年龄、随访ID、随访日期、访视类型、DAS28-CRP、DAS28-ESR、肿胀 / 压痛关节数、HAQ 健康评分、CRP、血沉、类风湿因子、主证、西药、中成药、本次是否发生不良反应。

| 接口 | 地址 | 入参 | 出参 |
|---|---|---|---|
| 可直接下载的范围 | `POST /api/ra/export/range` | 无 | `freeStartDate`、`today`、`freeMonths`、`mailConfigured`（弹窗默认日期） |
| 下载 CSV | `POST /api/ra/export/visitsCsv` | `doctorId`、`startDate`、`endDate`（yyyy-MM-dd）、`keyword` / `followStatus` / `completeness`（同患者列表）、`patientIds`（勾选的患者，逗号分隔） | 成功：CSV 文件（UTF-8 带 BOM，Excel 可直接打开）；失败：JSON，`code = NEED_APPLY` 表示开始日期超过半年需申请，`400` 日期不对 |
| 提交申请 | `POST /api/ra/export/apply` | 同下载，另加 `reason`（申请原因 / 用途，必填，≤ 1000 字） | `data` 为申请编号 |

依赖：先执行 `sql/20261006_export_application.sql`。

---

## 六、新增患者页（patient-create.html）

页面：基本信息与患者详情页一致；ACR/EULAR 2010 用弹窗评估，结果回显到基本信息。填完身份证号先调 6.1 查重，保存调 6.2。

### 6.1 身份证号查重

`POST /api/ra/patient/checkCardNo`（表单参数）

| 参数 | 必填 | 说明 |
|---|---|---|
| `doctorId` | 是 | 当前医生ID |
| `cardNo` | 否 | 身份证号；空则返回 NEW；格式不对返回 400 |

出参 `data`：

| 字段 | 说明 |
|---|---|
| `result` | `NEW` 新患者 / `OTHER_DISEASE` 已在其他病种库（如 AS），保存时复用档案、新增 RA 关系 / `RA_OTHER_DOCTOR` 已在 RA 库其他医生名下，可转到自己名下 / `MINE` 已在本医生名下 |
| `patientId` / `name` | 已存在时的患者ID、姓名 |
| `otherDoctorName` | `RA_OTHER_DOCTOR` 时所属医生（`user.name`，空则「医生 ID n」） |
| `otherDiseases` | `OTHER_DISEASE` 时所在病种，如 `["强直性脊柱炎"]`；未确认名称的显示「研究类型 n」 |
| `basic` | `OTHER_DISEASE` 时已有的基本信息（结构同 6.2 的 `basic`），用于预填 |

判断规则：同身份证号（忽略大小写、首尾空格）的患者里，有本医生的 RA 关系（research_type 在 RA 白名单）→ `MINE`；有其他医生的 RA 关系 → `RA_OTHER_DOCTOR`；只有其他病种 → `OTHER_DISEASE`。

### 6.2 新建患者

`POST /api/ra/patient/createPatient`（JSON）

```json
{
  "doctorId": 82394,
  "transfer": false,
  "basic": {
    "name": "张三", "cardNo": "110105199203046021", "gender": 2, "mobile": "13812345678",
    "nation": "汉族", "marry": 1, "confirmDate": "2024-05-01", "happenDate": "2024-01-10",
    "height": "162", "weight": "55.5", "waistline": "72", "heartRate": "78", "systolic": "125", "diastolic": "80",
    "smoke": 1, "smokeYears": 10, "smokeCountByDay": 5, "smokeStop": 0,
    "allergyHistory": "青霉素", "familyHistory": null, "pastHistory": null, "followCycle": 12,
    "acrEular": { "jointScore": 3, "serologyScore": 2, "durationScore": 1, "acuteScore": 1 },
    "comorbidities": [ { "code": "FM", "sinceYear": 2020 } ]
  }
}
```

| 字段 | 必填 | 存到 `patient_basic_info` | 说明 |
|---|---|---|---|
| `name` | 是 | `name` | ≤100 字 |
| `cardNo` | 否 | `card_no`（转大写） | 校验出生日期；同时算建档时年龄写 `age` |
| `gender` | 是 | `gender` | 1 男 / 2 女 |
| `mobile` | 否 | `mobile` | 11 位，1 开头 |
| `nation` | 否 | `nation` | 民族全称，如 汉族 |
| `marry` | 否 | `marry` | 0 未婚 / 1 已婚 / 2 离异 / 3 丧偶 |
| `confirmDate` / `happenDate` | 否 | `confirm_date` / `happen_date` | yyyy-MM-dd，不晚于今天；同时写入医患关系 |
| `height` / `weight` / `waistline` | 否 | `height` / `weight` / `waistline` | 数字，范围 30~250 cm / 2~300 kg / 20~250 cm |
| `heartRate` | 否 | `xl` | 20~250 |
| `systolic` / `diastolic` | 否 | `xy_h`（`xy` 同写一份）/ `xy_l` | 40~300 / 20~200 |
| `smoke` / `smokeYears` / `smokeCountByDay` / `smokeStop` | 否 | `smoke` / `smoke_years` / `smoke_count_by_day` / `smoke_stop` | smoke 0 无 / 1 有；有时才写后 3 项 |
| `allergyHistory` / `familyHistory` / `pastHistory` | 否 | `gms` / `jzs` / `jws` | ≤255 字 |
| `followCycle` | 否 | `follow_cycle` | 3 / 6 / 12 / 24，默认 12 |
| `acrEular` | 否 | 总分写 `acr_eular_score`；各部分插入新表 `patient_acr_eular` | 4 部分须全选或全不选；总分后端重算（joint 0/1/2/3/5、serology 0/2/3、duration 0/1、acute 0/1） |
| `comorbidities[]` | 否 | 新表 `patient_comorbidity`（source=manual） | code：FM / AS / SS / RA-ILD / RA-MS |
| `diagnosisInformed` / `consentSigned` | 否 | 新表 `patient_ra_extra`（`diagnosis_informed` / `consent_signed`） | 诊断结果告知 / 患者知情同意书签订：1 是 / 0 否；老表没有这两项 |

另外：`study_no` 自动生成（RA-今天-当天序号，冲突重试）、`create_date` = 当前时间、`follow_up_count` = 0；
医患关系 `patient_relation_doctor`：`doctor_id`、`patient_id`、`research_type` = 配置 `ra.patient.research-type`（= 0 类风湿关节炎研究平台）、`confirm_date`、`happen_date`、`create_date` = 当前时间，`follow_count` / `miss` / `revisit` / `last_verify_status` = 0。

身份证号已存在时（同 6.1 规则）：

| 情况 | 处理 |
|---|---|
| `MINE` | 409「该患者已在您名下（姓名，ID号 n）」 |
| `RA_OTHER_DOCTOR`，`transfer=false` | 409「该患者已在 某医生 名下，确认要转到自己名下吗？」 |
| `RA_OTHER_DOCTOR`，`transfer=true` | 把该 RA 医患关系的 `doctor_id` 改为本医生（原医生不再看到），写修改记录「转入名下」；不改基本信息 |
| `OTHER_DISEASE` | 不新建患者行：只更新医生填了且与原值不同的字段（不清空原有信息），没有研究编号的补上；新增 RA 医患关系；原病种（已确认的：6 = AS）记入 `patient_comorbidity`（source=auto）；修改记录写改了哪些字段 |

出参 `data`：`patientId`、`studyNo`、`action`（`created` / `linked` / `transferred`）。研究类型未配置时返回 400「新建患者的研究类型未配置」。

## 九、数据看板（dashboard.html）

`POST /api/ra/dashboard/summary`，入参 `doctorId`。**只统计该医生名下的 RA 患者**。口径复用：随访状态 / DAS28-CRP 活动度 / 资料完整性 / 合并症 = 患者列表（`PatientService.listAllPatients`）；逾期 / 到期 / 按期随访率 / 疑似失访 = 随访管理（第八节）。

| 字段 | 含义 | 取的方式 |
|---|---|---|
| `scale.total` | 总患者数 | 名下 RA 患者（含已脱落） |
| `scale.active` | 活跃患者 | 未脱落，且最近一次随访在 183 天内 |
| `scale.newThisMonth` | 本月新增 | 建档日期（`create_date`，空则 `createDate`）在本月 |
| `scale.withdrawn` / `scale.lost` | 已脱落 / 疑似失访 | 本医生 RA 关系 `miss=1` / 随访管理的 `lost` |
| `followup.overdue` | 已逾期 | 随访管理 `overdue` |
| `followup.due7` / `due30` | 7 / 30 日内待随访 | 已做基线、预计日期在今天到 7 / 30 天内 |
| `followup.pendingFirst` | 待首次随访 | 随访管理 `pending_first` |
| `followup.onTimeRate` | 按期随访率 % | （已做基线的计划数 − 逾期）÷ 已做基线的计划数 |
| `attention.notTarget` / `highActivity` | 未达标 / 高疾病活动 | 最近一次 DAS28-CRP 为中、高 / 高（>4.1） |
| `attention.highCrp` | 炎症指标升高 | 最近一次填了 CRP 的随访，CRP > 10 mg/L |
| `attention.fm` | 合并 FM | 常见相关疾病有 FM |
| `activity.dist` / `targetRate` | DAS28-CRP 分布 / 达标率 % | 最近一次评分；达标 = 缓解 + 低 |
| `quality.completeRate` / `byItem` | 资料完整率 % / 各缺失项人数 | 患者列表「待补全」同一规则 |
| `research.validSample` | 有效研究样本 | 有随访、资料完整、未脱落 |
| `research.totalVisits` / `visited30` / `visited90` | 累计访视 / 近 30、90 天有随访的人数 | |
| `risks[]` | 风险名单（各最多返回 50 人） | `rebound` 最近一次 DAS28-CRP 比上一次升高 ≥0.6；`high` 最近一次 >4.1；`overdue` 逾期超 90 天 |
| `recentHighCrp[]` | 最近炎症指标偏高（5 人） | 最近一次 CRP > 20 mg/L，按随访日期倒序 |
| `recentVisits[]` | 最近完成随访（5 条） | `label`：基线访视 / 第 N 次随访 |

页面上的「智能研究队列」暂不做（已隐藏）。

---

## 八、随访管理页（followups.html）

### 8.1 随访计划

| 项 | 值 |
|---|---|
| 地址 | `POST /api/ra/followup/plans` |
| 入参 | `doctorId` |
| 范围 | 该医生名下、未脱落（本医生 RA 关系没有 `miss=1`）的 RA 患者；只读 |
| 排序 | 预计日期从早到晚（逾期最久的在最前），预计日期未知的排最后 |
| 分页 | 不分页，检索（姓名 / ID号 / 研究编号）、按状态 / 周期筛选、分页都在页面完成 |

出参 `data` 为数组：

| 字段 | 中文含义 | 取的方式 |
|---|---|---|
| `patientId` / `studyNo` / `name` / `sex` / `birthYear` | 患者 | `patient_basic_info`；出生年份按身份证号 |
| `visitCount` | 随访记录条数（含基线访视） | RA 随访记录条数；只建档、没录随访为 0 |
| `nth` / `first` | 下次是第几次随访 / 下次是否基线访视 | 第一条记录是基线访视、不算随访：`nth` = `visitCount`（0 = 下次是基线）；`first` = `visitCount = 0` |
| `followCycle` | 随访周期（月） | `follow_cycle` |
| `lastVisitDate` | 最近随访日期 | `COALESCE(follow_up_date, followUpDate)` 最大值 |
| `dueDate` | 预计日期 | 最近随访 + 随访周期；没有随访取建档日期 |
| `days` | 距今天数（上海时区），负数 = 已逾期 | |
| `status` / `statusLabel` | 与患者列表同一规则：`pending_first` 待首次随访（还没有随访）/ `overdue` 随访逾期 / `soon` 近期需随访（14 天内）/ `active` 随访中 / `lost` 疑似失访 | 疑似失访：最近一次随访（没有随访取建档日期）距今超过 `ra.followup.lost-months` 个月（默认 24），优先于其他状态 |

页面统计：计划中随访、已逾期、14 天内到期、按期随访率（=(已做基线的计划数−逾期数)/已做基线的计划数，待首次随访不计入）都**不含疑似失访**；疑似失访单独一张卡片，点开筛出名单。列表默认不显示疑似失访，状态筛选选「疑似失访」才显示。

页面：点患者进入患者详情（`patient-visits.html?id=…&from=followups`），详情页「返回」用浏览器后退回到随访管理（保留检索条件和页码）。

---

### 6.3 编辑档案 · 表单当前值

| 项 | 值 |
|---|---|
| 地址 | `POST /api/ra/patient/editForm` |
| 入参 | `doctorId`、`patientId`（患者须在本医生名下，否则 404） |
| 出参 | `patientId`、`studyNo`（不可改）、`acrEularScore`（总分，老数据可能只有总分）、`basic`（字段同 6.2 的 basic；`acrEular` 为最近一次各部分评估，与患者表总分一致时才带出；`comorbidities` 带 `source`：manual 医生勾选 / auto 其他病种库自动记入，页面不能取消） |

### 6.4 编辑档案 · 保存

| 项 | 值 |
|---|---|
| 地址 | `POST /api/ra/patient/updatePatient`（JSON） |
| 入参 | `doctorId`、`patientId`、`basic`（同 6.2）、`changed`：医生实际改过的字段名数组（basic 里的字段名，如 `mobile`、`weight`、`acrEular`、`comorbidities`） |
| 规则 | **只更新 `changed` 里列出、且与库里原值不同的字段**；没改的字段不校验、不写（老数据里不规范的值原样保留）。吸烟 4 项（smoke / smokeYears / smokeCountByDay / smokeStop）作为一组一起改。收缩压同步写 `xy`（老系统读）。身份证号改成其他患者已用的 → 409 |
| 写库 | 老表 `patient_basic_info` 只写改了的列；确诊日期 / 发病时间改了时同步本医生 RA 医患关系上的同名字段；ACR/EULAR 改了：总分写 `acr_eular_score`、各部分插 `patient_acr_eular` 一行，清除评估则 `acr_eular_score` 置空；诊断结果告知 / 知情同意书写 `patient_ra_extra`；常见相关疾病：新勾的插入、取消的删除（只删 manual）、起病年份更新 |
| 出参 | `data` = 实际修改项数，0 = 没有变化（不写修改记录） |
| 留痕 | 修改记录「修改档案」：字段 旧值 → 新值 |
| 页面 | `patient-edit.html` 与新增患者共用表单和脚本（`patient-create.js`，body `data-mode="edit"`）；保存后返回来源页（浏览器后退） |

---

## 七、AI 队列完整分析与有期结果分页（P03_RESULT）

### 7.1 当前医生 RA 队列

本节 `/api/ra/ai/cohort` 使用§7.9整体启动门；下列原业务合同在准入放行后适用。

页面：当前交付 RA/now/6m/活动度/内部 id 及 sex/age/sero/cm 临床筛选及 tx 治疗/data完整性筛选、五卡四图/FM描述、人数和首10的姓名/研究编号及临床/评估/治疗来源。`completion=P03_RESULT` 表示描述/推断与单次数值保留、签名分页已接入；授权相似条件入口见§7.3，仍须由客户端显式调用本接口完成分析。私有定义保存/元数据生命周期与动态重开见§7.4，同源完整比较与有期重读见§7.5；规则解析、已交付受控AI网关及显式应用见§7.6；普通xlsx与申请通知见§7.7/§7.8，§7.9仅为启动准入；真实服务及对外发布仍须原外部接收。

| 项 | 值 |
|---|---|
| 地址 | `POST /api/ra/ai/cohort` |
| 请求 | UTF-8 JSON 对象，例如 `{"filters":{"studyCode":"RA","at":"now","act":"target","ids":["1","7"]}}` |
| 代码 | `AiCohortController` → `AiCohortServiceImpl` → `CohortSourceAdapter` → `AiCohortSourceMapper.xml` |
| 身份 | `PrincipalProvider.requireCurrent()` 提供映射过的正 long 医生 id；默认生产 bean 一律401，真实登录 Adapter 尚未接入 |

顶层仅接收 `filters` 与可选 `doctorId`；`filters` 缺省/null 按空对象处理。`doctorId` 缺省/null 不校验，否则必须是规范正整数十进制字符串且与可信医生相同；不一致403。请求 doctorId、伪造 header 和未经映射的 principal.name 都不能提供认证或扩权。

| filters 字段 | 支持值与缺省规则 |
|---|---|
| `studyCode` | 只支持 `RA`；缺省/null/空串为RA |
| `at` | 字符串 `now` / `6m`；缺省/null/空串为now；其他枚举/类型SQL前400 |
| `act` | `target` / `mod-high` / `remission` / `low` / `moderate` / `high`；缺省/null/空串为不限 |
| `sex` | 字符串 `F` / `M`；缺省/null/空串不限，未知性别不能匹配 |
| `age` | 字符串 `min-max` / `min-`，0～120、min≤max、两端含；缺省/null/空串不限，未知年龄不能匹配 |
| `sero` | 仅字符串 `"1"` 匹配截至本次日期曾经阳性；缺省/null/空串不限 |
| `cm` | 字符串 `FM` / `AS` / `none`；none 仅匹配 FM 与 AS 均 FALSE，当前正向实际源返回空；缺省/null/空串不限 |
| `tx` | 字符串 `csDMARD` / `bio` / `TNFi` / `JAKi` / `IL-6i` / `Abatacept`；缺省/null/空串不限，bio仅四种明确单靶向类别并集 |
| `data` | 仅字符串 `complete` / `missing`；缺省/null/空串不限，非法类型/值SQL前400；只使用四M，不混入逻辑L |
| `ids` | 仅规范正整数十进制字符串数组，最大 long 为9223372036854775807；缺省/null不限，`[]`严格空集合。内部数值去重、排序后与本人范围取交集 |

未知顶层/filters键、重复JSON键、非对象body、非法JSON/类型/枚举或非法id均400。q、duration、保存来源等后续条件明确拒绝，不能静默退化成无条件分析。原ids数组超过5000项返回413（去重前检查）；原UTF-8 body超过128KiB返回413，解析前最多读取128KiB+1字节。合法但不存在/无权限的id只过滤，不逐项透露原因。

#### 范围、源读取与评分口径

- U仅包含当前可信医生在**同一关系行**符合共享RA白名单的 `patient_basic_info.id`；按内部id唯一计数，脱落miss=1不默认排除，相同姓名不同id仍分别计数，孤立关系/访视不创造患者
- 五条固定批量SELECT（临床患者列、患者id、必要访视列（含zlfa）、合并症行、原三M缺失事实）在同一个短InnoDB REPEATABLE_READ事务/读取视图中执行。源事务释放session/连接后才解析必要JSON、纯计算和装配响应；旧源表访问仅SELECT；新增结果表的独立事务见7.2，任何失败不回部分数据或旧结果
- 一次请求以注入Clock固定上海asOfDate。now选择日期非空、≤asOfDate且CRP有效的最新临床日期；同日按访视id最大决胜，忽略时刻差异。最新坏值不挡历史有效值；下一新请求重新读取已提交源变化并重新评估Clock
- CRP仅取 `bqpg.result.crpScore`，数值/字符串保持十进制原标量精度并复用共享两位HALF_UP canonical。0有效；malformed JSON、null/负值/非法值、只有ESR不成为有效CRP，ESR不能补CRP。缺组成项仍可使用存量值，不启用未核实复算或写回历史
- C=U∩ids限制∩sex∩age∩sero∩cm∩act∩tx∩at资格∩data。at=now无act时无评分患者仍计入n；at=6m仅纳入可靠当前episode且有合格6m评分者，baseline缺失不单独排除；有act必须有所选时点有效CRP。target为canonical≤2.7，mod-high为>2.7；四档为<2.3、2.3～2.7、>2.7且≤4.1、>4.1

#### 成功 data（HTTP200，success=true，code="200"）

| 字段 | 实际含义 |
|---|---|
| `n` / `studyTotal` | 当前C人数 / 本医生U人数；studyTotal不受任何筛选影响 |
| `submittedUniqueIdsN` / `effectiveIdsN` | 去重提交数 / 提交集合∩U人数；均在临床/act/tx/at/data筛选前计算，未提供ids时两者null |
| `activity.current` / `base` | 本次at评估点 / baseline四档，均恰为remission/low/moderate/high四项，含level/label/count/value/status/displayText；value分母分别为evalN/baseN，空队列也有四个0/NO_DATA |
| `activity.baseN` / `baseUnknownN` | C内有效baseline人数 / n-baseN |
| `activity.evalN` / `unknownN` | C内所选at有效DAS28-CRP人数 / n-evalN；缺失与执行失败分开 |
| `activity.unknownTxN` | 最终C中治疗UNKNOWN/CONFLICT人数；NONE不计未知 |
| `analysisId` | 每次实时新分析成功提交后的canonical UUID；不按filters复用结果 |
| `patients` | total=n，完整C按das28At降序/null末尾/数值patientId升序；首10，含items/returnedCount/nextCursor/hasMore，后页见7.2 |
| `items[]` | name/studyNo来自同一源事务的patient_basic_info.name/study_no，缺失为null；patientId字符串、das28At/das28Base/das28Current/deltaDas28 canonical数值或null、activity编码或null、scoreProvenance/baselineProvenance、selection、crpAt/crpCurrent、clinical、clinicalProvenance、evaluation、treatment、qc |
| `stats` / `metricMeta` | 平面描述数值 / 对应七指标的分母、未知数、状态及显示文本，详见下节 |
| `byTx` / `lines` / `fm` | 治疗达标组 / 固定三线占比 / FM逐指标描述或诚实不足，详见下节 |
| `meta` | at、asOfDate、readStartedAt/readCompletedAt/computedAt ISO instant、policyVersions、traceId、supportedFilters、completion=P03_RESULT、expiresAt（UTC Instant）、patientProjection=LIVE_SOURCE_V04 |

有效值provenance为source=`LEGACY_STORED`、sourceVisitId字符串、sourceField=`bqpg.result.crpScore`、observedAt=`yyyy-MM-dd`、raw原标量文本及quality。存量至少 `LEGACY_UNVERIFIED`；已知TJC/SJC/GH/CRP任一缺失再标 `COMPONENTS_MISSING`，仅作质量说明，不代表医学已核实。无可选值时source/sourceVisitId/sourceField/observedAt/raw均null，missingReason=`NO_VALID_CRP`；quality只保留实际观察到的字段质量（如 `INVALID_JSON`）。仅在当次医生授权名单显示姓名/研究编号，不返回手机、身份证、生日或整份病例JSON；本片显示不授权导出姓名。

#### 描述统计与显示（开发政策）

同一请求先为U每患者派生一次治疗时间线与baseline/now/6m事实，然后筛C；研究基准只取同at的U，不继承ids、sex、age、sero、cm、tx、data或act条件。at=6m时C的资格规则不改变U人数，U中无合格评估者只增加研究基准unknownN。每次实时计算全C数值及聚合，再显式投影并保存完整排序结果；成功提交才发布新的analysisId/首屏游标，后页不重算旧数值。

| stats键 | 分子、分母与缺失 |
|---|---|
| cohortRate | C.n/U.n，unknownN=0；空C或U为0/NO_DATA，仍保留真实U分母 |
| ageMedian / durationMedian | C内已知年龄/确诊病程整年的精确中位；奇数取中间值，偶数两中间值平均，无有效值为null/NO_DATA |
| femaleRate | 已知F人数/C.n；sex=null计unknownN，未知仍留分母 |
| seroRate | 明确TRUE人数/C.n；UNKNOWN计unknownN，阴性与未知分开 |
| targetRate / evaluable | canonical所选DAS≤2.7人数/C.evalN / C.evalN；unknownN=C.n−evalN；无评分为0/NO_DATA，不把缺评分当零值 |
| completeRate / incompleteCount / qcUnknownN | COMPLETE人数/C.n / MISSING人数 / 明确QC UNKNOWN人数；当前覆盖政策仅COMPLETE/MISSING，qcUnknownN=0，源或程序失败不伪装UNKNOWN |

metricMeta恰含cohortRate、ageMedian、femaleRate、durationMedian、seroRate、targetRate、completeRate七键。比例元信息含numerator/denominator/unknownN/status/displayText；中位元信息含validN/unknownN/status/displayText。比例原值为JSON数值0～1，BigDecimal按至少16位HALF_UP保留，显示整数百分比HALF_UP；中位原值精确，显示1位HALF_UP并去整数尾零；无中位显示“—”。显示值不覆盖数值。

activity.current/base仍固定四档及原count，每行新增value/status/displayText，分母分别evalN/baseN；unknownN/baseUnknownN仍按C.n扣有效n。byTx.groups按csDMARD/TNFi/JAKi/IL-6i/Abatacept顺序，仅有成员的ACTIVE已知组出现，含tx/n/evalN/targetN/rate/status/displayText。已知治疗但无评分仍保留n并显示rate=0/NO_DATA；unknownTxN仅UNKNOWN/CONFLICT，NONE只增加noCurrentTxN。studyTargetRate与studyTargetMeta为同at的U研究基准（meta形状同targetRate）。

lines.groups固定line=1/2/3，含line/count/value/status/displayText，分母C.n；unknownLineN独立，未知不补1线且不将已知线占比归一化。第一种靶向2线、第二种及以后3线的已有政策不变，真实药物史不封顶。

fm含nFM/nOther/unknownN/status/reason/rows，仅明确TRUE/FALSE进入两组，UNKNOWN不并入FALSE。任组成员<3则INSUFFICIENT_SAMPLE/GROUP_SIZE_LT_3且rows=[]；足够时OK/reason=null，固定das28/tjc/sjc/crp/pain/haq六行。每行包含metric/meanFM/meanOther/nFMValid/nOtherValid/difference/highlight/status/reason/displayFM/displayOther/displayDifference，仅取同eval访视合法value；无有效值的均值为null/显示“—”，双方有值才有差值和高亮，否则NO_DATA/MISSING_METRIC、difference/highlight=null。差方向FM−Other，均值至少16位；高亮用未舍入总和/人数的交叉乘比较绝对差，pain≥5、haq≥0.2、其余≥0.5；显示DAS/TJC/SJC/CRP1位、pain0位、HAQ2位。显示0.5或0.20不会将真实0.49999/0.19999升级为高亮。存在的每行追加下述test；描述差值/高亮保持原阈值，与P显著性独立。

真实FM源仍只有TRUE/UNKNOWN，真实HTTP诚实抑制比较表；真实pain.value仍null，不能猜尺度。明确合成derived FALSE与已确认尺度pain只能证明实际Service聚合器分支，不能证明真实临床源/医学接收。源显示随五SELECT一致读，独立writer在首SELECT后改姓名/编号时当前响应保持旧值、下一请求新值，无第六查询或N+1。

独立合成六人示例：U.n=6，ageMedian=50(valid5)、durationMedian=3(valid5)、femaleRate=0.5、seroRate=0.3333333333333333(unknown2)、targetRate=0.4(2/5/unknown1)、completeRate=0.6666666666666667(4/6)、incompleteCount=2。sex=F后排序C=[3,1,6]，n=3、cohortRate=0.5、ageMedian=55(valid2)、targetRate=0.5(1/2)，studyTargetRate仍0.4。显式ids=[]返回空C，studyTotal仍6，全部C比例0/NO_DATA、中位null、固定零活动/线桶、空groups/rows/items；无授权U时studyTotal=0。

#### 统计推断（dev-stats-v04开发政策）

实际cohort输出在源短事务结束后计算统计。byTx追加test与comparisonNotice="组间基线不同，差异不代表疗效差异"；fm存在的每描述行追加test，全部原描述字段/研究基准/高亮保留。meta.policyVersions追加statistics，completion=P03_RESULT，statisticalDisclosure="探索性分析，未作多重比较校正；组间差异不代表疗效或因果关系"。不做多重比较校正，不对U研究基准、活动或线数生成P。

test核心字段：method/status/pValue/displayP/significant/nA/nB/reason/warnings/policyVersion/testDenominator。OK时method为FISHER_EXACT、CHI_SQUARE、MANN_WHITNEY_EXACT或MANN_WHITNEY_ASYMPTOTIC；raw P有限且在[0,1]，双侧alpha=.05，significant仅按raw P<.05。P<.001显示<0.001，否则3位HALF_UP；.0496显示0.050仍显著，.05显示0.050且不显著。所有非OK的method/P/significant均null、displayP=—，reason明确；非有限/越界P及程序/库异常沿原503失败，不伪装正常缺失或返回旧值。

byTx.testDenominator=EVALUABLE_KNOWN_TREATMENT。按原五类顺序只纳入evalN≥5的已知治疗组，至少两组才可检验，否则INSUFFICIENT_SAMPLE/ELIGIBLE_GROUPS_LT_2。includedGroups是tx数组；excludedGroups是有描述行但evalN<5的{tx,n,evalN,reason:VALID_N_LT_5}数组；groupNs是纳入组{tx,n}数组，n=evalN。小组描述仍显示，UNKNOWN/CONFLICT/NONE及缺评分不补入任一组。恰两纳入组的nA/nB对应顺序有效n，多组或不足两组时二者null。

列联表为每组[canonical所选DAS≤2.7人数,其余有效人数]。engine先剔全零边际，剩不足2行/2列或总数0为DEGENERATE/ZERO_MARGIN；剩余非零行n<5为INSUFFICIENT_SAMPLE/VALID_N_LT_5。2×2任一期望频数<5用双侧Fisher，否则Pearson独立性检验（无Yates）；多组用Pearson整体P。用整数交叉乘精确比较期望与5，避免除法舍入或乘法溢出；数学期望恰5不判<5。实际RxC且超过20%期望格<5加SPARSE_EXPECTED_COUNTS，2×2无此警告。expectedBelow5Cells/expectedCellCount在正常计算表时是真实格数，不足或退化时null；不能宣称全部治疗组均入检验。

FM的三人描述门槛不变。testDenominator=NON_MISSING_EVALUATION_METRIC，A为明确FM TRUE、B为明确FALSE，UNKNOWN排除；每指标只取同evaluation访视，DAS取score，其余取该evaluation的value，逐字段剔缺后任何组n<5为INSUFFICIENT_SAMPLE/VALID_N_LT_5。3/3可有描述而无P。BigDecimal pooled compareTo排序/去重后把不同数值编码为1..k整数double交库，避免极近小数转double损失秩；.30/.300作为真实ties。描述原值/均值/差值不使用秩码。合格后仅一distinct值为DEGENERATE/ZERO_RANK_VARIANCE，无P/warning。无ties且min(nA,nB)≤8/max≤50显式EXACT，否则显式ASYMPTOTIC，双侧、tie及continuity校正开启，禁止AUTO；ties且任组n<10加SMALL_SAMPLE_TIES。无样本/退化warnings为空。

Java8使用唯一显式依赖org.apache.commons:commons-statistics-inference:1.3。独立输入/参数/期望及SciPy1.17.0版本记录见[测试资源](../src/test/resources/ai-cohort/p03b/independent-oracles.json)及[测试指南](测试运行指南.md#p03b真实统计消费与独立数值)。真实FM源仍无FALSE，pain尺度仍未核实，HTTP继续诚实不足；合成derived事实只证明实际聚合器数值，不代替临床源或最终医学/统计批准。

#### 实时资料完整性

首屏每条 qc={status,missingCodes,ruleVersion}，status 为 COMPLETE/MISSING，missingCodes按规则码稳定升序，ruleVersion=dev-missing-v04。四规则成功完成且无M才COMPLETE；有任一M即MISSING。SQL/程序异常或未完成覆盖不发布成功结果，HTTP503/data=null；不能把失败解释为完整或业务缺失。data与所有已支持条件为AND，U/studyTotal和effectiveIdsN含义不变。

M_DAS28是任意RA随访可用canonical CRP的存在性，独立于now/6m日期资格；仅未来或无日期评分可以QC完整但当前DAS未知。原三M及两L的精确字段/时间/哨兵见总览质控规则。新请求读取当前源重判，补全关闭缺失，删除/改非法重开；无QC持久化或人工关闭。

AI与旧列表/详情CRP、RF/CCP共同读取原始bqpg/fzjc，再交既有canonicalCrp/SerologyUtil；不先经SQL JSON_EXTRACT损失数字精度或接受重复键。严格重复键、malformed、trailing JSON整份模块拒绝，不能从非法fzjc另恢复合法CCP；原三M非空记录存在性不因此改变。旧RF/CCP保持原日期/时刻/id排序、截至asOf曾经阳性及最新可分类检测显示。

#### 共享临床政策与来源

`clinical` 包含 sex（F/M/null）、age、diseaseDurationYears（COALESCE(confirm_date,confirmDate) 至 asOf 整年，缺失/未来为 null）、sero/fm/as（TRUE/FALSE/UNKNOWN）。患者列表/详情实际复用 ClinicalPolicy 的精确年龄、曾经阳性和正向合并症政策；旧中文 sex 映射保留。年龄不缓存整数，不从旧 age/createYear 兜底。

血清学 ULN 共享配置为 RF20/CCP25；只接纳截至 asOf 的有日期有效结果。数字精确比较：≤ULN 阴性、ULN<值≤3×ULN 低阳、>3×ULN 高阳；支持 IU/mL/U/mL 后缀。明确「阴性」/-/—为阴性，历史「阳性」保持 low_positive；单独1～4个+为 positive。全匹配语法拒绝负数、非有限或夹杂文字。ULN20时 <20/≤20为negative；<100、>10、≥20未知；>20/≥21为positive，>60/≥60.1为high_positive；全角符号等价。未来/无日期结果不证明历史阳性。

FM/AS 仅有正向关联：存在非未来 since_year 行为 TRUE，否则 UNKNOWN；未知起年保留已记录阳性并标 DATE_UNSPECIFIED，没有完整阴性来源，不产 FALSE。`clinicalProvenance` 对每个临床值给出 source/sourceField/quality/missingReason；血清学 observations 记录来源访视/字段/日期/分类，合并症 observations 记录行id/起年。来源质量至少 LEGACY_UNVERIFIED；不输出身份证原文或生日。

`evaluation` 的 tjc/sjc/gh/haq/crp/pain 都取 at 选中的同一个有效 DAS28-CRP 访视，绝不拼入较新无CRP访视。每项包含 value/sourceVisitId/sourceField/observedAt/unit/quality/missingReason：

- tjc/sjc：bqpg.result.ytgjs/zzgjs，0～28整数，unit=count
- gh：bqpg.ztScoreByPatient，0～100；haq：bqpg.hqaScore，0～3；HAQ 不代替 GH。主键存在即优先，null/空/非法不退 alias；仅主键缺省读 result.hqaScore；双方有效且不同保留主值并标 DISCREPANCY
- crp：fzjc.cfydb，有限非负 mg/L；不新增公式复算
- pain：bqpg.tjScore 仅保留 scalar raw 文本，value/unit=null，quality 含 SCALE_UNVERIFIED，missingReason=UNVERIFIED_SCALE；尺度未经核实，不可汇总或猜换算
- 其他项缺失给 MISSING_VALUE/INVALID_VALUE/OUT_OF_RANGE/NON_INTEGER；无可用 CRP 访视时六项均 value=null、missingReason=NO_VALID_CRP

`meta.policyVersions` 为 statistics=dev-stats-v04、descriptive=dev-descriptive-v04、qc=dev-missing-v04、crp=dev-crp-v04、now=dev-now-v04、clinical=dev-clinical-v04、serology=dev-ever-serology-v04、treatment=dev-timeline-v04、visitMatcher=dev-visit-match-v04、drugDictionary=dev-drug-v04（注入字典时报告其固定版本）；supportedFilters 为 studyCode/at/act/ids/sex/age/sero/cm/tx/data，completion=P03_RESULT。真实关节映射、历史质量、疼痛尺度与医学接收尚未确认（AQC-EXT-02 OPEN）；合成证据不关闭该义务。

#### 治疗时间线开发政策

源读取包含同一访视批量SELECT的 `zlfa`，释放连接后只解析一次 `xyList` / `zcyList` 药物行的 drugName/startTime/endTime 与定位。cyList 的 zz/fj/bccy/qtbccy 和 zywzList 不作为此五类药物行；finish、tygc/tyyy、缺行、删行或空模块均不是结构化停药。非法JSON、record型、错误list/row形状为 INVALID_MEDICATION_SOURCE，不伪装空模块或停药。

默认开发字典只按trim后的精确通用名匹配：csDMARD为甲氨蝶呤/来氟米特/柳氮磺吡啶/羟氯喹；TNFi为阿达木单抗/依那西普/英夫利昔单抗/戈利木单抗/赛妥珠单抗；JAKi为托法替布/巴瑞替尼/乌帕替尼；IL-6i为托珠单抗/沙利鲁单抗；Abatacept为阿巴西普。genericId采用明确的 dev:通用名，不是真实旧字典主键。商品名/厂家变化不改变已识别identity；默认无品牌alias，商品名-only、MTX、任意长文本、category自由文字不提供身份。每次请求固定一个不可变字典视图；未映射药与已知DMARD同现亦为UNKNOWN/UNMAPPED_DRUG，字典执行异常仍503。

只用有合法visitDate且≤上海asOf的观察，按visitDate/id稳定排序；同日大id较新，已明确不同靶向generic的历史不丢失。日期须合法yyyy-MM-dd，start>end、非法日期标 INVALID_TREATMENT_DATE，重复实体的相互矛盾日期标 DATE_CONFLICT，不任选首行作为可靠日期。合法显式start须属于本次方案段；仅由合法且无冲突的定义成员end退出形成的新集合，以end次日为estimatedStartDate、startDate=null、startConfidence=ESTIMATED_EXPLICIT_END，并标END_DERIVED_SCHEME_START，保留原始药物日期/来源；较晚重复旧start不升级此段为EXPLICIT。缺起始日以首次观察为 ESTIMATED_FIRST_OBSERVED，可靠startDate为空，不作为可靠基线/6m起点。未来start不提前进入当前方案/已用靶向史，到达Clock日期后新请求重新判断。

有靶向药时方案身份取靶向集合，增减辅助csDMARD不重启；纯传统药按集合变化分段。同generic重复行不增加实体数；单靶向加传统归该靶向，≥2个不同靶向（含两个TNFi）为CONFLICT，无类别/线数且不匹配tx或bio，保留实体和可靠组合日期（所有定义药物显式日期取最晚）。endTime是唯一结构化停止证据，结束当天仍有效，之后不沿用；无明确新方案/停止且未过end的空模块沿用并标 CARRIED_FORWARD，新未知/坏形状不证明延续。已知定义药全部明确结束且没有未知药时NONE；无药物观察是UNKNOWN。

第一种实际观察/起始不在未来的靶向generic为2线，第二种及以后均3线；实际不同generic的targetedDrugHistoryN不封顶。明确停后同generic再启生成新episode而不增加不同药物史；当前csDMARD展示1线并保留targetedDrugHistoryN。新明确方案将前episode边界截在其开始之前；baseline/6m只使用本次current episode的可靠起点，详见下节。

`treatment` 字段：state（ACTIVE/NONE/UNKNOWN/CONFLICT）、category（五类或null）、line、targetedDrugHistoryN、schemeDurationMonths（有效或估算起点至asOf的完整月数，非30天）、startDate（可靠日期）、estimatedStartDate、endDate、startConfidence、episodeKey、genericDrugIds、provenance（visitId/field/observedAt/quality/missingReason/dictionaryVersion）。内部不可变episode序列先按visitDate/id接收观察，再重放已接收事实的start/end次日事件，实际供应本次类别/线数/时长；当前asOf选择不剪掉已结束历史成员，到日无需新访视也能转换。provenance.drugFacts仅保留通用id、原始起止日期及visitId/field/observedAt定位，方案边界不覆盖原药物日期；key由源/实体/episode确定。无确定当前方案的线数/月数为null；不输出整份用药JSON、商品名/厂家或自由文本。

真实旧字典表/主键/别名/维护资料仍待SK及药品负责人提供（AQC-EXT-03 OPEN），开发精确映射与合成alias验证不构成真实字典接收或医学分类批准；医学发布前继续核对Q-11/12。实时四类缺失QC和本节描述聚合已接入；推断统计及有期结果分页已接入，仍不代表真实字典、医学统计或生产部署接收。

#### 统一访视匹配与配对（开发政策）

所有候选属于同患者、有LocalDate、≤本次上海asOf且有canonical有效DAS28-CRP；先排无效再排序。可靠起点仅取当前ACTIVE/CONFLICT episode的非null显式start，estimatedStart及provenance.drugFacts原药物日期不能升级；NONE/UNKNOWN不借用已结束历史段。可靠CONFLICT可有6m资格，仍不匹配tx/bio并计unknownTxN。

- baseline：可靠s的[s−90天,s+14天]，绝对距离最小、同距较早日期（≤s）、同日最大id；允许s之前的观察指定为本次episode基线，无值不回退入组基线
- now：截至asOf最新有效LocalDate、同日最大id；无可靠起点亦可展示now。早于当前可靠或估算方案边界时selection.quality标EVAL_BEFORE_SCHEME，不做该方案配对
- 6m：t=s.plusMonths(6)，[t−60天,t+60天]两端含，最近/同距早/同日大id；不晚于明确episode end（当天可用），下一方案开始前一天为旧段截止。t仍未来而早侧有合格已观察候选时也可入选；无候选eligible6m=false并从at=6m的C排除，U不缩小
- deltaDas28=baseline−eval，保留两位正/负/零，仅同患者/所评价episode、有效两端、baselineDate≤evalDate且eval在方案范围时计算。起点前baseline允许配对；缺任一条件为null并给deltaMissingReason，不输出自动发现或EULAR等级

selection包含at、episodeKey、baselineVisitId/date、evalVisitId/date、nowVisitId/date、target6mDate、eligible6m、quality、missingReason、deltaMissingReason；其中日期字段名分别为baselineDate/evalDate/nowDate。缺值显式null。baselineProvenance与scoreProvenance采用相同来源结构；基线缺失原因为NO_RELIABLE_START/NO_VALID_BASELINE。配对缺失区分NO_RELIABLE_START、MISSING_BASELINE、MISSING_EVALUATION、PATIENT_MISMATCH、EPISODE_MISMATCH、UNDATED_PAIR、BASELINE_AFTER_EVALUATION、EVAL_BEFORE_SCHEME、EVAL_AFTER_SCHEME。

crpAt是所选eval访视的evaluation.crp；crpCurrent是所有截至asOf有日期/有效非负化验CRP的最新LocalDate/最大id，可来自较新的无DAS访视。二者均为{value,sourceVisitId,sourceField,observedAt,unit,quality,missingReason}，unit=mg/L、有效sourceField=fzjc.cfydb；绝不将DAS分数当化验CRP，也不把当前化验补入旧eval的TJC/SJC/HAQ/GH/CRP。无eval的crpAt理由NO_EVALUATION，无有效当前化验为NO_VALID_CRP_LAB。

独立合成示例：s=2023-08-31→t=2024-02-29；baseline 2023-08-30=3.46，eval Feb29=2.19/HAQ1.25/CRP4，now Mar01=3.90，scoreless Mar02化验CRP12。at=6m返回das28Base3.46、das28At2.19、das28Current3.90、delta1.27、crpAt4@Feb29、crpCurrent12@Mar02。医学/真实字段与字典批准仍未验证。

旧列表listPatients与详情getPatientDetail的latestDas28/current活动分档，使用它们同次asOf和同一VisitMatcher.now；Mapper保留visitId/LocalDate、原范围/RA/SQL排序，原始bqpg改由共同严格标量入口校验，不再用SQL JSON_VALID预筛选。此口径替代旧SQL首有效、未来/无日期及full-datetime优先选择；未改变历史逐访视原值/编辑/导出，亦不宣称旧列表所有其他字段共享新源数据库快照。

每个本入口响应带 `X-Trace-Id`，成功时与meta.traceId相同。时间仅表示本次读取/计算，不是旧系统业务水位。

| 失败code | HTTP | 说明 |
|---|---|---|
| `UNAUTHENTICATED` | 401 | 可信身份不可用；默认生产配置明确拒绝 |
| `FORBIDDEN` | 403 | doctorId断言与可信身份不一致 |
| `INVALID_FILTER` | 400 | 不支持/非法JSON条件、重复或未知键 |
| `LIMIT_EXCEEDED` | 413 | 原数组>5000或原body>128KiB |
| `SERVICE_UNAVAILABLE` | 503 | 数据库或程序失败；不降为n=0/缺评分或旧结果 |

失败success=false、data=null、message为安全通用提示，不暴露SQL/病例/连接或凭据。认证及输入拒绝发生在业务源SQL前。真实登录、目标生产库/隔离配置、医学与发布接收仍待对应owner，合成验证不能替代这些接入证据。

### 7.2 同次结果授权稳定分页

本节 `/api/ra/ai/cohortPatients` 使用§7.9整体启动门；下列原分页与错误顺序在准入放行后适用。

`POST /api/ra/ai/cohortPatients` 仍先可信Principal，根仅接收非空字符串analysisId与cursor。UTF-8 body最多4096字节（最多读取4097），cursor最多2048 ASCII字符；未知/重复键、尾随JSON、缺项/类型错误400/INVALID_REQUEST，超body413/LIMIT_EXCEEDED，游标语法/签名或偏移错误400/CURSOR_INVALID。

成功data恰为analysisId、patients、meta。patients含total/items/returnedCount/nextCursor/hasMore；首屏10，后页20，真实offset10→30→50，不采用page×20。可重复读取同一签名游标；末页不足20按实际返回，无剩余则nextCursor=null/hasMore=false。空C也保存完整空结果，items=[]/total=returnedCount=0，绝不扩为U。

meta保留分析at/asOfDate/readStartedAt/readCompletedAt/computedAt/policyVersions/expiresAt，当前traceId与X-Trace-Id一致，原trace另作为analysisTraceId，增加displayReadCompletedAt；completion=P03_RESULT、patientProjection=RETAINED_NUMERIC_V1。旧访视/临床/治疗/图表数值与政策保持创建时快照，新cohort仍实时读取并新建ID；后页只返回名单，不重复返回统计图表。

#### 当前范围、显示与保留隐私

- 新表ra_ai_analysis_run存owner、全U范围指纹、sort_key、payload_version=1、完整UTF-8数值JSON及SHA256、epoch毫秒created/expires；不与旧患者表建外键
- 指纹为SHA256(医生十进制ID+LF+RA+LF+完整U去重数值升序每ID+LF)，不是C或本页。后页先核资源owner，再于独立短REQUIRES_NEW/REPEATABLE_READ只读视图读取当前完整U并比对；删除/撤权/新增U成员均409/SCOPE_CHANGED，含C外成员，miss不默认排除
- U匹配后同一视图仅一次批量查询最多20个id/name/study_no，用绑定参数及相同doctor+RA EXISTS限制。读完释放后组装，名称/研究编号取当前显示，列null可返回；覆盖缺行或SQL错误503，不能用空姓名冒充记录。无card_no/病例JSON查询或N+1
- 载荷用逐层显式allowlist，保留全C合法数值、baseline/eval/now访视ID/日期、资格/缺失/质量、临床来源结构、映射通用药ID和合法日期、聚合、规范filters、分析时间/政策/trace；永不保存name/studyNo、身份证/生日、RF/CCP原文、未知药名、原病例JSON或任意pain.raw
- 原live首屏仍保留既有raw诊断。后页score/baseline raw仅留已验证合法数值标量文本，精确长小数不经double；未核实pain仍value/unit=null、原质量/UNVERIFIED_SCALE保留，raw=null且rawRetention=OMITTED。任意源raw调试字段不保证跨页持久化。小数从最初解码使用BigDecimal，必要类型/行数/合法唯一ID/排序或hash损坏503，不补0、不回源重建

#### 配置、签名、时间与错误优先级

部署注入ra.ai.analysis.result-ttl-seconds（默认900，正数且乘法/时间加法不溢出）、ra.ai.analysis.result-max-bytes（默认8388608，正数且≤16777215）、ra.ai.analysis.cursor-key-base64（默认空，标准Base64解码至少32字节）。不生成/配置/记录生产key；缺key/配置错误让本功能503/SERVICE_UNAVAILABLE且cohort读源SQL前拒绝，不阻其它bean启动。容量按最终UTF-8字节超限413/RESULT_TOO_LARGE，不能写run或发布ID；缩小当前容量不重新计算旧有效run。key轮换会使旧游标签名无效。

cursor为Base64url无padding的固定JSON载荷加点号加HMAC-SHA256，含v=1、analysisId、doctorId、nextOffset、固定sort=DAS28_AT_DESC_NULL_LAST_ID_ASC、expiresAtMs；签名覆盖原载荷全部字节、MAC常量时间比较，不把请求字段替换为签名字段或让客户端owner授予权限。

顺序：可信身份→有界body/游标语法及MAC（400）→签名doctor不符404/RESOURCE_NOT_FOUND且零DB→请求入口时已到签名expiry则410/ANALYSIS_EXPIRED且零DB（行已清走亦同）→持久行缺失/非owner404→行到期410→行与签名版本/sort/expiry不符、hash/载荷/SQL/程序损坏503→offset非法400→全U改变409→同视图显示并返回。不存在部分页、跨run恢复、旧源回退或对外SQL/载荷/key信息。

created_at_ms取本次computedAt的epoch毫秒，expires_at_ms加TTL毫秒，expiresAt从该最终毫秒值转UTC Instant；now≥expiresAt即到期，入口前1ms可读。源五SELECT事务先结束，全C计算/排序/allowlist编码及容量检查之后，独立写事务先DELETE expires_at_ms≤创建时点 ORDER BY expires_at_ms,id LIMIT 1000再INSERT，commit成功才构造成功响应。INSERT/commit前失败一并回滚新行与此轮清理，源表及有效旧结果不变；结果读亦是短独立只读事务，首次MyBatis SQL错误翻译的metadata读取共用该owner，异常返回前归还。

每次新创建只清理本新表最多1000条过期记录，下一创建继续；不读/输出其他医生载荷，不删有效run/旧患者，没有新增scheduler，无请求时不承诺整点物理清除，但过期永不能分页。commit后的HTTP写出/网络失败不能回滚数据库，可能留下无客户端成功ID的完整TTL孤立行，由后续清理收走，不自动重用该结果。合成验收实际覆盖HTTP writeInternal抛IOException，不宣称真实网络断连。

本接口不授予私有队列保存、下载、邮件、共享或AI访问权限。生产DDL/key/真实身份、目标隔离与数据保留政策仍须AQC-EXT-01/04及最终接收，不因本次合成开发完成而关闭；医学/统计批准和其余外部义务保持原边界。

### 7.3 授权相似患者条件入口

本节 `/api/ra/ai/resolveEntry` 使用§7.9整体启动门；下列原输入/身份/只读合同在准入放行后适用。

`POST /api/ra/ai/resolveEntry` 使用JSON根对象，恰接收两个非空字符串：`source="SIMILAR"`、`indexPatientId`。索引id采用现有规范正long十进制文本，不接受number、0、负值、前零、空白或溢出；source大小写精确，不接受数组、多source或其他类型。未知字段（包括doctorId、cohortId、compare、q、filters、ids）、重复键、尾随JSON、非对象/缺项/null/非法类型均400/INVALID_REQUEST，非UTF8编码或非法UTF8同样拒绝。body最多4096 UTF8字节，最多读取4097；超限413/LIMIT_EXCEEDED。上述拒绝在SQL前发生。

Controller先要求可信Principal，再固定入口Clock.instant，随后读body；匿名401/UNAUTHENTICATED且不读取body、不借连接，header/请求doctorId/principal.name不提供权限。以入口时刻固定Asia/Shanghai日期，沿现有SourceAdapter的本人doctor+RA同关系完整U、五条批量SELECT及短REPEATABLE_READ视图读取；先检查index在U，再查其PatientClinical。不存在、他人、非RA、物理删除或撤权统一404/RESOURCE_NOT_FOUND。SQL/程序错误503/SERVICE_UNAVAILABLE、data=null，不回退旧候选或解释为缺失。源owner在解析临床事实/条件装配及公开响应前退出，resolver只读，不创建或清理analysis run，也不需要结果签名key；cohort缺key仍按7.2返回503。

成功data恰含source、filters、missingBasis、status、canApply、reason、meta。source固定SIMILAR；meta恰含asOf（上海ISO日期）、readStartedAt/readCompletedAt（UTC Instant）、traceId（同X-Trace-Id）、policyVersion=`dev-similar-v01`。不会返回indexPatientId、姓名/研究编号、身份证/生日、病例JSON或临床provenance。

- sex为F/M则保留条件；未知不生成，missingBasis列sex
- 已知age在0～120则产生闭区间`max(0,age-5)-min(120,age+5)`；未知不估算，missingBasis列age。已知超出此域时UNSUPPORTED_BASIS、canApply=false、filters=null、reason=AGE_OUT_OF_SUPPORTED_RANGE，不静默删去年龄限制；missingBasis仍只列真实未知事实。既有临床事实可大于120，本接口不改ClinicalPolicy
- sero明确TRUE才生成字符串`"1"`；FALSE不生成且不是缺失，UNKNOWN不生成并列sero。沿原截至asOf曾经阳性政策，历史阳性后最新阴性仍TRUE，未来/无日期阳性不提供依据
- missingBasis按sex/age/sero固定顺序。至少生成一项限制时canApply=true，缺失表空为READY，否则PARTIAL_BASIS；filters经现有CohortQuery校验/规范化，全部键为studyCode/at/act/ids/sex/age/sero/cm/tx/data，studyCode=RA、at=now、其余未生成键为null
- 没有任何可生成限制时NO_BASIS、canApply=false、filters=null；全未知列三项，只有血清阴性且无sex/age时只列sex/age。不自动分析整体U或塞空ids。除UNSUPPORTED_BASIS外reason=null

canApply=true时客户端将filters原样包装成`{"filters":...}`调用7.1标准cohort；可显式追加/调整其普通条件，本入口不接收隐式合并。索引本人默认不排除，按自然条件匹配。候选只是当次授权事实产生的条件，不是权限凭证或analysis结果；两次HTTP各自固定asOf并重新读当前U/事实，后一次cohort不重新推断相似条件，也不保证index继续授权。旧候选不能让已撤权患者进入U。空C仍保留完整零桶/NO_DATA/null和真实analysisId，后页继续7.2当前范围复查、到期和保留数值规则。

本片提供SIMILAR与现有完整cohort/分页组合，私有定义另见§7.4，比较另见§7.5；规则问题解析、显式应用与已交付受控AI网关见§7.6及[P05开发技术Gate](../../docs/plans/ai-cohort/review-register.md#p05-gate)；合成验证不代表真实身份、医学/统计批准、生产DB/key/发布接收。

### 7.4 私有不可变条件定义与动态重开

本节save/list/open/rename/delete五路使用§7.9整体启动门；下列原业务、幂等与持久状态合同在准入放行后适用。

五路均为POST JSON：`/api/ra/cohort/save`、`list`、`open`、`rename`、`delete`。可信Principal先于body，资源和回执只按本人医生+RA限定；客户端doctorId/header/principal.name不赋权。匿名401且不读取body。每次响应有新的X-Trace-Id，成功data.meta.traceId相同，重放也生成新trace。统一DataResult：成功HTTP200/success=true/code="200"，失败success=false/data=null，错误不输出SQL或原输入。

请求只接受以下字段，未知/重复键、尾随JSON、非法UTF8/其它编码、不成对surrogate、非对象或错误字段类型在SQL前拒绝。save最多128KiB UTF8，其余最多4096 bytes，最多多读1 byte判超限。cohortId/requestId须标准小写UUID，不接受缩写、null/number或空白；version是1～Long.MAX_VALUE的JSON整数，不接受小数/指数/字符串。发生version溢出返回503。

| 路由 | 请求字段与含义 | 成功data |
|---|---|---|
| save | name、filters对象、requestId必需；note可省略或null。filters经§7.1同一个CohortQuery验证，不接收analysisId、问句、source或计算成员 | cohortId、version=1、meta |
| list | 仅page/size；默认1/20，page正整数且offset不溢出，size=1～100 | items、total、page、size、meta |
| open | 仅cohortId必需 | cohort、analysis、meta |
| rename | cohortId/version/requestId必需，name/note至少实际出现一项；省略保留，note:null清空，name:null拒绝；不能传filters | cohortId、变更后version、meta |
| delete | 仅cohortId/version/requestId，均必需 | cohortId、删除后version、meta |

name采用Java8 String.trim，仅首尾U+0000～U+0020被移除；结果1～30 Unicode码点，大小写及Unicode组合形式保持原样，NBSP不额外trim。note不trim，最多200码点，空串与null均保留含义。相同owner/RA活动名称由数据库UTF8字节唯一键兜底，其他owner可以同名。首次删除先CAS再逻辑删并version+1，释放活动名称；本人已删定义的新合法delete命令返回既有删除version，不受旧version限制、不复活或再+1。open/rename对已删或其他owner定义统一404。

list item/open.cohort恰含cohortId、name、note、完整规范filters、version、createdAt、updatedAt、currentN:null。两个时间由Clock epoch毫秒转UTC Instant。列表只返回本人未删定义，以createdAt倒序/id二进制升序，count与items在一个独立短REPEATABLE_READ只读视图中；超页正常空items，不查询临床表、不运行分析或填写人数。filters长期不可变，只保存显式筛选条件及用户输入ids；null ids不限，[]为空C，不把某次U交集、患者成员、人数、分析ID、姓名、身份证或病例JSON落入定义/回执。

open先完成本人有效定义的短读/codec验证并释放，再直接调用原§7.1完整analyze。每次重新读当前完整U/临床源，产生新的analysisId，保留原五卡四图/FM/统计/排序以及§7.2签名后页。显式ids每次重新交当前U；新访视、物理删除及撤关系影响下一open，原analysis数值仍是原创建快照。响应cohort固定本次读取的元数据，定义读取后并发rename/delete不追溯撤销已开始的分析；不承诺跨两个短读与网络的长事务。缺签名key时save/list/rename/delete仍可用，有效open沿原配置503且临床SQL前拒绝；不存在资源仍404，非法持久定义仍503。

SAVE/RENAME/DELETE的成功回执以owner/action/requestId唯一，业务结果只含cohortId/version，与定义变动在同一独立短写事务提交。首次trace保留在回执，本次响应trace独立。先验证body，再查回执，再看资源/活动/CAS/名称。同规范参数重放原结果，异参409/IDEMPOTENCY_CONFLICT；JSON属性顺序/空白、save ids顺序/重复及默认at等价，但rename省略与note:null不同。后续rename/delete不影响原save或原rename的重放。两独立实例竞争由数据库兜底；NAME/CAS或request键冲突后先结束原事务，再新短读已提交胜者回执，同参优先重放、异参优先幂等冲突，没有回执才返回实际名称/版本冲突。没有缓存或无限重试。

| code | HTTP | 含义 |
|---|---|---|
| UNAUTHENTICATED | 401 | 可信身份缺失，body/SQL未读 |
| INVALID_REQUEST | 400 | 根形状、元数据、UUID、page/version或严格解析失败 |
| INVALID_FILTER | 400 | 合法save根中的既有filters验证失败 |
| LIMIT_EXCEEDED | 413 | 原body或既有ids数组超过限额 |
| RESOURCE_NOT_FOUND | 404 | 不存在、他人或open/rename已删定义 |
| IDEMPOTENCY_CONFLICT / VERSION_CONFLICT / NAME_CONFLICT | 409 | 请求键异参 / 活动版本不符 / 活动同名 |
| SERVICE_UNAVAILABLE | 503 | SQL、持久定义/回执codec/非法值或程序失败；不回全U/空列表/旧分析 |

例：save `{"name":"Trial","note":null,"filters":{"act":"target","ids":["7","1"]},"requestId":"00000000-0000-0000-0000-000000000001"}`；返回data `{"cohortId":"00000000-0000-0000-0000-000000000100","version":1,"meta":{"traceId":"本次响应X-Trace-Id"}}`。list `{}`列出元信息；open `{"cohortId":"00000000-0000-0000-0000-000000000100"}`动态分析。rename `{"cohortId":"00000000-0000-0000-0000-000000000100","version":1,"requestId":"00000000-0000-0000-0000-000000000002","note":null}`清空说明并返回v2；delete使用同cohortId/version=2及新的requestId返回v3。

新DDL见[私有定义及命令表](../src/main/resources/sql/20261008_private_cohort_definition.sql)，两个新InnoDB表，无旧表写入/外键。活动名称用utf8mb4 VARCHAR(30)及二进制stored生成列唯一索引，filters用规范完整UTF8 BLOB/SHA256/v1，坏hash、缺键/额外键、重复/尾随、非规范bytes或未知版本均503。定义与成功回执长期保留，没有TTL清理或恢复已删资源。本片开发验收限于Java8/真实HTTP/MyBatis/loopback合成MySQL8.4；生产目标版本、DDL/隔离和长期留存归AQC-EXT-04，真实身份/医学统计及其余外部义务未关闭。同源比较及有期重读见§7.5，不授予共享、AI、文件或邮件权限。

### 7.5 同源两队列完整比较与有期重读

本节 `/api/ra/ai/compare` 与 `/api/ra/ai/compareResult` 使用§7.9整体启动门；下列原比较与重读合同在准入放行后适用。

`POST /api/ra/ai/compare` 的根对象恰接收必需的 `a`、`b` 和可省略的 `excludeOverlap`（默认false，显式值只能boolean）。每侧恰为 `{"source":"FILTERS","filters":{...}}` 或 `{"source":"SAVED","cohortId":"标准小写UUID"}`。合法FILTERS/SAVED两侧可以组合；同一侧混带两种来源拒绝。FILTERS空对象合法，完全复用§7.1的条件、默认now、ids:null不限与ids:[]空集。SAVED只读取本人未删定义并严格解码固定条件，投影本次cohortId和元数据version；不调用open的分析入口，不带name/note、旧analysisId、计算成员、snapshot、SIMILAR或q/AI。

可信身份先body；匿名401且body/SQL未读。compare原UTF8 body最多256KiB、最多读取上限+1；严格UTF8、重复键、尾随JSON、根/侧/类型和额外键校验。每侧保留原filters节点，包装为 `{"filters":...}` 后交同一CohortQuery，执行包装后128KiB以及去重前5000个ids的原限额。新协议不单独限制filters原文空白片段，也不按charset标头拒绝实际合法UTF8。两个侧的全部语法和条件先校验，再按A/B顺序读SAVED，之后判断规范at相同。FILTERS/FILTERS异at零DB；含SAVED异at最多定义短读，临床源零查询。非法输入400/INVALID_REQUEST，原字节或数组超限413/LIMIT_EXCEEDED；不会从坏定义回落全U。

比较固定一个started、同次上海asOf、一个字典snapshot、一次本人RA完整源U和一次派生。定义短读退出后才读取源；源五SELECT仍共用短REPEATABLE_READ视图，释放连接后才纯计算。`CohortComputation.derive/select` 由原analyze与新compare真实共用，沿原QC/临床/治疗/at/活动筛选及6m资格；原首屏、统计、DTO、分页和单队列v1不变。两个C先按患者id求交集，再逐指标缺失处理；排重时双方都减去完整交集，人数、聚合和日期覆盖重新计算。不调用两次analyze拼结果，也不创建单队列run。

成功data恰含 `compareResultId,studyTotal,a,b,overlapN,excludeOverlap,rows,meta`。studyTotal为完整U，overlapN为排除前交集人数，不返回交集id。每侧恰含 `source,filters,cohortId,cohortVersion,nBeforeExclusion,n,evalN,submittedUniqueIdsN,effectiveIdsN`；FILTERS id/version为null，SAVED为本次固定值；filters为完整规范条件。submitted/effective仍是显式ids去重数与先交U的数量，不因其它条件或排重改义。

rows按 `n,age,duration,das28At,crpAt,haqAt,female,sero,target,complete,activity,line` 固定12行；每行恰 `metric,kind,a,b,difference,test`。

- COUNT：每侧 `{n}`，difference/test均null，人数不检验。
- CONTINUOUS：每侧 `{validN,unknownN,q1,median,q3,iqr,status}`。年龄/病程沿原整年事实，DAS取所选at，CRP/HAQ只取同一evaluation访视，不补当前或其它访视。Type7以BigDecimal排序和精确线性插值，h=(n−1)p，p=.25/.5/.75；IQR=Q3−Q1，不先显示舍入。n=1四分位同值/IQR0；valid0四数null/NO_DATA，其余OK。difference=medianA−medianB，任侧空则null。
- BINARY：每侧 `{numerator,denominator,knownN,unknownN,value,status}`。女性F、sero TRUE、QC COMPLETE的显示分母是C.n；DAS≤2.7达标的显示分母是evalN。sex/sero UNKNOWN留在显示分母但不当检验阴性；当前真实QC只COMPLETE/MISSING，knownN=C.n，不制造第三态。target knownN=evalN、unknownN=C.n−evalN。比例沿原16位HALF_UP，分母0为0/NO_DATA；difference用原有理数计算100×(A−B)百分点，任侧分母0则null，不减两个已舍入比例。
- DISTRIBUTION：每侧 `{denominator,knownN,unknownN,status,groups}`，groups恰为 `{category,count,value}`。activity固定remission/low/moderate/high，分母evalN；line固定字符串1/2/3，显示分母C.n、knownN为三桶之和，未知另列不成为第四线。difference=null，每行只有整体test。

非COUNT的test核心沿§7.1 StatisticalPolicy：`method,status,pValue,displayP,significant,nA,nB,reason,warnings,policyVersion,testDenominator`；列联再含expectedBelow5Cells/expectedCellCount，非OK为null。连续检验分母NON_MISSING_METRIC；female/sero/complete为KNOWN_CATEGORY；target为EVALUABLE_TARGET；activity为EVALUABLE_ACTIVITY；line为KNOWN_LINE。nA/nB始终为该指标实际有效检验人数。

优先级为：未排重且有交集→OVERLAP/COHORTS_OVERLAP；否则任侧有效n<5→INSUFFICIENT_SAMPLE/VALID_N_LT_5；之后才真正调用既有双侧MW或列联检验。连续保留精确十进制秩/ties、EXACT/ASYMPTOTIC和continuity；binary输入每组[yes,no]，activity/line输入固定类别计数并沿原零边际裁剪、Fisher/Pearson及稀疏警告。全同值/单非零类别为既有DEGENERATE。raw P有限[0,1]、raw<.05显著；显示<0.001或三位HALF_UP，非OK方法/P/显著性为null且displayP=—。交换A/B描述交换、差反号、双侧P不变。

meta恰为 `at,asOfDate,readStartedAt,readCompletedAt,computedAt,expiresAt,traceId,policyVersions,sourceDateCoverage,completion,statisticalDisclosure`。completion=P04_COMPARE；原十项政策加comparison=dev-compare-v01，statistics仍dev-stats-v04；保留原探索性/非因果披露。started固定asOf，readCompleted/computed为真实完成时点，computed的epoch毫秒为created，expires为checkedAdd(created,共享TTL)。

sourceDateCoverage恰含a/b，各有除n外11指标；每项 `{validN,datedN,undatedN,missingN,minDate,maxDate,status}`，dated+undated=valid、missing=C.n−valid。有效0为NO_DATA，全有日期KNOWN，部分有日期PARTIAL，有效但均无可靠来源日UNKNOWN；dated0时min/max为null。DAS/target/activity用所选ScoreVisit日期，CRP/HAQ用同evaluation合法值已有observedAt；年龄/性别/病程、sero/line/QC保守undated，不用asOf、生日、确诊日或药史猜来源窗口。真实6m沿原最近访视规则，coverage反映选中访视，不能误传now或以asOf冒充来源日。P04只保存证据摘要，P06才判断xlsx字段与时间窗口。

新 [比较专表DDL](../src/main/resources/sql/20261008_ai_compare_run.sql) 建 `ra_ai_compare_run`，InnoDB、无旧表外键，含id、owner、完整U指纹、v1 MEDIUMBLOB/SHA256、created/expires毫秒与(expiry,id)索引。新codec独立allowlist双端校验完整键集、12行/枚举/规范条件/UUID/日期、计数分母与coverage、四分位/IQR/差值/比例/P状态及行时间；有效hash的语义坏载荷仍503。payload不保存compareResultId、姓名/研究编号/身份证/生日/原JSON/问句/name/note或患者明细；只有显式filters.ids可保留。

创建复用§7.2部署门槛（含签名key校验）、TTL与容量，没有新key。缺/错配置在临床源前503。最终UTF8 bytes超cap为413/RESULT_TOO_LARGE且零cleanup/INSERT；容量合格后，新结果owner在一个REQUIRES_NEW短写事务内仅清本新表expires≤created、按expiry/id最多1000行，再INSERT。提交成功才发布ID，任一SQL或post-INSERT/precommit故障回滚新行和本轮cleanup。定义/源事务此时均退出；不清单队列表，无后台缓存/清理。commit后的网络失败可能留下完整TTL孤立行；没有幂等key，合法重发可生成新run。

`POST /api/ra/ai/compareResult` 根仅 `{"compareResultId":"标准小写UUID"}`，最多4096原UTF8 bytes，同样严格解析。身份通过后、读body前固定requestedAt。顺序：身份→输入400/413→新表短find→缺行/非owner404→requestedAt≥expiry为410/COMPARE_EXPIRED→codec坏载荷503→独立短只读RR查询本人RA完整id并复验U指纹→变化409/SCOPE_CHANGED→固定完整表。当前U增、物理删、撤权均失效，包含不在A/B的患者；不读取display/姓名、临床/字典或SAVED定义，不重算旧值。普通访视/姓名/定义元数据更新不改旧表，真实fresh应用context可重读。清理后缺行404；没有signed expiry，不凭ID声称识别已清行的过期史。

read除meta.traceId为本次并追加comparisonTraceId为创建trace外，a/b/rows/交集/原时间政策和保留值一致。两接口均DataResult及新的X-Trace-Id；SQL/库/程序/损坏503/SERVICE_UNAVAILABLE、data=null，底层信息不外泄。定义失效404/RESOURCE_NOT_FOUND，范围变化409；不回旧值或部分结果。所有短事务/连接在成功或失败公开响应前退出；不承诺定义/源/当前U/网络跨多个owner的强原子性。

本片仅Java8、真实HTTP/MyBatis与隔离合成MySQL开发验证；八项真实身份/源/字典/目标DB/医学统计/发布义务未关闭，没有CI、生产DDL/部署、AI/SMTP或文件发送。[P04开发技术Gate](../../docs/plans/ai-cohort/review-register.md#p04-gate)已接收私有定义与比较的组合及证明边界；普通xlsx已交付见§7.7及[P06开发技术Gate](../../docs/plans/ai-cohort/review-register.md#p06-gate)，真实对外字段/日期发布限制保留。

### 7.6 规则与受控AI候选、显式应用及安全追加审计

本节 `/api/ra/ai/parse` 与 `/api/ra/ai/applyParse` 使用§7.9整体启动门；下列原网关、规则/应用与审计合同在准入放行后适用。

`POST /api/ra/ai/parse` 根恰为 `{"question":"女性35-45岁"}`。question必须为字符串，空/纯空白合法，最多200 Unicode码点。原body最多4096 UTF8字节；applyParse最多128KiB，均最多读取上限+1 byte。两路沿可信Principal-first、DataResult及每请求新X-Trace-Id；匿名401且不读取body或借连接。未知根键、重复键、尾随JSON、非对象/错误类型、非法UTF8及所有键/字符串内不成对surrogate返回400/INVALID_REQUEST；字节超限413/LIMIT_EXCEEDED。请求header、doctorId或principal.name不能赋权。

parse只产生候选，不接currentFilters、不读患者、不运行分析；缺省不发模型请求，只有下述服务器门全部通过时才尝试一次受控HTTP。成功data恰含questionRef、candidateFilters、parseStatus、applyMode、engine、matched、unrecognized、rejectedFields、warnings、notice、fallbackReason、meta。questionRef是新canonical小写UUID，仅供事件关联；不是原文检索键、权限票据或幂等保证。candidateFilters仅8个普通字段sex/age/sero/tx/at/act/cm/data的稀疏合法条件或null，没有ids/studyCode/doctorId/SQL/URL。默认engine=RULE、fallbackReason=AI_NOT_ENABLED；正常AI为engine=AI、fallbackReason=null，故障完整取本地规则决定，不混入失败模型半成品。

规则表按完整最长词跨度识别，独立条件只用三个有限子集关系留窄值：high⊂mod-high、remission⊂target、四个明确靶向类别⊂bio。例：“仍有高活动”→high、“中高活动”→mod-high、“缓解且已达标”→remission、“TNF生物制剂”→TNFi；不会先到先得或把未知“仍有明显活动”吞成活动条件。未消费实质文本使已有候选PARTIAL、无候选UNRECOGNIZED；空白EMPTY。OR/非白名单否定、真冲突、越域/反向年龄为UNSUPPORTED且无候选；“未达标”“不完整”是正向完整词例外。中性语法仅请/帮我/筛选/筛出/查找/看看/当前/治疗/年龄/的/患者/人群/且/并且/和及标点。本地规则v02仅新增完整词“TNFi”→tx=TNFi、“6个月后”→at=6m、“仍处于中高疾病活动”→act=mod-high；沿既有Unicode空白移除与Locale.ROOT，精确原句“TNFi 治疗 6 个月后仍处于中高疾病活动的患者”得到OK及恰三条件，尚未应用。旧TNF/6个月/六个月/半年仍有效；后/处于/仍不是新增中性词，插入未知文字仍PARTIAL，原句加“且吸烟”需明确确认。普通筛选值仍由§7.1同一CohortQuery校验。

matched每项恰{field,value}，规范字符串、每field最多一项，按tx/at/act/sex/age/cm/sero/data顺序，没有原文或位置。unrecognized至多一项{reason:UNMAPPED_TEXT,count:正整数}，count是剩余Unicode码点数，不回显未知文本。RULE的rejectedFields固定[]；正常AI只输出服务器过滤得到的{field,reason}，field限8普通键、ids/studyCode/doctorId/sql/url/OTHER，未知真实名统一OTHER；reason限FORBIDDEN_FIELD/UNKNOWN_FIELD/INVALID_TYPE/INVALID_VALUE/OUT_OF_RANGE/CONFLICT，每原字段至多一项、总数≤16并去重。warnings顺序为非空fallbackReason同名码、正常AI实际拒字段的MODEL_FIELDS_REJECTED、实际PARTIAL的PARTIAL_REQUIRES_CONFIRMATION，再按UNSUPPORTED_OR、UNSUPPORTED_NEGATION、CONFLICTING_CONDITIONS、AGE_OUT_OF_RANGE列真实规则原因并去重；无原文/异常消息。

notice是≤200码点纯文本。默认disabled以“AI未启用，已使用规则解析。”开头，其它AI故障以“AI解析未完成，已使用规则解析。”开头，安全阻断用“该问题未发送至AI，已使用规则解析。”；随后只接真实本地状态的固定规则说明。正常AI的合格notice来自同次模型HTTP，完整校验后逐字展示；不trim、脱敏拼接或再发解释请求。OK说明尚未应用，PARTIAL要求明确确认，未知/不支持保持旧条件并给有限格式示例。没有患者人数、诊疗结论、身份线索、原文或站外链接。meta恰为traceId（同响应头）、ruleVersion=dev-parse-rules-v02、schemaVersion=dev-parse-schema-v01、modelVersion/promptVersion默认null、durationMs非负整数。正常AI及实际已接收HTTP owner的TIMEOUT/HTTP_ERROR/IO_ERROR/INVALID_RESPONSE尝试使用已校验服务器model和dev-parse-ai-prompt-v01，外层自报model/usage不作真值；版本不证明对端收到。未接收owner时版本均null，包括编码/提交前耗尽的TIMEOUT；apply仍null。durationMs从身份成功后/读body前至决定及安全摘要完成、审计append前，以单调时钟向下取整；不包含INSERT/commit或HTTP写回，响应与审计冻结同值。Clock只提供事件createdAtMs。

`POST /api/ra/ai/applyParse` 根必需currentFilters对象、candidateFilters对象/null、parseStatus字符串、questionRef canonical UUID；仅可追加boolean confirmPartial（缺省false）。currentFilters必须显式对象，不把丢失/null当全体。current与候选通过现存CohortQuery.read校验；候选严格拒绝ids/studyCode/doctorId/SQL/未知键及非法值（400/INVALID_FILTER），即使PARTIAL未确认也先校验。OK/PARTIAL至少有一个非空有效普通条件；EMPTY/UNRECOGNIZED/UNSUPPORTED必须没有候选条件，状态/形状矛盾400/INVALID_REQUEST。

| parseStatus | applyMode | 显式applyParse结果 |
|---|---|---|
| OK | REPLACE_EXCEPT_IDS | applied=true |
| PARTIAL | REQUIRE_CONFIRMATION | 只有confirmPartial=true才applied=true，否则当前规范filters原样 |
| EMPTY / UNRECOGNIZED / UNSUPPORTED | KEEP_CURRENT | 即使confirmPartial=true仍applied=false、保当前规范filters |

apply成功data恰含questionRef、parseStatus、applyMode、applied、filters、meta。应用时用候选替换普通旧条件，不merge；未出现条件回默认（at=now、其它null），studyCode=RA，ids从可信校验后的currentFilters保留，严格区分null/[]/非空排序去重集合。未确认PARTIAL不会将候选F覆盖旧M或清除旧tx。questionRef不验证候选历史/来源，不读取原文，不重新解析；返回无人数、patient、analysisId或统计。调用方将完整filters原样包装为 `{"filters":...}` 独立调用§7.1，仍按本医生实时U/完整分析链消费。SIMILAR入口仍仅接受原专用协议，不能传question/q/filters/ids。

两路每个有效操作（含EMPTY/未识别/不支持及applied=false）成功响应前持久追加一个专用事件。新[审计DDL](../src/main/resources/sql/20261008_ai_parse_audit.sql)仅建ra_ai_parse_audit，无旧患者外键或旧表写入。事件只有eventId/questionRef/trace、可信actor/action、白名单candidate/effective普通条件摘要、engine/状态/应用标志/故障原因、冻结耗时/版本及Clock时间；永不存原文、原问句hash、ids/其hash、病例、notice或模型原文。仅INSERT，没有查询/删除/重放管理API、缓存或后台任务。

ParseAuditStore使用REQUIRES_NEW独立短事务，实际INSERT/commit完成并退出connection/session/statement/result后才公开成功。认证/格式拒绝无事件。SQL或真实INSERT后/precommit失败503/SERVICE_UNAVAILABLE、data=null，不回成功候选或filters，本次事件回滚、旧事件/源表不变。commit成功但HTTP响应写出丢失时完整事件保留；重试可以另追加事件，无跨DB/HTTP原子或幂等承诺。两路本地可用不依赖分析签名key或模型配置。

本片仅Java8/真实DispatcherServlet/MyBatis/loopback合成MySQL及下述合成HTTP验证，未调用生产/真实模型或启用真实AI外发。AQC-EXT-04生产DDL/留存/访问接收、AQC-EXT-05真实服务及外发/原文政策、真实身份/医学统计与其它发布义务仍开放；受控网关不关闭这些接收条件。


#### 7.6.1 一次AI尝试的服务器配置与有限安全语言

配置仅由服务器读取，parse输入不能指定endpoint/model/key。ra.ai.gateway.enabled与outbound-approved均默认false，只接受规范true/false；非法值降级AI_CONFIG_UNAVAILABLE，不使Spring本地解析服务启动失败。决定顺序是原身份/输入与本地规则B、已观察真实caller中断、enabled、批准、完整配置、EMPTY/UNSUPPORTED、完整安全语言、容量与唯一deadline。

生产endpoint固定https://api.deepseek.com/chat/completions；无其它host/path、userinfo/query/fragment。model须非空ASCII [A-Za-z0-9._-]{1,64}；api-key须非空可打印ASCII、无空白/CR/LF且≤512，只进入Authorization，可由RA_AI_API_KEY安全注入。密钥不进入日志、异常、响应或审计，也不保存至仓库。仅受控开发可设置synthetic-loopback=true，此时必须是字面http://127.0.0.1:显式端口/chat/completions，且model=p05b-synthetic-model、api-key=p05b-loopback-key；与真实provider配置互斥，localhost及其它IP/DNS不获准。

安全门完整消费原始连续词跨度，只按Locale.ROOT折大小写，不先删除/拼接空白或做NFKC/URL/HTML解码。独立保留的原v01出站完整词、ASCII词界、合法0～120年龄及原中性词，再额外接完整“吸烟”。允许空白仅ASCII空格/TAB/CR/LF，独立标点仅,，、;；:：。.!！?？()（）；斜杠/连字号/波浪号仅在既有b/tsDMARD、IL-6或完整年龄词内消费。任何未知残余整体不出站，不先删片段后外发；未获准不代表已判断它一定敏感。女性45至65岁、TNF治疗6个月、仍有高活动、女性且吸烟、吸烟人群可尝试；女性张三/宿舍/病例A、电话号码/URL/命令、女性仍有明显活动、“女 性”等跨空白拼词、U+3000/零宽或其它Unicode空白均不获准完整原句外发。本地规则仍可返回其合法候选。v02的三个新增本地词不加入出站词表：TNFi、6个月后、仍处于中高疾病活动及精确需求句即使enabled/outbound-approved为true仍RULE/AI_SAFETY_BLOCKED、modelVersion/promptVersion=null、零网络请求；本地识别或显式应用不授予原文外发许可。原已许可TNF治疗6个月和女性35-45岁仍可一次AI，不因本地扩词而全部关闭网关。

至多一个POST /chat/completions；UTF8固定JSON≤16384，messages只有固定版本系统指令与完整获准question，没有患者、医生、ids、currentFilters、questionRef、历史对话或浏览器headers。响应候选与notice来自同一次请求。私有OkHttp实例禁retry、redirect、认证follow-up、proxy、cookie/cache/日志拦截器，HTTP/1.1与one-shot固定length body防重放，503+Retry-After:0也只有一次；不改TLS默认校验。错误正文不读取/记录，先cancel再由唯一owner关闭。

仅200与application/json（无charset或UTF8）接收；identity或库透明gzip解压后实际body≤16384，最多读取上限+1，缺length/chunked/gzip也累计，压缩Content-Length不能绕过。两层严格UTF8/禁BOM/成对surrogate/单根/重复与尾随拒绝，streaming先限外层depth8/tokens512/单数值16字符；内content≤4096、depth4/tokens128。只接受固定官方envelope/一条stop assistant choice及恰5键inner，tool/function/reasoning文本不接受。

模型普通字段先白名单、再由真实ParseQuery.normalize在caller重验。有效稀疏候选C必须恰等于本地B全部可表达条件，不漏窄条件、不新增默认at；matched/unrecognized由服务器重建，模型声明只用于一致性检查。过滤字段的R非空或原问题未映射时，有候选的实际状态为PARTIAL；status/matched/unrecognized任一不一致整体AI_INVALID_RESPONSE。正常“女性”携额外ids只能正常过滤为AI/PARTIAL并报告有限拒绝码，模型仍称OK则整体RULE降级。

正常“女性且吸烟”的同次模型notice可逐字为“仅生成部分候选条件，尚未应用；需明确确认是否只应用这些候选。“吸烟”不在当前支持的筛选字段中。可用问法示例：女性45至65岁；这只是格式示例，不保留原问题的全部要求。”；“吸烟人群”可逐字为“未生成可用候选条件，当前条件保持不变。“吸烟”不在当前支持的筛选字段中。可用问法示例：TNF治疗6个月；这只是格式示例，不保留原问题的全部要求。”，分别REQUIRE_CONFIRMATION/KEEP_CURRENT。原因句必须符合本次B/R；即使句子在有限集合中，配错状态/事实也拒绝。任何追加姓名、URL、人数、诊疗、已应用、换行/零宽或前后缀使整个AI失败。

fallbackReason有限为AI_NOT_ENABLED、AI_NOT_APPROVED、AI_CONFIG_UNAVAILABLE、AI_NOT_APPLICABLE、AI_SAFETY_BLOCKED、AI_BUSY、AI_TIMEOUT、AI_HTTP_ERROR、AI_IO_ERROR、AI_INVALID_RESPONSE。正常AI仍null。

#### 7.6.2 单一预算、中断、owner与审计边界

网关获准尝试后、编码前仅建立一次单调t0；网络/解码截止N=t0+4500ms，caller域校验与取消收尾使用同一D=t0+5s。connect/read/write上限1000/4500/1000ms并受N截断，不为每阶段重新分配5秒，也不是整个parse含入口、审计、响应写回的SLA。仅最多2个具名daemon网络worker、SynchronousQueue无出站等待队列；满额立即RULE/AI_BUSY。容量由真正仍执行的runnable持有，Future超时/取消标志不能释放它。

网络worker只产出有界不可变结构或有限故障码，唯一caller负责域校验、公开选择与独立审计，无callback/DB权。正常完成先关闭body且实际runnable返回，再在D内选一次NORMAL；超时/真实中断/容器关闭幂等取消同一Call并用D剩余时间等同一未cancel的Future及owner退出。迟到完成没有发布/apply/追加事件权，正常已选择后的迟信号不改原因。实际Spring destroy关闭准入、取消已有Call、shutdown/evict私有pool并有限等待；准入已关闭是BUSY/null版本，已接收owner因关闭而退休为IO_ERROR/固定版本。

真实DispatcherServlet/service caller进入网关前已中断，或等待/正常选择前观察到Thread.interrupt，均503/SERVICE_UNAVAILABLE、data=null，不再成功规则审计；取消socket后有界收尾可暂清flag，finally恢复interrupt=true，不延长D。没有浏览器断连感知承诺。系统native DNS不能强杀：最多2个daemon owner可能仍占容量，到D caller可记录自己的RULE/TIMEOUT决定，但不能把该事件当作网络退出证明；DNS未退时关闭后的executor仍未terminated，如实保有状态，不伪清容量/重发/新增DNS平台。

HTTP与数据库无共同事务。正常HTTP owner退出后才进入原短审计，原文/模型notice/key不进事件；超时native残留的规则事件也不授予旧owner进展权。模型外发已经交给OS的字节不能撤回，随后真实INSERT后/precommit故障必须503/null且rollback、旧事件不变/DB资源退出，绝不重发模型。commit后HTTP写回故障保留完整事件，仍无跨网络/DB原子或幂等承诺。

[P05开发技术Gate](../../docs/plans/ai-cohort/review-register.md#p05-gate)已接收规则解析、受控单次AI协议与原cohort/短审计的合成组合及证明边界；真实模型、外发政策和生产启用仍由AQC-EXT-05在P07接收，普通xlsx及申请通知由P06交付。

### 7.7 授权保留引用到普通同步 XLSX

本节 `/api/ra/export/xlsx` 使用§7.9整体门及独立文件门；下列原引用/权限/字段窗口/审计与binaryPhase合同在准入放行后适用。

`POST /api/ra/export/xlsx`，UTF8 JSON body最多16KiB。先可信身份，再严格对象/重复键/尾随token/所有字符串及键的Unicode、引用和选择器校验；匿名401且不读body/SQL。客户端不能提交doctorId、患者数组、filters、日期覆盖、policy、申请或邮件凭据。

```json
{"source":"COHORT","analysisId":"00000000-0000-0000-0000-000000000001","fields":["das28Current","crpCurrent"]}
```

```json
{"source":"COMPARE","compareResultId":"00000000-0000-0000-0000-000000000001","metrics":["n","das28At","crpAt","haqAt","target","activity"]}
```

每种source仅接收示例中的键；选择器可省略，不得显式null、空数组、重复、未知或非字符串，两引用/选择器不可混带。省略为DEFAULT完整目录，显式数组为EXPLICIT并按目录排序；不自动补ID、不自动替换为免费列。内容直接消费同一已验run的固定payload/hash/完整U指纹，不取首屏、不重新analyze/统计、不读姓名/研究号或SAVED名称/备注。完整U包含当前C或A/B以外的本人RA患者，读取时和完整文件生成后均重验；最后再以新的Clock瞬时检验expiry。每个结果/范围/审计事务在POI和输出前退出，不宣称最后检查与外部源/网络强原子。

COHORT默认目录：`patientId,sex,age,studyNo,treatmentCategory,genericDrugIds,line,schemeDurationMonths,das28Base,das28At,das28Current,crpCurrent,fm,as,completeness`。每保留患者一行、原顺序和值不变；无ID子集仍保留全C。`das28At`标题明确now或6m，真正6m来自原selected访视，不新增独立six列。四个日期列分别取baselineProvenance.observedAt、scoreProvenance.observedAt、selection.nowDate、crpCurrent.observedAt，不互借日期。ID/sex/age/studyNo无可靠来源日；治疗/QC/病史缺完整复合来源证据，非空默认通常NEED_APPLY。任一患者被选字段缺日或越窗，阻断整次，不删行或把旧值抹空。可靠来源日期存在而值缺失可BLANK；空C只输出原表头、EMPTY和rowN=0。

COMPARE默认12指标：`n,age,duration,das28At,crpAt,haqAt,female,sero,target,complete,activity,line`；默认非空完整表会因age/female等undated需申请。显式六指标示例在真实coverage完整且窗内时成功，保留原A/B人数、unknown/known/validN、numerator/denominator、分布、差值、raw P/显示P和检验状态，不复算/二次排重。非空侧validN=0阻断；空侧不阻断，结构计数/原NO_DATA信息保留，不造P。

窗口固定本次上海today与`today.withDayOfMonth(1).minusMonths(6)`，含起点，不按180天，也不以查询/计算/生日/确诊日补来源。新接口复用`ra.export.free-months`的既有int绑定，缺省6且只允许绑定值6；5/12/0/负数在引用SQL前503。空串/非法文本/int越界保持真实Spring装配失败，不承诺可服务HTTP503。容量仅调用共享`AnalysisSettings.maxBytes()`，默认8388608、合法1..16777215；非法配置引用SQL前503，不调用无关cursor key/TTL校验。完整序列化bytes超过上限413/RESULT_TOO_LARGE，零文件输出，不截行/ZIP。

合法但需申请返回409、success=false、code=NEED_APPLY、message=“所选导出内容需要申请”。data恰含`decision,source,referenceId,selectionMode,requested,eligible,blocked,freeStart,today,policyVersion`，policyVersion=dev-export-v01。requested按目录列出本次内容；eligible是完整保留数据/同次窗口检查通过的目录代码（也包括未请求项），不包含临床值。客户端须重新显式选择，服务端不自动采用eligible。blocked只包含requested未通过项，每项恰`field,side,reason,affectedN`；COHORT side=null、COMPARE A/B，按内容目录/side/原因排序。有限原因是NO_VALID_CONTRIBUTION、INCOMPLETE_SOURCE_EVIDENCE、MISSING_SOURCE_DATE、SOURCE_BEFORE_WINDOW、SOURCE_AFTER_TODAY。COHORT affectedN为该原因实际行数；比较缺日/证据不全为undatedN，无有效贡献为非空侧n；比较窗前/未来为整个受阻聚合的validN，不能标作实际越窗人数。缺日与窗外原因可并存，人数不可相加。NEED_APPLY不记录申请、不发SMTP、不授权下载；伴随审计失败仍保留原409/data。

成功200直接返回`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`，固定安全UUID/UTC时刻文件名、Cache-Control:no-store和X-Trace-Id。COHORT sheets为cohort/metadata；COMPARE为comparison/metadata，不附明细。比较宽表列序为：

`metric,kind,category,aN,bN,aValidN,bValidN,aUnknownN,bUnknownN,aDenominator,bDenominator,aKnownN,bKnownN,aNumerator,bNumerator,aQ1,aMedian,aQ3,aIqr,bQ1,bMedian,bQ3,bIqr,aValue,bValue,aCount,bCount,difference,pValue,displayP,method,testStatus,reason,nA,nB,testDenominator,warnings`。

每distribution category按原groups一行，只有指标第一物理行放整体test，其余类别test格BLANK；默认12指标对应17物理行，示例六指标9行。metadata与审计分别记录实际物理rowN、所选指标数与原A/B人数，文件行数不是患者数。metadata保存实际选择/模式、引用、来源读取/计算/生成时间、at/asOf、窗口/政策/CRP阈值、单位/缺失、原探索性说明与Excel限制。安全条件只保规范filters和ids选择模式/去重及有效数量，SAVED只保UUID/version，不放ID数组、name/note/原JSON/问句。

数字为标准OOXML NUMERIC格，原Number直接canonicalDecimal到raw `<v>`，不先double舍入；raw P为数值、displayP为独立文本。POI raw-value及ZIP原值保留现有临床/Type7精度，但Excel计算/再次保存仍受双精度和有效位限制。非有限double范围/非零下溢到0失败，不改为文本或零。日期/条件/代码为STRING，=、+、−、@文本不成为FORMULA；缺失BLANK，没有宏、外部链接或隐藏患者列，不保磁盘文件/公开URL。

专用[审计DDL](../src/main/resources/sql/20261008_ai_export_audit.sql)只新增ra_ai_export_audit，25列/InnoDB/无旧表外键，id复用服务器trace UUID。每次请求是一条独立尝试，无下载幂等或审计grant。8个INSERT/UPDATE操作各REQUIRES_NEW短事务且必须影响1行；UPDATE绑定原id/可信owner和精确前态CAS。ALLOW先PREPARING/NOT_STARTED提交才允许POI；完整生成且Workbook关闭后READY保留generated/file_rows/file_bytes；最终scope/TTL拒绝尽力REJECTED，补记失败不覆盖原409/410/503。STARTED成功提交确认后才设二进制头/write/flush；其后错误终止容器输出，不拼JSON、不重发、不close容器stream。正常write/flush返回后WRITTEN仅证明服务器写出，delivery仍UNKNOWN；失败/提交确认丢失可能已提交，不能猜回滚、反向状态或重发。生成容量失败的伴随FAILED更新出错仍原413；中断保flag，不借新JDBC补记。

其它错误data=null：输入400/INVALID_REQUEST，body超限413/LIMIT_EXCEEDED，缺行/跨owner404/RESOURCE_NOT_FOUND；未清理的expiry边界410/ANALYSIS_EXPIRED或COMPARE_EXPIRED；完整U变化409/SCOPE_CHANGED；codec/SQL/配置/程序503/SERVICE_UNAVAILABLE。没有文件bytes时返回JSON，无伪.xlsx。技术验证仅合成Java8/MySQL/HTTP/POI；真实身份/目标DB/医学统计和AQC-REL-02字段/日期对外批准仍OPEN，申请通知技术接口见§7.8，真实SMTP/收件仍由EXT-06接收，不代表生产启用。

### 7.8 唯一导出申请、一次通知与同申请安全重试

本节 `/api/ra/export/applyAnalysis` 与 `/api/ra/export/retryNotification` 使用§7.9整体门，不受独立文件门控制；下列原幂等/claim/通知状态合同在准入放行后适用。

`POST /api/ra/export/applyAnalysis` 接收UTF8 JSON≤16KiB，根恰含以下六键，全部必填；export只接受§7.7的COHORT/COMPARE引用及显式非空fields/metrics，不接受DEFAULT、客户端人数/hash/blocked/filters/身份。可信Principal先于body，服务器产生X-Trace-Id。先严格UTF8及迭代token浅结构预检（apply最多3层，retry最多1层），再严格重复键、尾随、Unicode与固定shape校验；普通错误400/INVALID_REQUEST，超限413/LIMIT_EXCEEDED，匿名401/body与SQL均0。

```json
{"requestId":"00000000-0000-4000-8000-000000000001","export":{"source":"COHORT","analysisId":"00000000-0000-4000-8000-000000000002","fields":["patientId","sex"]},"startDate":"2026-01-01","endDate":"2026-10-07","purposeCode":"RESEARCH","reason":"合成本地申请说明"}
```

requestId为canonical小写UUID，按可信owner隔离。日期为1000..9999年四位ISO日期，start≤end；首次申请end不晚于本次上海today。日期表示申请范围，不重新筛/扩展引用数据。用途仅RESEARCH/DATA_CHECK/OTHER；reason仅trim首尾后1..1000合法Unicode码点，保存旧表，不入邮件/响应/应用日志。规范hash固定source/reference/目录顺序selected/date/purpose/reason，排除requestId、动态源和邮件配置；键序/JSON空白/字段数组重排同参，说明内部字符、字段、日期、用途或引用变化409/IDEMPOTENCY_CONFLICT。

静态解析后先按owner/requestId短读已提交回执。同参只回原applicationId及当前通知状态，不重读TTL/U/窗口，不新申请或隐式发信；异参409先于旧引用失效。首次才校验6个月政策与同owner/TTL/hash/完整U引用、实际blocked非空；ALLOW为409/APPLICATION_NOT_REQUIRED，缺行/非owner404，到期410，U变更409，损坏/SQL/配置503。申请链不调用xlsx prepare、POI或导出审计，不获取文件权利。

首次创建在REQUIRES_NEW短事务内预约新[ra_ai_export_request](../src/main/resources/sql/20261008_ai_export_request.sql)（owner/requestId主键、applicationId唯一），调用现有ExportMapper真实INSERT旧export_application，再回填正ID；提交只能两表完整，故障回滚。新companion仅保存dev-export-apply-v01安全摘要/条件选择计数/政策/人数与通知事实；无患者值/ID数组/姓名/备注/病例或自由说明。旧doctor_name/mail_to初始null、status保持pending；旧pending/approved/rejected不参与新授权或通知。仅本片创建的旧行允许mail_to/mail_sent更新；旧DDL、CSV/apply接口保持。

`POST /api/ra/export/retryNotification` 根恰`{"applicationId":"正long十进制文本"}`，body≤4096。非owner/只有旧行而无companion404。PENDING或明确FAILED才竞争一次claim；SENT/UNKNOWN只返回现态，零SMTP。retry消费原合法安全摘要，不回读临床/TTL/U，不生成历史文件。唯一竞争败者仅明确duplicate-key回滚后短读同参胜者；提交确认丢失不猜回滚、当次不进入SMTP。

已明确提交的申请返回200/success=true/code=200/message=申请已记录；data恰如下，无reason自由说明、审批/grant/downloadUrl：

```json
{"applicationId":"7","status":"APPLICATION_RECORDED","notification":{"mailStatus":"FAILED","attemptId":"00000000-0000-4000-8000-000000000003","retryable":true,"smtpAccepted":false,"statusConfirmed":true,"reason":"NOT_CONFIGURED","deliveryStatus":"UNKNOWN"}}
```

通知claim单独短事务锁定两表并CAS：PENDING/FAILED→UNKNOWN，新attempt UUID、attemptNo+1（禁止溢出）且先提交才网络。同申请并发最多一个claim；初次apply只有create胜者尝试，同参replay不触retry。首次有效配置绑定唯一管理员mail_to及发件mail_from，后续更换to/from为FAILED/RECIPIENT_CHANGED；修复host/端口/TLS配置不会改变requestHash。未配置/无容量/确定预DATA失败为FAILED可明确retry；进入DATA后不明、deadline/中断/停止为UNKNOWN且不可普通重发。真实finishData最终250后、实际网络worker退出后，caller短事务把旧mail_sent=1与同attempt SENT同时提交；只证明SMTP受理，delivery始终UNKNOWN。

claim/settle提交未确认返回已记录+UNKNOWN/statusConfirmed=false/retryable=false，保留本次已知smtpAccepted，不反向写状态或再发。下次短读反映实际已提交行；create提交未确认仍503/data=null/当次SMTP0。caller中断保flag，不能清flag补JDBC。HTTP写回失败不撤回已记录/已受理事实，重放只读取同申请。UNKNOWN无租约重置、后台恢复、重发开关或outbox。

服务器配置：ra.export.apply-mail-to唯一管理员；ra.export.smtp.enabled默认false、host、port、from、username、password、mode默认STARTTLS。以String调用时验证，坏SMTP配置不使全应用启动失败。地址必须单个InternetAddress.parse(true)+validate，无group/显示名/CRLF/CC/BCC；凭据同时有或同时无。STARTTLS强制enable/required、默认JVM信任链及hostname验证，认证只在TLS之后。LOOPBACK_PLAIN仅字面127.0.0.1/::1且禁auth，专供受控合成试验；无trust-all或任意主机明文开关，不支持隐式SMTPS。

邮件固定RA导出申请 #ID、UTF8 text/plain，无附件/处理URL。正文只含申请编号、可信医生ID、有限用途、申请日期范围、结果类型/UUID、引用人数、目录代码和“不表示审批或已生成历史文件”的固定说明；不含reason、条件取值、患者内容或P值。Message-ID固定&lt;ra-export-ID@notification.invalid&gt;仅关联，不承诺服务器去重；body≤4096/MIME≤16384实际计数。

SMTP由专用最多1线程/SynchronousQueue无队列executor及exact Flight真实raw socket拥有。一次networkDeadline9500ms、callerDeadline10000ms，最后500ms等退出；不含DB/入口/响应SLA。deadline、真实caller interrupt或bean destroy先CAS终止原因、close owned raw socket、interrupt worker，再最多500ms且不越callerDeadline等待exact OwnedTask.afterExecute；Future取消/network finally不替代退出。未退者保持原真实容量、UNKNOWN和禁重试；native DNS不可强杀，不增线程替换或伪释放。生产先close raw再在worker自身Transport.close，关闭异常不覆盖ACCEPTED，QUIT不拖住已受理流程。

reason仅null或NOT_CONFIGURED/INVALID_MAIL_CONFIG/RECIPIENT_CHANGED/NOTIFICATION_BUSY/PRE_DATA_REJECTED/PRE_DATA_IO_FAILED/DATA_OUTCOME_UNKNOWN/DEADLINE/CALLER_INTERRUPTED/SERVICE_STOPPED/STATE_UPDATE_UNCONFIRMED/IN_PROGRESS_OR_UNCERTAIN。不返回SMTP原异常/响应文本、host/user/password。技术仅合成Java8/真实MySQL/loopback SMTP及测试context独立CA的STARTTLS；真实收件、生产信任/配置/留存与EXT-06、REL-02/P07接收仍OPEN，无医学/部署/真实身份保证。

[P06开发技术Gate](../../docs/plans/ai-cohort/review-register.md#p06-gate)已接收普通同步XLSX、真实NEED_APPLY→唯一申请/受控通知及无文件授予的合成组合；真实SMTP收件、目标数据库、医学统计和对外字段日期政策仍由八项OPEN义务在P07逐项接收，不代表发布就绪。

### 7.9 启动配置准入与关闭边界

新功能使用不可变 `AiFeatureAvailability`，同一context的六个真实Controller共用 `AiCohortConfiguration` 提供的单例bean。两个已解析String配置在构造时固定，不读每请求Environment，没有setter、静态可变flag、管理API、后台线程或IO。

| 配置键 | 缺省 | 准入语义 |
|---|---|---|
| `ra.ai.api.enabled` | 字符串 `true` | 只有精确 `true` 放行下表全部15路；`false`或其他已解析字符串关闭整组 |
| `ra.export.xlsx.enabled` | 字符串 `true` | global放行后，只有精确 `true` 放行新xlsx；关闭不连带申请/重试、规则/AI或旧CSV |

不采用primitive boolean宽松转换。已解析空串、`TRUE`、`False`、` true `、`1`、`yes`均禁用，不因这些字符串本身让新bean/context启动失败；不覆盖无关Spring配置或外部PropertySource故障。

以下全部是POST，准确消费范围为3+2+5+2+1+2=15路：

| 真实Controller | 门与完整路由 |
|---|---|
| AiCohortController | requireApi：`/api/ra/ai/cohort`、`/api/ra/ai/cohortPatients`、`/api/ra/ai/resolveEntry` |
| AiParseController | requireApi：`/api/ra/ai/parse`、`/api/ra/ai/applyParse`（共用execute） |
| PrivateCohortController | requireApi：`/api/ra/cohort/save`、`/api/ra/cohort/list`、`/api/ra/cohort/open`、`/api/ra/cohort/rename`、`/api/ra/cohort/delete`（共用handle） |
| ComparisonController | requireApi：`/api/ra/ai/compare`、`/api/ra/ai/compareResult` |
| AiExportController | requireXlsx（先global后file）：`/api/ra/export/xlsx` |
| AiExportApplicationController | requireApi：`/api/ra/export/applyAnalysis`、`/api/ra/export/retryNotification`（共用handle） |

顺序统一为原 `principal.requireCurrent()` 成功后立即检查准入，再执行原Clock/body/Service，不增第二次鉴权、不更改业务载荷/原状态机。匿名仍401/UNAUTHENTICATED，body/SQL=0。可信身份通过但global禁用时，合法资源body或畸形body `{` 都在读取前直接503/SERVICE_UNAVAILABLE/message=`队列功能暂不可用`；file单独禁用为503/SERVICE_UNAVAILABLE/message=`文件导出暂不可用`，两键同时禁用时global原因优先。复用原DataResult和本次X-Trace-Id，success=false/data=null，不回显配置原值。

禁用时本次请求body读取bytes=0、数据库借用/查询/写入=0，没有新run/定义/回执/解析审计/导出审计/申请、POI文件或AI/SMTP。错误响应的JSON字节不等于文件字节；xlsx拒绝时binaryPhase仍false，不生成伪.xlsx或写后拼接JSON。

缺省/显式true保留原行为，放行不替代权限、TTL/U复验、字段/日期NEED_APPLY、医学或外发批准。file关闭时其余14路仍走原合同，申请可记录，SMTP关闭仍按原FAILED/NOT_CONFIGURED；显式retry只依原状态，不能因开门重发UNKNOWN。旧 `ra.ai.gateway.enabled=false` 独立于global，API开着时parse仍可200/RULE/AI_NOT_ENABLED、候选可显式应用并留安全审计，分析与文件不因此连带停用。

原 `ExportController` 的 `/api/ra/export/range`、`/api/ra/export/visitsCsv`、`/api/ra/export/apply` 以及其他旧接口不受两键影响，不能采用路径前缀拦截误封旧接口。本片兼容观测仅为旧range成功、visitsCsv/apply非法日期仍HTTP200业务code=400，不等于完整旧CSV或真实鉴权重新验收。

修改配置文件/PropertySource后必须重新创建context/重启实例；当前实例不热更新。即使旧Environment已可见false，旧实例仍用构造时值；另一个disabled新context拒新请求，旧实例已准入调用仍沿原Service完成。开关不追溯中断、不倒写持久数据或撤回外发；真正销毁context/进程仍遵从原shutdown规则，不保证被销毁实例继续成功或强杀native DNS。

关闭/重开保留同identity的定义/成功回执、分析/比较payload、导出审计以及申请/attempt/from/to；旧结果仅在原TTL内可重读，过期410/清理后404与完整U变化409保持，重开不延寿/重建旧ID。同参apply重放只读原回执，UNKNOWN/SENT不重发，FAILED仅显式retry经原claim获得新attempt；SENT只说明SMTP受理，WRITTEN只说明本地写出，均不代表送达、审批或文件grant。

SQL清单、配置关闭、状态留存及整组应用回退前提见 [AI队列发布与回滚手册](AI队列发布与回滚手册.md)。真实目标库预演/部署/回退和真实接入、医学统计/字段日期发布仍由 [唯一登记的八项接收义务](../../docs/plans/ai-cohort/review-register.md#接入与发布接收义务) 接收；开关和合成context重建不授予生产发布资格。

## 新增接口时的维护约定

每加一个接口，在本文件对应页面下补一节，写清楚：页面、地址、入参（含中文含义）、出参（含中文含义和计算口径）、返回示例。
