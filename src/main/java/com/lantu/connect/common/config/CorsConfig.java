package com.lantu.connect.common.config;

import com.lantu.connect.sysconfig.runtime.RuntimeAppConfigService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * CORS：按请求从 {@link RuntimeAppConfigService#cors()} 生成配置，支持库内 runtime_app_config 覆盖。
 */
@Configuration
@RequiredArgsConstructor
public class CorsConfig {

    private final RuntimeAppConfigService runtimeAppConfigService;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfigurationSource source = (HttpServletRequest request) -> {
            CorsBootstrapProperties c = runtimeAppConfigService.cors();
            CorsConfiguration cfg = new CorsConfiguration();
            cfg.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            cfg.setExposedHeaders(Arrays.asList("X-Trace-Id", "X-Request-Id", "X-Total-Count"));
            cfg.setAllowCredentials(true);
            cfg.setMaxAge(3600L);
            if (c.isAllowAllOrigins()) {
                cfg.setAllowedOriginPatterns(List.of("*"));
                cfg.setAllowedHeaders(List.of("*"));
                return cfg;
            }
            Set<String> patterns = new LinkedHashSet<>();
            if (c.isRelaxLocalhost()) {
                patterns.add("http://localhost:*");
                patterns.add("http://127.0.0.1:*");
                patterns.add("https://localhost:*");
                patterns.add("https://127.0.0.1:*");
            }
            String raw = c.getAllowedOrigins() != null ? c.getAllowedOrigins() : "";
            Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .forEach(patterns::add);
            if (patterns.isEmpty()) {
                patterns.add("http://localhost:*");
                patterns.add("http://127.0.0.1:*");
            }
            cfg.setAllowedOriginPatterns(new ArrayList<>(patterns));
            cfg.setAllowedHeaders(Arrays.asList(
                    "Authorization",
                    "Content-Type",
                    "Accept",
                    "X-Api-Key",
                    "X-Trace-Id",
                    "X-Request-Id",
                    "X-Sandbox-Token",
                    "Idempotency-Key"));
            return cfg;
        };
        return new CorsFilter(source);
    }
}
