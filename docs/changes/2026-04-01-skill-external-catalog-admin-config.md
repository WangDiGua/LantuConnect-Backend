# 技能在线市场：超管运行时配置（2026-04-01）

## 目标

- 技能市场相关参数（`lantu.skill-external-catalog` 结构）可由具备 `system:config` 权限的超管在前端通过接口保存，无需改部署文件即可在进程内生效。
- 数据库层**不新增表**：使用既有表 **`t_system_param`**，主键 **`skill_external_catalog`**，值为与 `SkillExternalCatalogProperties` 一致的 **JSON**。
- **无需 Flyway / 手工执行 DDL**：应用首次保存时 `INSERT` 该行即可；无该行时逻辑回退为 **`application.yml` 中 `lantu.skill-external-catalog` + 环境变量** 绑定的默认值。

## 后端 API

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/resource-center/skill-external-catalog/settings` | `system:config` | 返回 `config`（SkillsMP `apiKey` 清空）与 `skillsmpApiKeyConfigured` |
| PUT | `/resource-center/skill-external-catalog/settings` | `system:config` | Body 为完整 `SkillExternalCatalogProperties`；`skillsmp.apiKey` **留空表示保留原 Key**；需头 `X-User-Id` |

列表接口仍为 `GET /resource-center/skill-external-catalog`，读取**当前生效**配置（库中覆盖优先）。

## 实现要点

- `SkillExternalCatalogRuntimeConfigService#effective()`：有有效库内 JSON 则用库，否则克隆 YAML 默认。
- 出站 HTTP 请求使用 `SkillCatalogOutboundRestTemplateFactory`，按当前生效的 `outboundHttpProxy` **每次创建 RestTemplate**，避免代理变更后仍沿用旧 Bean。
- 技能市场列表缓存由 `SkillExternalCatalogCacheCoordinator` 持有；超管保存配置后 **invalidate**，下一次列表请求重新拉取。

## 前端（已落地）

- 仓库：`LantuConnect-Frontend`。路径：**资源中心 → 技能在线市场**，Tab **「市场列表 | 市场配置」**。
- `src/api/services/resource-center.service.ts`：`getSkillExternalCatalogSettings`、`putSkillExternalCatalogSettings`。
- `src/lib/http.ts`：PUT `/resource-center/skill-external-catalog/settings` 需已登录（`X-User-Id`）。
- `SkillExternalMarketSettingsForm.tsx`：表单覆盖 `SkillExternalCatalogProperties`；SkillsMP Key 密码框，留空表示保留；发现词多行；保存后刷新列表缓存（后端失效 + 可选触发列表重拉）。

## 运维注意

- 备份/迁移：导出 `t_system_param` 中 `key = 'skill_external_catalog'` 即可。
- 若 JSON 损坏，服务会打日志并回退 YAML，直至库内值被修复或通过 SQL 删除该行恢复纯文件配置。
