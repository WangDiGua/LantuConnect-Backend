package com.lantu.connect.common.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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

    @Value("${spring.datasource.url:}")
    private String dbUrl;

    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    @Value("${springdoc.api-docs.enabled:true}")
    private boolean springdocApiDocsEnabled;

    @Value("${lantu.encryption.key:lantu-connect-encryption-key-32b}")
    private String legacyEncryptionKey;

    private final SecurityProperties securityProperties;

    public SecurityConfigValidator(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @PostConstruct
    public void validate() {
        if (!isProductionLike() && !StringUtils.hasText(jwtSecret)) {
            log.warn("jwt.secret / JWT_SECRET is blank; using embedded local-dev fallback (set JWT_SECRET for multi-machine or shared dev)");
        }
        String effectiveJwt = StringUtils.hasText(jwtSecret) ? jwtSecret : JwtBootstrapSecrets.LOCAL_DEV_FALLBACK;
        if (isProductionLike()) {
            if (!StringUtils.hasText(jwtSecret)) {
                throw new IllegalStateException("Production requires JWT_SECRET");
            }
            if (jwtSecret.length() < 32) {
                throw new IllegalStateException("JWT_SECRET must be at least 32 characters long");
            }
            if (JwtBootstrapSecrets.LOCAL_DEV_FALLBACK.equals(jwtSecret.trim())) {
                throw new IllegalStateException("Production must not use the embedded local-dev JWT secret");
            }
        } else if (effectiveJwt.length() < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 characters long");
        }
        if (dbUsername == null || dbUsername.isBlank()) {
            throw new IllegalStateException("DB_USER environment variable must be set");
        }
        if (dbPassword == null || dbPassword.isBlank()) {
            throw new IllegalStateException("DB_PASSWORD environment variable must be set");
        }
        if (isProductionLike() && !isLocalProfileActive() && securityProperties.isAllowInsecureLocalDatabaseCredentials()) {
            throw new IllegalStateException("Production profile must not set lantu.security.allow-insecure-local-database-credentials=true");
        }
        if (securityProperties.isAllowInsecureLocalDatabaseCredentials()) {
            log.warn("lantu.security.allow-insecure-local-database-credentials=true: skipping weak DB credential checks (local dev only)");
        } else {
            rejectWeakDatabaseCredentials();
        }
        if (isProductionLike()) {
            if (!securityProperties.isJwtEnabled()) {
                throw new IllegalStateException("Production profile requires lantu.security.jwt-enabled=true");
            }
            if (securityProperties.isAllowHeaderUserIdFallback()) {
                throw new IllegalStateException("Production profile requires lantu.security.allow-header-user-id-fallback=false");
            }
            if (jwtSecret.contains("NexusAI-Dev-JWT-DoNotUseInProd")) {
                throw new IllegalStateException("Production profile must override default JWT secret");
            }
            if (isWeakSecret(jwtSecret)) {
                throw new IllegalStateException("Production profile requires a strong JWT secret");
            }
            if (securityProperties.isPermitPrometheusWithoutAuth()) {
                log.warn("Production: anonymous Prometheus scrape is enabled (lantu.security.permit-prometheus-without-auth). "
                        + "Metrics may expose JVM and business counters; prefer Bearer token or network-restricted scrape.");
            }
            if (!isLocalProfileActive()) {
                if (springdocApiDocsEnabled) {
                    throw new IllegalStateException("Production must disable API docs (springdoc.api-docs.enabled=false and swagger-ui.enabled=false)");
                }
                if (securityProperties.isExposeApiDocs()) {
                    throw new IllegalStateException("Production must set lantu.security.expose-api-docs=false");
                }
                if (legacyEncryptionKey == null || legacyEncryptionKey.isBlank()
                        || "lantu-connect-encryption-key-32b".equals(legacyEncryptionKey.trim())) {
                    throw new IllegalStateException("Production must set LANTU_ENCRYPTION_KEY / lantu.encryption.key to a strong secret");
                }
                if (legacyEncryptionKey.trim().length() < 32) {
                    throw new IllegalStateException("Production encryption key must be at least 32 characters");
                }
                if (dbUrl != null && dbUrl.toLowerCase(Locale.ROOT).contains("usessl=false")) {
                    throw new IllegalStateException("Production datasource must not disable TLS (useSSL=false)");
                }
            } else {
                log.warn("Profile 'local' is active with production-like settings: relaxed checks for Swagger, encryption key defaults, JDBC useSSL, and permit-prometheus (see application-local.yml).");
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

    /** 本机联调 profile，与 prod 叠加时放宽部分启动强校验（勿用于公网）。 */
    private boolean isLocalProfileActive() {
        if (activeProfiles == null || activeProfiles.isBlank()) {
            return false;
        }
        return activeProfiles.toLowerCase(Locale.ROOT).contains("local");
    }

    private void rejectWeakDatabaseCredentials() {
        String user = dbUsername.trim().toLowerCase(Locale.ROOT);
        String pwd = dbPassword.trim();
        if ("root".equals(user)) {
            throw new IllegalStateException("DB_USER must not be root");
        }
        if (isWeakSecret(pwd) || "root".equalsIgnoreCase(pwd) || "password".equalsIgnoreCase(pwd)) {
            throw new IllegalStateException("DB_PASSWORD is too weak");
        }
    }

    private static boolean isWeakSecret(String value) {
        if (value == null) {
            return true;
        }
        String v = value.trim();
        if (v.length() < 16) {
            return true;
        }
        String lower = v.toLowerCase(Locale.ROOT);
        return lower.contains("changeme")
                || lower.contains("default")
                || lower.contains("password")
                || lower.contains("test");
    }
}
