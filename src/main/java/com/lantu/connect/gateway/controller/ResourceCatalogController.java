package com.lantu.connect.gateway.controller;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.R;
import com.lantu.connect.gateway.dto.AggregatedCapabilityToolsVO;
import com.lantu.connect.gateway.dto.InvokeRequest;
import com.lantu.connect.gateway.dto.InvokeResponse;
import com.lantu.connect.dashboard.dto.ExploreHubData;
import com.lantu.connect.gateway.dto.ResourceCatalogItemVO;
import com.lantu.connect.gateway.dto.ResourceCatalogQueryRequest;
import com.lantu.connect.gateway.dto.ResourceResolveRequest;
import com.lantu.connect.gateway.dto.ResourceResolveVO;
import com.lantu.connect.gateway.dto.ResourceStatsVO;
import com.lantu.connect.gateway.dto.SearchSuggestion;
import com.lantu.connect.gateway.security.AppLaunchTokenService;
import com.lantu.connect.gateway.security.ApiKeyScopeService;
import com.lantu.connect.gateway.service.UnifiedGatewayService;
import com.lantu.connect.gateway.support.GatewayCallerResolver;
import com.lantu.connect.gateway.support.GatewayInvokeResponseSupport;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.usermgmt.entity.ApiKey;
import com.lantu.connect.sysconfig.runtime.RuntimeAppConfigService;
import com.lantu.connect.common.config.OpenApiConfiguration;
import com.lantu.connect.usermgmt.mapper.ApiKeyMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "资源目录与网关", description = "市场目录、资源解析、统一调用与应用启动跳转；`include` 与历史字段 `access_policy` 见 docs/api/public-catalog-contract.md。鉴权以有效 X-Api-Key、Key scope、资源 published（及网关 ResourceInvokeGrantService 规则）为准；逐资源授权表与工单已移除（2026-04-09 迁移）。")
public class ResourceCatalogController {

    private final UnifiedGatewayService unifiedGatewayService;
    private final ApiKeyScopeService apiKeyScopeService;
    private final GatewayCallerResolver gatewayCallerResolver;
    private final AppLaunchTokenService appLaunchTokenService;
    private final ApiKeyMapper apiKeyMapper;
    private final RuntimeAppConfigService runtimeAppConfigService;

    @Operation(summary = "资源目录分页", description = "需登录态和/或有效 X-Api-Key；`include` 可为 observability、quality、tags（逗号分隔）。闭包见单资源详情 `include=closure|bindings`。")
    @GetMapping("/catalog/resources")
    public R<PageResult<ResourceCatalogItemVO>> catalog(ResourceCatalogQueryRequest request,
                                                        @Parameter(description = "可选；与登录态二选一或并存，受 API Key scope（及 published）约束")
                                                        @RequestHeader(value = "X-Api-Key", required = false) String apiKeyRaw) {
        Long userId = gatewayCallerResolver.resolveTrustedUserIdOrNull();
        ApiKey apiKey = apiKeyScopeService.authenticateOrNull(apiKeyRaw);
        return R.ok(unifiedGatewayService.catalog(request, apiKey, userId));
    }

