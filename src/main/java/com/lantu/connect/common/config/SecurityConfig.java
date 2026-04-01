package com.lantu.connect.common.config;

import com.lantu.connect.auth.mapper.UserRoleRelMapper;
import com.lantu.connect.auth.support.AccessTokenBlacklist;
import com.lantu.connect.auth.support.SessionRevocationRegistry;
import com.lantu.connect.common.filter.JwtAuthenticationFilter;
import com.lantu.connect.common.filter.PathRateLimitWebFilter;
import com.lantu.connect.common.filter.UnassignedUserAccessFilter;
import com.lantu.connect.common.web.ClientIpResolver;
import com.lantu.connect.gateway.security.ApiKeyScopeService;
import com.lantu.connect.common.idempotency.IdempotencyFilter;
import com.lantu.connect.sysconfig.service.PathRateLimitRuleCache;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.util.JsonStringEscaper;
import com.lantu.connect.common.util.JwtUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
                                           PathRateLimitWebFilter pathRateLimitWebFilter,
                                           JwtAuthenticationFilter jwtAuthenticationFilter,
                                           UnassignedUserAccessFilter unassignedUserAccessFilter,
                                           IdempotencyFilter idempotencyFilter) throws Exception {
        List<String> permitList = new ArrayList<>(securityProperties.getPermitPatterns());
        if (!securityProperties.isExposeApiDocs()) {
            permitList.removeIf(p -> {
                String x = Objects.toString(p, "");
                return x.contains("swagger") || x.contains("api-docs");
            });
        }
        if (securityProperties.isPermitPrometheusWithoutAuth()) {
            permitList.add("/actuator/prometheus");
        }
        String[] permitPatterns = permitList.toArray(String[]::new);
        if (securityProperties.isRequireHttps()) {
            http.requiresChannel(channel -> channel.anyRequest().requiresSecure());
        }

        http
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> {
                    headers.contentTypeOptions(Customizer.withDefaults())
                            .frameOptions(frame -> frame.deny())
                            .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                            .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                            .addHeaderWriter((request, response) ->
                                    response.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()"));
                    if (securityProperties.isRequireHttps()) {
                        headers.httpStrictTransportSecurity(hsts -> hsts.maxAgeInSeconds(31536000).includeSubDomains(true));
                    }
                })
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
                .addFilterBefore(pathRateLimitWebFilter, JwtAuthenticationFilter.class)
                .addFilterAfter(unassignedUserAccessFilter, JwtAuthenticationFilter.class)
                .addFilterAfter(idempotencyFilter, UnassignedUserAccessFilter.class);
        return http.build();
    }

    @Bean
    public PathRateLimitWebFilter pathRateLimitWebFilter(PathRateLimitRuleCache pathRateLimitRuleCache,
                                                         ClientIpResolver clientIpResolver,
                                                         StringRedisTemplate stringRedisTemplate) {
        return new PathRateLimitWebFilter(pathRateLimitRuleCache, clientIpResolver, stringRedisTemplate);
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtUtil jwtUtil,
                                                           AccessTokenBlacklist accessTokenBlacklist,
                                                           SessionRevocationRegistry sessionRevocationRegistry,
                                                           SecurityProperties securityProperties,
                                                           ApiKeyScopeService apiKeyScopeService) {
        return new JwtAuthenticationFilter(
                jwtUtil, accessTokenBlacklist, sessionRevocationRegistry, securityProperties, apiKeyScopeService);
    }

    @Bean
    public UnassignedUserAccessFilter unassignedUserAccessFilter(UserRoleRelMapper userRoleRelMapper,
                                                                 SecurityProperties securityProperties) {
        return new UnassignedUserAccessFilter(userRoleRelMapper, securityProperties);
    }

    private static void writeJsonError(HttpServletResponse response, int httpStatus, int code, String message)
            throws java.io.IOException {
        response.setStatus(httpStatus);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":" + code + ",\"message\":\"" + JsonStringEscaper.escape(message) + "\",\"data\":null}");
    }
}
