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

所有接口都返回下面这个外层结构，业务数据在 `data` 里。

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

排序：最近非空随访完整时间倒序，无日期排最后；同时间按 patientId 倒序。keyword的LIKE转义、collation及followStatus保持原SQL语义。

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
| `followStatus` | String | 随访状态编码 | 按优先级命中即止：① 已脱落（任一医患关系 `miss=1`）→ `withdrawn`；② 无随访 → `pending_first`；③ `nextDueDate` 早于今天 → `overdue`；④ 14 天内到期 → `soon`；⑤ 其余 → `active` |
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
| `action` | 动作：新建档案 / 修改档案 / 新增随访 / 编辑随访 / 删除随访 / 标记脱落 / 质控处理（决定标签颜色） |
| `visitId` | 涉及的随访 ID，档案类操作为 `null` |
| `detail` | 修改内容；多项用「；」分隔，字段修改写成「字段：旧值 → 新值」，页面把旧值标红删除线、新值标绿 |
| `operator` | 操作人，如「陈医生（研究者）」 |

**怎么记录**：表 `patient_audit_log`。以后的写接口（新建患者、编辑档案、新增 / 编辑 / 删除随访、标记脱落）在业务更新的**同一事务**里调用：

```java
List<FieldChange> changes = auditLogService.diff(修改前Map, 修改后Map, 字段中文名Map);
auditLogService.record(patientId, visitId, "修改档案", changes, 附加说明, doctorId, "陈医生（研究者）");
```

`diff` 只比较列出的字段、只记录有变化的；`record` 把变化拼成页面显示的 `detail`，同时把结构化明细存成 JSON（`changes` 列），以后可按字段统计和回溯。

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

另外：`study_no` 自动生成（RA-今天-当天序号，冲突重试）、`create_date` = 当前时间、`follow_up_count` = 0；
医患关系 `patient_relation_doctor`：`doctor_id`、`patient_id`、`research_type` = 配置 `ra.patient.research-type`、`confirm_date`、`happen_date`、`create_date` = 当前时间，`follow_count` / `miss` / `revisit` / `last_verify_status` = 0。

身份证号已存在时（同 6.1 规则）：

| 情况 | 处理 |
|---|---|
| `MINE` | 409「该患者已在您名下（姓名，ID号 n）」 |
| `RA_OTHER_DOCTOR`，`transfer=false` | 409「该患者已在 某医生 名下，确认要转到自己名下吗？」 |
| `RA_OTHER_DOCTOR`，`transfer=true` | 把该 RA 医患关系的 `doctor_id` 改为本医生（原医生不再看到），写修改记录「转入名下」；不改基本信息 |
| `OTHER_DISEASE` | 不新建患者行：只更新医生填了且与原值不同的字段（不清空原有信息），没有研究编号的补上；新增 RA 医患关系；原病种（已确认的：6 = AS）记入 `patient_comorbidity`（source=auto）；修改记录写改了哪些字段 |

出参 `data`：`patientId`、`studyNo`、`action`（`created` / `linked` / `transferred`）。研究类型未配置时返回 400「新建患者的研究类型未配置」。

## 七、AI 队列完整分析与有期结果分页（P03_RESULT）

### 7.1 当前医生 RA 队列

页面：当前交付 RA/now/6m/活动度/内部 id 及 sex/age/sero/cm 临床筛选及 tx 治疗/data完整性筛选、五卡四图/FM描述、人数和首10的姓名/研究编号及临床/评估/治疗来源。`completion=P03_RESULT` 表示描述/推断与单次数值保留、签名分页已接入；授权相似条件入口见§7.3，仍须由客户端显式调用本接口完成分析。私有定义保存、比较、AI与导出由后续切片交付。

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

