package com.lantu.connect.compat.robotfactory.service;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.compat.robotfactory.dto.RobotFactoryResourceContext;
import com.lantu.connect.compat.robotfactory.dto.RobotFactorySessionContext;
import com.lantu.connect.compat.robotfactory.entity.RobotFactoryProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class RobotFactorySessionService {

    private final RobotFactorySettingsService settingsService;

    private final Map<String, RobotFactorySessionContext> sessions = new ConcurrentHashMap<>();

    public RobotFactorySessionContext createSession(RobotFactoryProjection projection,
                                                    RobotFactoryResourceContext resource) {
        cleanupExpiredSessions();
        long now = System.currentTimeMillis();
        String sessionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(0L);
        RobotFactorySessionContext session = RobotFactorySessionContext.builder()
                .sessionId(sessionId)
                .projectionId(projection.getId())
                .resourceId(resource.getResourceId())
                .createdAtMillis(now)
                .lastAccessMillis(now)
                .emitter(emitter)
                .projection(projection)
                .resource(resource)
                .build();
        emitter.onTimeout(() -> removeSession(sessionId));
        emitter.onCompletion(() -> removeSession(sessionId));
        emitter.onError(ex -> removeSession(sessionId));
        sessions.put(sessionId, session);
        return session;
    }

    public RobotFactorySessionContext requireSession(String sessionId) {
        cleanupExpiredSessions();
        RobotFactorySessionContext session = sessions.get(sessionId);
        if (session == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "session not found or closed");
        }
        session.setLastAccessMillis(System.currentTimeMillis());
        return session;
    }

    public void sendEndpointEvent(RobotFactorySessionContext session) throws IOException {
        session.getEmitter().send(SseEmitter.event()
                .name("endpoint")
                .data("message?session_id=" + session.getSessionId(), MediaType.TEXT_PLAIN));
    }

    public void sendMessageEvent(RobotFactorySessionContext session, String json) throws IOException {
        session.getEmitter().send(SseEmitter.event()
                .name("message")
                .data(json, MediaType.APPLICATION_JSON));
    }

    public void removeSession(String sessionId) {
        RobotFactorySessionContext removed = sessions.remove(sessionId);
        if (removed != null) {
            try {
                removed.getEmitter().complete();
            } catch (Exception ignored) {
            }
        }
    }

    public void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        long idleMs = Duration.ofMinutes(Math.max(1, settingsService.getSessionIdleMinutes())).toMillis();
        long lifetimeMs = Duration.ofMinutes(Math.max(1, settingsService.getSessionMaxLifetimeMinutes())).toMillis();
        sessions.entrySet().removeIf(entry -> {
            RobotFactorySessionContext session = entry.getValue();
            boolean idleExpired = now - session.getLastAccessMillis() > idleMs;
            boolean lifetimeExpired = now - session.getCreatedAtMillis() > lifetimeMs;
            if (idleExpired || lifetimeExpired) {
                try {
                    session.getEmitter().complete();
                } catch (Exception ignored) {
                }
                return true;
            }
            return false;
        });
    }
}
