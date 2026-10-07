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
| `enrolledPatients` | int | 已入组患者 | `patient_basic_info` 全部患者（含已脱落） |
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
| `M_DAS28` | 缺失 | 所有 RA 随访的病情评估 `bqpg` 里都没有 `result.crpScore` / `result.esrScore` |
| `M_BASELINE_LAB` | 缺失 | 首次随访的辅助检查为空（没有随访也算） |
| `M_COMORBIDITY` | 缺失 | 既往史（合并疾病）为空 |
| `M_MEDICATION` | 缺失 | 所有随访的治疗方案都为空 |
| `L_DATE_ORDER` | 逻辑冲突 | 发病晚于确诊；随访早于发病；随访日期在未来 |
| `L_ASSESSMENT` | 逻辑冲突 | 不吸烟却填了吸烟年数/支数；不饮酒却填了饮酒年数/量；ACR/EULAR 评分不在 0~10 |

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

排序：最近随访日期倒序，无随访的排最后。

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
| `birthYear` | Integer | 患者信息 · 出生年份 | 取身份证号 `card_no` 里的出生日期（18 位第 7–14 位、15 位第 7–12 位）；无有效身份证号时 = 建档年份 − 建档时年龄 `age`；都没有为 `null` |
| `age` | Integer | 患者信息 · xx 岁（页面显示「1964 年 · 62 岁」） | 有身份证号：按出生日期算周岁；否则 = 今年 − `birthYear` |
| `studyNo` | String | 研究信息 · 研究编号 | `study_no`，如 `RA-20261003-00001` |
| `visitCount` | int | 研究信息 · 已随访 N 次；最近随访 · 累计 N 次 | 该患者随访记录条数 |
| `followCycle` | int | 研究信息 · 每 N 个月 | `follow_cycle`：3 / 6 / 12 / 24，默认 12 |
| `subtype` | String | 疾病资料 · 分型 | 按 RF、抗CCP 计算（不落库）：任一阳性 → `血清阳性`；做过且都不阳性 → `血清阴性`；两项都未检测 → `null`（显示「分型未提供」） |
| `rf` / `ccp` | Object | 疾病资料 · 分型下方的 RF / CCP 结果 | 随访辅助检查 `fzjc.lfsyz`（类风湿因子 RF）/ `fzjc.kccpkt`（抗CCP抗体），各取**最近一次有结果**的随访 |
| `rf.status` / `statusLabel` | String | 状态 | `negative` 阴性：值 ≤ ULN，或写「<20」「阴性」「-」；`low_positive` 低滴度阳性：ULN < 值 ≤ 3×ULN，或只写「阳性」「+」；`high_positive` 高滴度阳性：值 > 3×ULN；`untested` 未检测：所有随访都没有结果 |
| `rf.value` / `uln` / `visitDate` | — | 化验原值 / 判定用的参考上限 / 取自哪次随访 | ULN 老数据没存，统一用配置 `ra.serology.rf-uln`（默认 20 IU/mL）、`ra.serology.ccp-uln`（默认 25 U/mL），待业务确认 |
| `latestDas28` | number | 疾病资料 · DAS28-CRP | 按原随访顺序取病情评估 `bqpg.result.crpScore` 的首个有效存值；数字/字符串等价，缺组成项仍接纳，0 有效，无医学上限。原值须非负，再两位 HALF_UP canonical；null/空白/未查/非法/负值继续历史查找，ESR 不补 CRP；均无有效值为 `null`（显示「DAS28-CRP 未提供」） |
| `das28Activity` / `das28ActivityLabel` | String | 疾病资料 · DAS28-CRP 后的活动度标签 | 按同一两位 canonical CRP 分级（当前开发默认，医学发布接收尚待确认）：`remission` 临床缓解 < 2.3；`low` 低疾病活动度 2.3 ~ 2.7；`moderate` 中疾病活动度 > 2.7 且 ≤ 4.1；`high` 高疾病活动度 > 4.1；无有效分值为 `null` |
| `comorbidities[]` | Array | 其他病史 | 读 `patient_comorbidity`；空数组显示「无」 |
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
| `comorbidities[]` | 常见相关疾病 | `patient_comorbidity`，同患者列表 |
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
4. **病情评估计算项自动重算**（与老系统同一算法，已用真实数据验证一致）：肿胀 / 压痛关节数、DAS28-CRP、DAS28-ESR、HAQ 得分；只在相关输入（关节、患者总体评分、CRP、血沉、HAQ 各题）有变化时重算。ACR20/50/70 需要对比上次随访，不重算。
5. 改随访日期：同时改 `follow_up_date` / `followUpDate`，病史病情里有 `followDate` 的同步修改；老系统的随访次数、首次 / 最近随访日期重算。
6. 打开表单后数据被别人（含老系统）改过：返回 `409`，提示刷新后重新编辑。
7. 写一条修改记录：动作「编辑随访」，内容逐项「模块·字段：旧值 → 新值」，自动计算项标「（自动计算）」。

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

