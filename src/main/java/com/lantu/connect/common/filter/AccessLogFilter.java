package com.lantu.connect.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.lantu.connect.common.web.TraceLogging;
import com.lantu.connect.sysconfig.runtime.RuntimeAppConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class AccessLogFilter extends OncePerRequestFilter {

    private static final String IGNORED_PATHS = "/actuator,/swagger-ui,/v3/api-docs";

    private final RuntimeAppConfigService runtimeAppConfigService;

    /** SSE/chunked 长响应不能使用 ContentCachingResponseWrapper 全量缓存，否则易占满内存。 */
    private static boolean isStreamingInvokePath(String uri) {
        return uri != null && uri.contains("/invoke-stream");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        String traceIdFull = TraceLogging.traceIdOrDash();
        String shortTraceId = traceIdFull.length() >= 8 && !"-".equals(traceIdFull)
                ? traceIdFull.substring(0, 8)
                : "--------";

        String uri = request.getRequestURI();
        String query = request.getQueryString();
        String fullUrl = query != null ? uri + "?" + query : uri;

        var logCfg = runtimeAppConfigService.logging();
        if (isStreamingInvokePath(uri)) {
            try {
                chain.doFilter(request, response);
            } finally {
                int st = response.getStatus();
                String hintStream = st >= 400 ? "(stream-body-not-captured)" : null;
                logAccessLine(request.getMethod(), fullUrl, traceIdFull, shortTraceId, start, st, uri, hintStream);
            }
            return;
        }

        ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(request, wrapped);
        } finally {
            int status = wrapped.getStatus();
            String bodyPreview = status >= 400 && logCfg.getAccessLogErrorBodyMaxChars() > 0
                    ? responseBodyPreview(wrapped, uri, logCfg.getAccessLogErrorBodyMaxChars(),
                    logCfg.getAccessLogErrorBodyMaxBytes(),
                    logCfg.getAccessLogSkipErrorBodyPathFragments())
                    : null;
            logAccessLine(request.getMethod(), fullUrl, traceIdFull, shortTraceId, start, status, uri, bodyPreview);
            wrapped.copyBodyToResponse();
        }
    }

    private void logAccessLine(String method, String fullUrl, String traceIdFull, String shortTraceId,
                               long start, int status, String uri, String bodyPreview) {
        long elapsed = System.currentTimeMillis() - start;
        String userId = MDC.get("userId");
        String user = userId != null ? userId : "-";

        if (isIgnoredPath(uri)) {
            return;
        }
        if (status >= 400) {
            String preview = bodyPreview != null ? bodyPreview : "";
            log.warn(
                    ">>> {} {} traceId={} shortId=[{}] ({}ms) user={} status={} responsePreview={}",
                    method, fullUrl, traceIdFull, shortTraceId, elapsed, user, status, preview);
        } else if (elapsed > 3000) {
            log.warn(">>> {} {} traceId={} shortId=[{}] ({}ms) user={} status={} [SLOW]",
                    method, fullUrl, traceIdFull, shortTraceId, elapsed, user, status);
        } else {
            log.info(">>> {} {} traceId={} shortId=[{}] ({}ms) user={} status={}",
                    method, fullUrl, traceIdFull, shortTraceId, elapsed, user, status);
        }
    }

    private boolean isIgnoredPath(String uri) {
        for (String ignored : IGNORED_PATHS.split(",")) {
            if (uri.startsWith(ignored.trim())) {
                return true;
            }
        }
        return false;
    }

    private static String responseBodyPreview(ContentCachingResponseWrapper wrapped, String requestUri, int maxChars,
                                              int maxBytes, String skipPathFragments) {
        if (shouldSkipBodyPreviewForPath(requestUri, skipPathFragments)) {
            return "(body-redacted-by-path-policy)";
        }
        byte[] buf = wrapped.getContentAsByteArray();
        if (buf == null || buf.length == 0) {
            return "";
        }
        if (maxBytes > 0 && buf.length > maxBytes) {
            return "(body-too-large-for-log: " + buf.length + " bytes)";
        }
        String s = new String(buf, StandardCharsets.UTF_8);
        s = redactSensitiveJsonish(s);
        if (s.length() <= maxChars) {
            return s.replace("\r\n", " ").replace('\n', ' ');
        }
        return s.substring(0, maxChars).replace("\r\n", " ").replace('\n', ' ') + "...";
    }

    private static boolean shouldSkipBodyPreviewForPath(String uri, String fragmentsConfig) {
        if (uri == null || fragmentsConfig == null || fragmentsConfig.isBlank()) {
            return false;
        }
        String u = uri.toLowerCase(Locale.ROOT);
        for (String raw : fragmentsConfig.split(",")) {
            String frag = raw.trim().toLowerCase(Locale.ROOT);
            if (!frag.isEmpty() && u.contains(frag)) {
                return true;
            }
        }
        return false;
    }

    /** 弱脱敏：错误日志预览中常见 token 字段，减少误打明文。 */
    private static String redactSensitiveJsonish(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return s.replaceAll("(?i)\"(access_token|refresh_token|password|token|secret)\"\\s*:\\s*\"[^\"]*\"",
                "\"$1\":\"[REDACTED]\"");
    }
}
