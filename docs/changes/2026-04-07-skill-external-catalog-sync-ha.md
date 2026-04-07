# 技能在线市场库同步（高可用运维摘要）

## Redis 分布式锁

- **Key**：`lantu:lock:skill-external-catalog-sync`
- **配置**（`lantu.skill-external-catalog`）：`sync-redis-lock-enabled`（默认 `true`）、`sync-redis-lock-ttl-minutes`（默认 `30`，应大于最坏单次全量同步耗时）
- **行为**：持锁失败时写 `pending_resync=1` 并打点 `skill.catalog.sync`(`result=lock_busy`)，不写镜像表数据；释放锁使用 Lua 比对 token 后 `DEL`
- **降级**：Redis 连接类异常时打 `result=redis_degraded`，退回本 JVM 内 `AtomicBoolean` 单飞（单机多线程仍互斥）

关闭跨 Pod 协调时可设 `sync-redis-lock-enabled: false`（仅 JVM 单飞）。

## pending_resync

- 超管保存市场配置成功后置 `1`；全量同步**成功**后若为 `1` 则清 `0` 并在事务提交后再调度**一轮**异步同步（使用当前库内生效配置 `effective()`），避免与未提交事务竞态。

## 指标（Prometheus / Micrometer）

- **Counter**：`skill.catalog.sync`，`result` = `success` | `empty` | `error` | `lock_busy` | `redis_degraded`
- **Timer**：`skill.catalog.sync.duration`，`result` 同上

## Actuator Health

- 组件名：`skillExternalCatalogSync`
- **UP**：`last_success_at` 距今不超过约 `2 * cacheTtlSeconds`，且无 `last_error`、无 `pending_resync`
- **DEGRADED**：快照过旧、`pending_resync`、或存在 `last_error`
- 未开启库镜像或 `provider=static` 时返回 UP 并附带说明详情

## 只读 HTTP

- `GET /resource-center/skill-external-catalog/sync-status`（权限 `skill:read`）：`lastSuccessAt`、`pendingResync`、`syncInProgressHint` 等

## 数据库

- Flyway：`V29__skill_external_catalog_sync_pending_resync.sql` 为 `t_skill_external_catalog_sync` 增加 `pending_resync`
