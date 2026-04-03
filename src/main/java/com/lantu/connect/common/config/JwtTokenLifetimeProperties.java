package com.lantu.connect.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 仅 token 有效期；密钥仍用 {@code jwt.secret}，勿写入 runtime JSON。
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtTokenLifetimeProperties {

    private long accessTokenExpiry = 7200L;
    private long refreshTokenExpiry = 604800L;
}
