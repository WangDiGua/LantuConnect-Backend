package com.lantu.connect.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT 令牌工具类
 *
 * @author 王帝
 * @date 2026-03-21
 */
public class JwtUtil {

    private final SecretKey key;
    private final long accessTokenExpiry;
    private final long refreshTokenExpiry;

    public JwtUtil(String secret, long accessTokenExpiry, long refreshTokenExpiry) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiry = accessTokenExpiry * 1000L;
        this.refreshTokenExpiry = refreshTokenExpiry * 1000L;
    }

    public String generateAccessToken(Long userId, String username, Map<String, Object> extraClaims) {
        return buildToken(userId, username, extraClaims, accessTokenExpiry);
    }

    public String generateRefreshToken(Long userId, String username) {
        return buildToken(userId, username, Map.of("type", "refresh"), refreshTokenExpiry);
    }

    private String buildToken(Long userId, String username, Map<String, Object> claims, long expiry) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claims(claims)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiry))
                .signWith(key)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public boolean isTokenExpired(String token) {
        try { return parseToken(token).getExpiration().before(new Date()); }
        catch (ExpiredJwtException e) { return true; }
    }

    public Long getUserId(String token) {
        return Long.valueOf(parseToken(token).getSubject());
    }

    public String getUsername(String token) {
        return parseToken(token).get("username", String.class);
    }

    public boolean isRefreshToken(String token) {
        return "refresh".equals(parseToken(token).get("type", String.class));
    }
}
