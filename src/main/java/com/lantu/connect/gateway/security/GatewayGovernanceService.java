package com.lantu.connect.gateway.security;

import com.lantu.connect.auth.entity.PlatformRole;
import com.lantu.connect.auth.mapper.PlatformRoleMapper;
import com.lantu.connect.common.config.GatewayInvokeProperties;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.usermgmt.entity.ApiKey;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayGovernanceService {

    private static final String CONCURRENT_PREFIX = "gw:concurrent:";
    private static final String LIMIT_CACHE_PREFIX = "gw:concurrent-limit:";
    private static final long LIMIT_CACHE_TTL_MS = 30_000L;
    private static final long CONCURRENT_TTL_MS = 10 * 60 * 1000L;

    private final PlatformRoleMapper platformRoleMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final GatewayInvokeProperties gatewayInvokeProperties;

    private final ConcurrentHashMap<String, CachedLimit> limitCache = new ConcurrentHashMap<>();

    public InvokeGovernanceLease applyPreInvoke(Long userId, ApiKey apiKey, String resourceType, Long resourceId, int tokens) {
        List<PlatformRole> roles = userId == null ? Collections.emptyList() : platformRoleMapper.selectRolesByUserId(userId);
        enforceRuleRateLimit(userId, roles, resourceType);
        enforceResourceRateLimit(resourceType, resourceId);
        String concurrentKey = acquireConcurrentPermit(resourceType, resourceId, tokens);
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
        } catch (RuntimeException e) {
            log.debug("invoke concurrent permit release failed: {}", e.toString());
        }
    }

    private void enforceRuleRateLimit(Long userId, List<PlatformRole> roles, String invokeResourceType) {
        List<Map<String, Object>> rules = jdbcTemplate.queryForList(
                "SELECT id, target, target_value, window_ms, max_requests, action, resource_scope "
                        + "FROM t_rate_limit_rule WHERE enabled = 1 ORDER BY priority DESC",
                new Object[0]);
        for (Map<String, Object> rule : rules) {
            if (!ruleAppliesToResourceScope(rule, invokeResourceType)) {
                continue;
            }
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
                stringRedisTemplate.expire(key, Objects.requireNonNull(Duration.ofMillis(windowMs + 1000L)));
            }
            if (count != null && count > maxRequests) {
                throw new BusinessException(ResultCode.RATE_LIMITED, "触发限流规则: " + str(rule.get("id")));
            }
        }
    }

    private void enforceResourceRateLimit(String resourceType, Long resourceId) {
        if (!StringUtils.hasText(resourceType) || resourceId == null) {
            return;
        }
        String limitKey = resolveLimitCacheKey(resourceType, resourceId);
        CachedLimit cached = limitCache.get(limitKey);
        if (cached != null && !cached.isExpired()) {
            return;
        }
        int limit = resolveConcurrencyLimitFromDb(resourceType, resourceId);
        limitCache.put(limitKey, new CachedLimit(limit, System.currentTimeMillis() + LIMIT_CACHE_TTL_MS));
    }

    private String acquireConcurrentPermit(String resourceType, Long resourceId, int tokens) {
        if (!StringUtils.hasText(resourceType) || resourceId == null) {
            return null;
        }
        int effectiveTokens = Math.max(1, tokens);
        int limit = resolveConcurrencyLimit(resourceType, resourceId);
        String key = CONCURRENT_PREFIX + resourceType.trim().toLowerCase(Locale.ROOT) + ":" + resourceId;
        Long current = stringRedisTemplate.opsForValue().increment(key, effectiveTokens);
        if (current != null && current == effectiveTokens) {
            stringRedisTemplate.expire(key, Objects.requireNonNull(Duration.ofMillis(CONCURRENT_TTL_MS)));
        }
        if (current != null && current > limit) {
            try {
                Long afterRollback = stringRedisTemplate.opsForValue().decrement(key, effectiveTokens);
                if (afterRollback != null && afterRollback <= 0) {
                    stringRedisTemplate.delete(key);
                }
            } catch (RuntimeException ex) {
                log.debug("rollback concurrent permit failed: {}", ex.toString());
            }
            throw new BusinessException(ResultCode.RATE_LIMITED, "资源并发已达上限: " + resourceType + "/" + resourceId);
        }
        return key;
    }

    private int resolveConcurrencyLimit(String resourceType, Long resourceId) {
        String limitKey = resolveLimitCacheKey(resourceType, resourceId);
        CachedLimit cached = limitCache.get(limitKey);
        if (cached != null && !cached.isExpired()) {
            return cached.limit;
        }
        int limit = resolveConcurrencyLimitFromDb(resourceType, resourceId);
        limitCache.put(limitKey, new CachedLimit(limit, System.currentTimeMillis() + LIMIT_CACHE_TTL_MS));
        return limit;
    }

    private int resolveConcurrencyLimitFromDb(String resourceType, Long resourceId) {
        String type = resourceType.trim().toLowerCase(Locale.ROOT);
        Integer limit = null;
        if ("agent".equals(type)) {
            limit = queryMaxConcurrency("SELECT max_concurrency FROM t_resource_agent_ext WHERE resource_id = ? LIMIT 1", resourceId);
        } else if ("skill".equals(type)) {
            limit = queryMaxConcurrency("SELECT max_concurrency FROM t_resource_skill_ext WHERE resource_id = ? LIMIT 1", resourceId);
        }
        if (limit != null && limit > 0) {
            return limit;
        }
        int fallback = gatewayInvokeProperties.getCapabilities().getDefaultMaxConcurrentPerResource();
        return fallback > 0 ? fallback : 100;
    }

    private Integer queryMaxConcurrency(String sql, Long resourceId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, new Object[]{resourceId});
        if (rows.isEmpty()) {
            return null;
        }
        Object v = rows.get(0).get("max_concurrency");
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String resolveLimitCacheKey(String resourceType, Long resourceId) {
        return LIMIT_CACHE_PREFIX + resourceType.trim().toLowerCase(Locale.ROOT) + ":" + resourceId;
    }

    private static boolean ruleAppliesToResourceScope(Map<String, Object> rule, String invokeResourceType) {
        String scope = str(rule.get("resource_scope"));
        if (!StringUtils.hasText(scope) || "all".equalsIgnoreCase(scope)) {
            return true;
        }
        return StringUtils.hasText(invokeResourceType) && scope.equalsIgnoreCase(invokeResourceType.trim());
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

    private record CachedLimit(int limit, long expiresAt) {
        boolean isExpired() {
            return System.currentTimeMillis() >= expiresAt;
        }
    }
}
