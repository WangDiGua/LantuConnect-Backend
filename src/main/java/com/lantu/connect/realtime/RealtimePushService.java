package com.lantu.connect.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.notification.entity.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 向在线用户的浏览器 WebSocket 推送站内通知与未读数变化。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RealtimePushService {

    private static final int PROTOCOL_VERSION = 1;

    private final UserWebSocketSessionRegistry registry;
    private final ObjectMapper objectMapper;

    public void pushNotificationCreated(Long userId, Notification n, long unreadCount) {
        if (userId == null || n == null) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("v", PROTOCOL_VERSION);
        body.put("type", "notification");
        body.put("action", "created");
        body.put("notification", toPayload(n));
        body.put("unreadCount", unreadCount);
        send(userId, body);
    }

    public void pushUnreadSync(Long userId, long unreadCount) {
        if (userId == null) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("v", PROTOCOL_VERSION);
        body.put("type", "notification");
        body.put("action", "unread_sync");
        body.put("unreadCount", unreadCount);
        send(userId, body);
    }

    private void send(Long userId, Map<String, Object> body) {
        try {
            registry.sendText(userId, objectMapper.writeValueAsString(body));
        } catch (JsonProcessingException e) {
            log.warn("实时推送序列化失败 userId={}: {}", userId, e.getMessage());
        }
    }

    private static Map<String, Object> toPayload(Notification n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", n.getId());
        m.put("userId", n.getUserId());
        m.put("type", n.getType());
        m.put("title", n.getTitle());
        m.put("body", n.getBody());
        m.put("sourceType", n.getSourceType());
        m.put("sourceId", n.getSourceId());
        m.put("isRead", n.getIsRead());
        m.put("createTime", n.getCreateTime());
        return m;
    }
}
