package com.lantu.connect.gateway.controller;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.security.GatewayAuthDetails;
import com.lantu.connect.common.result.R;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.gateway.dto.AggregatedCapabilityToolsVO;
import com.lantu.connect.gateway.dto.InvokeRequest;
import com.lantu.connect.gateway.dto.InvokeResponse;
import com.lantu.connect.gateway.dto.ResourceCatalogItemVO;
import com.lantu.connect.gateway.dto.ResourceCatalogQueryRequest;
import com.lantu.connect.gateway.dto.ResourceResolveRequest;
import com.lantu.connect.gateway.dto.ResourceResolveVO;
import com.lantu.connect.gateway.security.ApiKeyScopeService;
import com.lantu.connect.gateway.service.UnifiedGatewayService;
import com.lantu.connect.gateway.support.GatewayInvokeResponseSupport;
import com.lantu.connect.sysconfig.runtime.RuntimeAppConfigService;
import com.lantu.connect.common.config.OpenApiConfiguration;
import com.lantu.connect.usermgmt.entity.ApiKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.UUID;

@RestController
@RequestMapping("/sdk/v1")
@RequiredArgsConstructor
@Tag(name = "SDK统一网关", description = """
        稳定 v1 路径，语义与同服务根路径目录/解析/invoke 一致；本组所有接口必须携带有效 X-Api-Key。
        `include`（目录/详情/解析）为逗号分隔：observability、quality、tags、closure、bindings（单资源详情返回 bindingClosure）。
        调用授权以 API Key scope 与 published 为主；资源级 Grant 表已下线。
        外部门户的多轮编排、意图路由由集成方自行实现；本平台提供统一资源发现、capabilities/tools 聚合与单次 invoke。""")
@SecurityRequirement(name = OpenApiConfiguration.API_KEY_SECURITY)
public class SdkGatewayController {

    private final UnifiedGatewayService unifiedGatewayService;
    private final ApiKeyScopeService apiKeyScopeService;
    private final RuntimeAppConfigService runtimeAppConfigService;

    @Operation(summary = "资源目录分页查询", description = "查询参数与 GET /catalog/resources 一致；须 Key。")
    @GetMapping("/resources")
    public R<PageResult<ResourceCatalogItemVO>> catalog(ResourceCatalogQueryRequest request,
                                                        @Parameter(description = "应用API Key")
                                                        @RequestHeader(value = "X-Api-Key") String apiKeyRaw) {
        Long userId = resolveTrustedUserId();
        ApiKey apiKey = requireSdkApiKey(apiKeyRaw);
        return R.ok(unifiedGatewayService.catalog(request, apiKey, userId));
    }

    @Operation(summary = "按类型与ID查询资源详情", description = "等同 GET /catalog/resources/{type}/{id}；app 类型含 launch 逻辑时依赖 JWT 与 Key 归属。")
    @GetMapping("/resources/{type}/{id}")
    public R<ResourceResolveVO> getByTypeAndId(@PathVariable String type,
                                               @PathVariable String id,
                                               @RequestParam(value = "include", required = false)
                                               @Parameter(description = "可选：observability、quality、tags、closure、bindings") String include,
                                               @Parameter(description = "应用API Key")
                                               @RequestHeader(value = "X-Api-Key") String apiKeyRaw) {
        Long userId = resolveTrustedUserId();
        ApiKey apiKey = requireSdkApiKey(apiKeyRaw);
        return R.ok(unifiedGatewayService.getByTypeAndId(type, id, include, apiKey, userId));
    }

    @Operation(summary = "绑定闭包 MCP 工具聚合",
            description = "等同 GET /catalog/capabilities/tools。自 entryResourceType+entryResourceId 沿绑定边求无向闭包，"
                    + "对闭包内各 MCP 执行 tools/list，合并为 OpenAI tools + routes；与 invoke 时 payload._lantu.bindingExpansion 数据来源一致。"
                    + " 软上限见 lantu.gateway.capabilities（maxMcpsPerAggregate/maxToolsPerResponse，0=不限制），触发时 warnings 说明截断。")
    @GetMapping("/capabilities/tools")
    public R<AggregatedCapabilityToolsVO> aggregatedTools(
            @RequestParam @Parameter(description = "入口资源类型") String entryResourceType,
            @RequestParam @Parameter(description = "入口资源 ID") String entryResourceId,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @Parameter(description = "应用API Key")
            @RequestHeader(value = "X-Api-Key") String apiKeyRaw) {
        Long userId = resolveTrustedUserId();
        ApiKey apiKey = requireSdkApiKey(apiKeyRaw);
        return R.ok(unifiedGatewayService.aggregatedCapabilityTools(userId, traceId, apiKey, entryResourceType, entryResourceId));
    }

