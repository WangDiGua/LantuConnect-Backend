package com.lantu.connect.gateway.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.gateway.security.AppLaunchTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 应用类资源：不向下游 URL 发 HTTP，而是签发一次性 launch token，与 {@code GET /catalog/apps/launch} 闭环一致。
 */
@Component
@RequiredArgsConstructor
public class RedirectProtocolInvoker implements GatewayProtocolInvoker {

    private final AppLaunchTokenService appLaunchTokenService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(String protocol) {
        return protocol != null && "redirect".equals(protocol.trim().toLowerCase(Locale.ROOT));
    }

    @Override
    public ProtocolInvokeResult invoke(String endpoint,
                                       int timeoutSec,
                                       String traceId,
                                       Map<String, Object> payload,
                                       Map<String, Object> spec,
                                       ProtocolInvokeContext ctx) throws Exception {
        long t0 = System.nanoTime();
        if (!StringUtils.hasText(endpoint)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "应用 redirect 调用缺少 appUrl");
        }
        if (ctx == null || !StringUtils.hasText(ctx.apiKeyId()) || ctx.resourceId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "应用 redirect 调用缺少 API Key 或 resourceId 上下文");
        }
        AppLaunchTokenService.LaunchTicket ticket = appLaunchTokenService.issue(
                ctx.resourceId(),
                endpoint.trim(),
                ctx.apiKeyId().trim(),
                ctx.userId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", "app_launch");
        out.put("launchUrl", ticket.launchUrl());
        out.put("token", ticket.token());
        if (spec != null && spec.get("embedType") != null && StringUtils.hasText(String.valueOf(spec.get("embedType")))) {
            out.put("embedType", String.valueOf(spec.get("embedType")).trim());
        }
        String json = objectMapper.writeValueAsString(out);
        long ms = Math.max(0L, (System.nanoTime() - t0) / 1_000_000L);
        return new ProtocolInvokeResult(200, json, ms);
    }
}
