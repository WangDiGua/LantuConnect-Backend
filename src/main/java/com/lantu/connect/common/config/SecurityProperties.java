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
     * 是否将 {@code /actuator/prometheus} 加入匿名可访问列表。
     * 默认 false（指标可能泄露 JVM/业务度量）；需裸拉取时再设为 true，或改用需鉴权的 scrape。
     */
    private boolean permitPrometheusWithoutAuth = false;

    /**
     * 是否把 Swagger / OpenAPI 保留在匿名白名单中（生产建议 false 且关闭 springdoc）。
     */
    private boolean exposeApiDocs = true;

    /**
     * 是否信任 {@code X-Forwarded-For}（仅在上游为可信反向代理时开启）。
     */
    private boolean trustProxyForwardedHeaders = false;

    /**
     * 本地联调专用：为 true 时跳过数据源账号「禁止 root / 弱口令」的启动校验，便于使用本机 {@code root} 账号。
     * 生产必须为 false；{@code prod} profile 下若误开将直接启动失败。
     */
    private boolean allowInsecureLocalDatabaseCredentials = false;

    /**
     * 无需认证的 Servlet 路径（不含 context-path），Ant 风格。
     * 注意：不包含 {@code /actuator/prometheus}，由 {@link #permitPrometheusWithoutAuth} 控制。
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
            "/actuator/info"
    ));
}
