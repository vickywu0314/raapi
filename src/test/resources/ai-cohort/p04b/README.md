# P04b 队列比较独立 Oracle（准备材料）

此 ignored `.tmp` 目录保存 P04b 的固定合成输入与独立数值期望，不是业务实现或产品测试。没有修改产品代码、P04a Worker 文件或 Git，也没有运行 Maven/数据库。

## 固定依据与适用边界

- Charter：`docs/plans/ai-cohort/charter.md` §P04，特别是 P04b seam、同源/同一 at、先算患者交集再处理逐指标缺失、差值方向及有期结果引用。
- 需求：`docs/requirements/AI队列分析页需求文档v0.1.md` §§8.2–8.3。
- 详细设计：`docs/design/AI队列分析页-详细设计草案.md` §§7.1–7.3。
- 现有政策实现：`raapi/src/main/java/com/wenwen/ai/statistics/StatisticalPolicy.java`，版本 `dev-stats-v04`。既有 P03b 独立统计资源：`raapi/src/test/resources/ai-cohort/p03b/independent-oracles.json`。

独立脚本不导入产品代码。计算环境为 Python 3.12.14、SciPy 1.17.0、NumPy 2.3.5。Mann–Whitney 显式使用 `alternative="two-sided"` 与 `use_continuity=True`：仅在无 ties 且 `min(nA,nB) <= 8 && max(nA,nB) <= 50` 时显式 `method="exact"`，否则显式 `method="asymptotic"`。先按 Decimal 精确排序及判定 ties，再把单调秩码传给 SciPy，避免先转 double 后改变 Java `BigDecimal.compareTo` 的秩关系。2×2 数学期望任一格 `<5` 时显式双侧 Fisher；Pearson 显式 `correction=False`（无 Yates 校正）。Type7 使用精确 `Decimal` 插值，`h=(n-1)p`。P 值对拍容差：绝对误差 `1e-12`；非零值相对误差 `1e-10`；零只用绝对误差。

## 文件、正式资源位置与重算

- `fixed-inputs.json`：14人主表、分母例、Type7探针、样本门槛/ties/精度/重叠边界。
- `compute_oracles.py`：独立计算脚本；含 Type7 与交换 A/B 后双侧 P 不变的断言。
- `independent-oracles.json`：分位数、描述差值、P/状态/方法/分母/警告、Python/SciPy/NumPy版本。
- `README.md`：本说明。

脚本路径通过 `Path(__file__).resolve().parent` 定位 `fixed-inputs.json` 和输出文件。因此 C1 将四个文件正式复制到 `raapi/src/test/resources/ai-cohort/p04b/` 后，可从仓库根目录运行：

`python3 raapi/src/test/resources/ai-cohort/p04b/compute_oracles.py`

这会在正式资源目录内读取同目录的固定输入并写回 `independent-oracles.json`，与当前工作目录无关。当前 `.tmp/p04-preparation/comparison-oracles/` 是准备来源/哈希校验位置，不是最终或唯一证据位置。当前会话只读核对了上述路径解析，没有运行复制后的脚本、Maven或数据库。

## at 与日期覆盖

比较请求两侧使用相同 `at=now`；测试时钟对应的固定临床 `asOfDate` / 评估日为 `2026-10-07`，同次评价及 CRP、HAQ 来源访视日期均为 `2026-10-07`。这里的 `asOfDate=2026-10-07` 是源事实日期，不表示请求使用 `at=2026-10-07`。年龄由当日同月同日出生日期精确得到20–33岁，病程由当日同月同日的 `confirmDate` 得到1–14年。HAQ患者8在该同访视缺值，但患者8仍属 A/B 交集；不得把其他日期的值拼到该指标。

主表每人均有 DAS28、CRP；除患者8外，HAQ有值。活动档按权威边界派生：remission `<2.3`、low `2.3–2.7`（含端点）、moderate `>2.7–4.1`（含4.1）、high `>4.1`。固定边界值2.3、2.7、4.1可防止分档歧义。治疗线只固定期望的派生事实：id1–6 `[1,1,2,2,3,3]`，id7 UNKNOWN，id8=2，id9–14 `[1,2,2,3,3,3]`。后续构造产品 fixture 应复用现有 `TreatmentHttpFixture` / `DevelopmentDrugDictionary`，不新造治疗推导机制。血清学类别是可达的合成派生事实；QC仅使用当前可达的 `COMPLETE` / `MISSING`：`MissingDataStatus` 为 final，`getStatus()` 只有这两个状态，成功的 `MissingDataPolicy` 覆盖整个 U；失败应整体报错，不产生第三种 UNKNOWN QC 状态。此处期望不是新源映射或新 QC 规则。

## 主表期望

A=`ids 1–8`，B=`ids 7–14`，交集=`ids 7,8`。未排除时仍返回描述值，但所有比较 P 行必须优先为 `OVERLAP`；包括HAQ，尽管重叠id8缺HAQ。选择排除交集后，必须从两组同时移除 ids 7、8 并重算，形成 A=`ids 1–6`、B=`ids 9–14`。

