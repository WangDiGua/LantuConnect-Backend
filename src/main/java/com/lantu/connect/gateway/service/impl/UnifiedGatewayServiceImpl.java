package com.lantu.connect.gateway.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.util.UserDisplayNameResolver;
import com.lantu.connect.common.web.ServletContextPathUtil;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.dashboard.dto.ExploreHubData;
import com.lantu.connect.gateway.dto.InvokeRequest;
import com.lantu.connect.gateway.dto.InvokeResponse;
import com.lantu.connect.gateway.dto.ResourceCatalogItemVO;
import com.lantu.connect.gateway.dto.ResourceCatalogQueryRequest;
import com.lantu.connect.gateway.dto.ResourceResolveRequest;
import com.lantu.connect.gateway.dto.ResourceResolveSpecSanitizer;
import com.lantu.connect.gateway.dto.ResourceResolveVO;
import com.lantu.connect.gateway.dto.ResourceStatsVO;
import com.lantu.connect.gateway.dto.SearchSuggestion;
import com.lantu.connect.gateway.model.ResourceAccessPolicy;
import com.lantu.connect.gateway.protocol.McpJsonRpcProtocolInvoker;
import com.lantu.connect.gateway.protocol.McpOutboundHeaderBuilder;
import com.lantu.connect.gateway.protocol.ProtocolInvokeContext;
import com.lantu.connect.gateway.protocol.ProtocolInvokeResult;
import com.lantu.connect.gateway.protocol.ProtocolInvokerRegistry;
import com.lantu.connect.gateway.security.AppLaunchTokenService;
import com.lantu.connect.gateway.security.ApiKeyScopeService;
import com.lantu.connect.gateway.security.GatewayGovernanceService;
import com.lantu.connect.gateway.security.GatewayUserPermissionService;
import com.lantu.connect.gateway.security.ResourceInvokeGrantService;
import com.lantu.connect.gateway.service.UnifiedGatewayService;
import com.lantu.connect.sysconfig.runtime.RuntimeAppConfigService;
import com.lantu.connect.monitoring.entity.CallLog;
import com.lantu.connect.monitoring.entity.TraceSpan;
import com.lantu.connect.monitoring.mapper.CallLogMapper;
import com.lantu.connect.monitoring.mapper.TraceSpanMapper;
import com.lantu.connect.useractivity.entity.UsageRecord;
import com.lantu.connect.useractivity.mapper.UsageRecordMapper;
import com.lantu.connect.usermgmt.entity.ApiKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UnifiedGatewayServiceImpl implements UnifiedGatewayService {

    private static final String TYPE_AGENT = "agent";
    private static final String TYPE_SKILL = "skill";
    private static final String TYPE_MCP = "mcp";
    private static final String TYPE_APP = "app";
    private static final String TYPE_DATASET = "dataset";
    private static final String STATUS_PUBLISHED = "published";
    private static final String DEFAULT_RESOURCE_VERSION = "v1";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final CallLogMapper callLogMapper;
    private final TraceSpanMapper traceSpanMapper;
    private final UsageRecordMapper usageRecordMapper;
    private final ObjectMapper objectMapper;
    private final ApiKeyScopeService apiKeyScopeService;
    private final GatewayUserPermissionService gatewayUserPermissionService;
    private final ResourceInvokeGrantService resourceInvokeGrantService;

    @Value("${server.servlet.context-path:/regis}")
    private String servletContextPath;

    private final AppLaunchTokenService appLaunchTokenService;
    private final GatewayGovernanceService gatewayGovernanceService;
    private final ProtocolInvokerRegistry protocolInvokerRegistry;
    private final McpJsonRpcProtocolInvoker mcpJsonRpcProtocolInvoker;
    private final RuntimeAppConfigService runtimeAppConfigService;
    private final UserDisplayNameResolver userDisplayNameResolver;

    /**
     * 统一资源目录查询：固定从新模型主表检索，再叠加用户权限和 API Key scope 裁剪。
     */
    @Override
    public PageResult<ResourceCatalogItemVO> catalog(ResourceCatalogQueryRequest request, ApiKey apiKey, Long userId) {
        if (userId == null && apiKey == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "目录查询需要登录态或 API Key");
        }
        String type = normalizeType(request.getResourceType());
        Set<String> includes = parseIncludes(request.getInclude());
        String status = StringUtils.hasText(request.getStatus()) ? request.getStatus().trim() : null;
        String keyword = StringUtils.hasText(request.getKeyword()) ? request.getKeyword().trim() : null;
        int page = request.getPage() == null ? 1 : Math.max(1, request.getPage());
        int pageSize = request.getPageSize() == null ? 20 : Math.min(100, Math.max(1, request.getPageSize()));

        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT r.id, r.resource_type, r.resource_code, r.display_name, r.description, r.status, r.source_type, r.update_time, r.created_by, r.access_policy, COALESCE(r.view_count, 0) AS view_count
                FROM t_resource r
                WHERE r.deleted = 0
                """);
        if (StringUtils.hasText(type)) {
            sql.append(" AND r.resource_type = ? ");
            args.add(type);
        }
        if (StringUtils.hasText(status)) {
            sql.append(" AND r.status = ? ");
            args.add(status);
        }
        if (StringUtils.hasText(keyword)) {
            sql.append(" AND (r.resource_code LIKE ? OR r.display_name LIKE ? OR r.description LIKE ?) ");
            String like = "%" + keyword + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (request.getCategoryId() != null) {
            sql.append(" AND r.category_id = ? ");
            args.add(request.getCategoryId());
        }
        if (request.getTags() != null && !request.getTags().isEmpty()) {
            sql.append(" AND r.id IN (SELECT rt.resource_id FROM t_resource_tag_rel rt "
                    + "INNER JOIN t_tag t ON t.id = rt.tag_id WHERE t.name IN (");
            for (int ti = 0; ti < request.getTags().size(); ti++) {
                if (ti > 0) sql.append(",");
                sql.append("?");
                args.add(request.getTags().get(ti));
            }
            sql.append(")) ");
        }

        String sortBy = StringUtils.hasText(request.getSortBy())
                ? request.getSortBy().toLowerCase(Locale.ROOT) : null;
        boolean needCallCountJoin = "callcount".equals(sortBy);
        boolean needRatingJoin = "rating".equals(sortBy);

        if (needCallCountJoin) {
            sql.insert(sql.indexOf("WHERE"),
                    "LEFT JOIN (SELECT agent_id, COUNT(*) AS _call_cnt FROM t_call_log GROUP BY agent_id) _cc ON r.id = _cc.agent_id ");
        }
        if (needRatingJoin) {
            sql.insert(sql.indexOf("WHERE"),
                    "LEFT JOIN (SELECT target_id, AVG(rating) AS _avg_rating FROM t_review WHERE deleted = 0 GROUP BY target_id) _rv ON r.id = _rv.target_id ");
        }

        String orderClause = " ORDER BY r.update_time DESC ";
        if (sortBy != null) {
            String dir = "desc".equalsIgnoreCase(request.getSortOrder()) ? "DESC" : "ASC";
            orderClause = switch (sortBy) {
                case "name" -> " ORDER BY r.display_name " + dir;
                case "publishedat" -> " ORDER BY r.update_time " + dir;
                case "callcount" -> " ORDER BY COALESCE(_cc._call_cnt, 0) " + dir;
                case "rating" -> " ORDER BY COALESCE(_rv._avg_rating, 0) " + dir;
                default -> " ORDER BY r.update_time DESC ";
            };
        }
        sql.append(orderClause);

        int from = (page - 1) * pageSize;
        int to = from + pageSize;
        int dbOffset = 0;
        int batchSize = Math.max(200, pageSize * 4);
        int filteredCount = 0;
        List<ResourceCatalogItemVO> paged = new ArrayList<>();
        GatewayUserPermissionService.CatalogTypePredicate catalogTypeOk = gatewayUserPermissionService.catalogTypePredicate(userId);
        while (true) {
            String batchSql = sql + " LIMIT ? OFFSET ? ";
            List<Object> batchArgs = new ArrayList<>(args);
            batchArgs.add(batchSize);
            batchArgs.add(dbOffset);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(batchSql, batchArgs.toArray());
            if (rows.isEmpty()) {
                break;
            }
            for (Map<String, Object> row : rows) {
                String rType = valueOf(row.get("resource_type"));
                String rId = valueOf(row.get("id"));
                if (!catalogTypeOk.allow(rType)) {
                    continue;
                }
                /*
                 * 仅 API Key、无登录态：目录按 Key 的 scope + Resource Grant 裁剪（集成方只能看到自己被授予的资源）。
                 * 浏览器常在 axios 拦截器里附带个人 Key 与 JWT 并存：若对已登录用户仍按 Grant 逐行过滤，
                 * 则 grant_required 且非本人发布的资源会从广场消失（表现为「仅开发者本人能看见」）。
                 * 登录态下市场发现只受 RBAC（catalogTypeOk）约束；Grant 仍在 resolve/invoke  enforce。
                 */
                if (apiKey != null && userId == null) {
                    if (!apiKeyScopeService.canCatalog(apiKey, rType, rId)) {
                        continue;
                    }
                    if (!resourceInvokeGrantService.canCatalog(apiKey, rType, parseId(rId), null)) {
                        continue;
                    }
                }
                if (Boolean.TRUE.equals(request.getCallableOnly())) {
                    Long rid = parseId(rId);
                    if (!isResourcePhysicallyCallable(rid, rType)) {
                        continue;
                    }
                }
                if (filteredCount >= from && filteredCount < to) {
                    Long ridForGrant = parseId(rId);
                    Boolean grantFlag = null;
                    if (apiKey != null && ridForGrant != null) {
                        grantFlag = resourceInvokeGrantService.isInvokeGrantSatisfied(apiKey, rType, ridForGrant, userId);
                    }
                    paged.add(ResourceCatalogItemVO.builder()
                            .resourceType(rType)
                            .resourceId(rId)
                            .resourceCode(valueOf(row.get("resource_code")))
                            .displayName(valueOf(row.get("display_name")))
                            .description(valueOf(row.get("description")))
                            .status(valueOf(row.get("status")))
                            .sourceType(valueOf(row.get("source_type")))
                            .accessPolicy(ResourceAccessPolicy.fromStored(row.get("access_policy")).wireValue())
                            .updateTime(toDateTime(row.get("update_time")))
                            .createdBy(longOrNull(row.get("created_by")))
                            .viewCount(longValue(row.get("view_count")))
                            .tags(new ArrayList<>())
                            .hasGrantForKey(grantFlag)
                            .build());
                }
                filteredCount++;
            }
            dbOffset += rows.size();
            if (rows.size() < batchSize) {
                break;
            }
        }
        attachCatalogTagNames(paged);
        attachCatalogCreatorNames(paged);
        attachCatalogReviewAggregates(paged);
        attachCatalogEngagementMetrics(paged);
        attachIncludesToCatalogItems(paged, includes);
        return PageResult.of(paged, filteredCount, page, pageSize);
    }

    /**
     * 市场卡片：调用量（呼叫日志）、应用使用量（usage_record）、技能下载量（技能包下载事件）。
     * 浏览量在 SQL 主查询中随 {@code t_resource.view_count} 返回，并在详情 GET 成功路径上增量更新。
     */
    private void attachCatalogEngagementMetrics(List<ResourceCatalogItemVO> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<Long> ids = new ArrayList<>();
        for (ResourceCatalogItemVO vo : items) {
            Long id = longOrNull(vo.getResourceId());
            if (id != null) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        String placeholders = ids.stream().map(i -> "?").collect(Collectors.joining(","));
        Object[] idArgs = ids.toArray();

        Map<Long, Long> callById = new HashMap<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(
                "SELECT agent_id, COUNT(*) AS c FROM t_call_log WHERE agent_id IN (" + placeholders + ") GROUP BY agent_id",
                idArgs)) {
            Long aid = longOrNull(row.get("agent_id"));
            if (aid != null) {
                callById.put(aid, longValue(row.get("c")));
            }
        }

        Map<Long, Long> usageById = new HashMap<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(
                "SELECT resource_id, COUNT(*) AS c FROM t_usage_record WHERE type = 'app' AND action = 'invoke' AND resource_id IN ("
                        + placeholders
                        + ") GROUP BY resource_id",
                idArgs)) {
            Long rid = longOrNull(row.get("resource_id"));
            if (rid != null) {
                usageById.put(rid, longValue(row.get("c")));
            }
        }

        Map<Long, Long> downloadById = new HashMap<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(
                "SELECT resource_id, COUNT(*) AS c FROM t_skill_pack_download_event WHERE resource_id IN ("
                        + placeholders
                        + ") GROUP BY resource_id",
                idArgs)) {
            Long rid = longOrNull(row.get("resource_id"));
            if (rid != null) {
                downloadById.put(rid, longValue(row.get("c")));
            }
        }

        for (ResourceCatalogItemVO vo : items) {
            Long rid = longOrNull(vo.getResourceId());
            if (rid == null) {
                vo.setCallCount(0L);
                vo.setUsageCount(0L);
                vo.setDownloadCount(0L);
                continue;
            }
            vo.setCallCount(callById.getOrDefault(rid, 0L));
            vo.setUsageCount(usageById.getOrDefault(rid, 0L));
            vo.setDownloadCount(downloadById.getOrDefault(rid, 0L));
        }
    }

    private void attachCatalogCreatorNames(List<ResourceCatalogItemVO> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<Long> ids = new ArrayList<>();
        for (ResourceCatalogItemVO vo : items) {
            if (vo.getCreatedBy() != null) {
                ids.add(vo.getCreatedBy());
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, String> names = userDisplayNameResolver.resolveDisplayNames(ids);
        for (ResourceCatalogItemVO vo : items) {
            if (vo.getCreatedBy() != null) {
                vo.setCreatedByName(names.get(vo.getCreatedBy()));
            }
        }
    }

    private void attachCatalogReviewAggregates(List<ResourceCatalogItemVO> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        StringBuilder orClause = new StringBuilder();
        List<Object> args = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                orClause.append(" OR ");
            }
            orClause.append("(target_type = ? AND target_id = ?)");
            ResourceCatalogItemVO vo = items.get(i);
            args.add(vo.getResourceType());
            try {
                args.add(Long.parseLong(vo.getResourceId()));
            } catch (Exception ex) {
                args.add(-1L);
            }
        }
        String sql = "SELECT target_type, target_id, AVG(rating) AS avg_r, COUNT(*) AS cnt FROM t_review WHERE deleted = 0 AND ("
                + orClause + ") GROUP BY target_type, target_id";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args.toArray());
        Map<String, AggRatingRow> byKey = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String t = valueOf(row.get("target_type")).trim().toLowerCase(Locale.ROOT);
            String idPart = valueOf(row.get("target_id")).trim();
            String key = t + ":" + idPart;
            double avgR = 0.0;
            Object ar = row.get("avg_r");
            if (ar instanceof BigDecimal bd) {
                avgR = bd.doubleValue();
            } else if (ar instanceof Number n) {
                avgR = n.doubleValue();
            }
            long cnt = row.get("cnt") instanceof Number n ? n.longValue() : 0L;
            byKey.put(key, new AggRatingRow(avgR, cnt));
        }
        for (ResourceCatalogItemVO vo : items) {
            String key = vo.getResourceType().trim().toLowerCase(Locale.ROOT) + ":" + vo.getResourceId();
            AggRatingRow agg = byKey.get(key);
            if (agg != null) {
                vo.setRatingAvg(agg.avgRating);
                vo.setReviewCount(agg.reviewCount);
            }
        }
    }

    private static final class AggRatingRow {
        final double avgRating;
        final long reviewCount;

        AggRatingRow(double avgRating, long reviewCount) {
            this.avgRating = avgRating;
            this.reviewCount = reviewCount;
        }
    }

    /**
     * 资源详情/resolve 与 {@link #catalog} 列表项对齐：补充 {@code t_review} 聚合，避免前端 Tab 角标与页眉仅用详情接口时始终为 0。
     */
    private void attachResolveReviewAggregates(ResourceResolveVO vo) {
        if (vo == null || !StringUtils.hasText(vo.getResourceType()) || !StringUtils.hasText(vo.getResourceId())) {
            return;
        }
        ResourceCatalogItemVO bridge = ResourceCatalogItemVO.builder()
                .resourceType(vo.getResourceType().trim().toLowerCase(Locale.ROOT))
                .resourceId(vo.getResourceId().trim())
                .build();
        attachCatalogReviewAggregates(List.of(bridge));
        vo.setRatingAvg(bridge.getRatingAvg());
        vo.setReviewCount(bridge.getReviewCount());
    }

    private void attachCatalogTagNames(List<ResourceCatalogItemVO> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<Long> ids = new ArrayList<>();
        for (ResourceCatalogItemVO vo : items) {
            try {
                ids.add(Long.parseLong(vo.getResourceId()));
            } catch (Exception ignored) {
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Object> args = new ArrayList<>(ids);
        List<Map<String, Object>> tagRows = jdbcTemplate.queryForList(
                "SELECT rt.resource_id, t.name FROM t_resource_tag_rel rt "
                        + "INNER JOIN t_tag t ON t.id = rt.tag_id WHERE rt.resource_id IN ("
                        + placeholders + ") ORDER BY rt.resource_id, t.name",
                args.toArray());
        Map<Long, List<String>> byId = new LinkedHashMap<>();
        for (Map<String, Object> row : tagRows) {
            Object ridObj = row.get("resource_id");
            if (ridObj == null) {
                continue;
            }
            long rid = ridObj instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(ridObj));
            String n = row.get("name") == null ? null : String.valueOf(row.get("name"));
            if (StringUtils.hasText(n)) {
                byId.computeIfAbsent(rid, k -> new ArrayList<>()).add(n);
            }
        }
        for (ResourceCatalogItemVO vo : items) {
            try {
                long id = Long.parseLong(vo.getResourceId());
                vo.setTags(byId.getOrDefault(id, List.of()));
            } catch (Exception e) {
                vo.setTags(List.of());
            }
        }
    }

    /**
     * 统一资源解析入口：用于目录项二次解析，返回可调用端点与协议元信息。
     */
    @Override
    public ResourceResolveVO resolve(ResourceResolveRequest request, ApiKey apiKey, Long userId) {
        return getByTypeAndId(request.getResourceType(), request.getResourceId(), request.getVersion(),
                request.getInclude(), apiKey, userId, "resolve");
    }

    /**
     * 目录 GET /catalog/resources/{type}/{id}：允许仅登录（无 Key）返回展示用元数据；
     * 应用真实 URL 仍由 {@link #issueAppLaunchTicket} 在无 Key 时剥离。
     * POST {@link #resolve} 须带有效 X-Api-Key（内部 action {@code resolve}）。
     */
    @Override
    public ResourceResolveVO getByTypeAndId(String resourceType, String resourceId, String include, ApiKey apiKey, Long userId) {
        return getByTypeAndId(resourceType, resourceId, null, include, apiKey, userId, "catalog_read");
    }

    private ResourceResolveVO getByTypeAndId(String resourceType, String resourceId, String requestedVersion,
                                             String include, ApiKey apiKey, Long userId, String action) {
        if (userId == null && apiKey == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "资源解析需要登录态或 API Key");
        }
        if ("resolve".equalsIgnoreCase(action) && apiKey == null) {
            throw new BusinessException(ResultCode.GATEWAY_API_KEY_REQUIRED, "资源解析须提供有效的 X-Api-Key");
        }
        String type = requireType(resourceType);
        Long id = parseId(resourceId);
        if (TYPE_APP.equals(type)) {
            if (userId == null) {
                throw new BusinessException(ResultCode.UNAUTHORIZED, "应用访问需要登录态");
            }
            // 浏览器仅 JWT：允许拉取展示用元数据；真实 app_url 与 launch 票据仅在携带本人 API Key 时下发（见 issueAppLaunchTicket）。
            if (apiKey != null) {
                ensureAppKeyOwnedByUser(apiKey, userId);
            }
        }
        if (userId != null) {
            gatewayUserPermissionService.ensureAccess(userId, type);
        }
        boolean jwtCatalogMetadata = userId != null && "catalog_read".equalsIgnoreCase(action);
        if (apiKey != null && !jwtCatalogMetadata) {
            if ("invoke".equals(action)) {
                apiKeyScopeService.ensureInvokeAllowed(apiKey, type, String.valueOf(id));
            } else {
                apiKeyScopeService.ensureResolveAllowed(apiKey, type, String.valueOf(id));
            }
            resourceInvokeGrantService.ensureApiKeyGranted(apiKey, action, type, id, userId);
        }

        Map<String, Object> base = findResourceBase(type, id);
        String resolvedVersion = resolveResourceVersion(id, requestedVersion);
        ResourceResolveVO resolved = switch (type) {
            case TYPE_AGENT -> resolveAgent(base, resolvedVersion);
            case TYPE_SKILL -> resolveSkill(base, resolvedVersion);
            case TYPE_MCP -> resolveMcp(base, resolvedVersion);
            case TYPE_APP -> resolveApp(base, resolvedVersion, apiKey, userId, action);
            case TYPE_DATASET -> resolveDataset(base, resolvedVersion);
            default -> throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的资源类型");
        };
        Map<String, Object> snapshot = loadVersionSnapshot(id, resolvedVersion);
        ResourceResolveVO merged = applyVersionSnapshot(resolved, snapshot);
        if (TYPE_APP.equals(type)) {
            merged = issueAppLaunchTicket(merged, apiKey, userId, action);
        }
        merged = attachIncludesToResolve(merged, parseIncludes(include));
        enrichResolveCreator(merged);
        attachResolveReviewAggregates(merged);
        if ("catalog_read".equalsIgnoreCase(action)) {
            jdbcTemplate.update("UPDATE t_resource SET view_count = view_count + 1 WHERE id = ? AND deleted = 0", id);
        }
        return ResourceResolveSpecSanitizer.sanitize(merged);
    }

    private void enrichResolveCreator(ResourceResolveVO vo) {
        if (vo == null || vo.getCreatedBy() == null) {
            return;
        }
        vo.setCreatedByName(userDisplayNameResolver.resolveDisplayName(vo.getCreatedBy()));
    }

    /**
     * 统一调用网关：协议路由 + 熔断治理 + 观测日志写入。
     */
    @Override
    public InvokeResponse invoke(Long userId, String traceId, String ip, InvokeRequest request, ApiKey apiKey) {
        String reqId = UUID.randomUUID().toString();
        String type = requireType(request.getResourceType());
        ensureSkillNotInvokable(type);
        Long id = parseId(request.getResourceId());
        if (apiKey == null) {
            if (userId == null) {
                throw new BusinessException(ResultCode.UNAUTHORIZED, "调用网关需要登录或有效的 X-Api-Key");
            }
            throw new BusinessException(ResultCode.GATEWAY_API_KEY_REQUIRED, "统一网关调用须提供有效的 X-Api-Key");
        }
        ResourceResolveVO resolved = getByTypeAndId(type, String.valueOf(id), request.getVersion(), null, apiKey, userId, "invoke");
        ensurePublishedForInvoke(resolved);
        ensureNotCircuitOpen(type, resolved.getResourceCode());
        ensureResourceHealthNotDown(id);
        if (!StringUtils.hasText(resolved.getEndpoint())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "资源未配置可调用 endpoint");
        }
        String protocol = normalizeProtocol(resolved.getInvokeType(), "http");
        if (!protocolInvokerRegistry.isSupported(protocol)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该资源协议暂不支持统一调用: " + protocol);
        }

        int timeoutSec = request.getTimeoutSec() == null ? 30 : Math.max(1, Math.min(120, request.getTimeoutSec()));
        int statusCode;
        String respBody;
        String status;
        long latencyMs;
        String errMsg = null;
        Exception invokeCaught = null;
        GatewayGovernanceService.InvokeGovernanceLease governanceLease = null;
        try {
            governanceLease = gatewayGovernanceService.applyPreInvoke(userId, apiKey, type, id, 1);
            ProtocolInvokeContext protoCtx = ProtocolInvokeContext.of(apiKey.getId(), id, userId);
            ProtocolInvokeResult resp = protocolInvokerRegistry.invoke(
                    protocol,
                    resolved.getEndpoint(),
                    timeoutSec,
                    traceId,
                    request.getPayload(),
                    resolved.getSpec(),
                    protoCtx);
            statusCode = resp.statusCode();
            respBody = resp.body();
            latencyMs = resp.latencyMs();
            status = statusCode >= 200 && statusCode < 300 ? "success" : "error";
        } catch (Exception e) {
            invokeCaught = e;
            statusCode = 500;
            respBody = "";
            latencyMs = 0L;
            status = "error";
            errMsg = e.getMessage();
        } finally {
            gatewayGovernanceService.release(governanceLease);
        }
        if (errMsg == null && "error".equals(status) && StringUtils.hasText(respBody)) {
            errMsg = abbreviateForCallLog(respBody, 4000);
        }

        Boolean circuitOutcome = classifyGatewayInvokeCircuitOutcome(status, statusCode, invokeCaught);
        CallLog log = new CallLog();
        log.setId(reqId);
        log.setTraceId(traceId);
        log.setAgentId(String.valueOf(id));
        log.setAgentName(resolved.getResourceCode());
        log.setResourceType(StringUtils.hasText(type) ? type.trim().toLowerCase() : null);
        log.setUserId(userId == null ? "0" : String.valueOf(userId));
        log.setMethod("POST /invoke");
        log.setStatus(status);
        log.setStatusCode(statusCode);
        log.setLatencyMs((int) Math.max(0L, latencyMs));
        log.setErrorMessage(errMsg);
        log.setIp(StringUtils.hasText(ip) ? ip : "0.0.0.0");
        log.setCreateTime(LocalDateTime.now());
        TraceSpan span = new TraceSpan();
        span.setTraceId(traceId);
        span.setOperationName("gateway.invoke");
        span.setServiceName("unified-gateway");
        span.setStartTime(LocalDateTime.now().minusNanos(Math.max(0L, latencyMs) * 1_000_000L));
        span.setDuration((int) Math.max(0L, latencyMs));
        span.setStatus(status);
        span.setTags(Map.of(
                "resourceType", type,
                "resourceId", String.valueOf(id),
                "statusCode", statusCode,
                "requestId", reqId
        ));
        final String finalStatus = status;
        final long finalLatencyMs = latencyMs;
        transactionTemplate.executeWithoutResult(tx -> {
            recordCircuitResult(type, resolved.getResourceCode(), circuitOutcome);
            callLogMapper.insert(log);
            traceSpanMapper.insert(span);
            UsageRecord usageRecord = buildUsageRecord(userId, type, resolved, request, finalStatus, finalLatencyMs);
            if (usageRecord != null) {
                usageRecordMapper.insert(usageRecord);
            }
            apiKeyScopeService.markUsed(apiKey);
        });

        logGatewayInvokeOutcome(
                "POST /invoke",
                traceId,
                reqId,
                type,
                id,
                resolved.getResourceCode(),
                userId,
                status,
                statusCode,
                latencyMs,
                errMsg,
                respBody,
                request.getPayload());

        return InvokeResponse.builder()
                .requestId(reqId)
                .traceId(traceId)
                .resourceType(type)
                .resourceId(String.valueOf(id))
                .statusCode(statusCode)
                .status(status)
                .latencyMs(Math.max(0L, latencyMs))
                .body(respBody)
                .build();
    }

    @Override
    public void invokeStream(Long userId,
                             String traceId,
                             String ip,
                             InvokeRequest request,
                             ApiKey apiKey,
                             OutputStream responseBody) {
        if (responseBody == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "响应流不能为空");
        }
        String reqId = UUID.randomUUID().toString();
        String type = requireType(request.getResourceType());
        ensureSkillNotInvokable(type);
        Long id = parseId(request.getResourceId());
        if (apiKey == null) {
            if (userId == null) {
                throw new BusinessException(ResultCode.UNAUTHORIZED, "调用网关需要登录或有效的 X-Api-Key");
            }
            throw new BusinessException(ResultCode.GATEWAY_API_KEY_REQUIRED, "统一网关调用须提供有效的 X-Api-Key");
        }
        ResourceResolveVO resolved = getByTypeAndId(type, String.valueOf(id), request.getVersion(), null, apiKey, userId, "invoke");
        ensurePublishedForInvoke(resolved);
        ensureNotCircuitOpen(type, resolved.getResourceCode());
        ensureResourceHealthNotDown(id);
        if (!StringUtils.hasText(resolved.getEndpoint())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "资源未配置可调用 endpoint");
        }
        String protocol = normalizeProtocol(resolved.getInvokeType(), "http");
        if (!"mcp".equalsIgnoreCase(protocol)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "invoke-stream 仅支持 MCP（invokeType=mcp）");
        }
        if (mcpJsonRpcProtocolInvoker.isWebSocketMcp(resolved.getEndpoint(), resolved.getSpec())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "invoke-stream 不支持 WebSocket MCP，请使用 POST /invoke");
        }

        int timeoutSec = request.getTimeoutSec() == null ? 120 : Math.max(1, Math.min(600, request.getTimeoutSec()));
        long t0 = System.nanoTime();
        int[] statusCode = {200};
        String[] status = {"success"};
        String[] errMsg = {null};
        GatewayGovernanceService.InvokeGovernanceLease governanceLease = null;
        RuntimeException toRethrow = null;
        try {
            governanceLease = gatewayGovernanceService.applyPreInvoke(userId, apiKey, type, id, 1);
            ProtocolInvokeContext protoCtx = ProtocolInvokeContext.of(apiKey.getId(), id, userId);
            mcpJsonRpcProtocolInvoker.streamMcpHttpResponseTo(
                    resolved.getEndpoint(),
                    timeoutSec,
                    traceId,
                    request.getPayload(),
                    resolved.getSpec(),
                    protoCtx,
                    responseBody);
        } catch (BusinessException e) {
            status[0] = "error";
            statusCode[0] = 500;
            errMsg[0] = e.getMessage();
            toRethrow = e;
        } catch (IOException e) {
            status[0] = "error";
            statusCode[0] = 502;
            errMsg[0] = e.getMessage();
            toRethrow = new BusinessException(ResultCode.INTERNAL_ERROR, "MCP 流式转发失败: " + e.getMessage());
        } finally {
            gatewayGovernanceService.release(governanceLease);
        }
        long latencyMs = Math.max(0L, (System.nanoTime() - t0) / 1_000_000L);
        Boolean circuitOutcome = classifyGatewayInvokeCircuitOutcome(status[0], statusCode[0], toRethrow);
        CallLog log = new CallLog();
        log.setId(reqId);
        log.setTraceId(traceId);
        log.setAgentId(String.valueOf(id));
        log.setAgentName(resolved.getResourceCode());
        log.setResourceType(StringUtils.hasText(type) ? type.trim().toLowerCase() : null);
        log.setUserId(userId == null ? "0" : String.valueOf(userId));
        log.setMethod("POST /invoke-stream");
        log.setStatus(status[0]);
        log.setStatusCode(statusCode[0]);
        log.setLatencyMs((int) latencyMs);
        log.setErrorMessage(errMsg[0]);
        log.setIp(StringUtils.hasText(ip) ? ip : "0.0.0.0");
        log.setCreateTime(LocalDateTime.now());
        TraceSpan span = new TraceSpan();
        span.setTraceId(traceId);
        span.setOperationName("gateway.invoke-stream");
        span.setServiceName("unified-gateway");
        span.setStartTime(LocalDateTime.now().minusNanos(latencyMs * 1_000_000L));
        span.setDuration((int) latencyMs);
        span.setStatus(status[0]);
        span.setTags(Map.of(
                "resourceType", type,
                "resourceId", String.valueOf(id),
                "statusCode", statusCode[0],
                "requestId", reqId
        ));
        String finalStatus = status[0];
        long finalLatencyMs = latencyMs;
        transactionTemplate.executeWithoutResult(tx -> {
            recordCircuitResult(type, resolved.getResourceCode(), circuitOutcome);
            callLogMapper.insert(log);
            traceSpanMapper.insert(span);
            UsageRecord usageRecord = buildUsageRecord(userId, type, resolved, request, finalStatus, finalLatencyMs);
            if (usageRecord != null) {
                usageRecordMapper.insert(usageRecord);
            }
            apiKeyScopeService.markUsed(apiKey);
        });

        logGatewayInvokeOutcome(
                "POST /invoke-stream",
                traceId,
                reqId,
                type,
                id,
                resolved.getResourceCode(),
                userId,
                status[0],
                statusCode[0],
                latencyMs,
                errMsg[0],
                null,
                request.getPayload());

        if (toRethrow != null) {
            throw toRethrow;
        }
    }

    private UsageRecord buildUsageRecord(Long userId,
                                         String resourceType,
                                         ResourceResolveVO resolved,
                                         InvokeRequest request,
                                         String status,
                                         long latencyMs) {
        if (userId == null || resolved == null) {
            return null;
        }
        UsageRecord usage = new UsageRecord();
        usage.setUserId(userId);
        usage.setType(resourceType);
        try {
            usage.setResourceId(Long.valueOf(resolved.getResourceId()));
        } catch (NumberFormatException ignored) {
            usage.setResourceId(null);
        }
        usage.setAction("invoke");
        usage.setAgentName(resolved.getResourceCode());
        usage.setDisplayName(StringUtils.hasText(resolved.getDisplayName()) ? resolved.getDisplayName() : resolved.getResourceCode());
        usage.setInputPreview(safePreview(request == null ? null : request.getPayload(), 300));
        usage.setOutputPreview(null);
        usage.setLatencyMs((int) Math.max(0L, latencyMs));
        usage.setStatus(status);
        return usage;
    }

    private String safePreview(Object value, int maxLen) {
        if (value == null || maxLen <= 0) {
            return null;
        }
        String raw;
        if (value instanceof String s) {
            raw = s;
        } else {
            try {
                raw = objectMapper.writeValueAsString(value);
            } catch (Exception ex) {
                raw = String.valueOf(value);
            }
        }
        String cleaned = raw.replaceAll("\\s+", " ").trim();
        if (cleaned.length() <= maxLen) {
            return cleaned;
        }
        return cleaned.substring(0, maxLen) + "...";
    }

    private Map<String, Object> findResourceBase(String type, Long id) {
        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT id, resource_type, resource_code, display_name, description, status, created_by FROM t_resource WHERE deleted = 0 AND resource_type = ? AND id = ? LIMIT 1",
                type, id);
        if (list.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "资源不存在");
        }
        return list.get(0);
    }

    private ResourceResolveVO resolveAgent(Map<String, Object> base, String version) {
        Long id = longValue(base.get("id"));
        Map<String, Object> ext = queryOne(
                "SELECT spec_json, service_detail_md FROM t_resource_agent_ext WHERE resource_id = ? LIMIT 1",
                id);
        Map<String, Object> spec = parseJsonMap(ext == null ? null : ext.get("spec_json"));
        String invokeType = normalizeProtocol(spec == null ? null : spec.get("protocol"), "rest");
        String serviceMd = ext == null ? "" : valueOf(ext.get("service_detail_md"));
        return ResourceResolveVO.builder()
                .resourceType(TYPE_AGENT)
                .resourceId(String.valueOf(id))
                .version(version)
                .resourceCode(valueOf(base.get("resource_code")))
                .displayName(valueOf(base.get("display_name")))
                .status(valueOf(base.get("status")))
                .createdBy(longOrNull(base.get("created_by")))
                .invokeType(invokeType)
                .endpoint(specUrl(spec))
                .spec(spec)
                .serviceDetailMd(StringUtils.hasText(serviceMd) ? serviceMd : null)
                .build();
    }

    private ResourceResolveVO resolveSkill(Map<String, Object> base, String version) {
        Long id = longValue(base.get("id"));
        Map<String, Object> ext = queryOne("""
                        SELECT skill_type, artifact_uri, artifact_sha256, manifest_json, entry_doc, spec_json, parameters_schema, is_public, pack_validation_status, skill_root_path, service_detail_md
                        FROM t_resource_skill_ext WHERE resource_id = ? LIMIT 1
                        """,
                id);
        if (ext == null) {
            ext = Map.of();
        }
        Map<String, Object> spec = new LinkedHashMap<>();
        Map<String, Object> manifest = parseJsonMap(ext.get("manifest_json"));
        if (!manifest.isEmpty()) {
            spec.put("manifest", manifest);
        }
        if (StringUtils.hasText(valueOf(ext.get("entry_doc")))) {
            spec.put("entryDoc", valueOf(ext.get("entry_doc")).trim());
        }
        Map<String, Object> extra = parseJsonMap(ext.get("spec_json"));
        if (!extra.isEmpty()) {
            spec.put("extra", extra);
        }
        Map<String, Object> params = parseJsonMap(ext.get("parameters_schema"));
        if (!params.isEmpty()) {
            spec.put("parametersSchema", params);
        }
        if (StringUtils.hasText(valueOf(ext.get("artifact_sha256")))) {
            spec.put("artifactSha256", valueOf(ext.get("artifact_sha256")).trim());
        }
        if (StringUtils.hasText(valueOf(ext.get("pack_validation_status")))) {
            spec.put("packValidationStatus", valueOf(ext.get("pack_validation_status")).trim().toLowerCase(Locale.ROOT));
        }
        if (StringUtils.hasText(valueOf(ext.get("skill_root_path")))) {
            spec.put("skillRootPath", valueOf(ext.get("skill_root_path")).trim());
        }
        spec.put("packFormat", valueOf(ext.get("skill_type")));
        String artifactUri = valueOf(ext.get("artifact_uri"));
        boolean skillPublic = truthySkillPublic(ext.get("is_public"));
        String endpointOut = StringUtils.hasText(artifactUri) ? artifactUri.trim() : null;
        if (!skillPublic && StringUtils.hasText(artifactUri)) {
            spec.put("artifactDownloadApi",
                    ServletContextPathUtil.join(servletContextPath, "/resource-center/resources/" + id + "/skill-artifact"));
            endpointOut = null;
        }
        String skillDetailMd = valueOf(ext.get("service_detail_md"));
        return ResourceResolveVO.builder()
                .resourceType(TYPE_SKILL)
                .resourceId(String.valueOf(id))
                .version(version)
                .resourceCode(valueOf(base.get("resource_code")))
                .displayName(valueOf(base.get("display_name")))
                .status(valueOf(base.get("status")))
                .createdBy(longOrNull(base.get("created_by")))
                .invokeType("artifact")
                .endpoint(endpointOut)
                .spec(spec)
                .serviceDetailMd(StringUtils.hasText(skillDetailMd) ? skillDetailMd : null)
                .build();
    }

    private static boolean truthySkillPublic(Object o) {
        if (o == null) {
            return false;
        }
        if (o instanceof Boolean b) {
            return b;
        }
        if (o instanceof Number n) {
            return n.intValue() != 0;
        }
        return false;
    }

    private ResourceResolveVO resolveMcp(Map<String, Object> base, String version) {
        Long id = longValue(base.get("id"));
        Map<String, Object> ext = queryOne(
                "SELECT endpoint, protocol, auth_type, auth_config, service_detail_md FROM t_resource_mcp_ext WHERE resource_id = ? LIMIT 1", id);
        Map<String, Object> spec;
        String endpoint;
        String protocol;
        if (ext == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "MCP 资源扩展信息不存在");
        }
        Map<String, Object> parsed = parseJsonMap(ext.get("auth_config"));
        spec = parsed == null ? new HashMap<>() : new HashMap<>(parsed);
        String authTypeCol = valueOf(ext.get("auth_type"));
        if (StringUtils.hasText(authTypeCol)) {
            spec.put(McpOutboundHeaderBuilder.REGISTRY_AUTH_TYPE_KEY, authTypeCol.trim().toLowerCase(Locale.ROOT));
        }
        protocol = normalizeProtocol(ext.get("protocol"), "mcp");
        endpoint = valueOf(ext.get("endpoint"));
        String serviceMd = valueOf(ext.get("service_detail_md"));
        return ResourceResolveVO.builder()
                .resourceType(TYPE_MCP)
                .resourceId(String.valueOf(id))
                .version(version)
                .resourceCode(valueOf(base.get("resource_code")))
                .displayName(valueOf(base.get("display_name")))
                .status(valueOf(base.get("status")))
                .createdBy(longOrNull(base.get("created_by")))
                .invokeType(protocol)
                .endpoint(endpoint)
                .spec(spec)
                .serviceDetailMd(StringUtils.hasText(serviceMd) ? serviceMd : null)
                .build();
    }

    private ResourceResolveVO resolveApp(Map<String, Object> base, String version, ApiKey apiKey, Long userId, String action) {
        Long id = longValue(base.get("id"));
        Map<String, Object> ext = queryOne(
                "SELECT app_url, embed_type, icon, screenshots, service_detail_md FROM t_resource_app_ext WHERE resource_id = ? LIMIT 1",
                id);
        Map<String, Object> spec = new HashMap<>();
        spec.put("embedType", valueOf(ext == null ? null : ext.get("embed_type")));
        if (ext != null && StringUtils.hasText(valueOf(ext.get("icon")))) {
            spec.put("icon", valueOf(ext.get("icon")).trim());
        }
        if (ext != null && ext.get("screenshots") != null) {
            spec.put("screenshots", parseJsonList(ext.get("screenshots")));
        }
        String appDetailMd = ext == null ? "" : valueOf(ext.get("service_detail_md"));
        return ResourceResolveVO.builder()
                .resourceType(TYPE_APP)
                .resourceId(String.valueOf(id))
                .version(version)
                .resourceCode(valueOf(base.get("resource_code")))
                .displayName(valueOf(base.get("display_name")))
                .status(valueOf(base.get("status")))
                .createdBy(longOrNull(base.get("created_by")))
                .invokeType("redirect")
                .endpoint(valueOf(ext == null ? null : ext.get("app_url")))
                .spec(spec)
                .serviceDetailMd(StringUtils.hasText(appDetailMd) ? appDetailMd : null)
                .build();
    }

    private ResourceResolveVO issueAppLaunchTicket(ResourceResolveVO resolved, ApiKey apiKey, Long userId, String action) {
        if (resolved == null || !TYPE_APP.equalsIgnoreCase(resolved.getResourceType())) {
            return resolved;
        }
        if (apiKey == null || !StringUtils.hasText(apiKey.getId())) {
            resolved.setEndpoint(null);
            resolved.setLaunchUrl(null);
            resolved.setLaunchToken(null);
            return resolved;
        }
        if (!StringUtils.hasText(resolved.getEndpoint())) {
            return resolved;
        }
        AppLaunchTokenService.LaunchTicket ticket = appLaunchTokenService.issue(
                parseId(resolved.getResourceId()),
                resolved.getEndpoint(),
                apiKey.getId(),
                userId);
        resolved.setLaunchToken(ticket.token());
        resolved.setLaunchUrl(ticket.launchUrl());
        // 解析阶段不再直接暴露真实 app_url，避免前端绕过授权直连第三方地址。
        if (!"invoke".equalsIgnoreCase(action)) {
            resolved.setEndpoint(null);
        }
        return resolved;
    }

    private static void ensureAppKeyOwnedByUser(ApiKey apiKey, Long userId) {
        if (apiKey == null || userId == null) {
            throw new BusinessException(ResultCode.GATEWAY_API_KEY_REQUIRED, "应用访问须提供本人有效的 X-Api-Key");
        }
        if (!"user".equalsIgnoreCase(apiKey.getOwnerType())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "应用访问仅支持用户级 API Key");
        }
        String ownerId = apiKey.getOwnerId();
        if (!StringUtils.hasText(ownerId) || !String.valueOf(userId).equals(ownerId.trim())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前账号不能使用该 API Key 打开应用");
        }
    }

    private ResourceResolveVO resolveDataset(Map<String, Object> base, String version) {
        Long id = longValue(base.get("id"));
        Map<String, Object> ext = queryOne(
                "SELECT data_type, format, record_count, file_size, tags, service_detail_md FROM t_resource_dataset_ext WHERE resource_id = ? LIMIT 1",
                id);
        Map<String, Object> spec = new HashMap<>();
        spec.put("dataType", valueOf(ext == null ? null : ext.get("data_type")));
        spec.put("format", valueOf(ext == null ? null : ext.get("format")));
        spec.put("recordCount", longValue(ext == null ? null : ext.get("record_count")));
        spec.put("fileSize", longValue(ext == null ? null : ext.get("file_size")));
        spec.put("tags", parseJsonList(ext == null ? null : ext.get("tags")));
        String dsDetailMd = ext == null ? "" : valueOf(ext.get("service_detail_md"));
        return ResourceResolveVO.builder()
                .resourceType(TYPE_DATASET)
                .resourceId(String.valueOf(id))
                .version(version)
                .resourceCode(valueOf(base.get("resource_code")))
                .displayName(valueOf(base.get("display_name")))
                .status(valueOf(base.get("status")))
                .createdBy(longOrNull(base.get("created_by")))
                .invokeType("metadata")
                .endpoint(null)
                .spec(spec)
                .serviceDetailMd(StringUtils.hasText(dsDetailMd) ? dsDetailMd : null)
                .build();
    }

    private Map<String, Object> queryOne(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 与健康检查配置联动：{@code down} / {@code disabled} 时拒绝 invoke，避免用户持续命中已知不可用资源。
     */
    private void ensureResourceHealthNotDown(Long resourceId) {
        if (resourceId == null) {
            return;
        }
        Map<String, Object> row = queryOne(
                "SELECT health_status FROM t_resource_health_config WHERE resource_id = ? LIMIT 1",
                resourceId);
        if (row == null) {
            return;
        }
        String hs = valueOf(row.get("health_status")).trim().toLowerCase(Locale.ROOT);
        if ("down".equals(hs)) {
            throw new BusinessException(ResultCode.RESOURCE_HEALTH_DOWN);
        }
        if ("disabled".equals(hs)) {
            throw new BusinessException(ResultCode.RESOURCE_HEALTH_DOWN, "资源健康检查已关闭或未启用，暂不可调用");
        }
    }

    /**
     * 目录 {@code callableOnly=true}：与前端 {@code isCatalogMcpCallable} 对齐，避免「广场已标不可调用仍出现在 MCP 对外集成」。
     * <p>
     * 健康侧与 {@link #ensureResourceHealthNotDown} 对齐：仅 {@code down} / {@code disabled} 视为不可调用；
     * {@code degraded} 仍可调（与网关 invoke 一致），由观测/降级提示传达风险。
     * 熔断侧：{@code OPEN}/{@code FORCED_OPEN} 不可入目录；{@code HALF_OPEN} 允许入目录以便完成半开探测恢复。
     */
    private boolean isResourcePhysicallyCallable(Long resourceId, String resourceType) {
        if (resourceId == null) {
            return false;
        }
        Map<String, Object> healthRow = queryOne(
                "SELECT health_status FROM t_resource_health_config WHERE resource_id = ? LIMIT 1",
                resourceId);
        if (healthRow != null) {
            String hs = valueOf(healthRow.get("health_status"));
            if (isHealthStatusExcludedFromCallableCatalog(hs)) {
                return false;
            }
        }
        return !isCircuitExcludedFromCallableCatalog(resourceId, resourceType);
    }

    private static boolean isHealthStatusExcludedFromCallableCatalog(String raw) {
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        String h = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return "down".equals(h) || "disabled".equals(h);
    }

    /**
     * 与前端目录「不可调用」一致：仅 FULL_OPEN 态拒入；HALF_OPEN 仍展示为可尝试（与 {@link #ensureNotCircuitOpen} 限流探测一致）。
     */
    private boolean isCircuitExcludedFromCallableCatalog(Long resourceId, String resourceType) {
        if (resourceId == null || !StringUtils.hasText(resourceType)) {
            return false;
        }
        String rt = resourceType.trim().toLowerCase(Locale.ROOT);
        Map<String, Object> row = queryOne(
                "SELECT current_state FROM t_resource_circuit_breaker WHERE resource_type = ? AND resource_id = ? LIMIT 1",
                rt, resourceId);
        if (row == null) {
            return false;
        }
        String state = valueOf(row.get("current_state")).trim().toUpperCase(Locale.ROOT);
        return "OPEN".equals(state) || "FORCED_OPEN".equals(state);
    }

    private void ensureNotCircuitOpen(String resourceType, String resourceCode) {
        Long resourceId = resolveCanonicalResourceId(resourceType, resourceCode);
        if (resourceId == null) {
            return;
        }
        Map<String, Object> row = queryOne(
                "SELECT current_state, fallback_message, half_open_max_calls, success_count, failure_count FROM t_resource_circuit_breaker WHERE resource_type = ? AND resource_id = ? LIMIT 1",
                resourceType, resourceId);
        if (row == null) {
            return;
        }
        String state = valueOf(row.get("current_state"));
        if ("OPEN".equalsIgnoreCase(state) || "FORCED_OPEN".equalsIgnoreCase(state)) {
            String fallbackMessage = valueOf(row.get("fallback_message"));
            throw new BusinessException(
                    ResultCode.CIRCUIT_OPEN,
                    StringUtils.hasText(fallbackMessage) ? fallbackMessage : "目标资源已熔断，请稍后重试");
        }
        if ("HALF_OPEN".equalsIgnoreCase(state)) {
            long maxCalls = Math.max(1L, longValue(row.get("half_open_max_calls")));
            long attempts = longValue(row.get("success_count")) + longValue(row.get("failure_count"));
            if (attempts >= maxCalls) {
                throw new BusinessException(ResultCode.CIRCUIT_OPEN, "目标资源正在半开试探中，请稍后重试");
            }
        }
    }

    /**
     * 将网关调用结果映射为资源级熔断计数：
     * <ul>
     *   <li>{@code true}：计为成功（清零失败计数、半开恢复等，沿用原逻辑）</li>
     *   <li>{@code false}：计为失败（累加 failure_count，可能触发 OPEN）</li>
     *   <li>{@code null}：不计入熔断（调用方错误、上游 4xx 等，不应当把资源算作宕机）</li>
     * </ul>
     */
    private static Boolean classifyGatewayInvokeCircuitOutcome(String status, int statusCode, Throwable invokeError) {
        if ("success".equals(status)) {
            return true;
        }
        if (invokeError instanceof BusinessException be && isCircuitNeutralBusinessCode(be.getCode())) {
            return null;
        }
        if (statusCode >= 400 && statusCode < 500) {
            return null;
        }
        return false;
    }

    /**
     * 本端校验/策略类错误：不代表上游 MCP/HTTP 服务不可用，不参与资源熔断统计。
     */
    private static boolean isCircuitNeutralBusinessCode(int code) {
        return code == ResultCode.PARAM_ERROR.getCode()
                || code == ResultCode.UNAUTHORIZED.getCode()
                || code == ResultCode.FORBIDDEN.getCode()
                || code == ResultCode.NOT_FOUND.getCode()
                || code == ResultCode.GATEWAY_API_KEY_REQUIRED.getCode()
                || code == ResultCode.RATE_LIMITED.getCode()
                || code == ResultCode.DAILY_QUOTA_EXHAUSTED.getCode()
                || code == ResultCode.MONTHLY_QUOTA_EXHAUSTED.getCode()
                || code == ResultCode.QUOTA_EXCEEDED.getCode()
                || code == ResultCode.RESOURCE_HEALTH_DOWN.getCode()
                || code == ResultCode.CIRCUIT_OPEN.getCode();
    }

    private void recordCircuitResult(String resourceType, String resourceCode, Boolean success) {
        if (success == null) {
            return;
        }
        boolean successBool = success;
        Long resourceId = resolveCanonicalResourceId(resourceType, resourceCode);
        if (resourceId == null) {
            return;
        }
        Map<String, Object> current = queryOne(
                "SELECT id, current_state, success_count, failure_count, failure_threshold, half_open_max_calls FROM t_resource_circuit_breaker WHERE resource_type = ? AND resource_id = ? LIMIT 1",
                resourceType, resourceId);
        if (current == null) {
            Map<String, Object> base = queryOne(
                    "SELECT resource_code, display_name FROM t_resource WHERE id = ? AND resource_type = ? LIMIT 1",
                    resourceId, resourceType);
            if (base == null) {
                return;
            }
            jdbcTemplate.update(
                    "INSERT INTO t_resource_circuit_breaker(resource_id, resource_type, resource_code, display_name, current_state, failure_threshold, open_duration_sec, half_open_max_calls, success_count, failure_count, create_time, update_time) "
                            + "VALUES(?, ?, ?, ?, 'CLOSED', 5, 60, 3, ?, ?, NOW(), NOW())",
                    resourceId,
                    resourceType,
                    valueOf(base.get("resource_code")),
                    valueOf(base.get("display_name")),
                    successBool ? 1L : 0L,
                    successBool ? 0L : 1L);
            if (!successBool) {
                applyAutoOpen(resourceType, resourceId);
            }
            return;
        }

        String currentState = valueOf(current.get("current_state"));
        if (successBool) {
            if ("HALF_OPEN".equalsIgnoreCase(currentState)) {
                long successCount = longValue(current.get("success_count")) + 1L;
                long allowed = Math.max(1L, longValue(current.get("half_open_max_calls")));
                if (successCount >= allowed) {
                    jdbcTemplate.update(
                            "UPDATE t_resource_circuit_breaker SET success_count = ?, failure_count = 0, current_state = 'CLOSED', update_time = NOW() "
                                    + "WHERE resource_type = ? AND resource_id = ?",
                            successCount, resourceType, resourceId);
                } else {
                    jdbcTemplate.update(
                            "UPDATE t_resource_circuit_breaker SET success_count = ?, failure_count = 0, current_state = 'HALF_OPEN', update_time = NOW() "
                                    + "WHERE resource_type = ? AND resource_id = ?",
                            successCount, resourceType, resourceId);
                }
                return;
            }
            jdbcTemplate.update(
                    "UPDATE t_resource_circuit_breaker SET success_count = COALESCE(success_count,0) + 1, failure_count = 0, current_state = 'CLOSED', update_time = NOW() "
                            + "WHERE resource_type = ? AND resource_id = ?",
                    resourceType, resourceId);
            return;
        }

        if ("HALF_OPEN".equalsIgnoreCase(currentState)) {
            jdbcTemplate.update(
                    "UPDATE t_resource_circuit_breaker SET current_state = 'OPEN', last_opened_at = NOW(), success_count = 0, failure_count = COALESCE(failure_count,0) + 1, update_time = NOW() "
                            + "WHERE resource_type = ? AND resource_id = ?",
                    resourceType, resourceId);
            return;
        }

        jdbcTemplate.update(
                "UPDATE t_resource_circuit_breaker SET failure_count = COALESCE(failure_count,0) + 1, update_time = NOW() "
                        + "WHERE resource_type = ? AND resource_id = ?",
                resourceType, resourceId);
        applyAutoOpen(resourceType, resourceId);
    }


    private void applyAutoOpen(String resourceType, Long resourceId) {
        Map<String, Object> row = queryOne(
                "SELECT failure_count, failure_threshold FROM t_resource_circuit_breaker WHERE resource_type = ? AND resource_id = ? LIMIT 1",
                resourceType, resourceId);
        if (row == null) {
            return;
        }
        long failureCount = longValue(row.get("failure_count"));
        long threshold = Math.max(1L, longValue(row.get("failure_threshold")));
        if (failureCount >= threshold) {
            jdbcTemplate.update(
                    "UPDATE t_resource_circuit_breaker SET current_state = 'OPEN', last_opened_at = NOW(), success_count = 0, update_time = NOW() "
                            + "WHERE resource_type = ? AND resource_id = ?",
                    resourceType, resourceId);
        }
    }

    private Long resolveCanonicalResourceId(String resourceType, String resourceCode) {
        if (!StringUtils.hasText(resourceCode)) {
            return null;
        }
        Map<String, Object> row = queryOne(
                "SELECT id FROM t_resource WHERE deleted = 0 AND resource_type = ? AND resource_code = ? LIMIT 1",
                resourceType, resourceCode);
        if (row == null) {
            return null;
        }
        return longValue(row.get("id"));
    }

    private static String specUrl(Map<String, Object> spec) {
        if (spec == null) return null;
        Object v = spec.get("url");
        return v == null ? null : String.valueOf(v);
    }

    private static String normalizeProtocol(Object raw, String defaultProtocol) {
        if (raw == null) {
            return defaultProtocol;
        }
        String s = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(s)) {
            return defaultProtocol;
        }
        return s;
    }

    private static String normalizeType(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private static String requireType(String raw) {
        String type = normalizeType(raw);
        if (!StringUtils.hasText(type)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "resourceType 不能为空");
        }
        if (!List.of(TYPE_AGENT, TYPE_SKILL, TYPE_MCP, TYPE_APP, TYPE_DATASET).contains(type)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的资源类型: " + raw);
        }
        return type;
    }

    private static Long parseId(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "resourceId 不能为空");
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "resourceId 非法");
        }
    }

    private Map<String, Object> parseJsonMap(Object raw) {
        if (raw == null) {
            return Map.of();
        }
        try {
            if (raw instanceof String s) {
                if (!StringUtils.hasText(s)) {
                    return Map.of();
                }
                return objectMapper.readValue(s, new TypeReference<Map<String, Object>>() {
                });
            }
            return objectMapper.convertValue(raw, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private List<Object> parseJsonList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        try {
            if (raw instanceof String s) {
                if (!StringUtils.hasText(s)) {
                    return List.of();
                }
                return objectMapper.readValue(s, new TypeReference<List<Object>>() {
                });
            }
            return objectMapper.convertValue(raw, new TypeReference<List<Object>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String valueOf(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static LocalDateTime toDateTime(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof LocalDateTime ldt) {
            return ldt;
        }
        if (v instanceof Timestamp ts) {
            return ts.toLocalDateTime();
        }
        return null;
    }

    private void logGatewayInvokeOutcome(
            String httpLabel,
            String traceId,
            String requestId,
            String resourceType,
            Long resourceNumericId,
            String resourceCode,
            Long userId,
            String status,
            int statusCode,
            long latencyMs,
            String errMsg,
            String respBody,
            Object payload) {
        var logCfg = runtimeAppConfigService.logging();
        int bodyMax = Math.min(4096, Math.max(128, logCfg.getGatewayInvokeBodyPreviewMax()));
        DegradedJsonFields dj = parseDegradedJsonFields(respBody);
        String resolvedErr = StringUtils.hasText(errMsg) ? errMsg : dj.jsonMessage();
        String bodyPreview = StringUtils.hasText(respBody) ? abbreviateForCallLog(respBody, bodyMax) : null;
        String payloadPreview = buildPayloadPreviewForLog(payload);
        String uid = userId == null ? "-" : String.valueOf(userId);
        String rid = resourceNumericId == null ? "-" : String.valueOf(resourceNumericId);
        String bcStr = dj.businessCode() == null ? "-" : String.valueOf(dj.businessCode());

        if (!"success".equals(status)) {
            log.warn(
                    "[gateway.invoke] {} traceId={} requestId={} resourceType={} resourceId={} resourceCode={} userId={} status={} statusCode={} latencyMs={} businessCode={} errorMessage={} bodyPreview={} payloadPreview={}",
                    httpLabel,
                    traceId,
                    requestId,
                    resourceType,
                    rid,
                    resourceCode,
                    uid,
                    status,
                    statusCode,
                    latencyMs,
                    bcStr,
                    resolvedErr == null ? "" : resolvedErr,
                    bodyPreview == null ? "" : bodyPreview,
                    payloadPreview == null ? "" : payloadPreview);
        } else if (logCfg.isGatewayInvokeLogSuccess()) {
            log.info(
                    "[gateway.invoke] {} traceId={} requestId={} resourceType={} resourceId={} resourceCode={} userId={} status={} statusCode={} latencyMs={} payloadPreview={}",
                    httpLabel,
                    traceId,
                    requestId,
                    resourceType,
                    rid,
                    resourceCode,
                    uid,
                    status,
                    statusCode,
                    latencyMs,
                    payloadPreview == null ? "" : payloadPreview);
        }
    }

    private String buildPayloadPreviewForLog(Object payload) {
        var logCfg = runtimeAppConfigService.logging();
        if (!logCfg.isGatewayInvokeLogPayloadPreview() || payload == null) {
            return null;
        }
        int max = Math.min(4096, Math.max(64, logCfg.getGatewayInvokePayloadPreviewMax()));
        try {
            String raw = payload instanceof String s ? s : objectMapper.writeValueAsString(payload);
            return abbreviateForCallLog(raw, max);
        } catch (Exception e) {
            return "(payload-preview-failed)";
        }
    }

    private DegradedJsonFields parseDegradedJsonFields(String respBody) {
        if (!StringUtils.hasText(respBody)) {
            return new DegradedJsonFields(null, null);
        }
        String t = respBody.trim();
        if (!t.startsWith("{")) {
            return new DegradedJsonFields(null, null);
        }
        try {
            JsonNode n = objectMapper.readTree(t);
            Integer bc = n.hasNonNull("businessCode") && n.get("businessCode").isNumber()
                    ? n.get("businessCode").asInt()
                    : null;
            String jm = n.hasNonNull("message") && n.get("message").isTextual() ? n.get("message").asText() : null;
            return new DegradedJsonFields(bc, jm);
        } catch (Exception e) {
            return new DegradedJsonFields(null, null);
        }
    }

    private record DegradedJsonFields(Integer businessCode, String jsonMessage) {}

    private static String abbreviateForCallLog(String text, int maxLen) {
        if (!StringUtils.hasText(text) || maxLen <= 0) {
            return text;
        }
        String t = text.trim();
        return t.length() <= maxLen ? t : t.substring(0, maxLen) + "...";
    }

    private static Long longOrNull(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            String s = String.valueOf(v).trim();
            if (!StringUtils.hasText(s)) {
                return null;
            }
            return Long.valueOf(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static Long longValue(Object v) {
        if (v == null) {
            return 0L;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(v));
        } catch (Exception e) {
            return 0L;
        }
    }

    private void ensurePublishedForInvoke(ResourceResolveVO resolved) {
        if (resolved == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "资源不存在");
        }
        if (!STATUS_PUBLISHED.equalsIgnoreCase(resolved.getStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "资源未发布，暂不可调用");
        }
    }

    private String resolveResourceVersion(Long resourceId, String requestedVersion) {
        String normalizedRequest = StringUtils.hasText(requestedVersion) ? requestedVersion.trim() : null;
        try {
            if (normalizedRequest != null) {
                List<Map<String, Object>> exact = jdbcTemplate.queryForList(
                        "SELECT version FROM t_resource_version WHERE resource_id = ? AND version = ? AND status = 'active' LIMIT 1",
                        resourceId, normalizedRequest);
                if (exact.isEmpty()) {
                    throw new BusinessException(ResultCode.NOT_FOUND, "指定版本不存在或不可用");
                }
                return String.valueOf(exact.get(0).get("version"));
            }
            List<Map<String, Object>> current = jdbcTemplate.queryForList(
                    "SELECT version FROM t_resource_version WHERE resource_id = ? AND status = 'active' ORDER BY is_current DESC, create_time DESC LIMIT 1",
                    resourceId);
            if (!current.isEmpty() && current.get(0).get("version") != null) {
                return String.valueOf(current.get(0).get("version"));
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            // If version table is unavailable during transition, fallback to default version.
        }
        if (normalizedRequest != null && !DEFAULT_RESOURCE_VERSION.equalsIgnoreCase(normalizedRequest)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "指定版本不存在");
        }
        return DEFAULT_RESOURCE_VERSION;
    }

    private Map<String, Object> loadVersionSnapshot(Long resourceId, String version) {
        if (resourceId == null || !StringUtils.hasText(version)) {
            return Map.of();
        }
        Map<String, Object> row = queryOne(
                "SELECT snapshot_json FROM t_resource_version WHERE resource_id = ? AND version = ? AND status = 'active' LIMIT 1",
                resourceId, version);
        if (row == null) {
            return Map.of();
        }
        return parseJsonMap(row.get("snapshot_json"));
    }

    private ResourceResolveVO applyVersionSnapshot(ResourceResolveVO resolved, Map<String, Object> snapshot) {
        if (resolved == null || snapshot == null || snapshot.isEmpty()) {
            return resolved;
        }
        if (snapshot.containsKey("resourceCode") && StringUtils.hasText(valueOf(snapshot.get("resourceCode")))) {
            resolved.setResourceCode(valueOf(snapshot.get("resourceCode")));
        }
        if (snapshot.containsKey("displayName") && StringUtils.hasText(valueOf(snapshot.get("displayName")))) {
            resolved.setDisplayName(valueOf(snapshot.get("displayName")));
        }
        // 生命周期状态以主表 t_resource 为准；快照里的 status 可能停留在提审前，覆盖会导致已发布资源 invoke 报「未发布」
        if (snapshot.containsKey("invokeType") && StringUtils.hasText(valueOf(snapshot.get("invokeType")))) {
            resolved.setInvokeType(normalizeProtocol(snapshot.get("invokeType"), resolved.getInvokeType()));
        }
        if (snapshot.containsKey("endpoint") && StringUtils.hasText(valueOf(snapshot.get("endpoint")))) {
            resolved.setEndpoint(valueOf(snapshot.get("endpoint")));
        }
        if (snapshot.containsKey("spec")) {
            Map<String, Object> snapshotSpec = parseJsonMap(snapshot.get("spec"));
            if (!snapshotSpec.isEmpty()) {
                resolved.setSpec(snapshotSpec);
            }
        }
        if (snapshot.containsKey("serviceDetailMd")) {
            String md = valueOf(snapshot.get("serviceDetailMd"));
            resolved.setServiceDetailMd(StringUtils.hasText(md) ? md.trim() : null);
        }
        return resolved;
    }

    @Override
    public ResourceStatsVO getResourceStats(String resourceType, String resourceId) {
        String type = normalizeType(resourceType);

        long parsedId;
        try {
            parsedId = Long.parseLong(resourceId);
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "无效的资源 ID");
        }
        List<Map<String, Object>> existCheck = jdbcTemplate.queryForList(
                "SELECT 1 FROM t_resource WHERE id = ? AND deleted = 0 LIMIT 1", parsedId);
        if (existCheck.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "资源不存在");
        }

        Long callCount = 0L;
        Long successCount = 0L;
        try {
            callCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_call_log WHERE agent_id = ?",
                    Long.class, resourceId);
            successCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_call_log WHERE agent_id = ? AND status = 'success'",
                    Long.class, resourceId);
        } catch (Exception ignored) {
        }
        if (callCount == null) callCount = 0L;
        if (successCount == null) successCount = 0L;
        double successRate = callCount > 0 ? (double) successCount / callCount * 100.0 : 0.0;

        Double rating = null;
        try {
            rating = jdbcTemplate.queryForObject(
                    "SELECT AVG(rating) FROM t_review WHERE target_type = ? AND target_id = ? AND deleted = 0",
                    Double.class, type, resourceId);
        } catch (Exception ignored) {
        }

        Long favoriteCount = 0L;
        try {
            favoriteCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_favorite WHERE target_type = ? AND target_id = ?",
                    Long.class, type, resourceId);
        } catch (Exception ignored) {
        }
        if (favoriteCount == null) favoriteCount = 0L;

        List<Map<String, Object>> callTrend = jdbcTemplate.queryForList(
                "SELECT DATE(create_time) AS day, COUNT(*) AS cnt "
                        + "FROM t_call_log WHERE agent_id = ? AND create_time >= DATE_SUB(NOW(), INTERVAL 7 DAY) "
                        + "GROUP BY DATE(create_time) ORDER BY day",
                resourceId);

        List<Map<String, Object>> related = jdbcTemplate.queryForList(
                "SELECT id AS resourceId, resource_type, resource_code, display_name "
                        + "FROM t_resource WHERE deleted = 0 AND status = 'published' "
                        + "AND resource_type = ? AND id <> ? "
                        + "ORDER BY update_time DESC LIMIT 5",
                type, parsedId);

        return ResourceStatsVO.builder()
                .callCount(callCount)
                .successRate(Math.round(successRate * 100.0) / 100.0)
                .rating(rating)
                .favoriteCount(favoriteCount)
                .callTrend(callTrend)
                .relatedResources(related)
                .build();
    }

    @Override
    public List<ExploreHubData.ExploreResourceItem> trending(String resourceType, Integer limit, Long userId) {
        int lim = limit != null ? Math.min(50, Math.max(1, limit)) : 10;
        String type = normalizeType(resourceType);
        GatewayUserPermissionService.CatalogTypePredicate typeOk = gatewayUserPermissionService.catalogTypePredicate(userId);
        int fetchCap = Math.min(200, lim * 15);

        StringBuilder sql = new StringBuilder(
                "SELECT r.id, r.resource_type, r.resource_code, r.display_name, r.description, r.status, r.created_by, "
                        + "COALESCE(c.cnt, 0) AS call_count, "
                        + "rv.avg_rating AS rating, "
                        + "COALESCE(rv.review_count, 0) AS review_count, "
                        + "COALESCE(fav.fav_cnt, 0) AS favorite_count, "
                        + "r.update_time "
                        + "FROM t_resource r "
                        + "LEFT JOIN (SELECT agent_id, COUNT(*) AS cnt FROM t_call_log "
                        + "WHERE create_time >= DATE_SUB(NOW(), INTERVAL 7 DAY) GROUP BY agent_id) c "
                        + "ON r.id = c.agent_id "
                        + "LEFT JOIN (SELECT target_type, target_id, AVG(rating) AS avg_rating, COUNT(*) AS review_count FROM t_review "
                        + "WHERE deleted = 0 GROUP BY target_type, target_id) rv "
                        + "ON r.resource_type = rv.target_type AND r.id = rv.target_id "
                        + "LEFT JOIN (SELECT target_type, target_id, COUNT(*) AS fav_cnt FROM t_favorite "
                        + "GROUP BY target_type, target_id) fav "
                        + "ON r.resource_type = fav.target_type AND r.id = fav.target_id "
                        + "WHERE r.deleted = 0 AND r.status = 'published' ");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(type)) {
            sql.append(" AND r.resource_type = ? ");
            args.add(type);
        }
        sql.append(" ORDER BY call_count DESC LIMIT ? ");
        args.add(fetchCap);

        List<ExploreHubData.ExploreResourceItem> rows = jdbcTemplate.query(sql.toString(), args.toArray(), (rs, i) -> {
            Double ratingVal = null;
            double ratingRaw = rs.getDouble("rating");
            if (!rs.wasNull()) {
                ratingVal = ratingRaw;
            }
            return ExploreHubData.ExploreResourceItem.builder()
                    .resourceType(rs.getString("resource_type"))
                    .resourceId(String.valueOf(rs.getLong("id")))
                    .resourceCode(rs.getString("resource_code"))
                    .displayName(rs.getString("display_name"))
                    .description(rs.getString("description"))
                    .status(rs.getString("status"))
                    .callCount(rs.getLong("call_count"))
                    .reviewCount(rs.getLong("review_count"))
                    .favoriteCount(rs.getLong("favorite_count"))
                    .rating(ratingVal)
                    .creatorUserId(longOrNull(rs.getObject("created_by")))
                    .publishedAt(rs.getTimestamp("update_time") != null
                            ? rs.getTimestamp("update_time").toLocalDateTime() : null)
                    .build();
        });
        List<ExploreHubData.ExploreResourceItem> out = rows.stream()
                .filter(item -> typeOk.allow(item.getResourceType()))
                .limit(lim)
                .collect(Collectors.toList());
        attachTrendingAuthors(out);
        return out;
    }

    private void attachTrendingAuthors(List<ExploreHubData.ExploreResourceItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<Long> ids = items.stream()
                .map(ExploreHubData.ExploreResourceItem::getCreatorUserId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, String> names = userDisplayNameResolver.resolveDisplayNames(ids);
        for (ExploreHubData.ExploreResourceItem item : items) {
            Long uid = item.getCreatorUserId();
            if (uid != null) {
                item.setAuthor(names.get(uid));
            }
            item.setCreatorUserId(null);
        }
    }

    @Override
    public List<SearchSuggestion> searchSuggestions(String query, Long userId) {
        if (!StringUtils.hasText(query) || query.trim().length() < 1) {
            return List.of();
        }
        GatewayUserPermissionService.CatalogTypePredicate typeOk = gatewayUserPermissionService.catalogTypePredicate(userId);
        String keyword = "%" + query.trim() + "%";
        List<SearchSuggestion> rows = jdbcTemplate.query(
                "SELECT id, resource_type, resource_code, display_name "
                        + "FROM t_resource WHERE deleted = 0 AND status = 'published' "
                        + "AND (resource_code LIKE ? OR display_name LIKE ?) "
                        + "ORDER BY update_time DESC LIMIT 50",
                new Object[]{keyword, keyword},
                (rs, i) -> {
                    String name = rs.getString("display_name");
                    return SearchSuggestion.builder()
                            .text(name)
                            .resourceType(rs.getString("resource_type"))
                            .resourceId(String.valueOf(rs.getLong("id")))
                            .highlightedText(name)
                            .build();
                });
        return rows.stream().filter(s -> typeOk.allow(s.getResourceType())).limit(10).toList();
    }

    private static void ensureSkillNotInvokable(String type) {
        if (TYPE_SKILL.equals(type)) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "技能包（resourceType=skill）不支持网关远程调用，请由 Agent 运行时加载；远程工具请使用 resourceType=mcp。");
        }
    }

    private Set<String> parseIncludes(String includeRaw) {
        if (!StringUtils.hasText(includeRaw)) {
            return Set.of();
        }
        Set<String> includes = new HashSet<>();
        for (String part : includeRaw.split(",")) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            includes.add(part.trim().toLowerCase(Locale.ROOT));
        }
        return includes;
    }

    private void attachIncludesToCatalogItems(List<ResourceCatalogItemVO> items, Set<String> includes) {
        if (items == null || items.isEmpty() || includes == null || includes.isEmpty()) {
            return;
        }
        boolean needObs = includes.contains("observability");
        boolean needQuality = includes.contains("quality");
        if (!needObs && !needQuality) {
            return;
        }
        for (ResourceCatalogItemVO item : items) {
            Long rid;
            try {
                rid = Long.valueOf(item.getResourceId());
            } catch (Exception ex) {
                continue;
            }
            Map<String, Object> quality = computeQualitySummarySafe(rid);
            if (needObs) {
                item.setObservability(observabilityView(quality));
            }
            if (needQuality) {
                item.setQuality(qualityView(quality));
            }
        }
    }

    private ResourceResolveVO attachIncludesToResolve(ResourceResolveVO resolved, Set<String> includes) {
        if (resolved == null || includes == null || includes.isEmpty()) {
            return resolved;
        }
        Long rid;
        try {
            rid = Long.valueOf(resolved.getResourceId());
        } catch (Exception ex) {
            return resolved;
        }
        if (includes.contains("tags")) {
            String rtype = StringUtils.hasText(resolved.getResourceType())
                    ? resolved.getResourceType().trim().toLowerCase(Locale.ROOT)
                    : TYPE_AGENT;
            List<String> tags = jdbcTemplate.query(
                    "SELECT t.name FROM t_resource_tag_rel rr INNER JOIN t_tag t ON t.id = rr.tag_id "
                            + "WHERE rr.resource_id = ? AND rr.resource_type = ? ORDER BY t.name",
                    (rs, i) -> rs.getString(1),
                    rid,
                    rtype);
            resolved.setTags(tags == null ? List.of() : tags);
        }
        if (includes.contains("observability") || includes.contains("quality")) {
            Map<String, Object> quality = computeQualitySummarySafe(rid);
            if (includes.contains("observability")) {
                resolved.setObservability(observabilityView(quality));
            }
            if (includes.contains("quality")) {
                resolved.setQuality(qualityView(quality));
            }
        }
        return resolved;
    }

    private Map<String, Object> computeQualitySummarySafe(Long resourceId) {
        try {
            return computeQualitySummary(resourceId);
        } catch (Exception ex) {
            log.warn("computeQualitySummary failed for resourceId={}", resourceId, ex);
            return defaultQualitySummary();
        }
    }

    private static Map<String, Object> defaultQualitySummary() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("healthStatus", "unknown");
        m.put("circuitState", "unknown");
        m.put("qualityScore", 0);
        m.put("successRate", 1.0D);
        m.put("avgLatencyMs", 0.0D);
        m.put("callCount7d", 0L);
        m.put("degradationCode", "");
        m.put("degradationHint", "");
        return m;
    }

    private static Map<String, Object> observabilityView(Map<String, Object> quality) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("healthStatus", nzStr(quality.get("healthStatus")));
        m.put("circuitState", nzStr(quality.get("circuitState")));
        m.put("degradationCode", nzStr(quality.get("degradationCode")));
        m.put("degradationHint", nzStr(quality.get("degradationHint")));
        return m;
    }

    private static Map<String, Object> qualityView(Map<String, Object> quality) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("qualityScore", quality.get("qualityScore") != null ? quality.get("qualityScore") : 0);
        m.put("successRate", quality.get("successRate") != null ? quality.get("successRate") : 1.0D);
        m.put("avgLatencyMs", quality.get("avgLatencyMs") != null ? quality.get("avgLatencyMs") : 0.0D);
        m.put("callCount7d", quality.get("callCount7d") != null ? quality.get("callCount7d") : 0L);
        return m;
    }

    private static String nzStr(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private Map<String, Object> computeQualitySummary(Long resourceId) {
        Map<String, Object> calls = queryOne("""
                SELECT
                    COUNT(1) AS total_calls,
                    SUM(CASE WHEN status = 'success' THEN 1 ELSE 0 END) AS success_calls,
                    ROUND(AVG(latency_ms), 2) AS avg_latency
                FROM t_call_log
                WHERE agent_id = ? AND create_time >= DATE_SUB(NOW(), INTERVAL 7 DAY)
                """, String.valueOf(resourceId));
        long total = longValue(calls == null ? null : calls.get("total_calls"));
        long success = longValue(calls == null ? null : calls.get("success_calls"));
        double avgLatency = 0D;
        if (calls != null && calls.get("avg_latency") != null) {
            try {
                avgLatency = Double.parseDouble(String.valueOf(calls.get("avg_latency")));
            } catch (NumberFormatException ignored) {
                avgLatency = 0D;
            }
        }
        double successRate = total <= 0 ? 1D : ((double) success / (double) total);
        double latencyFactor = Math.max(0D, 1D - (avgLatency / 8000D));
        int qualityScore = (int) Math.round(successRate * 70D + latencyFactor * 30D);
        qualityScore = Math.max(0, Math.min(100, qualityScore));
        String healthStatus = valueOf(queryOne("SELECT health_status FROM t_resource_health_config WHERE resource_id = ? LIMIT 1", resourceId) == null
                ? null
                : queryOne("SELECT health_status FROM t_resource_health_config WHERE resource_id = ? LIMIT 1", resourceId).get("health_status"));
        String circuitState = valueOf(queryOne("SELECT current_state FROM t_resource_circuit_breaker WHERE resource_id = ? LIMIT 1", resourceId) == null
                ? null
                : queryOne("SELECT current_state FROM t_resource_circuit_breaker WHERE resource_id = ? LIMIT 1", resourceId).get("current_state"));
        String degradationCode = null;
        String degradationHint = null;
        if ("OPEN".equalsIgnoreCase(circuitState)) {
            degradationCode = "CIRCUIT_OPEN";
            degradationHint = "当前资源暂时不可用，请稍后重试";
        } else if ("degraded".equalsIgnoreCase(healthStatus)) {
            degradationCode = "HEALTH_DEGRADED";
            degradationHint = "当前资源响应不稳定，建议稍后再试";
        }
        return new LinkedHashMap<>(Map.of(
                "healthStatus", healthStatus == null ? "unknown" : healthStatus,
                "circuitState", circuitState == null ? "unknown" : circuitState,
                "qualityScore", qualityScore,
                "successRate", successRate,
                "avgLatencyMs", avgLatency,
                "callCount7d", total,
                "degradationCode", degradationCode == null ? "" : degradationCode,
                "degradationHint", degradationHint == null ? "" : degradationHint));
    }
}
