package com.lantu.connect.common.config;

import com.lantu.connect.auth.mapper.UserRoleRelMapper;
import com.lantu.connect.auth.support.AccessTokenBlacklist;
import com.lantu.connect.auth.support.SessionRevocationRegistry;
import com.lantu.connect.common.filter.JwtAuthenticationFilter;
import com.lantu.connect.common.filter.UnassignedUserAccessFilter;
import com.lantu.connect.common.idempotency.IdempotencyFilter;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.util.JwtUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

/**
 * Security 配置
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final SecurityProperties securityProperties;

    public SecurityConfig(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationFilter jwtAuthenticationFilter,
                                           UnassignedUserAccessFilter unassignedUserAccessFilter,
                                           IdempotencyFilter idempotencyFilter) throws Exception {
        String[] permitPatterns = securityProperties.getPermitPatterns().toArray(String[]::new);
        if (securityProperties.isRequireHttps()) {
            http.requiresChannel(channel -> channel.anyRequest().requiresSecure());
        }

        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED,
                                        ResultCode.UNAUTHORIZED.getCode(), "未认证"))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeJsonError(response, HttpServletResponse.SC_FORBIDDEN,
                                        ResultCode.FORBIDDEN.getCode(), "权限不足")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(permitPatterns).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(unassignedUserAccessFilter, JwtAuthenticationFilter.class)
                .addFilterAfter(idempotencyFilter, UnassignedUserAccessFilter.class);
        return http.build();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtUtil jwtUtil,
                                                           AccessTokenBlacklist accessTokenBlacklist,
                                                           SessionRevocationRegistry sessionRevocationRegistry,
                                                           SecurityProperties securityProperties) {
        return new JwtAuthenticationFilter(jwtUtil, accessTokenBlacklist, sessionRevocationRegistry, securityProperties);
    }

    @Bean
    public UnassignedUserAccessFilter unassignedUserAccessFilter(UserRoleRelMapper userRoleRelMapper) {
        return new UnassignedUserAccessFilter(userRoleRelMapper);
    }

    private static void writeJsonError(HttpServletResponse response, int httpStatus, int code, String message)
            throws java.io.IOException {
        response.setStatus(httpStatus);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":" + code + ",\"message\":\"" + escapeJson(message) + "\",\"data\":null}");
    }

    private static String escapeJson(String s) {
        if (!StringUtils.hasText(s)) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
