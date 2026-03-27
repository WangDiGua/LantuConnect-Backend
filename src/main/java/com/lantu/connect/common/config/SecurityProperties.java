package com.lantu.connect.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 安全相关可配置项：JWT 校验、白名单路径、是否允许仅传 X-User-Id（联调/网关场景）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "lantu.security")
public class SecurityProperties {

    /**
     * 是否启用 JWT 校验（解析 Authorization 并校验黑名单）；关闭则完全回退为仅依赖 X-User-Id。
     */
    private boolean jwtEnabled = true;

    /**
     * 当请求无有效 Bearer 时，是否仍允许使用 X-User-Id（默认 false，避免头注入风险）。
     */
    private boolean allowHeaderUserIdFallback = false;

    /**
     * 是否强制 HTTPS（生产环境建议开启）。
     */
    private boolean requireHttps = false;

    /**
     * 无需认证的 Servlet 路径（不含 context-path），Ant 风格。
     */
    private List<String> permitPatterns = new ArrayList<>(List.of(
            "/auth/login",
            "/auth/register",
            "/auth/refresh",
            "/auth/logout",
            "/auth/send-sms",
            "/catalog/apps/launch",
            "/captcha/**",
            "/error",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/actuator/health",
            "/actuator/info",
            "/actuator/prometheus"
    ));
}
