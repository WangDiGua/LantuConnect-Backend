package com.lantu.connect.gateway.security;

import com.lantu.connect.auth.entity.PlatformRole;
import com.lantu.connect.auth.mapper.PlatformRoleMapper;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.sysconfig.service.QuotaCheckService;
import com.lantu.connect.usermgmt.entity.ApiKey;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GatewayGovernanceService {

    private final QuotaCheckService quotaCheckService;
    private final PlatformRoleMapper platformRoleMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final JdbcTemplate jdbcTemplate;

    public InvokeGovernanceLease applyPreInvoke(Long userId, ApiKey apiKey, String resourceType, Long resourceId, int tokens) {
        Long quotaUserId = resolveQuotaUserId(userId, apiKey);
        if (quotaUserId != null) {
            quotaCheckService.checkAndConsume(quotaUserId, Math.max(1, tokens));
        }

        List<PlatformRole> roles = userId == null ? Collections.emptyList() : platformRoleMapper.selectRolesByUserId(userId);
        enforceRuleRateLimit(userId, roles);
        enforceResourceRateLimit(resourceType, resourceId);
        String concurrentKey = acquireConcurrentPermit(resourceType, resourceId);
        return InvokeGovernanceLease.builder()
                .concurrentKey(concurrentKey)
                .build();
    }

    public void release(InvokeGovernanceLease lease) {
        if (lease == null || !StringUtils.hasText(lease.concurrentKey())) {
            return;
        }
        try {
            Long n = stringRedisTemplate.opsForValue().decrement(lease.concurrentKey());
            if (n != null && n <= 0) {
                stringRedisTemplate.delete(lease.concurrentKey());
            }
        } catch (Exception ignore) {
            // ignore release failures to avoid masking invoke errors.
        }
    }

    private void enforceRuleRateLimit(Long userId, List<PlatformRole> roles) {
        List<Map<String, Object>> rules = jdbcTemplate.queryForList(
                "SELECT id, target, target_value, window_ms, max_requests, action FROM t_rate_limit_rule WHERE enabled = 1 ORDER BY priority DESC");
        for (Map<String, Object> rule : rules) {
            String target = str(rule.get("target"));
            String targetValue = str(rule.get("target_value"));
            if (!matchRuleTarget(target, targetValue, userId, roles)) {
                continue;
            }
            long windowMs = Math.max(1000L, num(rule.get("window_ms")));
            int maxRequests = (int) Math.max(1L, num(rule.get("max_requests")));
            String subject = buildSubject(target, userId, targetValue);
            String bucket = String.valueOf(System.currentTimeMillis() / windowMs);
            String key = "gw:rate:" + str(rule.get("id")) + ":" + subject + ":" + bucket;
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                stringRedisTemplate.expire(key, Duration.ofMillis(windowMs + 1000L));
            }
            if (count != null && count > maxRequests) {
                throw new BusinessException(ResultCode.RATE_LIMITED, "触发限流规则: " + str(rule.get("id")));
            }
        }
    }

    private void enforceResourceRateLimit(String resourceType, Long resourceId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, max_requests_per_min, max_requests_per_hour FROM t_quota_rate_limit WHERE enabled = 1 AND target_type = ? AND target_id = ? LIMIT 1",
                resourceType, resourceId);
        if (rows.isEmpty()) {
            return;
        }
        Map<String, Object> row = rows.get(0);
        String id = str(row.get("id"));
        int maxPerMin = (int) Math.max(1L, num(row.get("max_requests_per_min")));
        int maxPerHour = (int) Math.max(1L, num(row.get("max_requests_per_hour")));

        checkWindow("gw:qrl:min:" + id, 60_000L, maxPerMin);
        checkWindow("gw:qrl:hour:" + id, 3_600_000L, maxPerHour);
    }

    private String acquireConcurrentPermit(String resourceType, Long resourceId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, max_concurrent FROM t_quota_rate_limit WHERE enabled = 1 AND target_type = ? AND target_id = ? LIMIT 1",
                resourceType, resourceId);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        int maxConcurrent = (int) Math.max(1L, num(row.get("max_concurrent")));
        String key = "gw:qrl:concurrent:" + str(row.get("id"));
        Long current = stringRedisTemplate.opsForValue().increment(key);
        stringRedisTemplate.expire(key, Duration.ofMinutes(10));
        if (current != null && current > maxConcurrent) {
            stringRedisTemplate.opsForValue().decrement(key);
            throw new BusinessException(ResultCode.RATE_LIMITED, "资源并发已达上限");
        }
        return key;
    }

    private void checkWindow(String keyPrefix, long windowMs, int limit) {
        String bucket = String.valueOf(System.currentTimeMillis() / windowMs);
        String key = keyPrefix + ":" + bucket;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, Duration.ofMillis(windowMs + 1000L));
        }
        if (count != null && count > limit) {
            throw new BusinessException(ResultCode.RATE_LIMITED);
        }
    }

    private static boolean matchRuleTarget(String target, String targetValue, Long userId, List<PlatformRole> roles) {
        if ("global".equalsIgnoreCase(target)) {
            return true;
        }
        if ("user".equalsIgnoreCase(target)) {
            if (userId == null) {
                return false;
            }
            return !StringUtils.hasText(targetValue) || String.valueOf(userId).equals(targetValue.trim());
        }
        if ("role".equalsIgnoreCase(target)) {
            if (!StringUtils.hasText(targetValue)) {
                return false;
            }
            return roles.stream().anyMatch(role -> targetValue.equals(role.getRoleCode()));
        }
        return false;
    }

    private static String buildSubject(String target, Long userId, String targetValue) {
        if ("user".equalsIgnoreCase(target) && userId != null) {
            return "u" + userId;
        }
        if ("role".equalsIgnoreCase(target) && StringUtils.hasText(targetValue)) {
            return "r" + targetValue.trim();
        }
        return "global";
    }

    private static Long resolveQuotaUserId(Long userId, ApiKey apiKey) {
        if (userId != null) {
            return userId;
        }
        if (apiKey == null || !"user".equalsIgnoreCase(apiKey.getOwnerType())) {
            return null;
        }
        if (!StringUtils.hasText(apiKey.getOwnerId())) {
            return null;
        }
        try {
            return Long.valueOf(apiKey.getOwnerId().trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static long num(Object v) {
        if (v == null) {
            return 0L;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    @Builder
    public record InvokeGovernanceLease(String concurrentKey) {
    }
}
