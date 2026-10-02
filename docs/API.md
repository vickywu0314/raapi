# RA 类风湿研究平台 接口说明

## 通用说明

| 项 | 值 |
|---|---|
| 本地地址 | `http://localhost:8065/gk` （端口 `server.port`，前缀 `server.servlet.context-path`） |
| 请求方式 | 所有接口均为 `POST`，参数放在 URL 查询串或 `application/x-www-form-urlencoded` 表单里 |
| 在线文档 | 启动后访问 `http://localhost:8065/gk/swagger-ui.html`，可直接在页面上调接口 |

### 统一返回结构 `DataResult`

所有接口都返回下面这个外层结构，业务数据在 `data` 里。

| 字段 | 类型 | 中文含义 |
|---|---|---|
| `success` | Boolean | 是否成功：`true` 成功，`false` 失败 |
| `code` | String | 状态码：`"200"` 成功，`"500"` 失败 |
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
| 地址 | `POST /gk/project/projectsData` |
| 代码 | `ProjectController.projectsData` → `ProjectServiceImpl.getProjectsData` → `ProjectMapper.xml` |

#### 入参

| 参数 | 类型 | 必填 | 中文含义 | 示例 |
|---|---|---|---|---|
| `ra` | Integer | 是 | 病种。`1` = RA 类风湿；目前只支持 1，传其它值返回“暂不支持该病种” | `1` |
| `doctorId` | Long | 是 | 当前登录医生 ID。总览是项目级统计，**目前不按医生过滤**，仅作接收 | `5065` |

#### 调用示例

```bash
curl -X POST "http://localhost:8065/gk/project/projectsData?ra=1&doctorId=5065"
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
| `ra` 不是 1 | `500` | 暂不支持该病种 |
| 数据库查询出错 | `500` | 获取项目总览数据失败（详细错误看后台日志） |

---

## 新增接口时的维护约定

每加一个接口，在本文件对应页面下补一节，写清楚：页面、地址、入参（含中文含义）、出参（含中文含义和计算口径）、返回示例。
