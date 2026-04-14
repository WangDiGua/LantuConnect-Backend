package com.lantu.connect.gateway.controller;

import com.lantu.connect.common.config.OpenApiConfiguration;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.R;
import com.lantu.connect.gateway.capability.CapabilityV2Service;
import com.lantu.connect.gateway.capability.dto.CapabilityCreateRequest;
import com.lantu.connect.gateway.capability.dto.CapabilityDetailVO;
import com.lantu.connect.gateway.capability.dto.CapabilityImportRequest;
import com.lantu.connect.gateway.capability.dto.CapabilityImportSuggestionVO;
import com.lantu.connect.gateway.capability.dto.CapabilityInvokeRequest;
import com.lantu.connect.gateway.capability.dto.CapabilityInvokeResultVO;
import com.lantu.connect.gateway.capability.dto.CapabilityResolveRequest;
import com.lantu.connect.gateway.capability.dto.CapabilityResolveResultVO;
import com.lantu.connect.gateway.capability.dto.CapabilitySummaryVO;
import com.lantu.connect.gateway.capability.dto.CapabilityToolSessionRequest;
import com.lantu.connect.gateway.capability.dto.CapabilityToolSessionVO;
import com.lantu.connect.gateway.dto.InvokeResponse;
import com.lantu.connect.gateway.dto.ResourceCatalogQueryRequest;
import com.lantu.connect.gateway.security.ApiKeyScopeService;
import com.lantu.connect.gateway.support.GatewayCallerResolver;
import com.lantu.connect.gateway.support.GatewayInvokeResponseSupport;
import com.lantu.connect.sysconfig.runtime.RuntimeAppConfigService;
import com.lantu.connect.usermgmt.entity.ApiKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v2/capabilities")
@RequiredArgsConstructor
@Tag(name = "Capability V2", description = "统一能力注册、发现、解析、试用与工具测试入口")
public class CapabilityV2Controller {

    private final CapabilityV2Service capabilityV2Service;
    private final ApiKeyScopeService apiKeyScopeService;
    private final GatewayCallerResolver gatewayCallerResolver;
    private final RuntimeAppConfigService runtimeAppConfigService;

    @Operation(summary = "智能导入识别")
    @PostMapping("/import")
    public R<CapabilityImportSuggestionVO> importCapability(@RequestHeader("X-User-Id") Long userId,
                                                            @Valid @RequestBody CapabilityImportRequest request) {
        return R.ok(capabilityV2Service.detect(request));
    }

    @Operation(summary = "创建能力草稿")
    @PostMapping
    public R<CapabilityDetailVO> create(@RequestHeader("X-User-Id") Long userId,
                                        @Valid @RequestBody CapabilityCreateRequest request) {
        return R.ok(capabilityV2Service.create(userId, request));
    }

    @Operation(summary = "能力列表")
    @GetMapping
    public R<PageResult<CapabilitySummaryVO>> list(ResourceCatalogQueryRequest request,
                                                   @RequestHeader(value = "X-Api-Key", required = false)
                                                   @Parameter(description = "可选 API Key") String apiKeyRaw) {
        Long userId = gatewayCallerResolver.resolveTrustedUserIdOrNull();
        ApiKey apiKey = apiKeyScopeService.authenticateOrNull(apiKeyRaw);
        return R.ok(capabilityV2Service.list(request, apiKey, userId));
    }

    @Operation(summary = "能力详情")
    @GetMapping("/{id}")
    public R<CapabilityDetailVO> getById(@PathVariable Long id,
                                         @RequestParam(value = "include", required = false) String include,
                                         @RequestHeader(value = "X-Api-Key", required = false)
                                         @Parameter(description = "可选 API Key") String apiKeyRaw) {
        Long userId = gatewayCallerResolver.resolveTrustedUserIdOrNull();
        ApiKey apiKey = apiKeyScopeService.authenticateOrNull(apiKeyRaw);
        return R.ok(capabilityV2Service.getById(id, include, apiKey, userId));
    }

    @Operation(summary = "能力解析")
    @SecurityRequirement(name = OpenApiConfiguration.API_KEY_SECURITY)
    @PostMapping("/{id}/resolve")
    public R<CapabilityResolveResultVO> resolve(@PathVariable Long id,
                                                @Valid @RequestBody(required = false) CapabilityResolveRequest request,
                                                @RequestHeader(value = "X-Api-Key", required = false)
                                                @Parameter(description = "可选 API Key") String apiKeyRaw) {
        Long userId = gatewayCallerResolver.resolveTrustedUserIdOrNull();
        ApiKey apiKey = apiKeyScopeService.authenticateOrNull(apiKeyRaw);
        return R.ok(capabilityV2Service.resolve(id, request, apiKey, userId));
    }

    @Operation(summary = "统一能力调用")
    @SecurityRequirement(name = OpenApiConfiguration.API_KEY_SECURITY)
    @PostMapping("/{id}/invoke")
    public ResponseEntity<R<CapabilityInvokeResultVO>> invoke(@PathVariable Long id,
                                                              @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
                                                              @RequestHeader(value = "X-Request-Id", required = false) String requestId,
                                                              @RequestHeader(value = "X-Api-Key", required = false)
                                                              @Parameter(description = "可选 API Key") String apiKeyRaw,
                                                              @Valid @RequestBody(required = false) CapabilityInvokeRequest request,
                                                              HttpServletRequest httpRequest) {
        Long userId = gatewayCallerResolver.resolveTrustedUserIdOrNull();
        ApiKey apiKey = apiKeyScopeService.authenticateOrNull(apiKeyRaw);
        String resolvedTraceId = resolveTraceId(traceId, requestId);
        CapabilityInvokeResultVO data = capabilityV2Service.invoke(id, userId, resolvedTraceId, httpRequest.getRemoteAddr(), request, apiKey);
        if (!runtimeAppConfigService.gateway().isInvokeHttpStatusReflectsUpstream()) {
            return ResponseEntity.ok(R.ok(data));
        }
        InvokeResponse response = data == null ? null : data.getResponse();
        return ResponseEntity.status(GatewayInvokeResponseSupport.toHttpStatus(response)).body(R.ok(data));
    }

    @Operation(summary = "统一工具测试会话")
    @SecurityRequirement(name = OpenApiConfiguration.API_KEY_SECURITY)
    @PostMapping("/{id}/tool-session")
    public R<CapabilityToolSessionVO> toolSession(@PathVariable Long id,
                                                  @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
                                                  @RequestHeader(value = "X-Request-Id", required = false) String requestId,
                                                  @RequestHeader(value = "X-Api-Key", required = false)
                                                  @Parameter(description = "可选 API Key") String apiKeyRaw,
                                                  @Valid @RequestBody(required = false) CapabilityToolSessionRequest request,
                                                  HttpServletRequest httpRequest) {
        Long userId = gatewayCallerResolver.resolveTrustedUserIdOrNull();
        ApiKey apiKey = apiKeyScopeService.authenticateOrNull(apiKeyRaw);
        String resolvedTraceId = resolveTraceId(traceId, requestId);
        return R.ok(capabilityV2Service.toolSession(id, userId, resolvedTraceId, httpRequest.getRemoteAddr(), request, apiKey));
    }

    private String resolveTraceId(String traceId, String requestId) {
        if (StringUtils.hasText(traceId)) {
            return traceId.trim();
        }
        if (StringUtils.hasText(requestId)) {
            return requestId.trim();
        }
        return UUID.randomUUID().toString();
    }
}
