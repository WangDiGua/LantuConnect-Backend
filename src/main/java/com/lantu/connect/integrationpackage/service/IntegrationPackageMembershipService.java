package com.lantu.connect.integrationpackage.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 集成套餐成员缓存：用于 API Key 在绑定 {@code integration_package_id} 时的白名单快速判定。
 * <p>
 * 不包含资源是否「已上线 / 健康」校验：该约束在统一网关 {@code invoke} 流程中与直配 scope 相同，由
 * {@link com.lantu.connect.gateway.service.impl.UnifiedGatewayServiceImpl} 在鉴权通过后统一执行。
 */
@Service
@RequiredArgsConstructor
public class IntegrationPackageMembershipService {

    private static final long CACHE_TTL_MS = 60_000L;

    private final JdbcTemplate jdbcTemplate;

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private static final class CacheEntry {
        final Set<String> keys;
        final long expiresAtMs;

        CacheEntry(Set<String> keys, long expiresAtMs) {
            this.keys = keys;
            this.expiresAtMs = expiresAtMs;
        }
    }

    /**
     * 资源是否在套餐内（套餐须为 active）。
     */
    public boolean contains(String packageId, String resourceType, String resourceId) {
        if (!StringUtils.hasText(packageId) || !StringUtils.hasText(resourceType) || !StringUtils.hasText(resourceId)) {
            return false;
        }
        String type = resourceType.trim().toLowerCase(Locale.ROOT);
        String rid = resourceId.trim();
        String composite = type + ":" + rid;
        Set<String> keys = loadMembers(packageId);
        return keys.contains(composite);
    }

    public void evict(String packageId) {
        if (StringUtils.hasText(packageId)) {
            cache.remove(packageId.trim());
        }
    }

    public void evictAll() {
        cache.clear();
    }

    private Set<String> loadMembers(String packageId) {
        String pid = packageId.trim();
        long now = System.currentTimeMillis();
        CacheEntry hit = cache.get(pid);
        if (hit != null && hit.expiresAtMs > now) {
            return hit.keys;
        }
        Integer st = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_integration_package WHERE id = ? AND status = 'active'",
                Integer.class,
                pid);
        if (st == null || st == 0) {
            Set<String> empty = Set.of();
            cache.put(pid, new CacheEntry(empty, now + CACHE_TTL_MS));
            return empty;
        }
        Set<String> keys = new HashSet<>();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT resource_type, resource_id FROM t_integration_package_item WHERE package_id = ?", pid);
        for (Map<String, Object> row : rows) {
            Object t = row.get("resource_type");
            Object idObj = row.get("resource_id");
            if (t != null && idObj != null) {
                keys.add(String.valueOf(t).trim().toLowerCase(Locale.ROOT) + ":" + ((Number) idObj).longValue());
            }
        }
        cache.put(pid, new CacheEntry(keys, now + CACHE_TTL_MS));
        return keys;
    }
}
