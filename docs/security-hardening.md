# 安全加固说明（运维与审计）

「完美」在安全工程中不存在，以下为当前后端已落实的控制与残余风险边界，便于部署与评审。

## 认证与会话

- **JWT**：生产必须使用环境变量 `JWT_SECRET`（≥32 字符），且勿使用仓库内开发默认值；部署前请自行核对，应用启动时不再做该项强制校验。
- **`X-User-Id`**：`lantu.security.allow-header-user-id-fallback` 默认为 `false`，避免未校验 Bearer 时的头注入冒充；生产 profile 禁止为 `true`。
- **JSON 错误体**：手写 JSON 中的消息字段经 `JsonStringEscaper` 转义，降低反射型注入噪声。

## 网络与传输

- **`REQUIRE_HTTPS` / `lantu.security.require-https`**：为 `true` 时要求 HTTPS 通道并下发 HSTS（按环境谨慎启用）。

## Actuator 与可观测性

- **匿名路径**：默认仅 `/actuator/health`、`/actuator/info`；**不包含** `/actuator/prometheus`。
- **Prometheus**：`lantu.security.permit-prometheus-without-auth`（`PERMIT_PROMETHEUS_WITHOUT_AUTH`）默认为 `false`。受控环境可开启；生产建议内网隔离或对 Prometheus 配置 **Bearer** 抓取。
- **未赋权用户**（已登录但无角色）：`UnassignedUserAccessFilter` 仅放行与匿名一致的 actuator 健康类路径，**不**再整站放行 `/actuator/**`，避免低权限账号读取指标与其它端点。

## HTTP 响应头

Spring Security 中已配置：`X-Content-Type-Options`、`X-Frame-Options: DENY`、`Referrer-Policy`；HSTS 随 HTTPS 要求启用。

## CORS

通过 `cors.allowed-origins`（逗号分隔）限制来源；勿在生产使用 `*` 与凭据并用。

## 技能包 URL 导入（SSRF）

`lantu.skill-pack-import`：主机后缀白名单、体大小与超时、`HttpClient` 重定向与事务边界（网络读取与写库分离）—见 `application.yml` 与 `SkillPackUrlFetcher`。

## 上传与 DoS 边界

`spring.servlet.multipart` 已设文件与请求大小上限；网关/反向代理层建议再加体大小与速率限制。

## API 文档

Swagger / OpenAPI 在默认白名单中对外可达。**生产**建议通过 **IP 限制**、**独立 profile 关闭** 或网关鉴权收缩暴露面。

## 依赖与密钥

- 父工程已升级至 **Spring Boot 3.2.12** 以纳入安全补丁；仍建议定期 `mvn dependency:check` / OWASP Dependency-Check。
- 数据库、Redis、第三方 API 密钥仅经环境变量或保密配置注入，勿提交仓库。
- `lantu.encryption.key`（`LANTU_ENCRYPTION_KEY`）在生产必须为非占位强密钥；`FIELD_ENCRYPTION_KEY` 用于字段级 `ENC:` 密文。
- **字段加密**：`FieldEncryptor` 对非 16/24/32 字节口令改为 SHA-256 派生，**若曾用短口令 + 零填充写入的 `ENC:` 数据，需轮转密钥并重写密文**。

## 认证滥用防护

- **Redis**：按 IP / 用户名的登录与注册窗口限流（`lantu.security.rate-limit.*`），与 Resilience4j 令牌桶叠加。
- **短信**：在原有按手机号冷却基础上增加按 IP 的小时额度。
- **验证码**：图形验证码生成按 IP 限流 + `captchaGenerate` 全局限流。

## GeoIP

公网查询仅允许**字面值 IPv4/IPv6**，禁止主机名，避免外呼 HTTP 客户端触发攻击者控制的 DNS 解析（SSRF）。

## 变更记录（摘要）

- Prometheus 默认改为需鉴权；Docker/本地裸拉取需显式环境变量或 Bearer scrape。
- 未赋权用户 actuator 范围收束为 health/info。
