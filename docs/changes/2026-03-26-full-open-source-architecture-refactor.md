# 全量开源化改造交付文档（2026-03-26）

## 1. 目标
本次一次性交付完成以下改造：

1) Resilience4j 全套治理（CB/RateLimiter/Retry/Bulkhead/TimeLimiter 配置）
2) Casbin 授权统一（RBAC + ABAC 能力落地）
3) Javers 对象级审计留痕
4) Redis 幂等防重层
5) 文件导入升级（txt/csv/xlsx）
6) 校验增强（可复用约束 + 统一异常处理）

---

## 2. 代码改动清单

### 2.1 Resilience4j 治理
- 新增/补齐 `resilience4j` 多模块配置（`application.yml`）：
  - `circuitbreaker.instances`: `skillInvoke/networkApply/aclPublish/httpInvoke/mcpInvoke`
  - `retry.instances`: 同上
  - `bulkhead.instances`: 同上
  - `timelimiter.instances`: 同上
- 将分散重试切换为 Resilience4j 注解链：
  - `HttpJsonProtocolInvoker#invoke`：`@CircuitBreaker + @Retry + @Bulkhead`
  - `McpJsonRpcProtocolInvoker#invoke`：`@CircuitBreaker + @Retry + @Bulkhead`
  - `NetworkApplyServiceImpl#apply`：新增 `@Retry + @Bulkhead`
  - `AclPublishServiceImpl#publish`：新增 `@Retry + @Bulkhead`
  - `SkillRemoteInvokeService#postJson`：新增 `@Retry + @Bulkhead`

### 2.2 Casbin（RBAC + ABAC）
- 新增依赖：`org.casbin:jcasbin`
- 新增授权门面：
  - `common/security/CasbinAuthorizationService`
  - 能力：
    - `hasAnyRole()`：角色授权（RBAC）
    - `hasPermissions()`：权限授权（RBAC）
    - `canManageOwnerResource()`：owner-or-admin（ABAC）
    - `isDeptAdminOnly()` / `userDepartmentMenuId()`（ABAC）
- 切面替换：
  - `RequireRoleAspect` 改为调用 `CasbinAuthorizationService`
  - `RequirePermissionAspect` 改为调用 `CasbinAuthorizationService`
- 服务层硬编码收敛（关键路径）：
  - `GatewayUserPermissionService`
  - `ResourceInvokeGrantService`
  - `ReviewServiceImpl`
  - `DeptScopeHelper`

### 2.3 Javers 审计
- 新增依赖：`org.javers:javers-core`
- 新增：
  - `common/audit/JaversConfig`
  - `common/audit/JaversAuditService`
  - `common/audit/AuditSnapshotEntry`
- `AuditLogAspect` 扩展：
  - 在原有 `t_audit_log` 记录之外，追加 `javers.commit(...)`
  - 提交内容：`action/resource/userId/result/traceId/requestArgs/responseBody`

### 2.4 幂等防重
- 新增：
  - `common/idempotency/IdempotencyProperties`
  - `common/idempotency/IdempotencyFilter`
- `SecurityConfig` 挂载过滤器（在鉴权链后执行）
- 策略：
  - 仅写请求（POST/PUT/PATCH/DELETE）
  - 依赖请求头 `Idempotency-Key`
  - Redis 键：`idem:req:{userId}:{uri}:{idempotencyKey}`
  - `processing`（短 TTL）与 `success`（长 TTL）状态机
  - 冲突时统一返回 `409 + code=1006 (DUPLICATE_SUBMIT)`

### 2.5 文件导入升级
- 新增依赖：`com.alibaba:easyexcel`
- `SensitiveWordService` 新增：
  - `importFromFile(byte[] fileBytes, String filename, ...)`
  - 支持：
    - `txt`：沿用原逻辑
    - `csv`：读取首列
    - `xlsx`：EasyExcel 读取首列
