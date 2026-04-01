package com.lantu.connect.common.filter;

import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.util.JsonStringEscaper;
import com.lantu.connect.common.web.ClientIpResolver;
import com.lantu.connect.sysconfig.entity.RateLimitRule;
import com.lantu.connect.sysconfig.service.PathRateLimitRuleCache;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * 将 {@code t_rate_limit_rule} 中 {@code target = path} 的规则作用于 HTTP 请求（按客户端 IP 计数），
 * 与 Resilience4j / {@link com.lantu.connect.common.security.RedisAuthRateLimiter} 可叠加。
 */
@RequiredArgsConstructor
public class PathRateLimitWebFilter extends OncePerRequestFilter {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final PathRateLimitRuleCache ruleCache;
    private final ClientIpResolver clientIpResolver;
    private final StringRedisTemplate redis;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String relativePath = relativePath(request);
        String clientIp = normalizeIp(clientIpResolver.resolve(request));
        List<RateLimitRule> rules = ruleCache.snapshot();
        for (RateLimitRule rule : rules) {
            if (rule == null || !StringUtils.hasText(rule.getTargetValue())) {
                continue;
            }
            String pattern = rule.getTargetValue().trim();
            if (!MATCHER.match(pattern, relativePath)) {
                continue;
            }
            long windowMs = rule.getWindowMs() == null ? 60_000L : Math.max(1000L, rule.getWindowMs());
            int max = rule.getMaxRequests() == null ? 100 : Math.max(1, rule.getMaxRequests());
            String bucket = String.valueOf(System.currentTimeMillis() / windowMs);
            String key = "http:pathrl:" + rule.getId() + ":ip:" + clientIp + ":" + bucket;
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, Duration.ofMillis(windowMs + 1500L));
            }
            if (count != null && count > max) {
                writeTooManyRequests(response, rule);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private static String relativePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return "/";
        }
        String ctx = request.getContextPath();
        if (StringUtils.hasText(ctx) && uri.startsWith(ctx)) {
            uri = uri.substring(ctx.length());
        }
        if (!uri.startsWith("/")) {
            uri = "/" + uri;
        }
        return uri;
    }

    private static String normalizeIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return "unknown";
        }
        String t = ip.trim();
        if (t.length() > 128) {
            t = t.substring(0, 128);
        }
        return t;
    }

    private static void writeTooManyRequests(HttpServletResponse response, RateLimitRule rule) throws IOException {
        String name = rule.getName() != null ? rule.getName() : rule.getId();
        String msg = "请求过于频繁（限流规则: " + name + "）";
        response.setStatus(429);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"code\":" + ResultCode.RATE_LIMITED.getCode()
                        + ",\"message\":\"" + JsonStringEscaper.escape(msg)
                        + "\",\"data\":null}");
    }
}