| 指标 | A Q1 / 中位数 / Q3 / IQR | B Q1 / 中位数 / Q3 / IQR | A−B中位差 | 独立检验/P |
|---|---|---|---:|---|
| 年龄 | 21.25 / 22.5 / 23.75 / 2.5 | 29.25 / 30.5 / 31.75 / 2.5 | −8 | MW exact，0.0021645021645021645 |
| 病程 | 2.25 / 3.5 / 4.75 / 2.5 | 10.25 / 11.5 / 12.75 / 2.5 | −8 | MW exact，0.0021645021645021645 |
| DAS28 | 2.075 / 2.5 / 2.925 / 0.85 | 4.775 / 5.05 / 5.775 / 1 | −2.55 | MW exact，0.0021645021645021645 |
| CRP | 0.9 / 1.4 / 1.9 / 1 | 4.1 / 4.6 / 5.1 / 1 | −3.2 | MW exact，0.0021645021645021645 |
| HAQ | 0.225 / 0.3 / 0.375 / 0.15 | 0.925 / 1.05 / 1.175 / 0.25 | −0.75 | MW asymptotic，0.004998124765082457；`SMALL_SAMPLE_TIES` |

其他剔交后核对：

- 女性：1/6 vs 5/6，展示分母 `C.n`；已知性别检验 n=6/6，表 `[[1,5],[5,1]]`，双侧 Fisher P=0.08008658008658008；A−B=−66.6667个百分点。
- 血清学阳性：展示3/6 vs 3/6，未知1 vs 0；检验仅已知类别，n=5/6、表 `[[3,2],[3,3]]`，双侧 Fisher P=1。
- 资料完整：A=3/6、B=4/6，QC未知为0；检验n=6/6，表 `[[3,3],[4,2]]`，期望频数 `[[3.5,2.5],[3.5,2.5]]`，因期望频数小于5用双侧 Fisher，P=1。
- 达标（DAS28≤2.7）：4/6 vs 0/6，以 `evalN` 为分母；双侧 Fisher P=0.060606060606060615。
- 活动分布（remission, low, moderate, high）：A `[2,2,2,0]`，B `[0,0,0,6]`，`evalN=6`；整体 Pearson P=0.007383160505359769，带 `SPARSE_EXPECTED_COUNTS`。
- 治疗线分布：A `[2,2,2]`，B `[1,2,3]`；整体 Pearson P=0.7659283383646487，带稀疏警告。未剔交集时展示分母为 `C.n=8`；id7的未知线另列。
- 交换 A/B 后，连续中位差和二分类百分点差反号；双侧 P 不变。

独立女性分母例：A `1 F / 5 M / 1 unknown`（`C.n=7`、已知n=6），B `5 F / 1 M / 2 unknown`（`C.n=8`、已知n=6）。展示率为1/7与5/8；检验仅使用 `[[1,5],[5,1]]`，双侧 Fisher P=0.08008658008658008。独立血清学分母例同样把未知保留在 `C.n` 展示分母而只从检验已知类别；QC未知例已删除，因为它在当前政策下不可达。活动用 `evalN`；治疗线用 `C.n` 并另列unknownLine。

## 边界输入与期望

- Type7手算：空集→分位数/IQR全null；`[4.25]`→4.25/4.25/4.25/IQR0；`[1,5]`→Q1=2、中位数=3、Q3=4、IQR=2；`[1,2,3,4,5,6]`→2.25/3.5/4.75/IQR2.5；`[1,1,2,2,2,8]`→1.25/2/2/IQR0.75。6个极近 BigDecimal：Q1=`0.1000000000000000035`、中位数=`0.100000000000000006`、Q3=`0.1000000000000000085`、IQR=`0.000000000000000005`。
- MW有效n：0 vs 6、4 vs 6→`INSUFFICIENT_SAMPLE/VALID_N_LT_5`且P=null；5 vs 5→exact，P=1/126；5 vs 6完全分离→exact，P=2/C(11,5)=0.004329004329004329。
- 每组有效n≥5但活动只剩一个非零类别→`DEGENERATE/ZERO_MARGIN`、P=null，不能伪装成成功且P=1。
- 2×2每格期望频数恰为5→Pearson且无Yates，P=1。
- ties 对照显式 asymptotic + continuity correction：U_A=7，P=0.2873330696714993，带 `SMALL_SAMPLE_TIES`。近BigDecimal严格有序对照使用精确秩，MW exact，P=1/126。
- overlap 优先对照：即使指标已知n=4/5，存在患者交集时仍应先返回 `OVERLAP`；不得先作不够样本判定或检验。主表id8缺HAQ但仍算交集。

以上数值是仓库所选开发默认下的技术性独立对拍期望，不构成统计/医学批准、生产数据适配证明或产品验收。