## 七、AI 队列分析示踪（P01_TRACER）

### 7.1 当前医生 RA 队列

页面：AI 队列分析的首个真实后端切片，当前只交付 RA/now/活动度/内部 id 筛选、人数、四档活动分布及首10。`completion=P01_TRACER` 不代表最终完整分析、统计、分页、保存、比较、AI 或导出已经完成。

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
| `at` | 只支持 `now`；缺省/null/空串为now |
| `act` | `target` / `mod-high` / `remission` / `low` / `moderate` / `high`；缺省/null/空串为不限 |
| `ids` | 仅规范正整数十进制字符串数组，最大 long 为9223372036854775807；缺省/null不限，`[]`严格空集合。内部数值去重、排序后与本人范围取交集 |

未知顶层/filters键、重复JSON键、非对象body、非法JSON/类型/枚举或非法id均400。`6m`、sex、age、tx、q、保存来源等未来条件明确拒绝，不能静默退化成无条件分析。原ids数组超过5000项返回413（去重前检查）；原UTF-8 body超过128KiB返回413，解析前最多读取128KiB+1字节。合法但不存在/无权限的id只过滤，不逐项透露原因。

#### 范围、源读取与评分口径

- U仅包含当前可信医生在**同一关系行**符合共享RA白名单的 `patient_basic_info.id`；按内部id唯一计数，脱落miss=1不默认排除，相同姓名不同id仍分别计数，孤立关系/访视不创造患者
- 两条实际SELECT（患者id与必要访视列）在同一个短InnoDB REPEATABLE_READ事务/读取视图中执行。源事务释放session/连接后才解析必要JSON、纯计算和装配响应；生产访问仅SELECT，任何失败不回部分数据或旧结果
- 一次请求以注入Clock固定上海asOfDate。now选择日期非空、≤asOfDate且CRP有效的最新临床日期；同日按访视id最大决胜，忽略时刻差异。最新坏值不挡历史有效值；下一新请求重新读取已提交源变化并重新评估Clock
- CRP仅取 `bqpg.result.crpScore`，数值/字符串保持十进制原标量精度并复用共享两位HALF_UP canonical。0有效；malformed JSON、null/负值/非法值、只有ESR不成为有效CRP，ESR不能补CRP。缺组成项仍可使用存量值，不启用未核实复算或写回历史
- C=U∩ids限制∩act。无act时无评分患者仍计入n；有act必须有有效CRP。target为canonical≤2.7，mod-high为>2.7；四档为<2.3、2.3～2.7、>2.7且≤4.1、>4.1

#### 成功 data（HTTP200，success=true，code="200"）

| 字段 | 实际含义 |
|---|---|
| `n` / `studyTotal` | 当前C人数 / 本医生U人数；studyTotal不受act或ids影响 |
| `submittedUniqueIdsN` / `effectiveIdsN` | 去重提交数 / 提交集合∩U人数；均在act前计算，未提供ids时两者null |
| `activity.current` | 恰为remission/low/moderate/high四项，含level/label/count；空队列也有四个0 |
| `activity.evalN` / `unknownN` | C内有效CRP人数 / n-evalN；缺失与执行失败分开 |
| `patients.total` / `items` | total=n，items是patientId数值升序首10，没有后续游标 |
| `items[]` | patientId字符串、das28At canonical数值或null、activity编码或null、scoreProvenance |
| `meta` | asOfDate、readStartedAt/readCompletedAt/computedAt ISO instant、policyVersions、traceId、supportedFilters、completion |

