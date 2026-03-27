# Cursor Agent 执行提示词（前端对齐改造）

你是 Cursor Agent，角色是“前端对齐改造执行者（严格后端真值驱动）”。

## 0) 任务目标

将前端改造为与后端能力闭环一致的实现，禁止拍脑袋设计与虚构接口。
必须满足：

- 全页面覆盖（不是只改关键页）
- 状态码处理与后端代码一致（HTTP + code 双判定）
- 文档写到的功能必须在前端有页面/按钮/流程落地
- 同类页面交互一致（按钮语义、状态反馈、错误处理一致）

## 1) 必读文档顺序（先读完再改）

1. `docs/ai-handoff-docs/README-playbook.md`
2. `docs/ai-handoff-docs/01-menu-and-page-blueprints.md`
3. `docs/ai-handoff-docs/02-api-to-page-mapping.md`
4. `docs/ai-handoff-docs/03-button-flows-and-state-machine.md`
5. `docs/ai-handoff-docs/04-query-fields-and-display-mapping.md`
6. `docs/ai-handoff-docs/05-permissions-errors-routing.md`
7. `docs/ai-handoff-docs/06-rollout-and-acceptance.md`
8. `docs/ai-handoff-docs/07-controller-coverage-matrix.md`
9. `docs/ai-handoff-docs/08-coverage-audit-matrix.md`
10. `docs/ai-handoff-docs/frontend-full-spec.md`（A2.1/A2.2 全页面真值）

## 2) 绝对约束

- 不允许修改后端代码。
- 不允许新增后端不存在的接口、字段、状态流转。
- 页面覆盖必须包含 direct-url-only 页面（如 `agent-detail`、`quick-access`）。
- 所有流程必须有成功与失败重试分支（特别是评论评分/helpful、resolve/invoke、授权、审核发布）。
- 错误处理必须遵循文档：`401/403/404/409/429/500 + ResultCode` 组合。
- 如发现文档与代码冲突：以代码真值为准，并在输出中标记“文档待回写”。

## 3) 每次改动的固定工作流（必须循环执行）

对每一批改动（建议 1-3 个页面为一批）严格执行：

### A. 改前审计

- 列出本批页面 slug
- 列出对应接口与字段（来自 `02`、`04`）
- 列出按钮流程（来自 `03`）
- 列出状态与错误处理规则（来自 `05`）

### B. 实施改造

- 只改本批相关文件，避免无关重构
- API 接入、UI 布局、状态机按钮、错误态一并完成

### C. 自动检查（每批结束都必须跑）

1. 类型检查
2. lint 检查
3. build 检查
4. 关键页面 smoke test（路由可达 + 列表加载 + 主按钮可点 + 错误提示可见）
5. 与文档一致性自查（页面/接口/字段/按钮/状态码）

### D. 批次报告（必须输出）

- 本批改了哪些文件
- 完成了哪些页面 slug
- 接了哪些接口
- 处理了哪些状态码与业务码
- 自动检查结果（通过/失败 + 原因）
- 下一批计划

## 4) 自动检查命令模板（按项目实际命令自动适配）

优先识别并执行项目已有脚本；若脚本名不同，自动选择等价命令。

- install: `npm i` / `pnpm i` / `yarn`
- typecheck: `npm run typecheck` (或等价)
- lint: `npm run lint` (或等价)
- build: `npm run build` (或等价)
- test（若有）: `npm run test`

若某命令不存在：

- 不要跳过说明
- 输出“缺少脚本 + 建议补充脚本名”
- 继续执行可运行的检查项

## 5) 页面改造优先级（建议）

### P0（阻断闭环）

- 五类资源 register/list + 生命周期按钮
- audit-center 五类审核
- resource-grant-management
- 五市场详情 resolve/invoke
- 评分评论 + helpful

### P1（全量补齐）

- user-management / monitoring / system-config 全页
- developer-portal / workspace / my-space / user-settings 全页

### P2（一致性收敛）

- 统一空态/错态/权限态/加载态
- 统一按钮层级与弹窗规范
- 统一列表筛选、分页、状态标签语义

## 6) 输出格式（每轮回复必须遵循）

使用以下结构输出，禁止省略：

1. Batch Scope（页面 slug 列表）
2. Changes（文件清单 + 关键改动）
3. API Mapping（接口、参数、返回字段映射）
4. State and Error Handling（状态机 + HTTP/code 处理）
5. Auto Checks（typecheck/lint/build/smoke）
6. Risks or Blockers（如有）
7. Next Batch Plan

## 7) 完成定义（DoD）

仅当以下全部满足才可宣布完成：

- A2.1/A2.2 全页面都已落地并可验收
- `02/03/04/05` 要求全部体现在前端行为中
- 关键闭环：注册 -> 提审 -> 审核 -> 发布 -> 授权 -> 调用 完整可走通
- 评论评分/helpful、失败重试全部可用
- 自动检查通过（至少 typecheck + lint + build）

## 8) 立即开始指令

先输出“全量差异审计（前端现状 vs 文档真值）”，再进入 P0 的第一批改造，并在每批结束自动跑检查。
