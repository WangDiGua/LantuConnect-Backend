package com.lantu.connect.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 用户级实时通道：服务端下发 JSON 事件；客户端可发 {@code {"type":"ping"}} 保活。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserPushWebSocketHandler extends TextWebSocketHandler {

    private final UserWebSocketSessionRegistry registry;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = extractUserId(session);
        if (userId == null) {
            try {
                session.close(CloseStatus.POLICY_VIOLATION);
            } catch (Exception ignored) {
            }
            return;
        }
        registry.register(userId, session);
        log.debug("WebSocket 已连接 userId={} session={}", userId, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String p = message.getPayload();
        if (p != null && p.contains("\"ping\"")) {
            try {
                session.sendMessage(new TextMessage("{\"type\":\"pong\",\"v\":1}"));
            } catch (Exception e) {
                log.debug("WebSocket pong 发送失败: {}", e.getMessage());
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = extractUserId(session);
        registry.unregister(userId, session);
        log.debug("WebSocket 已关闭 userId={} status={}", userId, status);
    }

    private static Long extractUserId(WebSocketSession session) {
        Object v = session.getAttributes().get(JwtWebSocketHandshakeInterceptor.ATTR_USER_ID);
        if (v instanceof Long l) {
            return l;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        return null;
    }
}
