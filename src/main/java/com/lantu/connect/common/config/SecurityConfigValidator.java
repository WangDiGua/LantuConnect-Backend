package com.lantu.connect.common.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Slf4j
@Component
public class SecurityConfigValidator {

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${spring.datasource.username:}")
    private String dbUsername;

    @Value("${spring.datasource.password:}")
    private String dbPassword;

    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    private final SecurityProperties securityProperties;

    public SecurityConfigValidator(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @PostConstruct
    public void validate() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET environment variable must be set");
        }
        if (jwtSecret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 characters long");
        }
        if (dbUsername == null || dbUsername.isBlank()) {
            throw new IllegalStateException("DB_USER environment variable must be set");
        }
        if (dbPassword == null || dbPassword.isBlank()) {
            throw new IllegalStateException("DB_PASSWORD environment variable must be set");
        }
        if (isProductionLike()) {
            if (!securityProperties.isJwtEnabled()) {
                throw new IllegalStateException("Production profile requires lantu.security.jwt-enabled=true");
            }
            if (securityProperties.isAllowHeaderUserIdFallback()) {
                throw new IllegalStateException("Production profile requires lantu.security.allow-header-user-id-fallback=false");
            }
            if (jwtSecret.contains("LantuConnect-Dev-JWT-DoNotUseInProd")) {
                throw new IllegalStateException("Production profile must override default JWT secret");
            }
        }
        log.info("Security configuration validated successfully");
    }

    private boolean isProductionLike() {
        if (activeProfiles == null || activeProfiles.isBlank()) {
            return false;
        }
        String normalized = activeProfiles.toLowerCase(Locale.ROOT);
        return normalized.contains("prod");
    }
}
