# 2026-03-31 Backend UX Closure Verification

## Scope

- Resource-center lifecycle closure for `agent/skill/mcp/app/dataset`
- Unified observability summary and quality history
- Catalog/detail include-based expansion
- Monitoring KPI trend fields and dashboard health summary enrichment

## API Verification Checklist

- [ ] `GET /resource-center/resources/mine` supports `resourceType/status/keyword/sortBy/sortOrder`
- [ ] `POST /resource-center/resources/{id}/submit` returns full `ResourceManageVO`
- [ ] `POST /resource-center/resources/{id}/withdraw` returns full `ResourceManageVO`
- [ ] `POST /resource-center/resources/{id}/deprecate` returns full `ResourceManageVO`
- [ ] `POST /resource-center/resources/{id}/versions/{version}/switch` returns full `ResourceManageVO`
- [ ] `GET /resource-center/resources/{id}/lifecycle-timeline` returns ordered timeline events
- [ ] `GET /resource-center/resources/{type}/{id}/observability-summary` returns health/circuit/quality/degradation
- [ ] `GET /catalog/resources?include=observability,quality,tags` returns extension blocks
- [ ] `GET /catalog/resources/{type}/{id}?include=observability,quality,tags` returns extension blocks
- [ ] `POST /catalog/resolve` with `include` returns extension blocks
- [ ] `GET /monitoring/kpis` returns `previousValue/changePercent/changeType`
- [ ] `GET /monitoring/resources/{type}/{id}/quality-history` returns time buckets with quality score
- [ ] `GET /dashboard/health-summary` returns `checks/statusDistribution/degradedResources`

## Demo Script

1. Create a resource in draft.
2. Query `mine` list with keyword and status; verify `allowedActions` + `statusHint`.
3. Submit resource and verify submit response includes updated status.
4. Open lifecycle timeline and confirm `created + submitted` events.
5. Query observability summary and verify quality score + degradation hint.
6. Query catalog/detail with `include=observability,quality,tags`; verify extension blocks.
7. Query monitoring KPI and confirm trend fields.
8. Query quality history for the same resource and confirm trend buckets.

## Regression Commands

- Compile: `.\mvnw.cmd -q -DskipTests compile`
- Tests: `.\mvnw.cmd "-Dtest=ResourceRegistryControllerWebMvcTest,SdkGatewayControllerTest" test`