- `SensitiveWordController` 新增接口：
  - `POST /api/sensitive-words/import`（multipart）
  - 保留原 `POST /api/sensitive-words/import-txt`

### 2.6 统一校验增强
- 新增可复用约束：
  - `@ResourceCode` + `ResourceCodeValidator`
  - `@VersionText` + `VersionTextValidator`
  - `@PhoneCN` + `PhoneCNValidator`
- 落地到 DTO：
  - `ResourceUpsertRequest.resourceCode` -> `@ResourceCode`
  - `ResourceVersionCreateRequest.version` -> `@VersionText`
  - `RegisterRequest.phone` -> `@PhoneCN`
- DTO 校验补强：
  - `AuditLogQueryRequest.page/pageSize` 增加 `@Min/@Max`
  - `SensitiveWordController.BatchAddRequest.words` 增加 `@NotEmpty`
  - `SensitiveWordController` 多处 `@RequestBody` 增加 `@Valid`
- 全局异常：
  - `GlobalExceptionHandler` 新增 `ConstraintViolationException` 处理

---

## 3. 配置变更（需要环境同步）

### 3.1 新增配置项
`application.yml` 已新增：

```yaml
lantu:
  idempotency:
    enabled: true
    processing-ttl-seconds: 60
    success-ttl-seconds: 86400
```

并补齐了 `resilience4j.retry/bulkhead/timelimiter` 相关实例配置。

### 3.2 Redis 要求
- 幂等层依赖 Redis，线上需保证 Redis 可用。

---

## 4. 数据库影响

## 4.1 本次改造“代码可运行”不强制变更表结构
- 现有业务表可继续使用。
- Casbin 当前采用“运行时构建策略（来自现有角色与权限表）”，未强制新增 Casbin policy 表。
- Javers 当前使用 `javers-core` 提交对象快照（未接入 SQL repository 表）。

## 4.2 生产建议（推荐后续补齐）
若需要“持久化查询与运维治理”，建议下一步加以下表：

1) Casbin policy 表（若切换 JDBC Adapter）  
2) Javers commit/snapshot/global_id 等标准表（若切换 SQL Repository）  
3) 幂等审计表（可选；当前用 Redis 轻量实现）

---

## 5. 前端联调要点

### 5.1 新接口
- 新增：`POST /api/sensitive-words/import`
  - `multipart/form-data`
  - `file` 支持 `txt/csv/xlsx`
  - 可选参数：`category/severity/source`

### 5.2 幂等头
- 对关键写接口建议前端统一传：
  - `Idempotency-Key: <uuid>`
- 重复请求会返回：
  - HTTP `409`
  - `code=1006`

### 5.3 权限行为
- 权限判断从切面硬编码切到 Casbin 门面，核心角色/权限语义保持兼容。
- owner-or-admin 类型操作现在通过统一 ABAC 门面判断。

---

## 6. 验证结果
- 全量改造后执行：`mvn -DskipTests compile` 通过。
- 未新增 IDE lints 错误（已对改动文件扫描）。

---

## 7. 回滚方案

若需要快速回滚：

1) 恢复 `RequireRoleAspect` / `RequirePermissionAspect` 到旧实现  
2) 关闭幂等：
   - `lantu.idempotency.enabled=false`
3) 导入接口可继续使用旧 `import-txt`，前端先不接入新 `/import`
4) Resilience4j 出现行为偏差时，临时调低 retry/bulkhead 配置，或先移除相关注解

---

## 8. 下一步可继续开源替换建议（本轮已完成敏感词与核心治理）

1) **Casbin 策略持久化**：接 JDBC Adapter + 后台可视化策略管理  
2) **Javers SQL Repository**：将快照持久化并提供审计检索接口  
3) **导入能力标准化**：统一模板、错误行导出、异步任务化  
4) **限流分层治理**：网关分布式限流（Redis）与应用层 R4j 规则形成统一策略中心  

