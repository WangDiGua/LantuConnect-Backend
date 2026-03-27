package com.lantu.connect.common.filter;

import com.lantu.connect.auth.mapper.UserRoleRelMapper;
import com.lantu.connect.common.result.ResultCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 未赋权账号仅允许访问申请开发者相关接口。
 */
@RequiredArgsConstructor
public class UnassignedUserAccessFilter extends OncePerRequestFilter {

    private static final List<String> UNASSIGNED_ALLOWED_PATTERNS = List.of(
            "/auth/me",
            "/auth/logout",
            "/auth/refresh",
            "/auth/profile",
            "/auth/change-password",
            "/auth/bind-phone",
            "/auth/login-history",
            "/developer/applications",
            "/developer/applications/**",
            "/error",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/actuator/**"
    );

    private final UserRoleRelMapper userRoleRelMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        Long userId = resolveAuthenticatedUserId();
        if (userId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean hasRole = !userRoleRelMapper.selectRoleIdsByUserId(userId).isEmpty();
        if (hasRole) {
            filterChain.doFilter(request, response);
            return;
        }

        String servletPath = request.getServletPath();
        for (String pattern : UNASSIGNED_ALLOWED_PATTERNS) {
            if (pathMatcher.match(pattern, servletPath)) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        writeForbidden(response, "未赋权账号仅可提交开发者入驻申请");
    }

    private static void writeForbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String body = "{\"code\":" + ResultCode.FORBIDDEN.getCode() + ",\"message\":\"" + escapeJson(message) + "\",\"data\":null}";
        response.getWriter().write(body);
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static Long resolveAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal() == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(authentication.getPrincipal()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
