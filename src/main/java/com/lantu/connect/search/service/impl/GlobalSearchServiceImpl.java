package com.lantu.connect.search.service.impl;

import com.lantu.connect.common.security.CasbinAuthorizationService;
import com.lantu.connect.common.security.RequirePermission;
import com.lantu.connect.gateway.security.GatewayUserPermissionService;
import com.lantu.connect.search.dto.GlobalSearchGroup;
import com.lantu.connect.search.dto.GlobalSearchItem;
import com.lantu.connect.search.dto.GlobalSearchResponse;
import com.lantu.connect.search.service.GlobalSearchService;
import lombok.RequiredArgsConstructor;
import org.casbin.jcasbin.main.Enforcer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GlobalSearchServiceImpl implements GlobalSearchService {

    private static final int DEFAULT_LIMIT = 6;
    private static final int MAX_LIMIT = 10;
    private static final int SQL_CANDIDATE_LIMIT = 50;
    private static final Set<String> SCOPES = Set.of("all", "resources", "mine", "admin");

    private final JdbcTemplate jdbcTemplate;
    private final GatewayUserPermissionService gatewayUserPermissionService;
    private final CasbinAuthorizationService casbinAuthorizationService;

    @Override
    public GlobalSearchResponse search(Long userId, String query, String scope, Integer limitPerGroup) {
        String q = normalizeQuery(query);
        String normalizedScope = normalizeScope(scope);
        int limit = normalizeLimit(limitPerGroup);
        boolean blank = !StringUtils.hasText(q);
        Enforcer enforcer = casbinAuthorizationService.loadEnforcerForUser(userId);

        List<GlobalSearchGroup> groups = new ArrayList<>();
        if (blank) {
            if ("all".equals(normalizedScope) || "resources".equals(normalizedScope)) {
                addGroup(groups, "trending", "热门资源", searchTrendingResources(userId, limit));
            }
            if ("all".equals(normalizedScope) || "admin".equals(normalizedScope)) {
                addGroup(groups, "admin_tasks", "常用管理入口", searchAdminTasks(userId, enforcer, q, limit));
            }
            if ("mine".equals(normalizedScope) || "all".equals(normalizedScope)) {
                addGroup(groups, "my_resources", "我的最近资源", searchMyResources(userId, q, limit));
            }
        } else {
            if ("all".equals(normalizedScope) || "resources".equals(normalizedScope)) {
                addGroup(groups, "resources", "资源", searchPublishedResources(userId, q, limit));
            }
            if ("all".equals(normalizedScope) || "mine".equals(normalizedScope)) {
                addGroup(groups, "my_resources", "我的资源", searchMyResources(userId, q, limit));
            }
            if ("all".equals(normalizedScope) || "admin".equals(normalizedScope)) {
                addGroup(groups, "admin_tasks", "管理与待办", searchAdminTasks(userId, enforcer, q, limit));
            }
        }

        return GlobalSearchResponse.builder()
                .query(q)
                .groups(groups)
                .build();
    }

    private List<GlobalSearchItem> searchPublishedResources(Long userId, String query, int limit) {
        String like = like(query);
        String prefix = query + "%";
        List<ResourceRow> rows = jdbcTemplate.query("""
                SELECT id, resource_type, resource_code, display_name, description, update_time,
                       CASE
                         WHEN LOWER(display_name) = LOWER(?) THEN 100
                         WHEN LOWER(resource_code) = LOWER(?) THEN 95
                         WHEN LOWER(display_name) LIKE LOWER(?) THEN 80
                         WHEN LOWER(resource_code) LIKE LOWER(?) THEN 70
                         WHEN LOWER(description) LIKE LOWER(?) THEN 40
                         ELSE 10
                       END AS score
                  FROM t_resource
                 WHERE deleted = 0
                   AND status = 'published'
                   AND (display_name LIKE ? OR resource_code LIKE ? OR description LIKE ?)
                ORDER BY score DESC, update_time DESC
                 LIMIT ?
                """, resourceRowMapper(), query, query, prefix, prefix, like, like, like, like, SQL_CANDIDATE_LIMIT);
        GatewayUserPermissionService.CatalogTypePredicate predicate = visibleTypePredicate(userId);
        return rows.stream()
                .filter(row -> predicate.allow(row.resourceType()))
                .limit(limit)
                .map(row -> toResourceItem(row, "resource", "已发布", resourcePath(row.resourceType(), row.id())))
                .toList();
    }

    private List<GlobalSearchItem> searchTrendingResources(Long userId, int limit) {
        List<ResourceRow> rows = jdbcTemplate.query("""
                SELECT r.id, r.resource_type, r.resource_code, r.display_name, r.description, r.update_time,
                       COALESCE(r.view_count, 0) + COALESCE(c.cnt, 0) AS score
                  FROM t_resource r
                  LEFT JOIN (
                    SELECT agent_id, COUNT(*) AS cnt
                      FROM t_call_log
                     WHERE agent_id IS NOT NULL
                     GROUP BY agent_id
                  ) c ON c.agent_id = r.id
                 WHERE r.deleted = 0
                   AND r.status = 'published'
                 ORDER BY score DESC, r.update_time DESC
                 LIMIT ?
                """, resourceRowMapper(), SQL_CANDIDATE_LIMIT);
        GatewayUserPermissionService.CatalogTypePredicate predicate = visibleTypePredicate(userId);
        return rows.stream()
                .filter(row -> predicate.allow(row.resourceType()))
                .limit(limit)
                .map(row -> toResourceItem(row, "resource", "热门", resourcePath(row.resourceType(), row.id())))
                .toList();
    }

    private List<GlobalSearchItem> searchMyResources(Long userId, String query, int limit) {
        boolean blank = !StringUtils.hasText(query);
        List<ResourceRow> rows;
        if (blank) {
            rows = jdbcTemplate.query("""
                    SELECT id, resource_type, resource_code, display_name, description, status, update_time,
                           60 AS score
                      FROM t_resource
                     WHERE deleted = 0
                       AND created_by = ?
                     ORDER BY update_time DESC
                     LIMIT ?
                    """, resourceRowMapper(), userId, limit);
        } else {
            String like = like(query);
            String prefix = query + "%";
            rows = jdbcTemplate.query("""
                    SELECT id, resource_type, resource_code, display_name, description, status, update_time,
                           CASE
                             WHEN LOWER(display_name) = LOWER(?) THEN 100
                             WHEN LOWER(resource_code) = LOWER(?) THEN 95
                             WHEN LOWER(display_name) LIKE LOWER(?) THEN 80
                             WHEN LOWER(resource_code) LIKE LOWER(?) THEN 70
                             WHEN LOWER(description) LIKE LOWER(?) THEN 40
                             ELSE 10
                           END AS score
                      FROM t_resource
                     WHERE deleted = 0
                       AND created_by = ?
                       AND (display_name LIKE ? OR resource_code LIKE ? OR description LIKE ?)
                     ORDER BY score DESC, update_time DESC
                     LIMIT ?
                    """, resourceRowMapper(), query, query, prefix, prefix, like, userId, like, like, like, SQL_CANDIDATE_LIMIT);
        }
        return rows.stream()
                .limit(limit)
                .map(row -> toResourceItem(row, "my_resource", statusLabel(row.status()), myResourcePath(row.resourceType(), row.id())))
                .toList();
    }

    private List<GlobalSearchItem> searchAdminTasks(Long userId, Enforcer enforcer, String query, int limit) {
        List<AdminCandidate> candidates = new ArrayList<>();
        if (hasAnyPermission(userId, enforcer, "resource:audit")) {
            candidates.add(new AdminCandidate(
                    "admin-resource-audit",
                    "audit",
                    "资源审核",
                    "审核中心",
                    "处理待审核资源、资源发布与驳回",
                    pendingAuditBadge(),
                    "/c/resource-audit",
                    "资源 审核 audit review 发布 驳回 待办"));
        }
        if (hasAnyPermission(userId, enforcer, "developer-application:review")) {
            candidates.add(new AdminCandidate(
                    "admin-developer-applications",
                    "developer_application",
                    "开发者申请",
                    "管理入口",
                    "审核开发者入驻与权限申请",
                    null,
                    "/c/developer-applications",
                    "开发者 申请 入驻 developer application review"));
        }
        if (hasAnyPermission(userId, enforcer, "monitor:view")) {
            candidates.add(new AdminCandidate("admin-monitoring-overview", "navigation", "监控总览", "监控运维", "查看调用、健康度与平台运行态", null, "/c/monitoring-overview", "监控 总览 运维 monitor dashboard"));
            candidates.add(new AdminCandidate("admin-call-logs", "navigation", "调用日志", "监控运维", "检索资源调用记录、请求链路与结果", null, "/c/call-logs", "调用 日志 API Key trace logs 请求"));
            candidates.add(new AdminCandidate("admin-alert-center", "navigation", "告警中心", "监控运维", "查看平台告警与处理状态", null, "/c/alert-center", "告警 告警中心 alert incident monitor"));
        }
        if (hasAnyPermission(userId, enforcer, "user:read", "user:manage")) {
            candidates.add(new AdminCandidate("admin-user-list", "navigation", "用户管理", "权限治理", "查看用户、状态与组织归属", null, "/c/user-list", "用户 管理 user account 权限"));
        }
        if (hasAnyPermission(userId, enforcer, "role:manage")) {
            candidates.add(new AdminCandidate("admin-role-management", "navigation", "角色管理", "权限治理", "维护角色、权限集与平台角色", null, "/c/role-management", "角色 管理 权限 role permission"));
        }
        if (hasAnyPermission(userId, enforcer, "org:manage")) {
            candidates.add(new AdminCandidate("admin-organization", "navigation", "组织架构", "权限治理", "维护组织、部门与成员边界", null, "/c/organization", "组织 架构 部门 学院 org"));
        }

        String normalized = normalizeForMatch(query);
        return candidates.stream()
                .map(candidate -> score(candidate, normalized))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(ScoredAdminCandidate::score).reversed())
                .limit(limit)
                .map(ScoredAdminCandidate::candidate)
                .map(candidate -> GlobalSearchItem.builder()
                        .id(candidate.id())
                        .kind(candidate.kind())
                        .title(candidate.title())
                        .subtitle(candidate.subtitle())
                        .description(candidate.description())
                        .badge(candidate.badge())
                        .path(candidate.path())
                        .score(score(candidate, normalized).score())
                        .build())
                .toList();
    }

    private GatewayUserPermissionService.CatalogTypePredicate visibleTypePredicate(Long userId) {
        return gatewayUserPermissionService.catalogTypePredicate(userId);
    }

    private boolean hasAnyPermission(Long userId, Enforcer enforcer, String... permissions) {
        return casbinAuthorizationService.hasPermissions(userId, permissions, RequirePermission.LogicalOperator.OR, enforcer);
    }

    private ScoredAdminCandidate score(AdminCandidate candidate, String normalizedQuery) {
        if (!StringUtils.hasText(normalizedQuery)) {
            return new ScoredAdminCandidate(candidate, 50);
        }
        String title = normalizeForMatch(candidate.title());
        String haystack = normalizeForMatch(candidate.title() + " " + candidate.subtitle() + " " + candidate.description() + " " + candidate.keywords());
        if (title.equals(normalizedQuery)) {
            return new ScoredAdminCandidate(candidate, 100);
        }
        if (title.startsWith(normalizedQuery)) {
            return new ScoredAdminCandidate(candidate, 80);
        }
        if (haystack.contains(normalizedQuery)) {
            return new ScoredAdminCandidate(candidate, 55);
        }
        return null;
    }

    private GlobalSearchItem toResourceItem(ResourceRow row, String kind, String badge, String path) {
        String type = normalizeType(row.resourceType());
        return GlobalSearchItem.builder()
                .id(kind + ":" + type + ":" + row.id())
                .kind(kind)
                .title(fallback(row.displayName(), row.resourceCode(), "未命名资源"))
                .subtitle(resourceTypeLabel(type) + (StringUtils.hasText(row.resourceCode()) ? " · " + row.resourceCode() : ""))
                .description(row.description())
                .badge(badge)
                .resourceType(type)
                .resourceId(String.valueOf(row.id()))
                .path(path)
                .score(row.score())
                .build();
    }

    private void addGroup(List<GlobalSearchGroup> groups, String key, String title, List<GlobalSearchItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        groups.add(GlobalSearchGroup.builder()
                .key(key)
                .title(title)
                .items(items)
                .build());
    }

    private RowMapper<ResourceRow> resourceRowMapper() {
        return (rs, rowNum) -> new ResourceRow(
                rs.getLong("id"),
                string(rs, "resource_type"),
                string(rs, "resource_code"),
                string(rs, "display_name"),
                string(rs, "description"),
                hasColumn(rs, "status") ? string(rs, "status") : "published",
                rs.getInt("score")
        );
    }

    private String pendingAuditBadge() {
        try {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM t_audit_item WHERE status = 'pending_review'",
                    Long.class);
            return count != null && count > 0 ? count + " 待审" : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String normalizeQuery(String query) {
        if (!StringUtils.hasText(query)) {
            return "";
        }
        String trimmed = query.trim();
        return trimmed.length() > 80 ? trimmed.substring(0, 80) : trimmed;
    }

    private static String normalizeScope(String scope) {
        if (!StringUtils.hasText(scope)) {
            return "all";
        }
        String normalized = scope.trim().toLowerCase(Locale.ROOT);
        return SCOPES.contains(normalized) ? normalized : "all";
    }

    private static int normalizeLimit(Integer limitPerGroup) {
        int n = limitPerGroup == null ? DEFAULT_LIMIT : limitPerGroup;
        if (n < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(n, MAX_LIMIT);
    }

    private static String like(String query) {
        return "%" + query + "%";
    }

    private static String resourcePath(String type, long id) {
        return switch (normalizeType(type)) {
            case "skill" -> "/c/skills-center/" + id;
            case "mcp" -> "/c/mcp-center/" + id;
            case "app" -> "/c/apps-center/" + id;
            case "dataset" -> "/c/dataset-center/" + id;
            case "agent" -> "/c/agents-center/" + id;
            default -> "/c/resource-market?resourceId=" + id;
        };
    }

    private static String myResourcePath(String type, long id) {
        return "/c/resource-center?type=" + normalizeType(type) + "&resourceId=" + id;
    }

    private static String resourceTypeLabel(String type) {
        return switch (normalizeType(type)) {
            case "agent" -> "Agent";
            case "skill" -> "Skill";
            case "mcp" -> "MCP";
            case "app" -> "App";
            case "dataset" -> "Dataset";
            default -> "Resource";
        };
    }

    private static String statusLabel(String status) {
        return switch (normalizeForMatch(status)) {
            case "draft" -> "草稿";
            case "pending_review" -> "待审核";
            case "published" -> "已发布";
            case "offline" -> "已下线";
            case "rejected" -> "已驳回";
            default -> StringUtils.hasText(status) ? status : "我的资源";
        };
    }

    private static String normalizeType(String type) {
        return StringUtils.hasText(type) ? type.trim().toLowerCase(Locale.ROOT) : "agent";
    }

    private static String normalizeForMatch(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String fallback(String first, String second, String fallback) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        if (StringUtils.hasText(second)) {
            return second;
        }
        return fallback;
    }

    private static String string(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? "" : value;
    }

    private static boolean hasColumn(ResultSet rs, String column) {
        try {
            rs.findColumn(column);
            return true;
        } catch (SQLException ignored) {
            return false;
        }
    }

    private record ResourceRow(
            long id,
            String resourceType,
            String resourceCode,
            String displayName,
            String description,
            String status,
            int score
    ) {
    }

    private record AdminCandidate(
            String id,
            String kind,
            String title,
            String subtitle,
            String description,
            String badge,
            String path,
            String keywords
    ) {
    }

    private record ScoredAdminCandidate(AdminCandidate candidate, int score) {
    }
}
