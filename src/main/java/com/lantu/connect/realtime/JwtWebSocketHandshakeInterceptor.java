package com.lantu.connect.realtime;

import com.lantu.connect.auth.support.AccessTokenBlacklist;
import com.lantu.connect.auth.support.SessionRevocationRegistry;
import com.lantu.connect.common.config.SecurityProperties;
import com.lantu.connect.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手中通过 query {@code access_token} 校验 Access JWT（浏览器无法为握手设置 Authorization）。
 */
@Component
@RequiredArgsConstructor
public class JwtWebSocketHandshakeInterceptor implements HandshakeInterceptor {

    static final String ATTR_USER_ID = "userId";

    private final JwtUtil jwtUtil;
    private final AccessTokenBlacklist accessTokenBlacklist;
    private final SessionRevocationRegistry sessionRevocationRegistry;
    private final SecurityProperties securityProperties;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletReq)) {
            return false;
        }
        String token = servletReq.getServletRequest().getParameter("access_token");
        if (!StringUtils.hasText(token)) {
            return false;
        }
        if (securityProperties.isJwtEnabled() && accessTokenBlacklist.contains(token)) {
            return false;
        }
        try {
            Claims claims = jwtUtil.parseToken(token.trim());
            if ("refresh".equals(claims.get("type", String.class))) {
                return false;
            }
            if (securityProperties.isJwtEnabled()) {
                String tokenSid = claims.get("sid", String.class);
                if (StringUtils.hasText(tokenSid) && sessionRevocationRegistry.isRevoked(tokenSid)) {
                    return false;
                }
            }
            long userId = Long.parseLong(claims.getSubject());
            attributes.put(ATTR_USER_ID, userId);
            return true;
        } catch (ExpiredJwtException | JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
    }
}
