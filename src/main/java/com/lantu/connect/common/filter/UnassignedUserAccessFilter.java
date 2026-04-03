package com.lantu.connect.common.filter;

import com.lantu.connect.auth.mapper.UserRoleRelMapper;
import com.lantu.connect.common.config.SecurityProperties;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.util.JsonStringEscaper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 未赋权账号（无 {@code user_role_rel}）仅允许访问入驻申请、账号基础能力与 health 等白名单路径。
 * OpenAPI/Swagger 是否加入白名单由 {@link SecurityProperties#isExposeApiDocs()} 控制，应与生产关闭文档暴露策略一致。
 */
public class UnassignedUserAccessFilter extends OncePerRequestFilter {

    private static final List<String> BASE_PATTERNS = List.of(
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
            "/actuator/health",
            "/actuator/info"
    );

    private static final List<String> SWAGGER_PATTERNS = List.of(
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**"
    );

    private final UserRoleRelMapper userRoleRelMapper;
    private final List<String> allowedPatterns;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public UnassignedUserAccessFilter(UserRoleRelMapper userRoleRelMapper, SecurityProperties securityProperties) {
        this.userRoleRelMapper = userRoleRelMapper;
        List<String> p = new ArrayList<>(BASE_PATTERNS);
        if (securityProperties.isExposeApiDocs()) {
            p.addAll(SWAGGER_PATTERNS);
        }
        this.allowedPatterns = List.copyOf(p);
    }

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
        for (String pattern : allowedPatterns) {
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
        String body = "{\"code\":" + ResultCode.FORBIDDEN.getCode() + ",\"message\":\"" + JsonStringEscaper.escape(message) + "\",\"data\":null}";
        response.getWriter().write(body);
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
