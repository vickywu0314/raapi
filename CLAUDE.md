# raapi · RA 类风湿研究平台后端

Spring Boot 2.2 + MyBatis，JDK 8 编译（pom 里的 lombok 1.18.0 在 JDK 17+ 编译不过）。前端工程：raweb。

## Git 协作规则（多人开发，必须遵守）

1. **开始改代码前先更新**：`git pull --rebase origin master`。
2. **提交前、推送前再更新一次**：`git pull --rebase origin master`，没有冲突再 `git push origin master`。
3. **有冲突时停下来**：不要用 `git push --force`、不要用 `git checkout --theirs/--ours` 整个文件覆盖别人的改动。逐处看清双方改了什么、合并后保证两边的改动都保留；拿不准就先问，不要自己决定丢掉哪一边。
4. **只提交自己改的文件**：`git add` 具体文件，不要 `git add -A` 把别人未提交的、本地临时文件一起带上；提交前 `git status` / `git diff --cached` 看一遍。
5. **一次提交只做一件事**，提交说明用中文写清楚改了什么、为什么。
6. **永远不要强推 master**，不要改写已经推送的历史（rebase / amend 已推送的提交）。

## 与老系统共用数据库（老系统仍在使用，不能影响它）

- 老表（patient_basic_info、patient_follow_up_history、patient_relation_doctor 等）**默认只读**。
- 必须写老表时：只改用户实际修改的字段；随访 7 个模块的 JSON 保持老系统的字段名、值类型和字段顺序，字典外字段原样保留（见 `VisitEditor`、`VisitJson`）。
- 老表只允许新增可为空或有默认值的字段，不改、不删老字段；新功能的数据放新表。
- 建表 / 改表 SQL 放 `src/main/resources/sql/`，文件名以日期开头，并写进 `docs/TODO.md` 的「上线前」。

## 约定

- 接口路径 `/api/ra/<业务>/<接口名>`，Controller / Service / Mapper / VO 分层（controller 只做参数和返回封装）。
- 只统计 RA：research_type 白名单只在 `ProjectMapper.xml` 的 `raResearchTypes` 一处配置（0,1,2,3,4,7）。
- 患者数据只能查看当前医生名下的（patient_relation_doctor.doctor_id）。
- 随访字段含义以 `docs/随访字段字典.md` / `VisitFieldDict` 为准（业务已确认）。
- 每加 / 改一个接口，同步更新 `docs/API.md`；待确认事项和上线前步骤记在 `docs/TODO.md`。
