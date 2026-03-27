package com.lantu.connect.gateway.controller;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.R;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.gateway.dto.InvokeRequest;
import com.lantu.connect.gateway.dto.InvokeResponse;
import com.lantu.connect.gateway.dto.ResourceCatalogItemVO;
import com.lantu.connect.gateway.dto.ResourceCatalogQueryRequest;
import com.lantu.connect.gateway.dto.ResourceResolveRequest;
import com.lantu.connect.gateway.dto.ResourceResolveVO;
import com.lantu.connect.gateway.security.ApiKeyScopeService;
import com.lantu.connect.gateway.service.UnifiedGatewayService;
import com.lantu.connect.usermgmt.entity.ApiKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/sdk/v1")
@RequiredArgsConstructor
@Tag(name = "SDK统一网关", description = "供开发者SDK稳定调用的v1接口")
public class SdkGatewayController {

    private final UnifiedGatewayService unifiedGatewayService;
    private final ApiKeyScopeService apiKeyScopeService;

    @Operation(summary = "资源目录分页查询")
    @GetMapping("/resources")
    public R<PageResult<ResourceCatalogItemVO>> catalog(ResourceCatalogQueryRequest request,
                                                        @Parameter(description = "调用人用户ID，可为空")
                                                        @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                                        @Parameter(description = "应用API Key")
                                                        @RequestHeader(value = "X-Api-Key") String apiKeyRaw) {
        ensureApiKeyPresent(apiKeyRaw);
        ApiKey apiKey = apiKeyScopeService.authenticateOrNull(apiKeyRaw);
        return R.ok(unifiedGatewayService.catalog(request, apiKey, userId));
    }

    @Operation(summary = "按类型与ID查询资源详情")
    @GetMapping("/resources/{type}/{id}")
    public R<ResourceResolveVO> getByTypeAndId(@PathVariable String type,
                                               @PathVariable String id,
                                               @Parameter(description = "调用人用户ID，可为空")
                                               @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                               @Parameter(description = "应用API Key")
                                               @RequestHeader(value = "X-Api-Key") String apiKeyRaw) {
        ensureApiKeyPresent(apiKeyRaw);
        ApiKey apiKey = apiKeyScopeService.authenticateOrNull(apiKeyRaw);
        return R.ok(unifiedGatewayService.getByTypeAndId(type, id, apiKey, userId));
    }

    @Operation(summary = "资源解析")
    @PostMapping("/resolve")
    public R<ResourceResolveVO> resolve(@Valid @RequestBody ResourceResolveRequest request,
                                        @Parameter(description = "调用人用户ID，可为空")
                                        @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                        @Parameter(description = "应用API Key")
                                        @RequestHeader(value = "X-Api-Key") String apiKeyRaw) {
        ensureApiKeyPresent(apiKeyRaw);
        ApiKey apiKey = apiKeyScopeService.authenticateOrNull(apiKeyRaw);
        return R.ok(unifiedGatewayService.resolve(request, apiKey, userId));
    }

    @Operation(summary = "统一调用入口")
    @PostMapping("/invoke")
    public R<InvokeResponse> invoke(@Parameter(description = "调用人用户ID，可为空")
                                    @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                    @Parameter(description = "链路追踪ID，可为空")
                                    @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
                                    @Parameter(description = "应用API Key")
                                    @RequestHeader(value = "X-Api-Key") String apiKeyRaw,
                                    @Valid @RequestBody InvokeRequest request,
                                    HttpServletRequest httpRequest) {
        ensureApiKeyPresent(apiKeyRaw);
        ApiKey apiKey = apiKeyScopeService.authenticateOrNull(apiKeyRaw);
        String resolvedTraceId = StringUtils.hasText(traceId) ? traceId.trim() : UUID.randomUUID().toString();
        return R.ok(unifiedGatewayService.invoke(userId, resolvedTraceId, httpRequest.getRemoteAddr(), request, apiKey));
    }

    private static void ensureApiKeyPresent(String apiKeyRaw) {
        if (!StringUtils.hasText(apiKeyRaw)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "SDK 调用必须提供 X-Api-Key");
        }
    }
}
