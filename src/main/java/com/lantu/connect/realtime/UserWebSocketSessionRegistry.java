package com.lantu.connect.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 按 userId 管理浏览器 WebSocket 会话（多标签页可多连接）。
 *
 * <p><b>多节点部署：</b>注册表为进程内内存。运行多个应用实例且未做跨节点消息广播时，由其它实例触发的
 * {@link RealtimePushService} 无法到达连接在本实例之外的会话。生产多副本需在推送链路上增加 Redis Pub/Sub、
 * 消息队列或与会话亲和的网关策略。
 */
@Component
@Slf4j
public class UserWebSocketSessionRegistry {

    private final ConcurrentHashMap<Long, CopyOnWriteArraySet<WebSocketSession>> byUser = new ConcurrentHashMap<>();

    public void register(Long userId, WebSocketSession session) {
        if (userId == null || session == null) {
            return;
        }
        byUser.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
    }

    public void unregister(Long userId, WebSocketSession session) {
        if (userId == null || session == null) {
            return;
        }
        Set<WebSocketSession> set = byUser.get(userId);
        if (set == null) {
            return;
        }
        set.remove(session);
        if (set.isEmpty()) {
            byUser.remove(userId, set);
        }
    }

    public void sendText(Long userId, String json) {
        if (userId == null || json == null) {
            return;
        }
        Set<WebSocketSession> set = byUser.get(userId);
        if (set == null || set.isEmpty()) {
            return;
        }
        TextMessage msg = new TextMessage(json);
        for (WebSocketSession s : set) {
            if (s == null || !s.isOpen()) {
                set.remove(s);
                continue;
            }
            try {
                synchronized (s) {
                    s.sendMessage(msg);
                }
            } catch (IOException e) {
                log.debug("WebSocket 推送失败 userId={} session={}: {}", userId, s.getId(), e.getMessage());
                try {
                    s.close();
                } catch (IOException ignored) {
                }
                set.remove(s);
            }
        }
    }
}
