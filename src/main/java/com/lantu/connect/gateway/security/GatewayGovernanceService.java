package com.lantu.connect.gateway.security;

import com.lantu.connect.auth.entity.PlatformRole;
import com.lantu.connect.auth.mapper.PlatformRoleMapper;
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
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayGovernanceService {

    private final PlatformRoleMapper platformRoleMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final JdbcTemplate jdbcTemplate;

    public InvokeGovernanceLease applyPreInvoke(Long userId, ApiKey apiKey, String resourceType, Long resourceId, int tokens) {
        List<PlatformRole> roles = userId == null ? Collections.emptyList() : platformRoleMapper.selectRolesByUserId(userId);
        enforceRuleRateLimit(userId, roles, resourceType);
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
        } catch (RuntimeException e) {
            log.debug("invoke concurrent permit release failed: {}", e.toString());
        }
    }

    private void enforceRuleRateLimit(Long userId, List<PlatformRole> roles, String invokeResourceType) {
        List<Map<String, Object>> rules = jdbcTemplate.queryForList(
                "SELECT id, target, target_value, window_ms, max_requests, action, resource_scope "
                        + "FROM t_rate_limit_rule WHERE enabled = 1 ORDER BY priority DESC");
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
                stringRedisTemplate.expire(key, Duration.ofMillis(windowMs + 1000L));
            }
            if (count != null && count > maxRequests) {
                throw new BusinessException(ResultCode.RATE_LIMITED, "触发限流规则: " + str(rule.get("id")));
            }
        }
    }

    /** 已移除 t_quota_rate_limit 资源级限流，统一由 t_rate_limit_rule 与用户维度规则承接。 */
    private void enforceResourceRateLimit(String resourceType, Long resourceId) {
    }

    private String acquireConcurrentPermit(String resourceType, Long resourceId) {
        return null;
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
}
