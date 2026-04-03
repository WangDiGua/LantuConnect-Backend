# 后端全量闭环与安全审计报告（2026-03-31）

## 1) 审计范围与方法

- 范围：全仓库后端相关资产（`src/main`、`src/test`、`sql`、`application*.yml`、`docker-compose.yml`、`prometheus.yml`、`docs` 契约文档）。
- 方法：静态代码审查 + 只读验证（不执行写操作，不修改业务代码）。
- 目标：判断功能闭环完整性、安全控制到位程度、漏洞与运维配置风险，并给出修复优先级。

## 2) 核心业务闭环矩阵

| 业务旅程 | 结论 | 关键依据 |
|---|---|---|
| 登录/JWT/刷新/登出/会话 | Mostly Yes | `auth/controller/AuthController.java`、`auth/service/impl/AuthServiceImpl.java`、`common/filter/JwtAuthenticationFilter.java` |
| 注册 -> 可用角色 | Partial | `AuthServiceImpl.register` 创建用户后依赖后续流程，未见同事务角色赋予闭环 |
| SMS 发送 -> 绑定手机 | Partial | `AuthServiceImpl.sendSms` 含 mock 日志语义，真实外发链路不完整 |
| 资源注册 -> 提审 -> 审核 -> 发布 | Yes | `gateway/service/impl/ResourceRegistryServiceImpl.java`、`audit/service/impl/AuditServiceImpl.java` |
| SDK 目录/解析/调用 | Yes (with auth risk) | `gateway/controller/SdkGatewayController.java`、`gateway/service/impl/UnifiedGatewayServiceImpl.java` |
| 沙箱会话 -> 调用 -> 配额统计 | Partial | `sandbox/service/impl/SandboxServiceImpl.java` 中可调用，但 `lastInvokeAt` 字段闭环不完整 |
| Provider/Tag 管理 | Yes (test gap) | `dataset/controller/ProviderController.java`、`dataset/controller/TagController.java` |
| 站内通知（读/已读） | Yes | `notification/controller/NotificationController.java`、`notification/service/impl/NotificationServiceImpl.java` |
| 多通道通知（站内+短信+邮件） | No | `notification/service/MultiChannelNotificationService.java` 可见，但主链路未接入 |

## 3) 安全与漏洞分级

### Critical

1. 文件上传路径穿越风险  
   - 证据：`common/service/FileStorageService.java` 使用 `category + "/" + datePath` 拼接相对路径，并直接 `Paths.get(uploadDir, relativePath)`。  
   - 风险：可构造目录逃逸，写入上传根目录之外。

### High

2. SDK/Gateway `X-User-Id` 由客户端提供，存在越权视图风险  
   - 证据：`gateway/controller/SdkGatewayController.java` 直接接收 `X-User-Id`；`UnifiedGatewayServiceImpl.catalog/resolve` 使用该 userId 参与授权裁剪。  
   - 风险：持有有效 `X-Api-Key` 的调用方可尝试伪造 userId 影响可见范围/授权判断。

3. 默认密钥/加密材料存在上线误用风险  
   - 证据：`application.yml` 中 `jwt.secret`、`lantu.encryption.key` 提供默认值。  
   - 风险：若生产未覆盖环境变量，令牌伪造或敏感字段保护失效。

4. MCP 出站 HTTP 客户端未显式禁用自动重定向  
   - 证据：`gateway/protocol/McpJsonRpcProtocolInvoker.java` 创建 `HttpClient` 未设置 `followRedirects(NEVER)`。  
   - 风险：注册端点可被重定向到非预期地址，扩大 SSRF/内网探测面。

5. 数据库连接默认不启用 TLS（基础配置）  
   - 证据：`application.yml` JDBC URL `useSSL=false`。  
   - 风险：网络链路中凭据与数据被窃听/篡改概率上升。

### Medium

6. CORS 配置可用但安全边界偏宽  
   - 证据：`common/config/CorsConfig.java` 使用 `allowedHeaders("*")` + `allowCredentials(true)`。  
   - 风险：在 origin 扩展或运维误配场景中放大跨域风险。