    @Operation(summary = "资源解析", description = "等同 POST /catalog/resolve；请求体见 ResourceResolveRequest。")
    @PostMapping("/resolve")
    public R<ResourceResolveVO> resolve(@Valid @RequestBody ResourceResolveRequest request,
                                        @Parameter(description = "应用API Key")
                                        @RequestHeader(value = "X-Api-Key") String apiKeyRaw) {
        Long userId = resolveTrustedUserId();
        ApiKey apiKey = requireSdkApiKey(apiKeyRaw);
        return R.ok(unifiedGatewayService.resolve(request, apiKey, userId));
    }

    @Operation(summary = "统一调用入口",
            description = "等同 POST /invoke。单次请求仅针对一对 resourceType+resourceId；多资源须多次调用。"
                    + " Skill 不可 invoke，请通过目录/resolve 获取规范。绑定展开与 payload.activeSkillIds 见 ResourceCatalogController.invoke 说明。")
    @PostMapping("/invoke")
    public ResponseEntity<R<InvokeResponse>> invoke(
                                    @Parameter(description = "链路追踪ID，可为空")
                                    @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
                                    @Parameter(description = "应用API Key")
                                    @RequestHeader(value = "X-Api-Key") String apiKeyRaw,
                                    @Valid @RequestBody InvokeRequest request,
                                    HttpServletRequest httpRequest) {
        Long userId = resolveTrustedUserId();
        ApiKey apiKey = requireSdkApiKey(apiKeyRaw);
        String resolvedTraceId = StringUtils.hasText(traceId) ? traceId.trim() : UUID.randomUUID().toString();
        InvokeResponse data = unifiedGatewayService.invoke(userId, resolvedTraceId, httpRequest.getRemoteAddr(), request, apiKey);
        R<InvokeResponse> body = GatewayInvokeResponseSupport.wrap(data);
        if (!runtimeAppConfigService.gateway().isInvokeHttpStatusReflectsUpstream()) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.status(GatewayInvokeResponseSupport.toHttpStatus(data).value()).body(body);
    }

    @Operation(summary = "MCP 流式调用（SSE/原始流）", description = "等同 POST /invoke-stream，响应 Content-Type: text/event-stream。")
    @PostMapping(value = "/invoke-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> invokeStream(
                                                              @Parameter(description = "链路追踪ID，可为空")
                                                              @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
                                                              @Parameter(description = "应用API Key")
                                                              @RequestHeader(value = "X-Api-Key") String apiKeyRaw,
                                                              @Valid @RequestBody InvokeRequest request,
                                                              HttpServletRequest httpRequest) {
        Long userId = resolveTrustedUserId();
        ApiKey apiKey = requireSdkApiKey(apiKeyRaw);
        String resolvedTraceId = StringUtils.hasText(traceId) ? traceId.trim() : UUID.randomUUID().toString();
        StreamingResponseBody body = outputStream -> unifiedGatewayService.invokeStream(
                userId, resolvedTraceId, httpRequest.getRemoteAddr(), request, apiKey, outputStream);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Accel-Buffering", "no")
                .body(body);
    }

    private ApiKey requireSdkApiKey(String apiKeyRaw) {
        if (!StringUtils.hasText(apiKeyRaw)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "SDK 调用必须提供 X-Api-Key");
        }
        ApiKey apiKey = apiKeyScopeService.authenticateOrNull(apiKeyRaw);
        if (apiKey == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "API Key 无效或已停用");
        }
        return apiKey;
    }

    /**
     * SDK 用户身份仅接受服务端鉴权上下文（JWT），不再信任客户端 X-User-Id 头。
     */
    private static Long resolveTrustedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return null;
        }
        String principal = String.valueOf(auth.getPrincipal()).trim();
        if (!StringUtils.hasText(principal) || "sandbox-token".equalsIgnoreCase(principal)) {
            return null;
        }
        if ("api-key".equalsIgnoreCase(principal)) {
            if (auth.getDetails() instanceof GatewayAuthDetails d && d.ownerUserId() != null) {
                return d.ownerUserId();
            }
            return null;
        }
        try {
            return Long.valueOf(principal);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
