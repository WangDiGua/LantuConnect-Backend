package com.lantu.connect.common.filter;

import com.lantu.connect.auth.support.AccessTokenBlacklist;
import com.lantu.connect.auth.support.SessionRevocationRegistry;
import com.lantu.connect.common.config.SecurityProperties;
import com.lantu.connect.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;

/**
 * 从 Authorization Bearer 解析用户 ID，校验黑名单；可通过包装请求注入 X-User-Id 供下游 Controller 使用。
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final AccessTokenBlacklist accessTokenBlacklist;
    private final SessionRevocationRegistry sessionRevocationRegistry;
    private final SecurityProperties securityProperties;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String servletPath = request.getServletPath();
        if (isPermitted(servletPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!securityProperties.isJwtEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String bearer = resolveBearerToken(request);
        Long userIdFromJwt = null;
        if (StringUtils.hasText(bearer)) {
            if (accessTokenBlacklist.contains(bearer)) {
                writeUnauthorized(response, "Token 已失效");
                return;
            }
            try {
                Claims claims = jwtUtil.parseToken(bearer);
                if ("refresh".equals(claims.get("type", String.class))) {
                    writeUnauthorized(response, "请使用 Access Token");
                    return;
                }
                String tokenSid = claims.get("sid", String.class);
                if (StringUtils.hasText(tokenSid) && sessionRevocationRegistry.isRevoked(tokenSid)) {
                    writeUnauthorized(response, "会话已失效");
                    return;
                }
                userIdFromJwt = Long.valueOf(claims.getSubject());
            } catch (ExpiredJwtException e) {
                writeUnauthorized(response, "Token 已过期");
                return;
            } catch (JwtException | IllegalArgumentException e) {
                writeUnauthorized(response, "Token 无效");
                return;
            }
        }

        if (userIdFromJwt != null) {
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    String.valueOf(userIdFromJwt), null, java.util.List.of());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(new UserIdHeaderRequestWrapper(request, userIdFromJwt), response);
            return;
        }

        // Sandbox invoke uses X-Sandbox-Token as primary auth material.
        if ("/sandbox/invoke".equalsIgnoreCase(servletPath)
                && StringUtils.hasText(request.getHeader("X-Sandbox-Token"))) {
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    "sandbox-token", null, java.util.List.of());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
            return;
        }

        // API Key flows should not be blocked by JWT-only gate.
        if (StringUtils.hasText(request.getHeader("X-Api-Key"))) {
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    "api-key", null, java.util.List.of());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
            return;
        }

        if (securityProperties.isAllowHeaderUserIdFallback()
                && StringUtils.hasText(request.getHeader("X-User-Id"))) {
            filterChain.doFilter(request, response);
            return;
        }

        writeUnauthorized(response, "未认证");
    }

    private boolean isPermitted(String servletPath) {
        for (String pattern : securityProperties.getPermitPatterns()) {
            if (pathMatcher.match(pattern, servletPath)) {
                return true;
            }
        }
        return false;
    }

    private static String resolveBearerToken(HttpServletRequest request) {
        String h = request.getHeader("Authorization");
        if (!StringUtils.hasText(h) || !h.startsWith("Bearer ")) {
            return null;
        }
        return h.substring(7).trim();
    }

    private static void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String body = "{\"code\":1002,\"message\":\"" + escapeJson(message) + "\",\"data\":null}";
        response.getWriter().write(body);
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class UserIdHeaderRequestWrapper extends HttpServletRequestWrapper {

        private final Long userId;

        UserIdHeaderRequestWrapper(HttpServletRequest request, Long userId) {
            super(request);
            this.userId = userId;
        }

        @Override
        public String getHeader(String name) {
            if ("X-User-Id".equalsIgnoreCase(name)) {
                return String.valueOf(userId);
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if ("X-User-Id".equalsIgnoreCase(name)) {
                return Collections.enumeration(Collections.singletonList(String.valueOf(userId)));
            }
            return super.getHeaders(name);
        }
    }
}
