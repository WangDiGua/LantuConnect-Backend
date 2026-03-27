package com.lantu.connect.common.config;

import com.lantu.connect.common.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jwt 配置
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Configuration
public class JwtConfig {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiry}")
    private long accessTokenExpiry;

    @Value("${jwt.refresh-token-expiry}")
    private long refreshTokenExpiry;

    @Bean
    public JwtUtil jwtUtil() {
        return new JwtUtil(secret, accessTokenExpiry, refreshTokenExpiry);
    }
}
