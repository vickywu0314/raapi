# RA 类风湿研究平台 接口说明

## 通用说明

| 项 | 值 |
|---|---|
| 本地地址 | `http://localhost:8065`（端口 `server.port`） |
| 路径规则 | `/api/<病种>/<业务>/<接口名>`，如 `/api/ra/project/projectsData` |
| 请求方式 | 所有接口均为 `POST`，参数放在 URL 查询串或 `application/x-www-form-urlencoded` 表单里 |
| 在线文档 | 启动后访问 `http://localhost:8065/swagger-ui.html`，可直接在页面上调接口 |

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
| `M_DAS28` | 缺失 | 所有随访的病情评估里都没有 DAS28 评分 |
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
| `birthYear` | Integer | 患者信息 · 出生年份 | 表中无出生日期：建档年份（`create_date`）− 建档时年龄（`age`）；`age` 为空则 `null` |
| `age` | Integer | 患者信息 · （xx 岁） | 今年 − `birthYear` |
| `studyNo` | String | 研究信息 · 研究编号 | `study_no`，如 `RA-20261003-00001` |
| `visitCount` | int | 研究信息 · 已随访 N 次；最近随访 · 累计 N 次 | 该患者随访记录条数 |
| `followCycle` | int | 研究信息 · 每 N 个月 | `follow_cycle`：3 / 6 / 12 / 24，默认 12 |
| `subtype` | String | 疾病资料 · 分型 | 一期无结构化数据，固定 `null`（显示「分型未提供」） |
| `latestDas28` | number | 疾病资料 · DAS28-CRP | 一期无结构化数据，固定 `null`（显示「DAS28-CRP 未提供」） |
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
                "subtype": null,
                "latestDas28": null,
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
| `subtype` | 疾病分型 | 一期 `null`，显示「待补充」 |
| `mobile` | 患者手机号 | `mobile` |
| `cardNoMasked` / `hasCardNo` | 患者身份证号（后 4 位打码）/ 是否显示「眼睛」按钮 | `card_no` |
| `nation` | 民族 | `nation` |
| `marry` / `marryLabel` | 婚史 | `marry` 原值；编码含义待确认，`marryLabel` 暂为 `null`，页面显示「代码 N」 |
| `createDate` | 建档日期 | `create_date` |
| `followStartDate` | 随访观察起始 | 基线访视（最早一次随访）日期；无随访取建档日期 |
| `confirmDate` / `happenDate` | 确诊日期 / 发病时间 | `confirm_date` / `happen_date` |
| `followCycle` / `nextDueDate` / `nextDueDays` | 随访周期 · 下次随访 · 已逾期 N 天 / N 天后 | `follow_cycle`；下次 = 最近随访 + 周期；`nextDueDays` 负数为已逾期 |
| `latestDas28` | DAS28-CRP | 一期 `null` |
| `height` / `weight` / `bmi` | 身高 / 体重 / BMI | `height`、`weight`；BMI = 体重 ÷ 身高(m)²，1 位小数 |
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
| `visits[].doctorId` | 记录医生 | `doctor_id` |

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

## 新增接口时的维护约定

每加一个接口，在本文件对应页面下补一节，写清楚：页面、地址、入参（含中文含义）、出参（含中文含义和计算口径）、返回示例。
