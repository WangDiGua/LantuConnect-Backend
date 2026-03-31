package com.lantu.connect.common.config;

/**
 * 当 {@code jwt.secret} / {@code JWT_SECRET} 未设置或为空白（例如环境里存在空字符串）时，
 * 非生产环境使用的兜底密钥。须与 {@code application.yml} 中 {@code jwt.secret} 默认值保持一致。
 */
public final class JwtBootstrapSecrets {

    public static final String LOCAL_DEV_FALLBACK =
            "Lc-dev-z9QmK4vR7nX2pW8hYtSbAeUgOiJ5wFxC3jHlMnP6qR0sT1uVkxY7bNd-local-only";

    private JwtBootstrapSecrets() {
    }
}