    @Operation(summary = "热门/趋势资源")
    @GetMapping("/catalog/resources/trending")
    public R<List<ExploreHubData.ExploreResourceItem>> trending(
            @Parameter(description = "按资源类型筛选，如 agent、skill")
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false, defaultValue = "10") Integer limit) {
        Long userId = gatewayCallerResolver.resolveTrustedUserIdOrNull();
        return R.ok(unifiedGatewayService.trending(resourceType, limit, userId));
    }

    @Operation(summary = "搜索建议")
    @GetMapping("/catalog/resources/search-suggestions")
    public R<List<SearchSuggestion>> searchSuggestions(@Parameter(description = "搜索前缀") @RequestParam String q) {
        Long userId = gatewayCallerResolver.resolveTrustedUserIdOrNull();
        return R.ok(unifiedGatewayService.searchSuggestions(q, userId));
    }

    @Operation(summary = "按类型与ID获取资源详情（GET）", description = "须登录或有效 X-Api-Key；`include` 逗号分隔：observability、quality、tags、closure、bindings（后两者返回 `bindingClosure`）。"
            + "浏览器仅 JWT 时可拉应用类展示字段，launch 票据须本人 Key。")
    @GetMapping("/catalog/resources/{type}/{id}")
    public R<ResourceResolveVO> getByTypeAndId(@PathVariable @Parameter(description = "agent|skill|mcp|app|dataset") String type,
                                               @PathVariable @Parameter(description = "资源数字 ID") String id,
                                               @RequestParam(required = false)
                                               @Parameter(description = "可选扩展块，逗号分隔，见 ResourceCatalogQueryRequest.include") String include,
                                               @RequestHeader(value = "X-Api-Key", required = false)
                                               @Parameter(description = "可选") String apiKeyRaw) {
        Long userId = gatewayCallerResolver.resolveTrustedUserIdOrNull();
        ApiKey apiKey = apiKeyScopeService.authenticateOrNull(apiKeyRaw);
        return R.ok(unifiedGatewayService.getByTypeAndId(type, id, include, apiKey, userId));
    }

    @Operation(summary = "绑定闭包内 MCP 工具聚合（可选 BFF）", description = "自某入口资源（如 agent）沿绑定边展开闭包，对各 MCP 调用 tools/list，合并为 OpenAI tools 列表并返回 `routes` 映射；须 X-Api-Key，且对入口资源 resolve、对各 MCP invoke 均在 scope 内。")
    @SecurityRequirement(name = OpenApiConfiguration.API_KEY_SECURITY)
    @GetMapping("/catalog/capabilities/tools")
    public R<AggregatedCapabilityToolsVO> aggregatedTools(
            @RequestParam @Parameter(description = "入口资源类型，如 agent、mcp") String entryResourceType,
            @RequestParam @Parameter(description = "入口资源数字 ID") String entryResourceId,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @RequestHeader(value = "X-Api-Key", required = false) @Parameter(description = "必填") String apiKeyRaw) {
        Long userId = gatewayCallerResolver.resolveTrustedUserIdOrNull();
        ApiKey apiKey = apiKeyScopeService.authenticateOrNull(apiKeyRaw);
        return R.ok(unifiedGatewayService.aggregatedCapabilityTools(userId, traceId, apiKey, entryResourceType, entryResourceId));
    }

    @Operation(summary = "资源统计（评分等）")
    @GetMapping("/catalog/resources/{type}/{id}/stats")
    public R<ResourceStatsVO> resourceStats(@PathVariable String type,
                                            @PathVariable String id) {
        return R.ok(unifiedGatewayService.getResourceStats(type, id));
    }

    @Operation(summary = "资源解析（POST）", description = "须提供有效 X-Api-Key（与登录态可并存）；行为同 GET 详情但强制 Key 路径。")
    @SecurityRequirement(name = OpenApiConfiguration.API_KEY_SECURITY)
    @PostMapping("/catalog/resolve")
    public R<ResourceResolveVO> resolve(@Valid @RequestBody ResourceResolveRequest request,
                                        @RequestHeader(value = "X-Api-Key", required = false)
                                        @Parameter(description = "必填，须能通过鉴权") String apiKeyRaw) {
        Long userId = gatewayCallerResolver.resolveTrustedUserIdOrNull();
        ApiKey apiKey = apiKeyScopeService.authenticateOrNull(apiKeyRaw);
        return R.ok(unifiedGatewayService.resolve(request, apiKey, userId));
    }

    @Operation(summary = "统一调用入口",
            description = "须提供有效 X-Api-Key。`skill`：仅 `execution_mode=hosted` 且已发布可走本接口。`mcp`：若登记了前置 Hosted Skill，网关内先归一化 JSON 再转发上游。"
                    + " **绑定展开**：对 `agent` 且存在 `agent_depends_mcp` 时，网关在转发上游前对绑定 MCP 执行 `tools/list`，将 `openAiTools`/`routes`/`warnings` 及入口 `entry` 写入请求 JSON 的 `_lantu.bindingExpansion`（可与 `lantu.gateway.binding-expansion` 开关关闭）。"
                    + " 对 **hosted `skill`** 且存在指向该技能的 `mcp_depends_skill` 时，同样写入上述结构（逆查 MCP 列表）。**单独 invoke `mcp` 不会**反向拉起 Agent。"
                    + " Key 须对被展开的各 MCP 具备 invoke scope。")
    @SecurityRequirement(name = OpenApiConfiguration.API_KEY_SECURITY)
    @PostMapping("/invoke")
    public ResponseEntity<R<InvokeResponse>> invoke(@RequestHeader(value = "X-Trace-Id", required = false)
                                                    @Parameter(description = "链路追踪 ID，可空") String traceId,
                                    @RequestHeader(value = "X-Request-Id", required = false)
                                    @Parameter(description = "与 X-Trace-Id 二选一作为追踪 id") String requestId,
                                    @RequestHeader(value = "X-Api-Key", required = false)
                                    @Parameter(description = "必填") String apiKeyRaw,
                                    @Valid @RequestBody InvokeRequest request,
                                    HttpServletRequest httpRequest) {
        Long userId = gatewayCallerResolver.resolveTrustedUserIdOrNull();
        ApiKey apiKey = apiKeyScopeService.authenticateOrNull(apiKeyRaw);
        String resolvedTraceId = StringUtils.hasText(traceId)
                ? traceId.trim()
                : (StringUtils.hasText(requestId) ? requestId.trim() : UUID.randomUUID().toString());
        InvokeResponse data = unifiedGatewayService.invoke(userId, resolvedTraceId, httpRequest.getRemoteAddr(), request, apiKey);
        R<InvokeResponse> body = GatewayInvokeResponseSupport.wrap(data);
        if (!runtimeAppConfigService.gateway().isInvokeHttpStatusReflectsUpstream()) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.status(GatewayInvokeResponseSupport.toHttpStatus(data)).body(body);
    }

    /**
     * MCP HTTP/SSE：原样流式转发上游响应（长 SSE）。需要与 {@link #invoke} 相同鉴权；不支持 WebSocket 上游。
     */
    @Operation(summary = "流式调用（MCP SSE 等）", description = "同 invoke，响应为 text/event-stream。")
    @SecurityRequirement(name = OpenApiConfiguration.API_KEY_SECURITY)
    @PostMapping(value = "/invoke-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> invokeStream(
            @RequestHeader(value = "X-Trace-Id", required = false) @Parameter(description = "可空") String traceId,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestHeader(value = "X-Api-Key", required = false) @Parameter(description = "必填") String apiKeyRaw,
            @Valid @RequestBody InvokeRequest request,
            HttpServletRequest httpRequest) {
        Long userId = gatewayCallerResolver.resolveTrustedUserIdOrNull();
        ApiKey apiKey = apiKeyScopeService.authenticateOrNull(apiKeyRaw);
        String resolvedTraceId = StringUtils.hasText(traceId)
                ? traceId.trim()
                : (StringUtils.hasText(requestId) ? requestId.trim() : UUID.randomUUID().toString());
        StreamingResponseBody body = outputStream -> unifiedGatewayService.invokeStream(
                userId, resolvedTraceId, httpRequest.getRemoteAddr(), request, apiKey, outputStream);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Accel-Buffering", "no")
                .body(body);
    }

    @Operation(summary = "应用启动重定向", description = "匿名可访问；消费 launch token 后 302 跳转真实 app_url。")
    @GetMapping("/catalog/apps/launch")
    public void launchApp(@RequestParam @Parameter(description = "resolve 下发的短期 launchToken") String token,
                          HttpServletResponse response) throws java.io.IOException {
        AppLaunchTokenService.AppLaunchClaims claims = appLaunchTokenService.consume(token);
        String appUrl = claims.appUrl();
        if (!StringUtils.hasText(appUrl)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "launch token 缺少 appUrl");
        }
        ApiKey apiKey = apiKeyMapper.selectById(claims.apiKeyId());
        if (apiKey == null || !"active".equalsIgnoreCase(apiKey.getStatus())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "launch token 绑定的 API Key 不可用");
        }
        apiKeyScopeService.ensureResolveAllowed(apiKey, "app", String.valueOf(claims.resourceId()));
        response.sendRedirect(appUrl.trim());
    }
}
