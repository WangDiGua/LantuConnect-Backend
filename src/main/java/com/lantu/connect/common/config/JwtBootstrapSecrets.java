package com.lantu.connect.common.config;

/**
 * 当 {@code jwt.secret} / {@code JWT_SECRET} 未设置或为空白（例如环境里存在空字符串）时，
 * 使用的仅适用于本地启动的兜底密钥。生产环境必须在环境变量或 {@code application-local.yml} 中显式配置 {@code jwt.secret}。
 */
public final class JwtBootstrapSecrets {

    public static final String LOCAL_DEV_FALLBACK =
            "Lc-dev-z9QmK4vR7nX2pW8hYtSbAeUgOiJ5wFxC3jHlMnP6qR0sT1uVkxY7bNd-local-only";

    private JwtBootstrapSecrets() {
    }
}
