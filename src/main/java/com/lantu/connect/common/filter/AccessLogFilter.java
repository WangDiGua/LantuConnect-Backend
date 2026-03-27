package com.lantu.connect.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AccessLogFilter extends OncePerRequestFilter {

    private static final String IGNORED_PATHS = "/actuator,/swagger-ui,/v3/api-docs";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        String traceId = MDC.get("traceId");
        String shortTraceId = traceId != null && traceId.length() >= 8 
                ? traceId.substring(0, 8) 
                : "--------";
        
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        String fullUrl = query != null ? uri + "?" + query : uri;

        ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(request, wrapped);
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            int status = wrapped.getStatus();
            String userId = MDC.get("userId");
            String user = userId != null ? userId : "-";

            if (!isIgnoredPath(uri)) {
                if (status >= 400) {
                    log.warn(">>> {} {} [{}] ({}ms) user={} status={}", 
                            request.getMethod(), fullUrl, shortTraceId, elapsed, user, status);
                } else if (elapsed > 3000) {
                    log.warn(">>> {} {} [{}] ({}ms) user={} status={} [SLOW]", 
                            request.getMethod(), fullUrl, shortTraceId, elapsed, user, status);
                } else {
                    log.info(">>> {} {} [{}] ({}ms) user={} status={}", 
                            request.getMethod(), fullUrl, shortTraceId, elapsed, user, status);
                }
            }
            wrapped.copyBodyToResponse();
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
}