有效值provenance为source=`LEGACY_STORED`、sourceVisitId字符串、sourceField=`bqpg.result.crpScore`、observedAt=`yyyy-MM-dd`、raw原标量文本及quality。存量至少 `LEGACY_UNVERIFIED`；已知TJC/SJC/GH/CRP任一缺失再标 `COMPONENTS_MISSING`，仅作质量说明，不代表医学已核实。无可选值时source/sourceVisitId/sourceField/observedAt/raw均null，missingReason=`NO_VALID_CRP`；quality只保留实际观察到的字段质量（如 `INVALID_JSON`）。不返回姓名、手机、身份证或整份病例JSON。

以下为合成fixture仅act=target（未提供ids）的响应data示例（外层继续使用DataResult，其其他未使用字段为null）：

```json
{
  "n": 2, "studyTotal": 5, "submittedUniqueIdsN": null, "effectiveIdsN": null,
  "activity": {
    "current": [
      {"level":"remission","label":"临床缓解","count":1},
      {"level":"low","label":"低疾病活动度","count":1},
      {"level":"moderate","label":"中疾病活动度","count":0},
      {"level":"high","label":"高疾病活动度","count":0}
    ], "evalN":2, "unknownN":0
  },
  "patients": {"total":2,"items":[
    {"patientId":"1","das28At":2.30,"activity":"low","scoreProvenance":{"source":"LEGACY_STORED","sourceVisitId":"11","sourceField":"bqpg.result.crpScore","observedAt":"2026-10-07","raw":"2.295","quality":["LEGACY_UNVERIFIED","COMPONENTS_MISSING"]}},
    {"patientId":"7","das28At":0.00,"activity":"remission","scoreProvenance":{"source":"LEGACY_STORED","sourceVisitId":"70","sourceField":"bqpg.result.crpScore","observedAt":"2026-10-07","raw":"0","quality":["LEGACY_UNVERIFIED","COMPONENTS_MISSING"]}}
  ]},
  "meta":{"asOfDate":"2026-10-07","readStartedAt":"2026-10-07T02:00:00Z","readCompletedAt":"2026-10-07T02:00:00Z","computedAt":"2026-10-07T02:00:00Z","policyVersions":{"crp":"dev-crp-v04","now":"dev-now-v04"},"traceId":"synthetic-example-trace","supportedFilters":["studyCode","at","act","ids"],"completion":"P01_TRACER"}
}
```

每个本入口响应带 `X-Trace-Id`，成功时与meta.traceId相同。时间仅表示本次读取/计算，不是旧系统业务水位。

| 失败code | HTTP | 说明 |
|---|---|---|
| `UNAUTHENTICATED` | 401 | 可信身份不可用；默认生产配置明确拒绝 |
| `FORBIDDEN` | 403 | doctorId断言与可信身份不一致 |
| `INVALID_FILTER` | 400 | 不支持/非法JSON条件、重复或未知键 |
| `LIMIT_EXCEEDED` | 413 | 原数组>5000或原body>128KiB |
| `SERVICE_UNAVAILABLE` | 503 | 数据库或程序失败；不降为n=0/缺评分或旧结果 |

失败success=false、data=null、message为安全通用提示，不暴露SQL/病例/连接或凭据。认证及输入拒绝发生在业务源SQL前。真实登录、目标生产库/隔离配置、医学与发布接收仍待对应owner，合成验证不能替代这些接入证据。

## 新增接口时的维护约定

每加一个接口，在本文件对应页面下补一节，写清楚：页面、地址、入参（含中文含义）、出参（含中文含义和计算口径）、返回示例。
