package com.lantu.connect.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Cors 配置
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origins:http://localhost:3000}")
    private String allowedOriginsRaw;

    @Value("${cors.relax-localhost:true}")
    private boolean relaxLocalhost;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        Set<String> patterns = new LinkedHashSet<>();
        if (relaxLocalhost) {
            patterns.add("http://localhost:*");
            patterns.add("http://127.0.0.1:*");
            patterns.add("https://localhost:*");
            patterns.add("https://127.0.0.1:*");
        }
        Arrays.stream(allowedOriginsRaw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .forEach(patterns::add);
        if (patterns.isEmpty()) {
            patterns.add("http://localhost:*");
            patterns.add("http://127.0.0.1:*");
        }
        List<String> patternList = new ArrayList<>(patterns);
        registry.addMapping("/**")
                .allowedOriginPatterns(patternList.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders(
                        "Authorization",
                        "Content-Type",
                        "Accept",
                        "X-Api-Key",
                        "X-Trace-Id",
                        "X-Request-Id",
                        "X-Sandbox-Token",
                        "Idempotency-Key")
                .exposedHeaders("X-Trace-Id", "X-Request-Id", "X-Total-Count")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
