# 敏感词模块开源改造说明（2026-03-26）

## 1. 改造目标
- 废弃自研 DFA 敏感词引擎实现。
- 统一切换到开源引擎 `houbb/sensitive-word`。
- 词库来源改为：`数据库词库 + 开源内置词库` 同时生效。
- 保持已有对外 API 路径与返回结构不变，减少前端改动成本。

开源项目：<https://github.com/houbb/sensitive-word>

## 2. 本次后端改动

### 2.1 依赖变更
- `pom.xml`
  - 新增属性：`sensitive-word.version=0.29.5`
  - 新增依赖：`com.github.houbb:sensitive-word`

### 2.2 引擎实现变更
- `SensitiveWordService` 由“调用自研 `SensitiveWordFilter`”改为“调用 `SensitiveWordBs` 引擎”。
- 词库构建策略：
  - 开源默认白名单：`WordAllows.defaults()`
  - 开源默认黑名单：`WordDenys.defaults()`
  - 数据库黑名单：`IWordDeny`（从 `t_sensitive_word` 读取启用词）
  - 最终：`WordDenys.chains(WordDenys.defaults(), dbWordDeny)`
- 刷新策略：
  - 应用启动刷新一次
  - 定时（每小时）刷新一次
  - 管理接口新增/批量新增/TXT 导入/编辑/删除后立即刷新

### 2.3 删除自研实现
- 删除文件：
  - `src/main/java/com/lantu/connect/common/sensitive/SensitiveWordFilter.java`
  - `src/main/java/com/lantu/connect/common/sensitive/SensitiveWordNode.java`

## 3. 接口兼容性（前端关注）

以下 API 路径保持不变：
- `GET /api/sensitive-words`
- `GET /api/sensitive-words/categories`
- `GET /api/sensitive-words/count`
- `POST /api/sensitive-words`
- `POST /api/sensitive-words/batch`
- `POST /api/sensitive-words/import-txt`（multipart）
- `PUT /api/sensitive-words/{id}`
- `DELETE /api/sensitive-words/{id}`
- `POST /api/sensitive-words/check`

### 行为差异说明
- 过滤逻辑由原自研规则改为开源默认规则，命中结果可能更严格（会多命中部分变体）。
- `filter(text, replacement)` 在自定义替换串时，按开源默认 `*` 掩码分组再替换。
- `/count` 目前返回“DB 启用词条数”（不包含开源内置词总量）。

## 4. 数据库改动建议

### 4.1 必须改动
- 本次改造**无需新增表或改字段**，现有 `t_sensitive_word` 可继续使用。

### 4.2 建议改动（可选，但推荐）
- 确保有唯一索引：`uk_sensitive_word(word)`（已有则无需改）。
- 统一历史数据规范：建议一次性将 `word` 做 `trim + lower` 清洗，减少重复与误差。
- 若需要更细粒度运营（未来按标签治理）可扩展：
  - `tag` 字段（或单独标签关系表）
  - `tenant_id`（多租户隔离时）

## 5. 前端联调建议
- 继续使用原有管理页面与接口路径。
- 重点回归：
  - `/sensitive-words/check` 的命中结果变化
  - 评论发布时的敏感词替换展示
  - TXT 导入后即时生效

## 6. 已验证项
- `mvn -DskipTests compile` 通过。
- 敏感词服务链路已切换到开源引擎。

## 7. 后续可直接开源替换的候选模块（建议）

为避免“自研不全面”，建议优先评估以下方向：

1) 限流与熔断（已部分使用 Resilience4j）
- 可进一步统一到 `resilience4j-bulkhead/retry/timelimiter` 全套策略，替代分散式自定义防抖/兜底逻辑。

2) 权限模型（RBAC/ABAC）
- 若后续权限复杂度增长，可评估 `casbin`（Java）做策略外置和可视化治理，减少硬编码判断。

3) 审计日志与操作留痕
- 可引入 `javers` 做对象级审计快照和差异追踪，降低手写审计字段拼装成本。

4) API 防重复提交与幂等
- 可引入成熟幂等组件/模式（Spring + Redis 锁与幂等键标准化），替代散点防重代码。

5) 文件导入解析
- 现已支持 TXT；若后续需要 CSV/Excel，建议用 `easyexcel`（读写性能和模板能力更成熟）。

6) 统一校验增强
- 在 `jakarta validation` 基础上补充可复用自定义约束（手机号、资源编码、版本号规则），减少重复 if-else 校验。

---

如需继续推进下一阶段，我建议从“权限模型外置（casbin PoC）”开始，收益通常最高且对业务风险可控。
