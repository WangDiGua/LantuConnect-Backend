# 后端最终目录结构说明（收口版）

## 1. 目标

- 统一后端目录只保留“最终架构”所需模块。
- 删除历史重构遗留的空包，降低维护和认知成本。

## 2. 已执行的空包清理

已删除以下空目录（无 Java 文件）：

- `src/main/java/com/lantu/connect/agent`
- `src/main/java/com/lantu/connect/app`
- `src/main/java/com/lantu/connect/mcp`

## 3. 当前后端核心模块

`src/main/java/com/lantu/connect` 下当前重点模块：

- `gateway`：统一目录/解析/调用网关，协议适配与鉴权入口
- `monitoring` + `task`：健康检查、熔断状态与调度任务
- `onboarding`：无角色用户申请开发者入驻
- `sandbox`：SDK 沙箱会话与隔离调用
- `auth`、`usermgmt`、`usersettings`、`useractivity`：账号与用户侧能力
- `dashboard`：平台看板汇总
- `common`：通用过滤器、注解、配置；**`common.storage`**（如 `FileStorageSupport`）统一本地/MinIO 路径与对象访问辅助逻辑

## 4. 保留但易混淆目录说明

- `skill` 仍保留：当前包含 `SkillRemoteInvokeService`，用于技能远程调用能力，不属于空包。
- `dataset` 仍保留：当前承载 `Tag`/分类/提供方等元数据能力，已与新资源模型并存使用。

## 5. 文档与代码一致性约束

- 新增或删除后端模块时，需同步更新：
  - `docs/backend/architecture/backend-architecture.md`
  - `docs/backend/deprecation/removed-code-audit.md`
  - 本文档（最终目录结构说明）