`POST /api/ra/ai/resolveEntry` 使用JSON根对象，恰接收两个非空字符串：`source="SIMILAR"`、`indexPatientId`。索引id采用现有规范正long十进制文本，不接受number、0、负值、前零、空白或溢出；source大小写精确，不接受数组、多source或其他类型。未知字段（包括doctorId、cohortId、compare、q、filters、ids）、重复键、尾随JSON、非对象/缺项/null/非法类型均400/INVALID_REQUEST，非UTF8编码或非法UTF8同样拒绝。body最多4096 UTF8字节，最多读取4097；超限413/LIMIT_EXCEEDED。上述拒绝在SQL前发生。

Controller先要求可信Principal，再固定入口Clock.instant，随后读body；匿名401/UNAUTHENTICATED且不读取body、不借连接，header/请求doctorId/principal.name不提供权限。以入口时刻固定Asia/Shanghai日期，沿现有SourceAdapter的本人doctor+RA同关系完整U、五条批量SELECT及短REPEATABLE_READ视图读取；先检查index在U，再查其PatientClinical。不存在、他人、非RA、物理删除或撤权统一404/RESOURCE_NOT_FOUND。SQL/程序错误503/SERVICE_UNAVAILABLE、data=null，不回退旧候选或解释为缺失。源owner在解析临床事实/条件装配及公开响应前退出，resolver只读，不创建或清理analysis run，也不需要结果签名key；cohort缺key仍按7.2返回503。

成功data恰含source、filters、missingBasis、status、canApply、reason、meta。source固定SIMILAR；meta恰含asOf（上海ISO日期）、readStartedAt/readCompletedAt（UTC Instant）、traceId（同X-Trace-Id）、policyVersion=`dev-similar-v01`。不会返回indexPatientId、姓名/研究编号、身份证/生日、病例JSON或临床provenance。

- sex为F/M则保留条件；未知不生成，missingBasis列sex
- 已知age在0～120则产生闭区间`max(0,age-5)-min(120,age+5)`；未知不估算，missingBasis列age。已知超出此域时UNSUPPORTED_BASIS、canApply=false、filters=null、reason=AGE_OUT_OF_SUPPORTED_RANGE，不静默删去年龄限制；missingBasis仍只列真实未知事实。既有临床事实可大于120，本接口不改ClinicalPolicy
- sero明确TRUE才生成字符串`"1"`；FALSE不生成且不是缺失，UNKNOWN不生成并列sero。沿原截至asOf曾经阳性政策，历史阳性后最新阴性仍TRUE，未来/无日期阳性不提供依据
- missingBasis按sex/age/sero固定顺序。至少生成一项限制时canApply=true，缺失表空为READY，否则PARTIAL_BASIS；filters经现有CohortQuery校验/规范化，全部键为studyCode/at/act/ids/sex/age/sero/cm/tx/data，studyCode=RA、at=now、其余未生成键为null
- 没有任何可生成限制时NO_BASIS、canApply=false、filters=null；全未知列三项，只有血清阴性且无sex/age时只列sex/age。不自动分析整体U或塞空ids。除UNSUPPORTED_BASIS外reason=null

canApply=true时客户端将filters原样包装成`{"filters":...}`调用7.1标准cohort；可显式追加/调整其普通条件，本入口不接收隐式合并。索引本人默认不排除，按自然条件匹配。候选只是当次授权事实产生的条件，不是权限凭证或analysis结果；两次HTTP各自固定asOf并重新读当前U/事实，后一次cohort不重新推断相似条件，也不保证index继续授权。旧候选不能让已撤权患者进入U。空C仍保留完整零桶/NO_DATA/null和真实analysisId，后页继续7.2当前范围复查、到期和保留数值规则。

本片只提供SIMILAR与现有完整cohort/分页组合，保存/比较及q/AI入口仍属后续能力；合成验证不代表真实身份、医学/统计批准、生产DB/key/发布接收。

## 新增接口时的维护约定

每加一个接口，在本文件对应页面下补一节，写清楚：页面、地址、入参（含中文含义）、出参（含中文含义和计算口径）、返回示例。
