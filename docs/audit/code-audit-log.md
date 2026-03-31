# 后端逐代码静态审阅记录（全量分批）

**审阅日期**：2026-03-31  
**范围**：[`src/main/java`](../../src/main/java) 主代码（约 371 个 `.java` 文件）、[`application.yml`](../../src/main/resources/application.yml)、[`logback-spring.xml`](../../src/main/resources/logback-spring.xml)。  
**方法**：按包分批核对配置、过滤器链、条件 Bean、REST 面、持久化策略；**不**将 `mvnw test` 等同为业务闭环证明。

---

## Batch 1 — 入口与横切

| 项 | 文件 / 配置 | 结论 | 闭环标注 |
|----|-------------|------|----------|
| 启动 | [`LantuConnectApplication.java`](../../src/main/java/com/lantu/connect/LantuConnectApplication.java) | `@SpringBootApplication`，`@EnableAsync` / `@EnableScheduling`，`@MapperScan` 含 `**.mapper` 与 `common.sensitive` | API存在 |
| 数据源 / 中间件 | [`application.yml`](../../src/main/resources/application.yml) | MySQL/Redis/RabbitMQ 默认 `localhost`；缺组件则**运行时**失败 | 外联依赖部署 |
| JWT / 安全属性 | [`SecurityProperties.java`](../../src/main/java/com/lantu/connect/common/config/SecurityProperties.java) | 白名单含登录注册、captcha、swagger（可关）、health/info；`allowHeaderUserIdFallback` 默认 false | 权限前置 |
| Security 链 | [`SecurityConfig.java`](../../src/main/java/com/lantu/connect/common/config/SecurityConfig.java) | CSRF 关闭；JWT Filter → `UnassignedUserAccessFilter` → `IdempotencyFilter`；HTTPS/HSTS 可配 | 权限前置 |
| JWT / API Key / Sandbox | [`JwtAuthenticationFilter.java`](../../src/main/java/com/lantu/connect/common/filter/JwtAuthenticationFilter.java) | Bearer 解析、`X-Api-Key` 与 `/sandbox/invoke`+`X-Sandbox-Token` 放行分支 | 业务闭环 |
| 旧 API | [`LegacyApiDeprecationFilter.java`](../../src/main/java/com/lantu/connect/common/filter/LegacyApiDeprecationFilter.java) | `/v1/**`、`/agents/**` 写操作 410 | API存在 |
| 其余 Filter | `TraceIdFilter`、`AccessLogFilter`、`UnassignedUserAccessFilter` | 补充观测与未分配角色拦截（需结合实现） | 权限前置 |
| 集成模拟 | `application.yml` → `lantu.system.integration-mock: true` | 与 `NetworkApplyServiceImpl`、`AclPublishServiceImpl`、`SystemParamFacadeServiceImpl` 一致：**默认模拟** | **外联模拟** |
| GeoIP | `application.yml` → `geoip.enabled: true` | 依赖外网 `ip-api.com`；内网需行为预期 | 外联 |
| 通知开关 | `lantu.notification.sms-enabled` / `email-enabled` | 默认 false | **缺省关闭** |
| Skill Pack URL | `lantu.skill-pack-import` | SSRF 相关：`allowed-host-suffixes` 默认可空、`require-allowed-host-suffixes` 默认 false | 配置敏感 |
| 配置类清单 | `common/config/*.java`（15） | `JwtConfig`、`Cors`、`MyBatisPlus`、`RabbitMqConfig`（条件）、`LegacyApiDeprecationProperties`、`BackendContractProperties` 等 | 已覆盖 |

---

## Batch 2 — `com.lantu.connect.common`（85 文件）

| 子域 | 代表路径 | 结论 |
|------|----------|------|
| 条件 Bean | `MockSmsServiceImpl` / `AliyunSmsServiceImpl` | **`lantu.sms.provider` 未出现在 `application.yml`** → `matchIfMissing=true` → **默认 Mock 短信** |
| 条件 Bean | `SmsNotificationChannel` / `EmailNotificationChannel` | 短信需 `lantu.notification.sms-enabled=true`；邮件需 `spring.mail.host` |
| 条件 Bean | `RabbitMqConfig` | `@ConditionalOnProperty(spring.rabbitmq.host)`；yml 默认有 host → **期望 Rabbit 可用** |
| 集成 | `NetworkApplyServiceImpl`、`AclPublishServiceImpl` | 读 `integration-mock`，真外联需 false + URL |
| 存储 | `FileStorageService`、`FileStorageSupport` | `file.storage-type` local/minio；与制品路径一致 |
| 安全 | `CasbinAuthorizationService`、`RequirePermission*`、`RequireRole*` | 与 Controller 注解配合 |
| 幂等 | `IdempotencyFilter`、`IdempotencyProperties` | 与 `lantu.idempotency` 对齐 |
| 其它 | captcha、geo、encrypt、validation、GlobalExceptionHandler | 支撑域，无搁置 TODO（全仓 `TODO|FIXME` grep 为 0） |

---

## Batch 3 — `auth` + `gateway`

### Auth（26 文件）

- **Controller**：[`AuthController`](../../src/main/java/com/lantu/connect/auth/controller/AuthController.java)（登录、注册、刷新、短信等）。
- **服务链**：`AuthServiceImpl` → 多 `*Mapper` + `SmsService`（默认 Mock）。
- **实体**：`t_user`、`t_sms_verify_code`、`t_login_history` 等（见 Batch 5 表清单）。

