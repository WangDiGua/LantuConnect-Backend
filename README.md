# NexusAI Connect 后端服务

NexusAI（NexusAI Connect）—— 面向高校的智能体接入与管理平台后端。

## 文档体系（与前端协作）

| 文档 | 维护方 / 读者 | 作用 |
|------|----------------|------|
| [docs/backend-architecture.md](docs/backend-architecture.md) | **前端**编写准则 → **后端**按此实现 | 目标规范与整体设计；文内 **[后端实现状态说明](docs/backend-architecture.md#后端实现状态说明相对本文目标的差距)** 对照代码列差距（快照日期见该节）。 |
| [docs/frontend-alignment-handbook.md](docs/frontend-alignment-handbook.md) | **后端**编写 → **前端**联调依据 | **以当前仓库代码为准** 的契约：路径、请求头、分页、RBAC、DTO、错误码与「实现差距」说明，减少仅按架构文档开发导致的前后端不一致。 |
| [docs/bug-fixes.md](docs/bug-fixes.md) | **后端**记录 | 已暴露问题与修复（BUG-xxx）；**实现审计**见 **AUDIT-xxx**；**功能补全**见 **IMPL-xxx**；**若影响接口行为**，应同步修订 `frontend-alignment-handbook.md`。 |
| [docs/remaining-work.md](docs/remaining-work.md) | **后端**维护 | **待办清单**（安全、业务、定时任务），与架构/对齐手册联动；完成一项则删减并更新 §9.5。 |

**建议维护约定**

1. **`backend-architecture.md` 变更后**：后端评估是否改代码；无论是否改代码，若影响联调契约，**同步更新** `frontend-alignment-handbook.md`（并通知前端）。  
2. **修复影响接口或约定行为的 Bug**：在 `bug-fixes.md` 记录；同时在 `frontend-alignment-handbook.md` 中更新相关说明（或在 bug 条目中写明「对齐手册 §… 已更新」）。  
3. **前端联调**：优先 **`frontend-alignment-handbook.md` + Swagger**；架构文档作背景阅读。

## 技术栈

| 层次 | 技术 |
|------|------|
| 语言 / 框架 | Java 17 + Spring Boot **3.2.12** |
| ORM | MyBatis-Plus 3.5 |
| 数据库 | MySQL 8.0 |
| 缓存 | Redis 7 |
| 消息队列 | RabbitMQ（可选；本地无 Broker 时请见下方 **运行 profile**） |
| 认证 | Spring Security + JWT（双 Token） |
| 限流 / 熔断 | Resilience4j |
| API 文档 | SpringDoc OpenAPI 3 |
| 监控 | Micrometer + Prometheus + Grafana |

## 项目结构

```
src/main/java/com/lantu/connect/
├── common/          通用基础设施（配置、过滤器、异常、AOP、工具类）
├── gateway/         统一网关与资源中心（invoke、资源注册/审核、技能包上传与校验、外部技能目录等）
├── auth/            认证模块（登录、注册、JWT、密码）
├── agent/           Agent 生命周期管理 + 版本管理
├── skill/           Skill / MCP 等历史或领域模块（与 gateway 资源模型并存时请以对齐手册为准）
├── app/             智能应用管理
├── dataset/         数据集、提供商、分类、标签
├── sysconfig/       系统配置（模型、限流规则、系统参数、安全设置）
├── monitoring/      监控（调用日志、告警、链路追踪、健康检查、熔断器）
├── usermgmt/        用户管理、角色、API Key、Token、组织架构
├── dashboard/       仪表盘（管理概览、用户工作台、用量统计）
├── audit/           审核工作流
├── review/          评论与评分
├── useractivity/    使用记录、收藏
├── usersettings/    用户设置
├── notification/    通知系统
└── task/            定时任务（配额重置、健康检查、熔断状态等）
```

## 快速开始

### 环境要求

- JDK 17+
- MySQL 8.0+、Redis 7+（本机或 Docker 均可）
- **可选** RabbitMQ（`docker-compose` 已包含；本机不配时请用 **`dev` profile** 或关闭监听，见配置说明）
- **可选** 全局 Maven 3.8+；未安装时可用仓库自带的 **Maven Wrapper**（`mvnw` / `mvnw.cmd`）

### 1. 初始化数据库

```bash
mysql -u root -p < sql/lantu_connect.sql
```

**增量升级（非首次建库）**：按 [`sql/migrations/README.md`](sql/migrations/README.md) 对 `sql/migrations/*.sql` 按名字顺序执行（或与 Flyway 二选一，见下）。

**Flyway（可选）**：若启用 `spring.flyway.enabled`，增量见 `src/main/resources/db/migration/`（当前含 V1～V5，与部分 `sql/migrations` 同内容，如技能包校验列、`skill_root_path` 等）。

### 2. 配置说明

配置已收敛为 **单一** `src/main/resources/application.yml`（文末以 `---` 定义 **`dev` profile**）。已不再使用 `application-dev.yml` / `application-prod.yml` 分文件；默认**不加** `spring.profiles.active` 时为上线向默认值。

| 项 | 默认值 / 说明 |
|----|----------------|
| 数据库 | `DB_HOST`/`DB_PORT`/`DB_NAME` 默认 `localhost:3306` / `lantu_connect` |
| 账号 | `DB_USER` / `DB_PASSWORD` 默认 `root` / `root` |
| Redis | `REDIS_HOST` / `REDIS_PORT` 默认 `localhost` / `6379`（无密码可不配 `password`） |
| RabbitMQ | `RABBITMQ_*`；**本地无 Rabbit 时**建议 `SPRING_PROFILES_ACTIVE=dev`（profile 内可将 `RABBITMQ_LISTENER_AUTO_STARTUP` 置为 `false`）或保证 Broker 已启动 |
| JWT | `JWT_SECRET` 未设置时使用文件内开发用默认值；**生产务必改为环境变量** |
| HTTPS | `REQUIRE_HTTPS=true` 时启用通道安全与 HSTS（见 `lantu.security.require-https`） |
| Prometheus | 默认 `PERMIT_PROMETHEUS_WITHOUT_AUTH=false`，`/actuator/prometheus` 需鉴权；本地或受控内网裸拉取可设 `true` |
| 敏感加解密 | `LANTU_ENCRYPTION_KEY` 覆盖默认密钥；生产禁止使用开发占位值 |
| 生产 API 文档 | 默认关闭 SpringDoc；本地可 `EXPOSE_API_DOCS=true` |
| 反向代理 | 置于 Nginx/Ingress 后可设 `TRUST_PROXY_FORWARDED_HEADERS=true` 以正确限流与审计客户端 IP |
| MySQL TLS | 生产向可设 `DB_USE_SSL=true`；本机无证书时可设 `DB_USE_SSL=false` |
| 日志 | `LOG_LEVEL_LANTU` 默认 `info`；排查 MyBatis 逐条 SQL 时可设 `LOG_LEVEL_MYBATIS=debug`（高流量慎用） |
| 连接池 | `HIKARI_MAX_POOL_SIZE` / `HIKARI_MIN_IDLE` / `HIKARI_CONNECTION_TIMEOUT_MS` 等见 `application.yml` |
| 技能外部目录 | `skill-external-catalog.yml`（由 `spring.config.import` 可选加载） |

更敏感的本地覆盖可放在 **已被 .gitignore 忽略** 的 `application-local.yml` 中。

**本地推荐启动示例（Windows PowerShell）：**

```powershell
$env:SPRING_PROFILES_ACTIVE = 'dev'
.\mvnw.cmd spring-boot:run -DskipTests
```

### 3. 启动

需已安装 **JDK 17**，并设置环境变量 **`JAVA_HOME`**（`mvnw.cmd` 依赖此项）。

```bash
# 项目根目录
./mvnw spring-boot:run -DskipTests
```

Windows：

```bat
mvnw.cmd spring-boot:run -DskipTests
```

已安装全局 Maven 时也可用：`mvn spring-boot:run -DskipTests`。

服务启动后访问：

- API 基础路径：`http://localhost:8080/api`
- Swagger 文档：`http://localhost:8080/api/swagger-ui.html`
- Actuator：`http://localhost:8080/api/actuator/health`

## 资源中心与技能包（摘要）

- **REST 前缀**：`/api/resource-center/resources`（见 `ResourceRegistryController`）：资源 CRUD、提审、版本、技能包上传与导入 URL 等。
- **技能包**：支持 multipart / JSON 元数据上传、HTTPS URL 导入、大文件 **分片断点续传**（`.../skills/package-upload/chunk/*`）；入库前 **安全扫描**（`SkillArtifactSafetyValidator`），语义与清单见 **Anthropic 子集校验**。
- **skillRoot**：表 `t_resource_skill_ext.skill_root_path` 表示 zip 内子树根路径，用于子树校验与 resolve 规格中的 `skillRootPath`。
- **制品存储**：`file.storage-type` 为 `local`（默认上传目录）或 `minio`；大文件分片会话目录默认为 `{upload-dir}/.skill-chunk-sessions/{uploadId}/`。

## Docker 部署

```bash
# 构建
mvn clean package -DskipTests

# 启动全部服务（MySQL + Redis + RabbitMQ + App 等，见 docker-compose.yml）
docker-compose up -d
```

`docker-compose` **不会**强绑 `SPRING_PROFILES_ACTIVE=prod`；需本地联调可在环境或 `.env` 中设 `SPRING_PROFILES_ACTIVE=dev`。

仓库内 `prometheus.yml` 默认抓取 `/api/actuator/prometheus`。自默认安全策略起该路径**不再匿名**：请在应用环境设置 `PERMIT_PROMETHEUS_WITHOUT_AUTH=true`（仅信任网络时，例如 `docker-compose` 可先 `export` 该变量再 `up`），或为 Prometheus 配置带 `Authorization: Bearer …` 的抓取，并将应用侧保持默认 `false`。详见 [docs/security-hardening.md](docs/security-hardening.md)。

## API 概览

| 模块 | 路径前缀 | 说明 |
|------|----------|------|
| 认证 | `/api/auth` | 登录、注册、JWT 刷新、密码修改 |
| 资源中心 | `/api/resource-center/resources` | Agent/Skill/MCP/App/Dataset 等资源注册、审核、版本、**技能包上传/分片** |
| Agent | `/api/agents` | Agent CRUD + 版本发布/回滚 |
| Skill | `/api/v1/skills` | Skill CRUD + 调用（与资源中心 skill 并存时对齐以前端联调文档为准） |
| MCP Server | `/api/v1/mcp-servers` | MCP 服务注册与 CRUD |
| 应用 | `/api/v1/apps` | 智能应用 CRUD |
| 数据集 | `/api/v1/datasets` | 数据集 CRUD + 权限申请 |
| 提供商 | `/api/v1/providers` | 服务提供商 CRUD |
| 分类 | `/api/v1/categories` | 分类树 CRUD |
| 标签 | `/api/tags` | 标签管理 + 批量创建 |
| 用户管理 | `/api/user-mgmt` | 用户/角色/API Key/Token/组织架构 |
| 监控 | `/api/monitoring` | KPI、调用日志、告警、链路追踪 |
| 健康检查 | `/api/health` | 健康配置 + 熔断器管理 |
| 系统配置 | `/api/system-config` | 模型/限流/参数/安全/审计日志 |
| 配额 | `/api/quotas` | 配额管理 |
| 仪表盘 | `/api/dashboard` | 管理概览 + 用量统计 |
| 审核 | `/api/audit` | Agent/Skill 审核流程 |
| 评论 | `/api/reviews` | 评分评论 + 有用标记 |
| 用户活动 | `/api/user` | 使用记录、收藏、个人统计 |
| 用户设置 | `/api/user-settings` | 工作区设置、个人 API Key |
| 通知 | `/api/notifications` | 通知列表、未读数、已读标记 |
| 文件 | `/api/files` | 文件上传 |

## License

MIT
