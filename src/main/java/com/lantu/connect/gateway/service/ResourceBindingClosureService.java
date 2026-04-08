package com.lantu.connect.gateway.service;

import com.lantu.connect.gateway.dto.ResourceSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 资源绑定无向闭包：边来自 {@code t_resource_relation} 中 agent/mcp/skill 绑定类。
 */
@Service
@RequiredArgsConstructor
public class ResourceBindingClosureService {

    private static final String BINDING_SQL_TYPES = "'agent_depends_mcp','agent_depends_skill','mcp_depends_skill'";

    private final JdbcTemplate jdbcTemplate;

    /**
     * 自给定资源 id 出发，沿绑定边无向遍历，返回闭包内资源摘要（含起点，仅 deleted=0，按 type、id 排序）。
     */
    public List<ResourceSummaryVO> closureForResourceId(long startResourceId) {
        Set<Long> visited = new HashSet<>();
        ArrayDeque<Long> q = new ArrayDeque<>();
        q.add(startResourceId);
        visited.add(startResourceId);
        while (!q.isEmpty()) {
            Long cur = q.poll();
            List<Long> neigh = jdbcTemplate.query(
                    "SELECT to_resource_id AS id FROM t_resource_relation WHERE from_resource_id = ? AND relation_type IN ("
                            + BINDING_SQL_TYPES + ") "
                            + "UNION SELECT from_resource_id AS id FROM t_resource_relation WHERE to_resource_id = ? AND relation_type IN ("
                            + BINDING_SQL_TYPES + ")",
                    (rs, i) -> rs.getLong("id"),
                    cur, cur);
            for (Long n : neigh) {
                if (n == null || n <= 0) {
                    continue;
                }
                if (visited.add(n)) {
                    q.add(n);
                }
            }
        }
        if (visited.isEmpty()) {
            return List.of();
        }
        String ph = visited.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(","));
        List<Object> args = new ArrayList<>(visited);
        List<ResourceSummaryVO> out = jdbcTemplate.query(
                "SELECT id, resource_type, resource_code, display_name, status FROM t_resource WHERE deleted = 0 AND id IN ("
                        + ph + ") ORDER BY resource_type, id",
                (rs, i) -> ResourceSummaryVO.builder()
                        .resourceId(String.valueOf(rs.getLong("id")))
                        .resourceType(rs.getString("resource_type") == null ? "" : rs.getString("resource_type").toLowerCase(Locale.ROOT))
                        .resourceCode(rs.getString("resource_code"))
                        .displayName(rs.getString("display_name"))
                        .status(rs.getString("status"))
                        .build(),
                args.toArray());
        out.sort(Comparator
                .comparing((ResourceSummaryVO v) -> v.getResourceType() == null ? "" : v.getResourceType())
                .thenComparing(v -> {
                    try {
                        return Long.parseLong(v.getResourceId());
                    } catch (Exception e) {
                        return 0L;
                    }
                }));
        return out;
    }
}