### Gateway（59 文件）

| 类 | 职责 |
|----|------|
| `ResourceRegistryController` | 资源 CRUD、版本、技能包上传/导入 URL、`skill-artifact` 下载 |
| `ResourceCatalogController` | 目录、`/invoke`、`/invoke-stream`、`/catalog/resolve`、app launch |
| `ResourceGrantController` | Grant CRUD |
| `GrantApplicationController` + `GrantApplicationServiceImpl` | 授权申请流 |
| `SdkGatewayController` | SDK 路由 |
| `UnifiedGatewayServiceImpl` | 大核心：大量 **`JdbcTemplate` 原生 SQL** 访问 `t_resource`、invoke 治理、统计、explore 等 |
| `ResourceRegistryServiceImpl` | 资源注册 JDBC 与生命周期 |
| `SkillPackUploadService` / `SkillPackUrlFetcher` / `SkillArtifactDownloadService` | 技能包与制品 |
| `protocol/*` | HTTP JSON、MCP JSON-RPC、SSE、OAuth2 client_credentials |
| `security/*` | API Key scope、Grant、治理、launch token |

**闭环标注**：invoke/resolve 链在代码层面完整，但依赖 **MySQL 表结构、`t_resource` 数据、Redis、外部 MCP/HTTP 上游**；与「仅单元测试」不等价。

---

## Batch 4 — 其余业务域（REST 清单）

以下与 [`controller-inventory.md`](controller-inventory.md) 一致（28 `@RestController` + `GlobalExceptionHandler` 为 Advice）：

| 模块 | Controller |
|------|------------|
| monitoring | `HealthController`, `MonitoringController` |
| dashboard | `DashboardController` |
| sysconfig | `SystemParamController`, `ModelConfigController`, `RateLimitRuleController`, `AnnouncementController`, `QuotaController`, `QuotaRateLimitController` |
| audit | `AuditController` |
| usermgmt | `UserMgmtController` |
| usersettings | `UserSettingsController` |
| useractivity | `UserActivityController` |
| notification | `NotificationController` |
| review | `ReviewController` |
| sandbox | `SandboxController` |
| onboarding | `DeveloperApplicationController`, `DeveloperStatisticsController` |
| dataset | `ProviderController`（**仅 GET 分页**）, `TagController` |
| common | `FileController`, `CaptchaController`, `SensitiveWordController` |

**Provider（已补全 CRUD，2026-03-31）**：

- [`ProviderController.java`](../../src/main/java/com/lantu/connect/dataset/controller/ProviderController.java)：`GET` 分页、`GET /{id}`、`POST`、`PUT /{id}`、`DELETE /{id}`；DTO [`ProviderCreateRequest`](../../src/main/java/com/lantu/connect/dataset/dto/ProviderCreateRequest.java) / [`ProviderUpdateRequest`](../../src/main/java/com/lantu/connect/dataset/dto/ProviderUpdateRequest.java)；逻辑删除走 MyBatis-Plus `@TableLogic`。

---

## Batch 5 — 持久化

| 项 | 结论 |
|----|------|
| MyBatis XML | `classpath:mapper/**/*.xml` 在仓库中**无对应 XML**（`src/main/resources` 下仅有 `logback-spring.xml`）；持久化以 **MyBatis-Plus 注解 Mapper + 部分 JdbcTemplate** 为主 |
| Flyway | 已引入 `flyway-core` / `flyway-mysql`，默认 `spring.flyway.enabled: false`（`FLYWAY_ENABLED`）；脚本目录 [`db/migration`](../../src/main/resources/db/migration)；**既有库**仍建议先执行 [`sql/`](../../sql) 下脚本再开启 Flyway 钉扎 |
| `@TableName` 实体 | 已 grep 全量：覆盖 `t_user`、`t_resource_*`、`t_alert_*`、`t_quota*`、`t_provider` 等（详见代码内注解） |
| `t_resource` | **无**单独 JPA/MP 实体类；由 `ResourceRegistryServiceImpl`、`UnifiedGatewayServiceImpl`、`SkillPackUploadService` 等 **原生 SQL** 访问 — **Schema 变更需人工审所有 SQL 字符串** |

---

## Batch 6 — 测试与静态审阅关系

- [`src/test`](../../src/test)：约 25 个测试类，以 **WebMvcTest / 单测** 为主，**不**加载全应用、**不**替代 MySQL 集成验证。
- **本 log** 记录静态可读闭环边界；**环境级**验证（真实 DB/Redis/Rabbit/MinIO）须另册。

---

## 汇总：不能声称「全部生产闭环」的代码级理由

1. **默认 `integration-mock: true`**：网络/ACL **模拟**。  
2. **默认 Mock 短信**（`lantu.sms.provider` 未配置）。  
3. **通知 channel** 多依赖显式 `enabled`/邮件 host。  
4. ~~**Provider API** 仅读~~ **已补写**：见上 ProviderController CRUD。  
5. **无自动化迁移（Flyway）** + 现存 **`sql/migrations` 需人工执行** + **大量 JdbcTemplate 手写 SQL** → 数据库与代码一致性依赖运维流程。  
6. **默认中间件 localhost** → 部署未配全时运行失败，属**部署闭环**非「代码未写」。

---

## 维护

- 代码变更后：**同步更新本文件对应 Batch** 或追加「变更条目」段落。  
- 与 [`findings.md`](findings.md) 配合：findings 保留 **分级摘要**，细节以本 log 为准。
