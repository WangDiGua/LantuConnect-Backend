package com.lantu.connect.gateway.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.dashboard.dto.ExploreHubData;
import com.lantu.connect.gateway.dto.InvokeRequest;
import com.lantu.connect.gateway.dto.InvokeResponse;
import com.lantu.connect.gateway.dto.ResourceCatalogItemVO;
import com.lantu.connect.gateway.dto.ResourceCatalogQueryRequest;
import com.lantu.connect.gateway.dto.ResourceResolveRequest;
import com.lantu.connect.gateway.dto.ResourceResolveVO;
import com.lantu.connect.gateway.dto.ResourceStatsVO;
import com.lantu.connect.gateway.dto.SearchSuggestion;
import com.lantu.connect.gateway.protocol.ProtocolInvokeContext;
import com.lantu.connect.gateway.protocol.ProtocolInvokeResult;
import com.lantu.connect.gateway.protocol.ProtocolInvokerRegistry;
import com.lantu.connect.gateway.security.AppLaunchTokenService;
import com.lantu.connect.gateway.security.ApiKeyScopeService;
import com.lantu.connect.gateway.security.GatewayGovernanceService;
import com.lantu.connect.gateway.security.GatewayUserPermissionService;
import com.lantu.connect.gateway.security.ResourceInvokeGrantService;
import com.lantu.connect.gateway.service.UnifiedGatewayService;
import com.lantu.connect.monitoring.entity.CallLog;
import com.lantu.connect.monitoring.entity.TraceSpan;
import com.lantu.connect.monitoring.mapper.CallLogMapper;
import com.lantu.connect.monitoring.mapper.TraceSpanMapper;
import com.lantu.connect.useractivity.entity.UsageRecord;
import com.lantu.connect.useractivity.mapper.UsageRecordMapper;
import com.lantu.connect.usermgmt.entity.ApiKey;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

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
    private final AppLaunchTokenService appLaunchTokenService;
    private final GatewayGovernanceService gatewayGovernanceService;
    private final ProtocolInvokerRegistry protocolInvokerRegistry;

    /**
     * 统一资源目录查询：固定从新模型主表检索，再叠加用户权限和 API Key scope 裁剪。
     */
    @Override
    public PageResult<ResourceCatalogItemVO> catalog(ResourceCatalogQueryRequest request, ApiKey apiKey, Long userId) {
        if (userId == null && apiKey == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "目录查询需要登录态或 API Key");
        }
        String type = normalizeType(request.getResourceType());
        String status = StringUtils.hasText(request.getStatus()) ? request.getStatus().trim() : null;
        String keyword = StringUtils.hasText(request.getKeyword()) ? request.getKeyword().trim() : null;
        int page = request.getPage() == null ? 1 : Math.max(1, request.getPage());
        int pageSize = request.getPageSize() == null ? 20 : Math.min(100, Math.max(1, request.getPageSize()));

        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT r.id, r.resource_type, r.resource_code, r.display_name, r.description, r.status, r.source_type, r.update_time, r.created_by, se.skill_type
                FROM t_resource r
                LEFT JOIN t_resource_skill_ext se ON se.resource_id = r.id
                WHERE r.deleted = 0
                """);
        if (StringUtils.hasText(type)) {
            if (TYPE_MCP.equals(type)) {
                sql.append(" AND (r.resource_type = 'mcp' OR (r.resource_type = 'skill' AND se.skill_type = 'mcp')) ");
            } else if (TYPE_SKILL.equals(type)) {
                sql.append(" AND r.resource_type = 'skill' AND (se.skill_type IS NULL OR se.skill_type <> 'mcp') ");
            } else {
                sql.append(" AND r.resource_type = ? ");
                args.add(type);
            }
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
                String rawType = valueOf(row.get("resource_type"));
                String skillType = valueOf(row.get("skill_type"));
                String rType = normalizeCatalogType(rawType, skillType);
                String rId = valueOf(row.get("id"));
                if (!gatewayUserPermissionService.canAccessType(userId, rType)) {
                    continue;
                }
                if (apiKey != null && !apiKeyScopeService.canCatalog(apiKey, rType, rId)) {
                    continue;
                }
                if (apiKey != null && !resourceInvokeGrantService.canCatalog(apiKey, rType, parseId(rId), userId)) {
                    continue;
                }
                if (filteredCount >= from && filteredCount < to) {
                    paged.add(ResourceCatalogItemVO.builder()
                            .resourceType(rType)
                            .resourceId(rId)
                            .resourceCode(valueOf(row.get("resource_code")))
                            .displayName(valueOf(row.get("display_name")))
                            .description(valueOf(row.get("description")))
                            .status(valueOf(row.get("status")))
                            .sourceType(valueOf(row.get("source_type")))
                            .updateTime(toDateTime(row.get("update_time")))
                            .build());
                }
                filteredCount++;
            }
            dbOffset += rows.size();
            if (rows.size() < batchSize) {
                break;
            }
        }
        return PageResult.of(paged, filteredCount, page, pageSize);
    }

    /**
     * 统一资源解析入口：用于目录项二次解析，返回可调用端点与协议元信息。
     */
    @Override
    public ResourceResolveVO resolve(ResourceResolveRequest request, ApiKey apiKey, Long userId) {
        return getByTypeAndId(request.getResourceType(), request.getResourceId(), request.getVersion(), apiKey, userId, "resolve");
    }

    /**
     * 按 type/id 解析资源，统一执行 RBAC 与 scope 双层鉴权。
     */
    @Override
    public ResourceResolveVO getByTypeAndId(String resourceType, String resourceId, ApiKey apiKey, Long userId) {
        return getByTypeAndId(resourceType, resourceId, null, apiKey, userId, "resolve");
    }

    private ResourceResolveVO getByTypeAndId(String resourceType, String resourceId, String requestedVersion, ApiKey apiKey, Long userId, String action) {
        if (userId == null && apiKey == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "资源解析需要登录态或 API Key");
        }
        String type = requireType(resourceType);
        Long id = parseId(resourceId);
        if (TYPE_APP.equals(type)) {
            if (userId == null) {
                throw new BusinessException(ResultCode.UNAUTHORIZED, "应用访问需要登录态");
            }
            if (apiKey == null) {
                throw new BusinessException(ResultCode.UNAUTHORIZED, "应用访问必须绑定并提供 X-Api-Key");
            }
            ensureAppKeyOwnedByUser(apiKey, userId);
        }
        if (userId != null) {
            gatewayUserPermissionService.ensureAccess(userId, type);
        }
        if (apiKey != null) {
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
            return issueAppLaunchTicket(merged, apiKey, userId, action);
        }
        return merged;
    }

    /**
     * 统一调用网关：协议路由 + 熔断治理 + 观测日志写入。
     */
    @Override
    public InvokeResponse invoke(Long userId, String traceId, String ip, InvokeRequest request, ApiKey apiKey) {
        if (apiKey == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "调用网关必须提供 X-Api-Key");
        }
        String reqId = UUID.randomUUID().toString();
        String type = requireType(request.getResourceType());
        Long id = parseId(request.getResourceId());
        ResourceResolveVO resolved = getByTypeAndId(type, String.valueOf(id), request.getVersion(), apiKey, userId, "invoke");
        ensurePublishedForInvoke(resolved);
        ensureNotCircuitOpen(type, resolved.getResourceCode());
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
        GatewayGovernanceService.InvokeGovernanceLease governanceLease = null;
        try {
            governanceLease = gatewayGovernanceService.applyPreInvoke(userId, apiKey, type, id, 1);
            ProtocolInvokeContext protoCtx = ProtocolInvokeContext.of(
                    apiKey.getId(), id, userId);
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
            statusCode = 500;
            respBody = "";
            latencyMs = 0L;
            status = "error";
            errMsg = e.getMessage();
        } finally {
            gatewayGovernanceService.release(governanceLease);
        }

        boolean invokeOk = "success".equals(status);
        CallLog log = new CallLog();
        log.setId(reqId);
        log.setTraceId(traceId);
        log.setAgentId(String.valueOf(id));
        log.setAgentName(resolved.getResourceCode());
        log.setUserId(userId == null ? "0" : String.valueOf(userId));
        log.setMethod("POST /invoke");
        log.setStatus(status);
        log.setStatusCode(statusCode);
        log.setLatencyMs((int) Math.max(0L, latencyMs));
        log.setInputTokens(0);
        log.setOutputTokens(0);
        log.setCost(BigDecimal.ZERO);
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
            recordCircuitResult(type, resolved.getResourceCode(), invokeOk);
            callLogMapper.insert(log);
            traceSpanMapper.insert(span);
            UsageRecord usageRecord = buildUsageRecord(userId, type, resolved, request, finalStatus, finalLatencyMs);
            if (usageRecord != null) {
                usageRecordMapper.insert(usageRecord);
            }
            apiKeyScopeService.markUsed(apiKey);
        });

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
        usage.setAction("invoke");
        usage.setAgentName(resolved.getResourceCode());
        usage.setDisplayName(StringUtils.hasText(resolved.getDisplayName()) ? resolved.getDisplayName() : resolved.getResourceCode());
        usage.setInputPreview(safePreview(request == null ? null : request.getPayload(), 300));
        usage.setOutputPreview(null);
        usage.setTokenCost(0);
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
        String sql;
        Object[] args;
        if (TYPE_MCP.equals(type)) {
            sql = """
                    SELECT r.id, r.resource_type, r.resource_code, r.display_name, r.description, r.status
                    FROM t_resource r
                    LEFT JOIN t_resource_skill_ext se ON se.resource_id = r.id
                    WHERE r.deleted = 0 AND r.id = ?
                      AND (r.resource_type = 'mcp' OR (r.resource_type = 'skill' AND se.skill_type = 'mcp'))
                    LIMIT 1
                    """;
            args = new Object[]{id};
        } else if (TYPE_SKILL.equals(type)) {
            sql = """
                    SELECT r.id, r.resource_type, r.resource_code, r.display_name, r.description, r.status
                    FROM t_resource r
                    LEFT JOIN t_resource_skill_ext se ON se.resource_id = r.id
                    WHERE r.deleted = 0 AND r.id = ? AND r.resource_type = 'skill'
                      AND (se.skill_type IS NULL OR se.skill_type <> 'mcp')
                    LIMIT 1
                    """;
            args = new Object[]{id};
        } else {
            sql = "SELECT id, resource_type, resource_code, display_name, description, status FROM t_resource WHERE deleted = 0 AND resource_type = ? AND id = ? LIMIT 1";
            args = new Object[]{type, id};
        }
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, args);
        if (list.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "资源不存在");
        }
        return list.get(0);
    }

    private ResourceResolveVO resolveAgent(Map<String, Object> base, String version) {
        Long id = longValue(base.get("id"));
        Map<String, Object> ext = queryOne(
                "SELECT spec_json FROM t_resource_agent_ext WHERE resource_id = ? LIMIT 1",
                id);
        Map<String, Object> spec = parseJsonMap(ext == null ? null : ext.get("spec_json"));
        String invokeType = normalizeProtocol(spec == null ? null : spec.get("protocol"), "rest");
        return ResourceResolveVO.builder()
                .resourceType(TYPE_AGENT)
                .resourceId(String.valueOf(id))
                .version(version)
                .resourceCode(valueOf(base.get("resource_code")))
                .displayName(valueOf(base.get("display_name")))
                .status(valueOf(base.get("status")))
                .invokeType(invokeType)
                .endpoint(specUrl(spec))
                .spec(spec)
                .build();
    }

    private ResourceResolveVO resolveSkill(Map<String, Object> base, String version) {
        Long id = longValue(base.get("id"));
        Map<String, Object> ext = queryOne(
                "SELECT spec_json FROM t_resource_skill_ext WHERE resource_id = ? LIMIT 1",
                id);
        Map<String, Object> spec = parseJsonMap(ext == null ? null : ext.get("spec_json"));
        String invokeType = normalizeProtocol(spec == null ? null : spec.get("protocol"), "rest");
        return ResourceResolveVO.builder()
                .resourceType(TYPE_SKILL)
                .resourceId(String.valueOf(id))
                .version(version)
                .resourceCode(valueOf(base.get("resource_code")))
                .displayName(valueOf(base.get("display_name")))
                .status(valueOf(base.get("status")))
                .invokeType(invokeType)
                .endpoint(specUrl(spec))
                .spec(spec)
                .build();
    }

    private ResourceResolveVO resolveMcp(Map<String, Object> base, String version) {
        Long id = longValue(base.get("id"));
        Map<String, Object> ext = queryOne("SELECT endpoint, protocol, auth_config FROM t_resource_mcp_ext WHERE resource_id = ? LIMIT 1", id);
        Map<String, Object> spec;
        String endpoint;
        String protocol;
        if (ext != null) {
            spec = parseJsonMap(ext.get("auth_config"));
            protocol = normalizeProtocol(ext.get("protocol"), "mcp");
            endpoint = valueOf(ext.get("endpoint"));
        } else {
            Map<String, Object> skillExt = queryOne("SELECT spec_json FROM t_resource_skill_ext WHERE resource_id = ? LIMIT 1", id);
            Map<String, Object> skillSpec = parseJsonMap(skillExt == null ? null : skillExt.get("spec_json"));
            spec = skillSpec == null ? Map.of() : new HashMap<>(skillSpec);
            protocol = normalizeProtocol(spec.get("protocol"), "mcp");
            endpoint = specUrl(spec);
        }
        return ResourceResolveVO.builder()
                .resourceType(TYPE_MCP)
                .resourceId(String.valueOf(id))
                .version(version)
                .resourceCode(valueOf(base.get("resource_code")))
                .displayName(valueOf(base.get("display_name")))
                .status(valueOf(base.get("status")))
                .invokeType(protocol)
                .endpoint(endpoint)
                .spec(spec)
                .build();
    }

    private ResourceResolveVO resolveApp(Map<String, Object> base, String version, ApiKey apiKey, Long userId, String action) {
        Long id = longValue(base.get("id"));
        Map<String, Object> ext = queryOne(
                "SELECT app_url, embed_type FROM t_resource_app_ext WHERE resource_id = ? LIMIT 1",
                id);
        Map<String, Object> spec = new HashMap<>();
        spec.put("embedType", valueOf(ext == null ? null : ext.get("embed_type")));
        return ResourceResolveVO.builder()
                .resourceType(TYPE_APP)
                .resourceId(String.valueOf(id))
                .version(version)
                .resourceCode(valueOf(base.get("resource_code")))
                .displayName(valueOf(base.get("display_name")))
                .status(valueOf(base.get("status")))
                .invokeType("redirect")
                .endpoint(valueOf(ext == null ? null : ext.get("app_url")))
                .spec(spec)
                .build();
    }

    private ResourceResolveVO issueAppLaunchTicket(ResourceResolveVO resolved, ApiKey apiKey, Long userId, String action) {
        if (resolved == null || !TYPE_APP.equalsIgnoreCase(resolved.getResourceType())) {
            return resolved;
        }
        if (apiKey == null || !StringUtils.hasText(apiKey.getId())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "应用访问必须绑定并提供 X-Api-Key");
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
            throw new BusinessException(ResultCode.UNAUTHORIZED, "应用访问缺少有效身份");
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
                "SELECT data_type, format, record_count, file_size, tags FROM t_resource_dataset_ext WHERE resource_id = ? LIMIT 1",
                id);
        Map<String, Object> spec = new HashMap<>();
        spec.put("dataType", valueOf(ext == null ? null : ext.get("data_type")));
        spec.put("format", valueOf(ext == null ? null : ext.get("format")));
        spec.put("recordCount", longValue(ext == null ? null : ext.get("record_count")));
        spec.put("fileSize", longValue(ext == null ? null : ext.get("file_size")));
        spec.put("tags", parseJsonList(ext == null ? null : ext.get("tags")));
        return ResourceResolveVO.builder()
                .resourceType(TYPE_DATASET)
                .resourceId(String.valueOf(id))
                .version(version)
                .resourceCode(valueOf(base.get("resource_code")))
                .displayName(valueOf(base.get("display_name")))
                .status(valueOf(base.get("status")))
                .invokeType("metadata")
                .endpoint(null)
                .spec(spec)
                .build();
    }

    private Map<String, Object> queryOne(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
        return rows.isEmpty() ? null : rows.get(0);
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
        if ("OPEN".equalsIgnoreCase(state)) {
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

    private void recordCircuitResult(String resourceType, String resourceCode, boolean success) {
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
                    success ? 1L : 0L,
                    success ? 0L : 1L);
            if (!success) {
                applyAutoOpen(resourceType, resourceId);
            }
            return;
        }

        String currentState = valueOf(current.get("current_state"));
        if (success) {
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
    public List<ExploreHubData.ExploreResourceItem> trending(String resourceType, Integer limit) {
        int lim = limit != null ? Math.min(50, Math.max(1, limit)) : 10;
        String type = normalizeType(resourceType);

        StringBuilder sql = new StringBuilder(
                "SELECT r.id, r.resource_type, r.resource_code, r.display_name, r.description, r.status, "
                        + "COALESCE(c.cnt, 0) AS call_count, "
                        + "COALESCE(rv.avg_rating, 0) AS rating, "
                        + "r.update_time "
                        + "FROM t_resource r "
                        + "LEFT JOIN (SELECT agent_id, COUNT(*) AS cnt FROM t_call_log "
                        + "WHERE create_time >= DATE_SUB(NOW(), INTERVAL 7 DAY) GROUP BY agent_id) c "
                        + "ON r.id = c.agent_id "
                        + "LEFT JOIN (SELECT target_id, AVG(rating) AS avg_rating FROM t_review "
                        + "WHERE deleted = 0 GROUP BY target_id) rv ON r.id = rv.target_id "
                        + "WHERE r.deleted = 0 AND r.status = 'published' ");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(type)) {
            sql.append(" AND r.resource_type = ? ");
            args.add(type);
        }
        sql.append(" ORDER BY call_count DESC LIMIT ? ");
        args.add(lim);

        return jdbcTemplate.query(sql.toString(), args.toArray(), (rs, i) ->
                ExploreHubData.ExploreResourceItem.builder()
                        .resourceType(rs.getString("resource_type"))
                        .resourceId(String.valueOf(rs.getLong("id")))
                        .resourceCode(rs.getString("resource_code"))
                        .displayName(rs.getString("display_name"))
                        .description(rs.getString("description"))
                        .status(rs.getString("status"))
                        .callCount(rs.getLong("call_count"))
                        .rating(rs.getDouble("rating"))
                        .publishedAt(rs.getTimestamp("update_time") != null
                                ? rs.getTimestamp("update_time").toLocalDateTime() : null)
                        .build());
    }

    @Override
    public List<SearchSuggestion> searchSuggestions(String query) {
        if (!StringUtils.hasText(query) || query.trim().length() < 1) {
            return List.of();
        }
        String keyword = "%" + query.trim() + "%";
        return jdbcTemplate.query(
                "SELECT id, resource_type, resource_code, display_name "
                        + "FROM t_resource WHERE deleted = 0 AND status = 'published' "
                        + "AND (resource_code LIKE ? OR display_name LIKE ?) "
                        + "ORDER BY update_time DESC LIMIT 10",
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
    }

    private static String normalizeCatalogType(String resourceType, String skillType) {
        if (TYPE_SKILL.equals(resourceType) && "mcp".equalsIgnoreCase(StringUtils.hasText(skillType) ? skillType : "")) {
            return TYPE_MCP;
        }
        return resourceType;
    }
}
