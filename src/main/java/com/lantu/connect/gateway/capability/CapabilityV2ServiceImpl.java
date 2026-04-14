package com.lantu.connect.gateway.capability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.gateway.capability.dto.CapabilityCreateRequest;
import com.lantu.connect.gateway.capability.dto.CapabilityDetailVO;
import com.lantu.connect.gateway.capability.dto.CapabilityImportRequest;
import com.lantu.connect.gateway.capability.dto.CapabilityImportSuggestionVO;
import com.lantu.connect.gateway.capability.dto.CapabilityInvokeRequest;
import com.lantu.connect.gateway.capability.dto.CapabilityInvokeResultVO;
import com.lantu.connect.gateway.capability.dto.CapabilityResolveRequest;
import com.lantu.connect.gateway.capability.dto.CapabilityResolveResultVO;
import com.lantu.connect.gateway.capability.dto.CapabilitySummaryVO;
import com.lantu.connect.gateway.capability.dto.CapabilityToolItemVO;
import com.lantu.connect.gateway.capability.dto.CapabilityToolSessionRequest;
import com.lantu.connect.gateway.capability.dto.CapabilityToolSessionVO;
import com.lantu.connect.gateway.dto.AggregatedCapabilityToolsVO;
import com.lantu.connect.gateway.dto.InvokeRequest;
import com.lantu.connect.gateway.dto.InvokeResponse;
import com.lantu.connect.gateway.dto.ResourceCatalogItemVO;
import com.lantu.connect.gateway.dto.ResourceCatalogQueryRequest;
import com.lantu.connect.gateway.dto.ResourceManageVO;
import com.lantu.connect.gateway.dto.ResourceResolveRequest;
import com.lantu.connect.gateway.dto.ResourceResolveVO;
import com.lantu.connect.gateway.dto.ResourceUpsertRequest;
import com.lantu.connect.gateway.dto.ToolDispatchRouteVO;
import com.lantu.connect.gateway.security.ApiKeyScopeService;
import com.lantu.connect.gateway.service.ResourceRegistryService;
import com.lantu.connect.gateway.service.UnifiedGatewayService;
import com.lantu.connect.usermgmt.entity.ApiKey;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CapabilityV2ServiceImpl implements CapabilityV2Service {

    private static final String TYPE_SKILL = "skill";
    private static final String TYPE_MCP = "mcp";
    private static final String TYPE_AGENT = "agent";
    private static final String INCLUDE_DEFAULT = "closure,bindings";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final CapabilityImportDetector capabilityImportDetector;
    private final UnifiedGatewayService unifiedGatewayService;
    private final ResourceRegistryService resourceRegistryService;
    @SuppressWarnings("unused")
    private final ApiKeyScopeService apiKeyScopeService;

    @Override
    public CapabilityImportSuggestionVO detect(CapabilityImportRequest request) {
        return capabilityImportDetector.detect(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CapabilityDetailVO create(Long operatorUserId, CapabilityCreateRequest request) {
        if (operatorUserId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "注册能力需要登录用户");
        }
        ResourceManageVO created = resourceRegistryService.create(operatorUserId, toUpsertRequest(request));
        if (Boolean.TRUE.equals(request.getSubmitForAudit())) {
            created = resourceRegistryService.submitForAudit(operatorUserId, created.getId());
        }
        return fromManage(created);
    }

    @Override
    public PageResult<CapabilitySummaryVO> list(ResourceCatalogQueryRequest request, ApiKey apiKey, Long userId) {
        PageResult<ResourceCatalogItemVO> page = unifiedGatewayService.catalog(request, apiKey, userId);
        List<CapabilitySummaryVO> rows = page.getList() == null
                ? List.of()
                : page.getList().stream().map(this::fromCatalogItem).toList();
        return PageResult.of(rows, page.getTotal(), page.getPage(), page.getPageSize());
    }

    @Override
    public CapabilityDetailVO getById(Long capabilityId, String include, ApiKey apiKey, Long userId) {
        CapabilityRef ref = requireCapabilityRef(capabilityId);
        ResourceResolveVO resolved = unifiedGatewayService.getByTypeAndId(
                ref.resourceType(),
                String.valueOf(capabilityId),
                StringUtils.hasText(include) ? include.trim() : INCLUDE_DEFAULT,
                apiKey,
                userId);
        return fromResolved(resolved);
    }

    @Override
    public CapabilityResolveResultVO resolve(Long capabilityId,
                                             CapabilityResolveRequest request,
                                             ApiKey apiKey,
                                             Long userId) {
        CapabilityRef ref = requireCapabilityRef(capabilityId);
        ResourceResolveRequest delegate = new ResourceResolveRequest();
        delegate.setResourceType(ref.resourceType());
        delegate.setResourceId(String.valueOf(capabilityId));
        delegate.setVersion(request == null ? null : request.getVersion());
        delegate.setInclude(StringUtils.hasText(request == null ? null : request.getInclude())
                ? request.getInclude().trim()
                : INCLUDE_DEFAULT);
        ResourceResolveVO resolved = unifiedGatewayService.resolve(delegate, apiKey, userId);
        return CapabilityResolveResultVO.builder()
                .capability(fromResolved(resolved))
                .resolved(resolved)
                .suggestedPayload(suggestedPayloadForResolved(resolved))
                .build();
    }

    @Override
    public CapabilityInvokeResultVO invoke(Long capabilityId,
                                           Long userId,
                                           String traceId,
                                           String ip,
                                           CapabilityInvokeRequest request,
                                           ApiKey apiKey) {
        CapabilityRef ref = requireCapabilityRef(capabilityId);
        if (TYPE_SKILL.equals(ref.resourceType())) {
            return invokeSkill(capabilityId, userId, traceId, ip, request, apiKey);
        }

        InvokeRequest delegate = new InvokeRequest();
        delegate.setResourceType(ref.resourceType());
        delegate.setResourceId(String.valueOf(capabilityId));
        delegate.setVersion(request == null ? null : request.getVersion());
        delegate.setTimeoutSec(request == null || request.getTimeoutSec() == null ? 30 : request.getTimeoutSec());
        delegate.setPayload(copyMap(request == null ? null : request.getPayload()));
        InvokeResponse response = unifiedGatewayService.invoke(userId, traceId, ip, delegate, apiKey);
        ResourceResolveVO resolved = unifiedGatewayService.getByTypeAndId(ref.resourceType(), String.valueOf(capabilityId), INCLUDE_DEFAULT, apiKey, userId);
        return CapabilityInvokeResultVO.builder()
                .capability(fromResolved(resolved))
                .response(response)
                .build();
    }

    @Override
    public CapabilityToolSessionVO toolSession(Long capabilityId,
                                               Long userId,
                                               String traceId,
                                               String ip,
                                               CapabilityToolSessionRequest request,
                                               ApiKey apiKey) {
        CapabilityRef ref = requireCapabilityRef(capabilityId);
        AggregatedCapabilityToolsVO aggregated = unifiedGatewayService.aggregatedCapabilityTools(
                userId,
                traceId,
                apiKey,
                ref.resourceType(),
                String.valueOf(capabilityId));
        List<CapabilityToolItemVO> tools = normalizeToolItems(aggregated.getOpenAiTools());
        String action = normalizeAction(request == null ? null : request.getAction());
        if (!"call_tool".equals(action)) {
            return CapabilityToolSessionVO.builder()
                    .capabilityId(capabilityId)
                    .capabilityType(ref.resourceType())
                    .action(action)
                    .tools(tools)
                    .routes(defaultList(aggregated.getRoutes()))
                    .warnings(defaultList(aggregated.getWarnings()))
                    .build();
        }

        String wantedTool = firstText(request == null ? null : request.getToolName(), null);
        ToolDispatchRouteVO route = defaultList(aggregated.getRoutes()).stream()
                .filter(item -> wantedTool != null && (wantedTool.equals(item.getUnifiedFunctionName())
                        || wantedTool.equals(item.getUpstreamToolName())))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.PARAM_ERROR, "未找到可调用的工具路由: " + wantedTool));

        InvokeRequest delegate = new InvokeRequest();
        delegate.setResourceType(route.getResourceType());
        delegate.setResourceId(route.getResourceId());
        delegate.setVersion(request == null ? null : request.getVersion());
        delegate.setTimeoutSec(request == null || request.getTimeoutSec() == null ? 45 : request.getTimeoutSec());
        delegate.setPayload(Map.of(
                "method", "tools/call",
                "params", Map.of(
                        "name", route.getUpstreamToolName(),
                        "arguments", copyMap(request == null ? null : request.getArguments()))
        ));
        InvokeResponse response = unifiedGatewayService.invoke(userId, traceId, ip, delegate, apiKey);
        return CapabilityToolSessionVO.builder()
                .capabilityId(capabilityId)
                .capabilityType(ref.resourceType())
                .action(action)
                .tools(tools)
                .routes(defaultList(aggregated.getRoutes()))
                .warnings(defaultList(aggregated.getWarnings()))
                .toolCallResponse(response)
                .build();
    }

    private CapabilityInvokeResultVO invokeSkill(Long capabilityId,
                                                 Long userId,
                                                 String traceId,
                                                 String ip,
                                                 CapabilityInvokeRequest request,
                                                 ApiKey apiKey) {
        ResourceResolveVO resolved = unifiedGatewayService.getByTypeAndId(
                TYPE_SKILL,
                String.valueOf(capabilityId),
                "closure",
                apiKey,
                userId);
        Map<String, Object> spec = copyMap(resolved.getSpec());
        Map<String, Object> manifest = nestedMap(spec, "manifest");
        String runtimeMode = firstText(
                mapText(spec.get("runtimeMode")),
                mapText(manifest.get("runtimeMode")));
        Map<String, Object> delegateDef = !nestedMap(spec, "delegate").isEmpty()
                ? nestedMap(spec, "delegate")
                : nestedMap(manifest, "delegate");
        if ("delegate_agent".equalsIgnoreCase(runtimeMode) || !delegateDef.isEmpty()) {
            String delegateType = firstText(mapText(delegateDef.get("resourceType")), TYPE_AGENT);
            String delegateId = firstText(mapText(delegateDef.get("resourceId")), null);
            if (!StringUtils.hasText(delegateId)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "技能已声明 delegate 模式，但缺少 delegate.resourceId");
            }
            InvokeRequest delegate = new InvokeRequest();
            delegate.setResourceType(delegateType);
            delegate.setResourceId(delegateId);
            delegate.setVersion(request == null ? null : request.getVersion());
            delegate.setTimeoutSec(request == null || request.getTimeoutSec() == null ? 30 : request.getTimeoutSec());
            delegate.setPayload(enrichSkillDelegatePayload(capabilityId, resolved, request == null ? null : request.getPayload()));
            InvokeResponse response = unifiedGatewayService.invoke(userId, traceId, ip, delegate, apiKey);
            return CapabilityInvokeResultVO.builder()
                    .capability(fromResolved(resolved))
                    .response(response)
                    .build();
        }

        Map<String, Object> promptBundle = new LinkedHashMap<>();
        promptBundle.put("capabilityId", capabilityId);
        promptBundle.put("capabilityType", TYPE_SKILL);
        promptBundle.put("invokeMode", "prompt_bundle");
        promptBundle.put("displayName", resolved.getDisplayName());
        promptBundle.put("resourceCode", resolved.getResourceCode());
        promptBundle.put("contextPrompt", mapText(spec.get("contextPrompt")));
        promptBundle.put("parametersSchema", copyMap(nestedMap(spec, "parametersSchema")));
        promptBundle.put("bindingClosure", resolved.getBindingClosure());
        promptBundle.put("input", copyMap(request == null ? null : request.getPayload()));
        InvokeResponse response = InvokeResponse.builder()
                .requestId(UUID.randomUUID().toString())
                .traceId(traceId)
                .resourceType(TYPE_SKILL)
                .resourceId(String.valueOf(capabilityId))
                .statusCode(200)
                .status("success")
                .latencyMs(0L)
                .body(writeJson(promptBundle))
                .build();
        return CapabilityInvokeResultVO.builder()
                .capability(fromResolved(resolved))
                .response(response)
                .build();
    }

    private Map<String, Object> enrichSkillDelegatePayload(Long capabilityId,
                                                           ResourceResolveVO resolved,
                                                           Map<String, Object> originalPayload) {
        Map<String, Object> payload = copyMap(originalPayload);
        Map<String, Object> lantu = nestedMap(payload, "_lantu");
        Map<String, Object> skillContext = new LinkedHashMap<>();
        skillContext.put("capabilityId", capabilityId);
        skillContext.put("resourceType", TYPE_SKILL);
        skillContext.put("resourceId", resolved.getResourceId());
        skillContext.put("displayName", resolved.getDisplayName());
        skillContext.put("resourceCode", resolved.getResourceCode());
        skillContext.put("contextPrompt", mapText(copyMap(resolved.getSpec()).get("contextPrompt")));
        skillContext.put("parametersSchema", nestedMap(copyMap(resolved.getSpec()), "parametersSchema"));
        if (resolved.getBindingClosure() != null) {
            skillContext.put("bindingClosure", resolved.getBindingClosure());
        }
        lantu.put("skillContext", skillContext);
        payload.put("_lantu", lantu);
        return payload;
    }

    private ResourceUpsertRequest toUpsertRequest(CapabilityCreateRequest request) {
        CapabilityImportSuggestionVO detected = capabilityImportDetector.detect(CapabilityImportRequest.builder()
                .source(request.getSource())
                .preferredType(request.getDetectedType())
                .displayName(request.getDisplayName())
                .description(request.getDescription())
                .build());
        String type = firstText(request.getDetectedType(), detected.getDetectedType());
        ResourceUpsertRequest upsert = new ResourceUpsertRequest();
        upsert.setResourceType(type);
        upsert.setDisplayName(firstText(request.getDisplayName(), detected.getDisplayName()));
        upsert.setResourceCode(firstText(request.getResourceCode(), detected.getResourceCode()));
        upsert.setDescription(firstText(request.getDescription(), detected.getDescription()));
        upsert.setSourceType(firstText(request.getSourceType(), "capability_v2"));
        upsert.setProviderId(request.getProviderId());
        upsert.setEnabled(Boolean.TRUE);
        upsert.setIsPublic(Boolean.TRUE);
        upsert.setHidden(Boolean.FALSE);

        Map<String, Object> defaults = mergeMaps(detected.getDefaults(), request.getDefaults());
        Map<String, Object> capabilities = mergeMaps(detected.getCapabilities(), request.getCapabilities());
        Map<String, Object> authRefs = copyMap(request.getAuthRefs());
        Map<String, Object> inputSchema = !copyMap(request.getInputSchema()).isEmpty()
                ? copyMap(request.getInputSchema())
                : copyMap(detected.getInputSchema());

        if (TYPE_AGENT.equals(type)) {
            upsert.setAgentType(firstText(mapText(capabilities.get("agentType")), "http_api"));
            upsert.setMode(firstText(mapText(capabilities.get("mode")), "TOOL"));
            upsert.setRegistrationProtocol(firstText(mapText(capabilities.get("registrationProtocol")), "openai_compatible"));
            upsert.setUpstreamEndpoint(firstText(mapText(defaults.get("endpoint")), request.getSource()));
            upsert.setModelAlias(firstText(mapText(defaults.get("modelAlias")), "default-model"));
            upsert.setCredentialRef(mapText(authRefs.get("credentialRef")));
            upsert.setUpstreamAgentId(mapText(defaults.get("upstreamAgentId")));
            upsert.setTransformProfile(mapText(defaults.get("transformProfile")));
            upsert.setSystemPrompt(mapText(defaults.get("systemPrompt")));
            upsert.setSpec(copyMap(inputSchema));
            return upsert;
        }

        if (TYPE_MCP.equals(type)) {
            upsert.setEndpoint(firstText(mapText(defaults.get("endpoint")), request.getSource()));
            upsert.setProtocol(firstText(mapText(defaults.get("protocol")), inferMcpProtocol(request.getRuntimeMode(), request.getSource())));
            upsert.setAuthType(firstText(mapText(defaults.get("authType")), "none"));
            upsert.setAuthConfig(!authRefs.isEmpty() ? authRefs : nestedMap(defaults, "authConfig"));
            upsert.setServiceDetailMd(mapText(defaults.get("serviceDetailMd")));
            return upsert;
        }

        upsert.setSkillType("context_v1");
        upsert.setExecutionMode("context");
        upsert.setContextPrompt(firstText(mapText(defaults.get("contextPrompt")), request.getSource()));
        upsert.setParametersSchema(inputSchema);
        upsert.setRelatedMcpResourceIds(request.getBindings());
        upsert.setServiceDetailMd(mapText(defaults.get("serviceDetailMd")));
        Map<String, Object> manifest = new LinkedHashMap<>();
        if (StringUtils.hasText(request.getRuntimeMode())) {
            manifest.put("runtimeMode", request.getRuntimeMode().trim());
        }
        Map<String, Object> capabilityDelegate = nestedMap(capabilities, "delegate");
        if (!capabilityDelegate.isEmpty()) {
            manifest.put("delegate", capabilityDelegate);
        }
        Map<String, Object> defaultDelegate = nestedMap(defaults, "delegate");
        if (!defaultDelegate.isEmpty()) {
            manifest.putIfAbsent("delegate", defaultDelegate);
        }
        if (!copyMap(capabilities).isEmpty()) {
            manifest.put("capabilities", copyMap(capabilities));
        }
        if (!manifest.isEmpty()) {
            upsert.setManifest(manifest);
        }
        return upsert;
    }

    private CapabilitySummaryVO fromCatalogItem(ResourceCatalogItemVO item) {
        return CapabilitySummaryVO.builder()
                .capabilityId(parseLong(item.getResourceId()))
                .capabilityType(item.getResourceType())
                .displayName(item.getDisplayName())
                .resourceCode(item.getResourceCode())
                .description(item.getDescription())
                .status(item.getStatus())
                .runtimeMode(inferRuntimeMode(item.getResourceType(), item.getExecutionMode(), null, Map.of()))
                .invokeMode(inferInvokeMode(item.getResourceType(), item.getExecutionMode(), Map.of()))
                .callCount(item.getCallCount())
                .viewCount(item.getViewCount())
                .ratingAvg(item.getRatingAvg())
                .reviewCount(item.getReviewCount())
                .tags(defaultList(item.getTags()))
                .build();
    }

    private CapabilityDetailVO fromManage(ResourceManageVO item) {
        Map<String, Object> capabilities = new LinkedHashMap<>();
        Map<String, Object> defaults = new LinkedHashMap<>();
        Map<String, Object> inputSchema = new LinkedHashMap<>();
        if (TYPE_AGENT.equals(item.getResourceType())) {
            capabilities.put("registrationProtocol", item.getRegistrationProtocol());
            capabilities.put("agentType", item.getAgentType());
            capabilities.put("mode", item.getMode());
            defaults.put("endpoint", item.getUpstreamEndpoint());
            defaults.put("modelAlias", item.getModelAlias());
            defaults.put("credentialRef", item.getCredentialRef());
            inputSchema.putAll(copyMap(item.getSpec()));
        } else if (TYPE_MCP.equals(item.getResourceType())) {
            defaults.put("endpoint", item.getEndpoint());
            defaults.put("protocol", item.getProtocol());
            defaults.put("authType", item.getAuthType());
            defaults.put("authConfig", copyMap(item.getAuthConfig()));
        } else if (TYPE_SKILL.equals(item.getResourceType())) {
            defaults.put("contextPrompt", item.getContextPrompt());
            inputSchema.putAll(copyMap(item.getParametersSchema()));
            capabilities.putAll(copyMap(item.getManifest()));
        }
        return CapabilityDetailVO.builder()
                .capabilityId(item.getId())
                .capabilityType(item.getResourceType())
                .displayName(item.getDisplayName())
                .resourceCode(item.getResourceCode())
                .status(item.getStatus())
                .version(item.getCurrentVersion())
                .runtimeMode(inferRuntimeMode(item.getResourceType(), item.getExecutionMode(), defaults.get("endpoint"), capabilities))
                .invokeMode(inferInvokeMode(item.getResourceType(), item.getExecutionMode(), capabilities))
                .invokeType(TYPE_SKILL.equals(item.getResourceType()) ? "portal_context" : "unified_gateway")
                .endpoint(firstText(mapText(defaults.get("endpoint")), null))
                .serviceDetailMd(item.getServiceDetailMd())
                .callable(!TYPE_SKILL.equals(item.getResourceType()) || StringUtils.hasText(item.getContextPrompt()))
                .inputSchema(inputSchema)
                .defaults(defaults)
                .authRefs(Map.of())
                .capabilities(capabilities)
                .bindingClosure(List.of())
                .build();
    }

    private CapabilityDetailVO fromResolved(ResourceResolveVO resolved) {
        Map<String, Object> spec = copyMap(resolved.getSpec());
        Map<String, Object> manifest = nestedMap(spec, "manifest");
        Map<String, Object> capabilities = new LinkedHashMap<>();
        if (TYPE_AGENT.equals(resolved.getResourceType())) {
            capabilities.put("registrationProtocol", mapText(spec.get("registrationProtocol")));
            capabilities.put("agentType", mapText(spec.get("agentType")));
            capabilities.put("mode", mapText(spec.get("mode")));
        } else if (TYPE_SKILL.equals(resolved.getResourceType())) {
            capabilities.putAll(manifest);
        }

        Map<String, Object> defaults = new LinkedHashMap<>();
        if (TYPE_SKILL.equals(resolved.getResourceType())) {
            defaults.put("contextPrompt", mapText(spec.get("contextPrompt")));
        } else if (StringUtils.hasText(resolved.getEndpoint())) {
            defaults.put("endpoint", resolved.getEndpoint());
        }

        return CapabilityDetailVO.builder()
                .capabilityId(parseLong(resolved.getResourceId()))
                .capabilityType(resolved.getResourceType())
                .displayName(resolved.getDisplayName())
                .resourceCode(resolved.getResourceCode())
                .status(resolved.getStatus())
                .version(resolved.getVersion())
                .runtimeMode(inferRuntimeMode(resolved.getResourceType(), mapText(spec.get("executionMode")), resolved.getEndpoint(), capabilities))
                .invokeMode(inferInvokeMode(resolved.getResourceType(), mapText(spec.get("executionMode")), capabilities))
                .invokeType(resolved.getInvokeType())
                .endpoint(resolved.getEndpoint())
                .serviceDetailMd(resolved.getServiceDetailMd())
                .callable(resolveCallable(resolved))
                .inputSchema(resolveInputSchema(resolved.getResourceType(), spec))
                .defaults(defaults)
                .authRefs(Map.of())
                .capabilities(capabilities)
                .bindingClosure(defaultList(resolved.getBindingClosure()))
                .build();
    }

    private Map<String, Object> resolveInputSchema(String type, Map<String, Object> spec) {
        if (TYPE_SKILL.equals(type)) {
            Map<String, Object> params = nestedMap(spec, "parametersSchema");
            if (!params.isEmpty()) {
                return params;
            }
        }
        Map<String, Object> extra = nestedMap(spec, "inputSchema");
        if (!extra.isEmpty()) {
            return extra;
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        if (TYPE_MCP.equals(type)) {
            props.put("method", Map.of("type", "string"));
            props.put("params", Map.of("type", "object"));
        } else {
            props.put("input", Map.of("type", "string"));
        }
        schema.put("properties", props);
        return schema;
    }

    private boolean resolveCallable(ResourceResolveVO resolved) {
        if (!TYPE_SKILL.equals(resolved.getResourceType())) {
            return true;
        }
        Map<String, Object> spec = copyMap(resolved.getSpec());
        Map<String, Object> manifest = nestedMap(spec, "manifest");
        return StringUtils.hasText(mapText(spec.get("contextPrompt")))
                || !nestedMap(spec, "delegate").isEmpty()
                || !nestedMap(manifest, "delegate").isEmpty();
    }

    private Map<String, Object> suggestedPayloadForResolved(ResourceResolveVO resolved) {
        Map<String, Object> schema = resolveInputSchema(resolved.getResourceType(), copyMap(resolved.getSpec()));
        Map<String, Object> properties = nestedMap(schema, "properties");
        Map<String, Object> payload = new LinkedHashMap<>();
        if (properties.isEmpty()) {
            payload.put("input", "");
            return payload;
        }
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            payload.put(entry.getKey(), defaultValueForSchema(entry.getValue()));
        }
        return payload;
    }

    private Object defaultValueForSchema(Object schemaNode) {
        Map<String, Object> node = schemaNode instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : Map.of();
        String type = mapText(node.get("type"));
        if ("number".equals(type) || "integer".equals(type)) {
            return 0;
        }
        if ("boolean".equals(type)) {
            return false;
        }
        if ("array".equals(type)) {
            return List.of();
        }
        if ("object".equals(type)) {
            return Map.of();
        }
        return "";
    }

    private CapabilityRef requireCapabilityRef(Long capabilityId) {
        if (capabilityId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "capabilityId 不能为空");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                        SELECT id, resource_type
                        FROM t_resource
                        WHERE id = ? AND deleted = 0
                        LIMIT 1
                        """,
                capabilityId);
        if (rows == null || rows.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "能力资源不存在");
        }
        Map<String, Object> row = rows.get(0);
        return new CapabilityRef(capabilityId, mapText(row.get("resource_type")));
    }

    private String inferRuntimeMode(String type, String executionMode, Object endpoint, Map<String, Object> capabilities) {
        if (TYPE_SKILL.equals(type)) {
            String runtime = firstText(mapText(capabilities.get("runtimeMode")), null);
            if (StringUtils.hasText(runtime)) {
                return runtime;
            }
            if (StringUtils.hasText(executionMode)) {
                return "prompt_" + executionMode.trim().toLowerCase(Locale.ROOT);
            }
            return "prompt_context";
        }
        if (TYPE_MCP.equals(type)) {
            String url = mapText(endpoint);
            if (StringUtils.hasText(url) && (url.startsWith("ws://") || url.startsWith("wss://"))) {
                return "mcp_websocket";
            }
            return "mcp_http";
        }
        return "remote_agent";
    }

    private String inferInvokeMode(String type, String executionMode, Map<String, Object> capabilities) {
        if (TYPE_SKILL.equals(type)) {
            Map<String, Object> delegate = nestedMap(capabilities, "delegate");
            if (!delegate.isEmpty() || "delegate_agent".equalsIgnoreCase(mapText(capabilities.get("runtimeMode")))) {
                return "delegate_invoke";
            }
            return "prompt_bundle";
        }
        if (TYPE_MCP.equals(type)) {
            return "tool_session";
        }
        return "direct_invoke";
    }

    private String inferMcpProtocol(String runtimeMode, String source) {
        if (StringUtils.hasText(runtimeMode) && runtimeMode.toLowerCase(Locale.ROOT).contains("websocket")) {
            return "websocket";
        }
        if (StringUtils.hasText(source)) {
            String normalized = source.trim().toLowerCase(Locale.ROOT);
            if (normalized.startsWith("ws://") || normalized.startsWith("wss://")) {
                return "websocket";
            }
        }
        return "sse";
    }

    private List<CapabilityToolItemVO> normalizeToolItems(List<Map<String, Object>> openAiTools) {
        if (openAiTools == null || openAiTools.isEmpty()) {
            return List.of();
        }
        List<CapabilityToolItemVO> rows = new ArrayList<>();
        for (Map<String, Object> raw : openAiTools) {
            Map<String, Object> function = nestedMap(raw, "function");
            String name = firstText(mapText(function.get("name")), mapText(raw.get("name")));
            if (!StringUtils.hasText(name)) {
                continue;
            }
            rows.add(CapabilityToolItemVO.builder()
                    .name(name)
                    .description(firstText(mapText(function.get("description")), mapText(raw.get("description"))))
                    .parameters(!nestedMap(function, "parameters").isEmpty()
                            ? nestedMap(function, "parameters")
                            : nestedMap(raw, "parameters"))
                    .build());
        }
        return rows;
    }

    private String normalizeAction(String action) {
        if (!StringUtils.hasText(action)) {
            return "connect";
        }
        String value = action.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "call_tool", "list_tools", "connect" -> value;
            default -> "connect";
        };
    }

    private Map<String, Object> mergeMaps(Map<String, Object> base, Map<String, Object> override) {
        Map<String, Object> merged = copyMap(base);
        merged.putAll(copyMap(override));
        return merged;
    }

    private Map<String, Object> copyMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(source);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedMap(Map<String, Object> source, String key) {
        if (source == null || source.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Object value = source.get(key);
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return new LinkedHashMap<>();
    }

    private <T> List<T> defaultList(List<T> source) {
        return source == null ? List.of() : source;
    }

    private String firstText(String preferred, String fallback) {
        if (StringUtils.hasText(preferred)) {
            return preferred.trim();
        }
        return fallback;
    }

    private String mapText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Long parseLong(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String writeJson(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "能力响应序列化失败");
        }
    }

    private record CapabilityRef(Long capabilityId, String resourceType) {
    }
}
