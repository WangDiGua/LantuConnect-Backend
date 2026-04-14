package com.lantu.connect.realtime;

import com.lantu.connect.common.config.RealtimePushProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 解析 WebSocket 推送收件人：按资源 owner 学校做租户收敛，并仅向具备治理/运维能力的角色下发对应事件，
 * 避免普通用户（无 {@code monitor:view} 等）收到监控类推送。
 */
@Component
@RequiredArgsConstructor
public class RealtimePushRecipientResolver {

    /**
     * 资源级治理（健康 / 熔断）：{@code platform_admin}、{@code reviewer} 全量可见；
     * 其他具备 {@code monitor:view} 的账号仅在与资源 owner 同学业（{@code school_id}）时可见。
     */
    private static final String SQL_GOVERNANCE_MONITOR_USERS = """
            SELECT DISTINCT ur.user_id
            FROM t_user_role_rel ur
            INNER JOIN t_platform_role pr ON pr.id = ur.role_id
            INNER JOIN t_user u ON u.user_id = ur.user_id
            WHERE COALESCE(u.deleted, 0) = 0
            AND JSON_CONTAINS(pr.permissions, '"monitor:view"', '$')
            AND (
                pr.role_code IN ('platform_admin', 'admin', 'reviewer', 'dept_admin', 'department_admin', 'auditor')
                OR (? IS NOT NULL AND u.school_id <=> ?)
            )
            """;

    /**
     * 全局运维类（告警 firing、待审队列汇总、KPI 摘要）：与审核队列一致，仅 {@code platform_admin} 与 {@code reviewer}。
     */
    private static final String SQL_GLOBAL_OPS_USERS = """
            SELECT DISTINCT ur.user_id
            FROM t_user_role_rel ur
            INNER JOIN t_platform_role pr ON pr.id = ur.role_id
            INNER JOIN t_user u ON u.user_id = ur.user_id
            WHERE COALESCE(u.deleted, 0) = 0
            AND JSON_CONTAINS(pr.permissions, '"monitor:view"', '$')
            AND pr.role_code IN ('platform_admin', 'admin', 'reviewer', 'dept_admin', 'department_admin', 'auditor')
            """;

    private final JdbcTemplate jdbcTemplate;
    private final RealtimePushProperties properties;

    private final ConcurrentHashMap<String, MonitorListCache> governanceCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MonitorListCache> opsCache = new ConcurrentHashMap<>();

    private record MonitorListCache(long timeMs, List<Long> ids) {}

    /**
     * 健康 / 熔断：资源 owner + 按租户收敛后的 monitor 视角用户。
     */
    public Set<Long> resolveGovernanceRecipients(Long resourceId) {
        ResourceOwnerContext ctx = resolveResourceOwnerContext(resourceId);
        LinkedHashSet<Long> out = new LinkedHashSet<>();
        if (ctx.ownerUserId() != null && ctx.ownerUserId() > 0) {
            out.add(ctx.ownerUserId());
        }
        out.addAll(listGovernanceMonitorUserIds(ctx.ownerSchoolId()));
        return out;
    }

    /**
     * 告警、监控 KPI 等全局事件（无资源绑定）。
     */
    public Set<Long> resolveGlobalOpsRecipients() {
        return new LinkedHashSet<>(listGlobalOpsUserIds());
    }

    /**
     * 待审核队列数量推送（与 /audit 可见范围一致）。
     */
    public Set<Long> resolveAuditDigestRecipients() {
        return resolveGlobalOpsRecipients();
    }

    public Long resolveResourceOwnerUserId(Long resourceId) {
        return resolveResourceOwnerContext(resourceId).ownerUserId();
    }

    public record ResourceOwnerContext(Long ownerUserId, Long ownerSchoolId) {}

    public ResourceOwnerContext resolveResourceOwnerContext(Long resourceId) {
        if (resourceId == null) {
            return new ResourceOwnerContext(null, null);
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT r.created_by AS owner_id, u.school_id AS school_id "
                            + "FROM t_resource r "
                            + "LEFT JOIN t_user u ON u.user_id = r.created_by AND COALESCE(u.deleted, 0) = 0 "
                            + "WHERE r.id = ? AND r.deleted = 0 LIMIT 1",
                    resourceId);
            if (rows.isEmpty()) {
                return new ResourceOwnerContext(null, null);
            }
            Map<String, Object> row = rows.get(0);
            Long owner = toLong(row.get("owner_id"));
            Long school = toLong(row.get("school_id"));
            return new ResourceOwnerContext(owner, school);
        } catch (DataAccessException e) {
            return new ResourceOwnerContext(null, null);
        }
    }

    private List<Long> listGovernanceMonitorUserIds(Long ownerSchoolId) {
        String key = ownerSchoolId == null ? "school:null" : "school:" + ownerSchoolId;
        return loadCachedList(governanceCache, key, () -> queryGovernanceMonitors(ownerSchoolId));
    }

    private List<Long> listGlobalOpsUserIds() {
        return loadCachedList(opsCache, "ops", this::queryGlobalOpsUsers);
    }

    private List<Long> loadCachedList(ConcurrentHashMap<String, MonitorListCache> map, String key, SupplierThrow listSupplier) {
        long ttl = Math.max(0L, properties.getMonitorRecipientCacheTtlMs());
        long now = System.currentTimeMillis();
        MonitorListCache cur = map.get(key);
        if (ttl > 0 && cur != null && now - cur.timeMs < ttl) {
            return cur.ids;
        }
        synchronized (map) {
            cur = map.get(key);
            if (ttl > 0 && cur != null && now - cur.timeMs < ttl) {
                return cur.ids;
            }
            try {
                List<Long> ids = listSupplier.get();
                List<Long> frozen = ids == null ? List.of() : List.copyOf(ids.stream()
                        .filter(Objects::nonNull)
                        .filter(uid -> uid > 0)
                        .toList());
                map.put(key, new MonitorListCache(System.currentTimeMillis(), frozen));
                return frozen;
            } catch (DataAccessException e) {
                return Collections.emptyList();
            }
        }
    }

    private List<Long> queryGovernanceMonitors(Long ownerSchoolId) {
        return jdbcTemplate.query(SQL_GOVERNANCE_MONITOR_USERS, (rs, rowNum) -> {
            long uid = rs.getLong("user_id");
            return rs.wasNull() ? null : uid;
        }, ownerSchoolId, ownerSchoolId);
    }

    private List<Long> queryGlobalOpsUsers() {
        return jdbcTemplate.query(SQL_GLOBAL_OPS_USERS, (rs, rowNum) -> {
            long uid = rs.getLong("user_id");
            return rs.wasNull() ? null : uid;
        });
    }

    private static Long toLong(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        return null;
    }

    @FunctionalInterface
    private interface SupplierThrow {
        List<Long> get() throws DataAccessException;
    }
}
