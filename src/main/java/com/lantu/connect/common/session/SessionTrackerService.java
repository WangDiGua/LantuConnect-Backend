package com.lantu.connect.common.session;

import com.lantu.connect.common.time.DisplayDateTimeFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 会话追踪服务
 *
 * @author 王帝
 * @date 2026-03-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionTrackerService {

    private static final String USER_SESSIONS_PREFIX = "lantu:session:user:";
    private static final String SESSION_USER_PREFIX = "lantu:session:token:";
    private static final String SESSION_META_PREFIX = "lantu:session:meta:";
    private static final Duration SESSION_TIMEOUT = Duration.ofDays(7);
    private static final DateTimeFormatter DT_FMT = DisplayDateTimeFormat.FORMATTER;

    private final StringRedisTemplate stringRedisTemplate;

    public void trackSession(Long userId, String sessionId) {
        String userSessionsKey = USER_SESSIONS_PREFIX + userId;
        String sessionUserKey = SESSION_USER_PREFIX + sessionId;
        stringRedisTemplate.opsForSet().add(userSessionsKey, sessionId);
        stringRedisTemplate.opsForValue().set(sessionUserKey, String.valueOf(userId), Objects.requireNonNull(SESSION_TIMEOUT));
        stringRedisTemplate.expire(userSessionsKey, SESSION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        log.debug("追踪会话: userId={}, sessionId={}", userId, sessionId);
    }

    public void trackSessionWithMeta(Long userId, String sessionId,
                                     String ip, String userAgent) {
        trackSession(userId, sessionId);

        String metaKey = SESSION_META_PREFIX + sessionId;
        Map<String, String> meta = new HashMap<>();
        meta.put("ip", ip != null ? ip : "");
        meta.put("userAgent", userAgent != null ? userAgent : "");
        meta.put("loginAt", LocalDateTime.now().format(DT_FMT));
        meta.put("lastActiveAt", LocalDateTime.now().format(DT_FMT));
        meta.put("device", parseDevice(userAgent));
        meta.put("os", parseOs(userAgent));
        meta.put("browser", parseBrowser(userAgent));
        stringRedisTemplate.opsForHash().putAll(metaKey, meta);
        stringRedisTemplate.expire(metaKey, SESSION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void touchSession(String sessionId) {
        String metaKey = SESSION_META_PREFIX + sessionId;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(metaKey))) {
            stringRedisTemplate.opsForHash().put(metaKey, "lastActiveAt",
                    LocalDateTime.now().format(DT_FMT));
        }
    }

    public Map<String, String> getSessionMeta(String sessionId) {
        String metaKey = SESSION_META_PREFIX + sessionId;
        Map<Object, Object> raw = Objects.requireNonNull(stringRedisTemplate.opsForHash().entries(metaKey));
        if (raw.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new HashMap<>();
        raw.forEach((k, v) -> result.put(String.valueOf(k), String.valueOf(v)));
        return result;
    }

    public void updateSessionMetaField(String sessionId, String field, String value) {
        if (field == null) {
            return;
        }
        String metaKey = SESSION_META_PREFIX + sessionId;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(metaKey))) {
            stringRedisTemplate.opsForHash().put(metaKey, field, value != null ? value : "");
        }
    }

    public void removeSession(String sessionId) {
        String sessionUserKey = SESSION_USER_PREFIX + sessionId;
        String userIdStr = stringRedisTemplate.opsForValue().get(sessionUserKey);
        if (userIdStr != null) {
            Long userId = Long.valueOf(userIdStr);
            String userSessionsKey = USER_SESSIONS_PREFIX + userId;
            stringRedisTemplate.opsForSet().remove(userSessionsKey, sessionId);
            stringRedisTemplate.delete(sessionUserKey);
            stringRedisTemplate.delete(SESSION_META_PREFIX + sessionId);
            log.debug("移除会话: userId={}, sessionId={}", userId, sessionId);
        }
    }

    public long getActiveSessionCount(Long userId) {
        String userSessionsKey = USER_SESSIONS_PREFIX + userId;
        Long count = stringRedisTemplate.opsForSet().size(userSessionsKey);
        return count != null ? count : 0L;
    }

    public Set<String> getActiveSessions(Long userId) {
        String userSessionsKey = USER_SESSIONS_PREFIX + userId;
        return stringRedisTemplate.opsForSet().members(userSessionsKey);
    }

    public void removeAllUserSessions(Long userId) {
        String userSessionsKey = USER_SESSIONS_PREFIX + userId;
        Set<String> sessions = stringRedisTemplate.opsForSet().members(userSessionsKey);
        if (sessions != null) {
            for (String sessionId : sessions) {
                stringRedisTemplate.delete(SESSION_USER_PREFIX + sessionId);
                stringRedisTemplate.delete(SESSION_META_PREFIX + sessionId);
            }
        }
        stringRedisTemplate.delete(userSessionsKey);
        log.debug("移除用户所有会话: userId={}", userId);
    }

    private static String parseDevice(String ua) {
        if (ua == null) return "Unknown";
        String lower = ua.toLowerCase();
        if (lower.contains("mobile") || lower.contains("android") || lower.contains("iphone")) return "Mobile";
        if (lower.contains("tablet") || lower.contains("ipad")) return "Tablet";
        return "Desktop";
    }

    private static String parseOs(String ua) {
        if (ua == null) return "Unknown";
        if (ua.contains("Windows")) return "Windows";
        if (ua.contains("Mac OS")) return "macOS";
        if (ua.contains("Linux")) return "Linux";
        if (ua.contains("Android")) return "Android";
        if (ua.contains("iPhone") || ua.contains("iPad")) return "iOS";
        return "Unknown";
    }

    private static String parseBrowser(String ua) {
        if (ua == null) return "Unknown";
        if (ua.contains("Edg/")) return "Edge";
        if (ua.contains("Chrome/") && !ua.contains("Edg/")) return "Chrome";
        if (ua.contains("Firefox/")) return "Firefox";
        if (ua.contains("Safari/") && !ua.contains("Chrome/")) return "Safari";
        return "Unknown";
    }
}
