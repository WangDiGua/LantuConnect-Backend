package com.lantu.connect.gateway.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.common.config.GatewayInvokeProperties;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.gateway.dto.AggregatedCapabilityToolsVO;
import com.lantu.connect.gateway.dto.ResourceResolveVO;
import com.lantu.connect.gateway.dto.ToolDispatchRouteVO;
import com.lantu.connect.gateway.protocol.ProtocolInvokeContext;
import com.lantu.connect.gateway.protocol.ProtocolInvokeResult;
import com.lantu.connect.gateway.protocol.ProtocolInvokerRegistry;
import com.lantu.connect.gateway.security.ApiKeyScopeService;
import com.lantu.connect.monitoring.trace.TraceRecorder;
import com.lantu.connect.usermgmt.entity.ApiKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

/**
 * 绑定关系驱动的 MCP tools/list 聚合（供 invoke 展开与 {@code aggregatedCapabilityTools} 复用）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GatewayBindingExpansionService {

    private static final String TYPE_MCP = "mcp";
    private static final String STATUS_PUBLISHED = "published";
    private static final long RELATION_CACHE_TTL_MS = 30_000L;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ProtocolInvokerRegistry protocolInvokerRegistry;
    private final ApiKeyScopeService apiKeyScopeService;
    private final GatewayInvokeProperties gatewayInvokeProperties;
    private final TraceRecorder traceRecorder;
    private final ConcurrentHashMap<String, CachedIds> relationCache = new ConcurrentHashMap<>();

    public List<Long> listAgentBoundMcpIds(long agentResourceId) {
        return cachedRelationIds("agent", agentResourceId,
                """
                        SELECT to_resource_id FROM t_resource_relation
                        WHERE from_resource_id = ? AND relation_type = 'agent_depends_mcp'
                        ORDER BY id ASC
                        """);
    }

    public List<Long> listSkillBoundMcpIds(long skillResourceId) {
        return cachedRelationIds("skill", skillResourceId,
                """
                        SELECT to_resource_id FROM t_resource_relation
                        WHERE from_resource_id = ? AND relation_type = 'skill_depends_mcp'
                        ORDER BY id ASC
                        """);
    }

    /**
     * 对给定 MCP id 列表依次 tools/list 聚合（顺序保留）；跳过的 MCP 写入 warnings。
     *
     * @param entry 非空时写入返回 VO（invoke 展开）；聚合类接口可传 null
     */
    public AggregatedCapabilityToolsVO aggregateMcpTools(List<Long> mcpIds,
                                                      Map<String, String> entry,
                                                      Long userId,
                                                      String traceId,
                                                      ApiKey apiKey,
                                                      McpInvokeResolveFetcher fetcher) {
        List<Map<String, Object>> openAiTools = new ArrayList<>();
        List<ToolDispatchRouteVO> routes = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Set<String> usedFunctionNames = new HashSet<>();
        String rpcTrace = traceRecorder.normalizeTraceId(traceId);
        boolean aggregateTruncated = false;
        int mcpListSuccessCount = 0;

        if (mcpIds == null || mcpIds.isEmpty()) {
            return AggregatedCapabilityToolsVO.builder()
                    .entry(entry)
                    .openAiTools(openAiTools)
                    .routes(routes)
                    .warnings(warnings)
                    .mcpQueriedCount(0)
                    .toolFunctionCount(0)
                    .aggregateTruncated(false)
                    .build();
        }

        TraceRecorder.TraceSpanScope aggregateSpan = traceRecorder.openSpan(
                rpcTrace,
                "binding.aggregate-tools",
                "gateway-binding-expansion",
                Map.of("spanKind", "internal"));
        try (aggregateSpan) {
            if (entry != null && !entry.isEmpty()) {
                aggregateSpan.tag("entryResourceType", entry.get("resourceType"));
                aggregateSpan.tag("entryResourceId", entry.get("resourceId"));
            }

            List<Long> effectiveIds = new ArrayList<>();
            for (Long id : mcpIds) {
                if (id != null && id > 0) {
                    effectiveIds.add(id);
                }
            }
            int maxMcps = gatewayInvokeProperties.getCapabilities().getMaxMcpsPerAggregate();
            if (maxMcps > 0 && effectiveIds.size() > maxMcps) {
                warnings.add("aggregate truncated: maxMcpsPerAggregate=" + maxMcps + " (remaining MCP ids skipped)");
                aggregateTruncated = true;
                effectiveIds = new ArrayList<>(effectiveIds.subList(0, maxMcps));
            }
            int maxTools = gatewayInvokeProperties.getCapabilities().getMaxToolsPerResponse();
            aggregateSpan.tag("requestedMcpCount", effectiveIds.size());

            mcpLoop:
            for (Long mcpId : effectiveIds) {
            try {
                apiKeyScopeService.ensureInvokeAllowed(apiKey, TYPE_MCP, String.valueOf(mcpId));
            } catch (BusinessException ex) {
                warnings.add("skip mcp " + mcpId + ": " + ex.getMessage());
                continue;
            }
            ResourceResolveVO mcpResolved;
            try {
                mcpResolved = fetcher.resolve(mcpId);
            } catch (BusinessException ex) {
                warnings.add("resolve mcp " + mcpId + ": " + ex.getMessage());
                continue;
            }
            if (!ensurePublishedForInvoke(mcpResolved, warnings, mcpId)) {
                continue;
            }
            if (!StringUtils.hasText(mcpResolved.getEndpoint())) {
                warnings.add("skip mcp " + mcpId + ": missing endpoint");
                continue;
            }
            String protocol = normalizeProtocol(mcpResolved.getInvokeType(), "http");
            if (!"mcp".equalsIgnoreCase(protocol)) {
                warnings.add("skip mcp " + mcpId + ": invokeType is not mcp");
                continue;
            }
            try {
                TraceRecorder.TraceSpanScope toolsListSpan = traceRecorder.openSpan(
                        null,
                        "mcp.tools/list",
                        "gateway-binding-expansion",
                        Map.of(
                                "resourceType", TYPE_MCP,
                                "resourceId", String.valueOf(mcpId),
                                "protocol", protocol,
                                "method", "tools/list",
                                "spanKind", "internal"));
                try (toolsListSpan) {
                ProtocolInvokeContext protoCtx = ProtocolInvokeContext.of(apiKey.getId(), mcpId, userId);
                Map<String, Object> listPayload = new LinkedHashMap<>();
                listPayload.put("method", "tools/list");
                ProtocolInvokeResult resp = protocolInvokerRegistry.invoke(
                        protocol,
                        mcpResolved.getEndpoint(),
                        45,
                        rpcTrace,
                        listPayload,
                        mcpResolved.getSpec(),
                        protoCtx);
                toolsListSpan.tag("statusCode", resp.statusCode());
                if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                    toolsListSpan.fail("tools/list HTTP " + resp.statusCode());
                    warnings.add("mcp " + mcpId + " tools/list HTTP " + resp.statusCode());
                    continue;
                }
                JsonNode root = objectMapper.readTree(resp.body() == null ? "{}" : resp.body());
                JsonNode toolsNode = root.path("result").path("tools");
                if (!toolsNode.isArray()) {
                    toolsListSpan.fail("unexpected result.tools");
                    warnings.add("mcp " + mcpId + " tools/list: unexpected result.tools");
                    continue;
                }
                mcpListSuccessCount++;
                for (JsonNode tool : toolsNode) {
                    String origName = tool.path("name").asText("");
                    if (!StringUtils.hasText(origName)) {
                        continue;
                    }
                    String unified = allocateUnifiedToolName(mcpId, origName, usedFunctionNames);
                    String description = tool.path("description").asText("");
                    JsonNode schemaNode = tool.get("inputSchema");
                    if (schemaNode == null || schemaNode.isNull()) {
                        schemaNode = objectMapper.readTree("{\"type\":\"object\",\"properties\":{}}");
                    }
                    Map<String, Object> parameters = objectMapper.convertValue(schemaNode, new TypeReference<>() { });
                    Map<String, Object> fn = new LinkedHashMap<>();
                    fn.put("name", unified);
                    fn.put("description", description);
                    fn.put("parameters", parameters);
                    Map<String, Object> oai = new LinkedHashMap<>();
                    oai.put("type", "function");
                    oai.put("function", fn);
                    openAiTools.add(oai);
                    routes.add(ToolDispatchRouteVO.builder()
                            .unifiedFunctionName(unified)
                            .resourceType(TYPE_MCP)
                            .resourceId(String.valueOf(mcpId))
                            .upstreamToolName(origName)
                            .build());
                    if (maxTools > 0 && openAiTools.size() >= maxTools) {
                        warnings.add("aggregate truncated: maxToolsPerResponse=" + maxTools + " (remaining tools skipped)");
                        aggregateTruncated = true;
                        break mcpLoop;
                    }
                }
                toolsListSpan.tag("toolCount", toolsNode.size());
                toolsListSpan.success();
                }
            } catch (Exception ex) {
                warnings.add("mcp " + mcpId + " tools/list failed: " + ex.getMessage());
            }
            }

            if (entry != null && !entry.isEmpty()) {
                log.info("gateway.bindingExpansion aggregate mcpCount={} tools={} warnings={} entryType={} entryId={}",
                        effectiveIds.size(), openAiTools.size(), warnings.size(),
                        entry.get("resourceType"), entry.get("resourceId"));
            }
            aggregateSpan.tag("toolFunctionCount", openAiTools.size());
            aggregateSpan.success();

            return AggregatedCapabilityToolsVO.builder()
                    .entry(entry)
                    .openAiTools(openAiTools)
                    .routes(routes)
                    .warnings(warnings)
                    .mcpQueriedCount(mcpListSuccessCount)
                    .toolFunctionCount(openAiTools.size())
                    .aggregateTruncated(aggregateTruncated)
                    .build();
        }
    }

    private List<Long> cachedRelationIds(String kind, long resourceId, String sql) {
        String key = kind + ":" + resourceId;
        CachedIds cached = relationCache.get(key);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAt > now) {
            return cached.ids;
        }
        List<Long> ids = List.copyOf(jdbcTemplate.query(sql, (rs, i) -> rs.getLong(1), resourceId));
        relationCache.put(key, new CachedIds(ids, now + RELATION_CACHE_TTL_MS));
        return ids;
    }

    private static boolean ensurePublishedForInvoke(ResourceResolveVO resolved, List<String> warnings, long mcpId) {
        if (resolved == null) {
            warnings.add("resolve mcp " + mcpId + ": missing resolve");
            return false;
        }
        if (!STATUS_PUBLISHED.equalsIgnoreCase(resolved.getStatus())) {
            warnings.add("skip mcp " + mcpId + ": not published");
            return false;
        }
        return true;
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

    private static String allocateUnifiedToolName(long mcpId, String originalToolName, Set<String> used) {
        String base = "lantu_mcp_" + mcpId + "_" + safeToolNameSegment(originalToolName);
        if (used.add(base)) {
            return base;
        }
        for (int i = 2; i < 10_000; i++) {
            String candidate = base + "_" + i;
            if (used.add(candidate)) {
                return candidate;
            }
        }
        return base + "_x" + System.nanoTime();
    }

    private static String safeToolNameSegment(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "tool";
        }
        return raw.trim().replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private record CachedIds(List<Long> ids, long expiresAt) {
    }
}
