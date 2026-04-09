# 目录与资源消费 — 对外契约（API-1）

本文面向前端 / 集成方，与 SpringDoc 生成的 OpenAPI 一致；权威运行时行为以代码与 `R` 包装响应为准。

## 打开 Swagger 与 OpenAPI JSON

- 主配置默认关闭 SpringDoc；本地请在启用 `springdoc.api-docs.enabled` / `swagger-ui.enabled` 的 profile（如 `application-local.yml`）下访问。
- 假定 `server.servlet.context-path=/regis` 时：
  - Swagger UI：`http://localhost:8080/regis/swagger-ui.html`
  - OpenAPI 3 JSON：`http://localhost:8080/regis/v3/api-docs`
- 匿名访问文档依赖 `lantu.security.expose-api-docs=true`（生产建议关闭）。

统一成功响应：`{"code":0,"data":...,"message":"ok","timestamp":...}`。

## `access_policy`（目录项 `accessPolicy`）

存于 `t_resource.access_policy`，**历史兼容字段**：迁移 `20260409_remove_resource_grants_and_open_catalog.sql` 已将存量刷为 `open_platform`，新建资源在 `ResourceRegistryServiceImpl` 亦固定写入 `open_platform`。

**当前网关真值**：`resolve` / `invoke` **不再**根据本字段或已删除的 `t_resource_invoke_grant` 做「逐资源 Grant」裁决；在资源存在、生命周期允许的前提下，主要由 **有效 `X-Api-Key`**、**Key 的 scope**、**`published`** 及控制台/RBAC 目录类型权限等共同约束（见 `ResourceInvokeGrantService` 类注释与 `PRODUCT_DEFINITION.md` §4）。

| 取值 | 含义（仅作库表/JSON 兼容说明，**不是** invoke 的独立开关） |
|------|----------------|
| `grant_required` / `open_org` / `open_platform` | 历史枚举名仍可出现在旧数据或 OpenAPI 中；**不应**再按「须 Grant / 免 Grant」理解网关行为。 |

## `include`（目录与解析）

-query / body 字段 **`include`** 为 **逗号分隔** 小写片段，仅请求的块会在响应中填充：

| 片段 | 响应字段 |
|------|-----------|
| `observability` | `observability`（`Map`） |
| `quality` | `quality`（`Map`） |
| `tags` | `tags`（`List<String>`，解析 VO；目录项标签行为见 OpenAPI 模型说明） |

适用接口示例：`GET /catalog/resources`、`GET /catalog/resources/{type}/{id}`、`POST /catalog/resolve`；SDK 镜像路径见 [SDK OpenAPI 与沙箱](../backend/api/sdk-openapi-and-sandbox.md)。

## 推荐前端调用链（详情 + 口碑）

1. 列表：`GET /catalog/resources`（分页，含 `createdByName`、`ratingAvg`、`reviewCount`、`accessPolicy` 等）。
2. 详情：`GET /catalog/resources/{type}/{id}` 或 `POST /catalog/resolve`（**POST 解析必须带 X-Api-Key**）。
3. 评分摘要：`GET /catalog/resources/{type}/{id}/stats`。
4. 评论分页：`GET /reviews/page?resourceType=...&resourceId=...`（参数别名：`targetType`/`targetId`）。

评论写操作须已认证，并依赖服务端注入的 `X-User-Id`（见 OpenAPI 安全方案：`bearerAuth` 与 `apiKey`）。

## 相关代码索引

- 全局 OpenAPI 与安全方案：`com.lantu.connect.common.config.OpenApiConfiguration`
- 目录控制器：`ResourceCatalogController`
- SDK v1：`SdkGatewayController`、`/sdk/v1/*`
