package com.lantu.connect.gateway.controller;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.R;
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
import com.lantu.connect.gateway.security.ResourceInvokeGrantService;
import com.lantu.connect.gateway.service.UnifiedGatewayService;
import com.lantu.connect.gateway.support.GatewayCallerResolver;
import com.lantu.connect.gateway.support.GatewayInvokeResponseSupport;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.usermgmt.entity.ApiKey;
import com.lantu.connect.usermgmt.mapper.ApiKeyMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
public class ResourceCatalogController {

    private final UnifiedGatewayService unifiedGatewayService;
    private final ApiKeyScopeService apiKeyScopeService;
    private final GatewayCallerResolver gatewayCallerResolver;
    private final AppLaunchTokenService appLaunchTokenService;
    private final ApiKeyMapper apiKeyMapper;
    private final ResourceInvokeGrantService resourceInvokeGrantService;

    @Value("${lantu.gateway.invoke-http-status-reflects-upstream:true}")
    private boolean invokeHttpStatusReflectsUpstream;

    @GetMapping("/catalog/resources")
    public R<PageResult<ResourceCatalogItemVO>> catalog(ResourceCatalogQueryRequest request,
                                                        @RequestHeader(value = "X-Api-Key", required = false) String apiKeyRaw) {
        Long userId = gatewayCallerResolver.resolveTrustedUserIdOrNull();
        ApiKey apiKey = apiKeyScopeService.authenticateOrNull(apiKeyRaw);
        return R.ok(unifiedGatewayService.catalog(request, apiKey, userId));
    }

    @GetMapping("/catalog/resources/trending")
    public R<List<ExploreHubData.ExploreResourceItem>> trending(
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false, defaultValue = "10") Integer limit) {
        return R.ok(unifiedGatewayService.trending(resourceType, limit));
    }

    @GetMapping("/catalog/resources/search-suggestions")
    public R<List<SearchSuggestion>> searchSuggestions(@RequestParam String q) {
        return R.ok(unifiedGatewayService.searchSuggestions(q));
    }

    @GetMapping("/catalog/resources/{type}/{id}")
    public R<ResourceResolveVO> getByTypeAndId(@PathVariable String type,
                                               @PathVariable String id,
                                               @RequestParam(required = false) String include,
                                               @RequestHeader(value = "X-Api-Key", required = false) String apiKeyRaw) {
        Long userId = gatewayCallerResolver.resolveTrustedUserIdOrNull();
        ApiKey apiKey = apiKeyScopeService.authenticateOrNull(apiKeyRaw);
        return R.ok(unifiedGatewayService.getByTypeAndId(type, id, include, apiKey, userId));
    }

    @GetMapping("/catalog/resources/{type}/{id}/stats")
    public R<ResourceStatsVO> resourceStats(@PathVariable String type,
                                            @PathVariable String id) {
        return R.ok(unifiedGatewayService.getResourceStats(type, id));
    }

    @PostMapping("/catalog/resolve")
    public R<ResourceResolveVO> resolve(@Valid @RequestBody ResourceResolveRequest request,
                                        @RequestHeader(value = "X-Api-Key", required = false) String apiKeyRaw) {
        Long userId = gatewayCallerResolver.resolveTrustedUserIdOrNull();
        ApiKey apiKey = apiKeyScopeService.authenticateOrNull(apiKeyRaw);
        return R.ok(unifiedGatewayService.resolve(request, apiKey, userId));
    }

    @PostMapping("/invoke")
    public ResponseEntity<R<InvokeResponse>> invoke(@RequestHeader(value = "X-Trace-Id", required = false) String traceId,
                                    @RequestHeader(value = "X-Request-Id", required = false) String requestId,
                                    @RequestHeader(value = "X-Api-Key", required = false) String apiKeyRaw,
                                    @Valid @RequestBody InvokeRequest request,
                                    HttpServletRequest httpRequest) {
        Long userId = gatewayCallerResolver.resolveTrustedUserIdOrNull();
        ApiKey apiKey = apiKeyScopeService.authenticateOrNull(apiKeyRaw);
        String resolvedTraceId = StringUtils.hasText(traceId)
                ? traceId.trim()
                : (StringUtils.hasText(requestId) ? requestId.trim() : UUID.randomUUID().toString());
        InvokeResponse data = unifiedGatewayService.invoke(userId, resolvedTraceId, httpRequest.getRemoteAddr(), request, apiKey);
        R<InvokeResponse> body = GatewayInvokeResponseSupport.wrap(data);
        if (!invokeHttpStatusReflectsUpstream) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.status(GatewayInvokeResponseSupport.toHttpStatus(data)).body(body);
    }

    /**
     * MCP HTTP/SSE：原样流式转发上游响应（长 SSE）。需要与 {@link #invoke} 相同鉴权；不支持 WebSocket 上游。
     */
    @PostMapping(value = "/invoke-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> invokeStream(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestHeader(value = "X-Api-Key", required = false) String apiKeyRaw,
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

    @GetMapping("/catalog/apps/launch")
    public void launchApp(@RequestParam String token, HttpServletResponse response) throws java.io.IOException {
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
        resourceInvokeGrantService.ensureApiKeyGranted(
                apiKey,
                "resolve",
                "app",
                claims.resourceId(),
                claims.userId());
        response.sendRedirect(appUrl.trim());
    }
}