7. SQL 审计日志在 DEBUG 时记录参数值  
   - 证据：`common/mybatis/SqlAuditInterceptor.java` 输出 `params=[...]`。  
   - 风险：敏感字段（手机号、邮箱、标识符）进入日志系统。

8. 文档开放默认值偏宽  
   - 证据：`application.yml` `lantu.security.expose-api-docs: true`。  
   - 风险：提升匿名探测面（生产可由 profile 关闭）。

### Low

9. CSRF 关闭在 Bearer-only 场景合理，但若未来引入 cookie 需复核  
   - 证据：`common/config/SecurityConfig.java` `csrf(AbstractHttpConfigurer::disable)`。

## 4) 配置、部署与迁移一致性风险

1. 迁移流程双轨：Flyway 路径与手工 SQL 并行  
   - `src/main/resources/db/migration/` 仅有 baseline marker；实际变更集中于 `sql/migrations/*.sql`。

2. `docker-compose.yml` 与应用默认 DB 用户口令易错配  
   - MySQL 使用 `MYSQL_ROOT_PASSWORD` + `MYSQL_USER=lantu`；app 仅传 `DB_PASSWORD`，未显式 `DB_USER`。

3. Prometheus 抓取路径与鉴权默认值需要显式协调  
   - `prometheus.yml` 访问 `/api/actuator/prometheus`；默认 `PERMIT_PROMETHEUS_WITHOUT_AUTH=false`。

4. SQL 幂等性与基线漂移风险  
   - `sql/migrations/20260401_skill_pack_validation.sql` 为非幂等 `ADD COLUMN/CREATE INDEX`；  
   - `sql/lantu_connect.sql` 已含同名字段/索引，重复执行会冲突。

## 5) 测试覆盖现状与高优先缺口

### 现有强项

- 鉴权链路：`auth/controller/AuthControllerWebMvcTest.java`、`common/security/AuthChainWebMvcTest.java`、`common/filter/JwtAuthenticationFilterTest.java`
- 网关主链路：`gateway/controller/SdkGatewayControllerTest.java`、`gateway/controller/ResourceRegistryControllerWebMvcTest.java`
- 技能包链路：`gateway/service/SkillPackUploadServiceTest.java`、`gateway/service/support/AnthropicSkillPackValidatorTest.java`

### 高优先缺口

1. 审核域缺少测试目录（`src/test/java/com/lantu/connect/audit` 不存在）  
   - 建议新增 `AuditController` WebMvc + `AuditServiceImpl` 单测（状态迁移与回写一致性）。

2. 数据集域测试薄弱（`dataset` 测试目录缺失）  
   - 建议新增 `ProviderController/ProviderService`、`TagController/TagService` 用例。

3. 通知域只有 facade 单测，缺少 HTTP 与通道失败场景测试  
   - 建议补 `NotificationControllerWebMvcTest`、`EmailNotificationChannel`/`SmsNotificationChannel` 失败重试或降级行为测试。

4. 上传安全用例缺失  
   - 建议新增 `FileStorageService` 路径规范化/目录越界拒绝测试。

## 6) 修复优先级（执行序）

### P0（立刻）
- 修复上传路径穿越：统一使用 `base.resolve(rel).normalize()` + `startsWith(base)`。
- 移除/硬阻断生产默认密钥：启动时 fail-fast（空值、弱值、默认值）。
- 收紧 SDK user identity：不信任客户端 `X-User-Id`，由服务端身份或绑定关系推导。

### P1（本周）
- MCP 出站显式 `followRedirects(NEVER)` 并做每跳重校验。
- 生产强制 DB TLS 与证书校验策略。
- 关闭生产 API docs 匿名暴露（如无需公开）。

### P2（两周内）
- 完成审核域、数据集域、通知域关键测试补齐。
- 统一迁移策略：决定 Flyway 托管或手工迁移托管，避免双轨漂移。
- 收敛配置默认值与 Compose 文档，减少环境错配。

## 7) 审计结论

- 结论：后端主流程“可运行闭环”总体成立，但存在“安全高风险 + 运维一致性 + 测试缺口”三类显著短板。  
- 判定：当前状态不建议直接作为高暴露生产基线；建议按 P0/P1 优先级先完成安全加固再扩大流量。
