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
- **可选** RabbitMQ（`docker-compose` 已包含；裸跑不配时**无需**装 Broker，默认不启消息监听，见配置说明）
- **可选** 全局 Maven 3.8+；未安装时可用仓库自带的 **Maven Wrapper**（`mvnw` / `mvnw.cmd`）

### 1. 初始化数据库

```bash
mysql -u root -p < sql/lantu_connect.sql
```

**增量升级（非首次建库）**：按 [`sql/migrations/README.md`](sql/migrations/README.md) 对 `sql/migrations/*.sql` 按名字顺序执行（或与 Flyway 二选一，见下）。

**Flyway（可选）**：若启用 `spring.flyway.enabled`，增量见 `src/main/resources/db/migration/`（当前含 V1～V5，与部分 `sql/migrations` 同内容，如技能包校验列、`skill_root_path` 等）。

### 2. 配置说明

主配置为 `src/main/resources/application.yml`：**结构 + `${环境变量:默认}` 占位**；文末 `---` 为 **`dev` profile**。具体填法见 **`application-database.example.yml`**（复制为 **`application-database.yml`** 后按需修改，已 gitignore）。

| 项 | 说明 |
|----|------|
| 推荐本地 | 对照 **application-database.example.yml** 维护 **application-database.yml**（库、Redis、RabbitMQ、JWT、`lantu.*`、`file.*`、`cors.*`、日志等） |
| 环境变量 | 占位键名见 `application.yml`；Docker/K8s 可只注入变量、不写覆盖文件 |
| 列表项 | 如 `lantu.api-deprecation.*-patterns`、`skill-pack-import.allowed-host-suffixes` 须在 YAML 中写多行列表（见 example） |
| 技能外部目录 | `skill-external-catalog.yml`（`spring.config.import` 可选加载） |
| 文件存储 | 默认 `FILE_STORAGE_TYPE=local`（本地上传目录）；对象存储见 MinIO 配置 |
| RabbitMQ 监听 | 默认 `RABBITMQ_LISTENER_AUTO_STARTUP=false`（无 Broker 可启动）；**`docker-compose` 对 app 默认注入 `true`**（栈内有 Rabbit）；**`SPRING_PROFILES_ACTIVE=dev`** 时默认仍为 `false`，需要队列时请设 `RABBITMQ_LISTENER_AUTO_STARTUP=true` |

可选：`application-local.yml`（已 gitignore）用于零散覆盖。

**常规做法**：维护 **`application-database.yml`**／对照 **`application-database.example.yml`**，覆盖库表、Redis、JWT、`lantu.*`、`file.*`、`cors.*` 等所有业务向配置。

也可用 **`.env`** + Docker Compose；Spring 不直接读取 `.env`，需由 Compose 或 shell `export` 注入为环境变量。

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

- API 基础路径：`http://localhost:8080/regis`
- Swagger 文档：`http://localhost:8080/regis/swagger-ui.html`
- Actuator：`http://localhost:8080/regis/actuator/health`

## 资源中心与技能包（摘要）

- **REST 前缀**：`/regis/resource-center/resources`（见 `ResourceRegistryController`）：资源 CRUD、提审、版本、技能包上传与导入 URL 等。
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

仓库内 `prometheus.yml` 默认抓取 `/regis/actuator/prometheus`。自默认安全策略起该路径**不再匿名**：请在应用环境设置 `PERMIT_PROMETHEUS_WITHOUT_AUTH=true`（仅信任网络时，例如 `docker-compose` 可先 `export` 该变量再 `up`），或为 Prometheus 配置带 `Authorization: Bearer …` 的抓取，并将应用侧保持默认 `false`。详见 [docs/security-hardening.md](docs/security-hardening.md)。

## API 概览

| 模块 | 路径前缀 | 说明 |
|------|----------|------|
| 认证 | `/regis/auth` | 登录、注册、JWT 刷新、密码修改 |
| 资源中心 | `/regis/resource-center/resources` | Agent/Skill/MCP/App/Dataset 等资源注册、审核、版本、**技能包上传/分片** |
| Agent | `/regis/agents` | Agent CRUD + 版本发布/回滚 |
| Skill | `/regis/v1/skills` | Skill CRUD + 调用（与资源中心 skill 并存时对齐以前端联调文档为准） |
| MCP Server | `/regis/v1/mcp-servers` | MCP 服务注册与 CRUD |
| 应用 | `/regis/v1/apps` | 智能应用 CRUD |
| 数据集 | `/regis/v1/datasets` | 数据集 CRUD + 权限申请 |
| 提供商 | `/regis/v1/providers` | 服务提供商 CRUD |
| 分类 | `/regis/v1/categories` | 分类树 CRUD |
| 标签 | `/regis/tags` | 标签管理 + 批量创建 |
| 用户管理 | `/regis/user-mgmt` | 用户/角色/API Key/Token/组织架构 |
| 监控 | `/regis/monitoring` | KPI、调用日志、告警、链路追踪 |
| 健康检查 | `/regis/health` | 健康配置 + 熔断器管理 |
| 系统配置 | `/regis/system-config` | 模型/限流/参数/安全/审计日志 |
| 配额 | `/regis/quotas` | 配额管理 |
| 仪表盘 | `/regis/dashboard` | 管理概览 + 用量统计 |
| 审核 | `/regis/audit` | Agent/Skill 审核流程 |
| 评论 | `/regis/reviews` | 评分评论 + 有用标记 |
| 用户活动 | `/regis/user` | 使用记录、收藏、个人统计 |
| 用户设置 | `/regis/user-settings` | 工作区设置、个人 API Key |
| 通知 | `/regis/notifications` | 通知列表、未读数、已读标记 |
| 文件 | `/regis/files` | 文件上传 |

## License

MIT
