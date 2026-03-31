# 审查发现摘要（分级）

## P0（功能不闭环或明显损坏）

1. ~~**`AgentVersionPage` 未挂路由且逻辑依赖下线接口**~~ **已处理（2026-03-31）**  
   - 已删除未路由页面与 `version.service.ts`；Agent 版本请使用 **资源注册中心** `resourceCenterService.listVersions` / `createVersion` / `switchVersion`。

## P1（一致性 / 可维护性）

1. ~~**`grantApplicationService` 未从 barrel 导出**~~ **已处理**：`index.ts` 导出，相关页面改为 `from '../../api/services'`。

2. ~~**Legacy 审核页 orphaned**~~ **已处理**：删除 `AgentAuditList.tsx`、`SkillAuditList.tsx`、`audit.service.ts`；统一使用 `resourceAuditService` + `/audit/resources`。

3. ~~**技能包受控下载未暴露于控制台**~~ **已处理（2026-03-31）**：`GET .../skill-artifact` 已由 `resourceCenterService.downloadSkillArtifact` 与资源管理页「下载制品」使用（私有制品场景）。

## P2（文档与矩阵）

1. Playbook [07-controller-coverage-matrix.md](../frontend-alignment-playbook/07-controller-coverage-matrix.md) 曾写 27 个 Controller，已与代码 **29 + ResourceCatalog 特例** 不一致 — **已在本轮 resync**。

2. 全量矩阵仍需：对每个 `@Mapping` 行填「使用该 API 的 `.tsx` 路径」（脚本或人工分批）。

3. ~~**告警规则可选指标仅前端常量**~~ **已处理（2026-03-31）**：`monitoringService.listAlertRuleMetrics` 拉取 `GET /monitoring/alert-rule-metrics`，失败时回退本地默认列表。

4. ~~**Dataset Provider 分页 API 与前端 Provider 页数据源不一致**~~ **已处理（2026-03-31）**：`providerService` 对接 `/providers` 分页；**续（同日）**：后端已补 `POST` / `PUT` / `DELETE` / `GET /{id}`，前端 `create` / `update` / `remove` / `getById` 已接实装。

5. **后端逐代码静态审阅（2026-03-31）**：全量分批结论见 [code-audit-log.md](code-audit-log.md)。**不等价于**连库集成测试。

6. ~~**Dataset Provider 后端仅有 GET**~~ **已处理（2026-03-31）**：[`ProviderController`](../../src/main/java/com/lantu/connect/dataset/controller/ProviderController.java) 全 CRUD + [`ProviderServiceImpl`](../../src/main/java/com/lantu/connect/dataset/service/impl/ProviderServiceImpl.java)。

7. **Flyway 已引入（默认关闭）+ 手工 `sql/migrations` + 大量 `t_resource` JDBC SQL**：设 `FLYWAY_ENABLED=true` 前须已执行 [`sql/`](../../sql) 基线；与 [`ResourceRegistryServiceImpl`](../../src/main/java/com/lantu/connect/gateway/service/impl/ResourceRegistryServiceImpl.java) / [`UnifiedGatewayServiceImpl`](../../src/main/java/com/lantu/connect/gateway/service/impl/UnifiedGatewayServiceImpl.java) 一致性仍须在运维侧核对，见 [code-audit-log.md](code-audit-log.md) Batch 5。
