package com.lantu.connect.common.filter;

import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.sysconfig.runtime.RuntimeAppConfigService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
/**
 * 旧接口第一阶段下线：仅标记废弃，不做硬删除。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
@Slf4j
@RequiredArgsConstructor
public class LegacyApiDeprecationFilter extends OncePerRequestFilter {

    private final RuntimeAppConfigService runtimeAppConfigService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        var properties = runtimeAppConfigService.apiDeprecation();
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getServletPath();
        if (isDeprecatedPath(path)) {
            response.addHeader("Deprecation", "true");
            response.addHeader("Sunset", "TBD-by-review");
            response.addHeader("Link", "</catalog/resources>; rel=\"successor-version\"");
            log.warn("legacy-api-called method={} path={} from={}", request.getMethod(), path, request.getRemoteAddr());
            if (isWriteMethod(request.getMethod()) && isWriteBlockedPath(path)) {
                writeGone(response);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isDeprecatedPath(String path) {
        for (String pattern : runtimeAppConfigService.apiDeprecation().getDeprecatedPatterns()) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private boolean isWriteBlockedPath(String path) {
        for (String pattern : runtimeAppConfigService.apiDeprecation().getWriteBlockedPatterns()) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWriteMethod(String method) {
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method);
    }

    private static void writeGone(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_GONE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":" + ResultCode.NOT_FOUND.getCode() + ",\"message\":\"接口已下线，请迁移到统一网关接口\",\"data\":null}");
    }
}
