package com.lantu.connect.useractivity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.PageResults;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.useractivity.dto.AuthorizedSkillVO;
import com.lantu.connect.useractivity.dto.FavoriteCreateRequest;
import com.lantu.connect.useractivity.dto.RecentUseVO;
import com.lantu.connect.useractivity.dto.UserStatsVO;
import com.lantu.connect.useractivity.entity.Favorite;
import com.lantu.connect.useractivity.entity.UsageRecord;
import com.lantu.connect.useractivity.mapper.FavoriteMapper;
import com.lantu.connect.useractivity.mapper.UsageRecordMapper;
import com.lantu.connect.useractivity.service.UserActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserActivityServiceImpl implements UserActivityService {

    private final UsageRecordMapper usageRecordMapper;
    private final FavoriteMapper favoriteMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public PageResult<UsageRecord> pageUsageRecords(Long userId, int page, int pageSize, String type) {
        LambdaQueryWrapper<UsageRecord> q = new LambdaQueryWrapper<UsageRecord>()
                .eq(UsageRecord::getUserId, userId)
                .orderByDesc(UsageRecord::getCreateTime);
        if (StringUtils.hasText(type)) {
            q.eq(UsageRecord::getType, type);
        }
        Page<UsageRecord> p = new Page<>(page, pageSize);
        Page<UsageRecord> result = usageRecordMapper.selectPage(p, q);
        return PageResults.from(result);
    }

    @Override
    public List<Favorite> listFavorites(Long userId) {
        return favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .orderByDesc(Favorite::getCreateTime));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Favorite addFavorite(Long userId, FavoriteCreateRequest request) {
        Long cnt = favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getTargetType, request.getTargetType())
                .eq(Favorite::getTargetId, request.getTargetId()));
        if (cnt != null && cnt > 0) {
            throw new BusinessException(ResultCode.FAVORITE_EXISTS);
        }
        Favorite f = new Favorite();
        f.setUserId(userId);
        f.setTargetType(request.getTargetType());
        f.setTargetId(request.getTargetId());
        favoriteMapper.insert(f);
        return favoriteMapper.selectById(f.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeFavorite(Long userId, Long favoriteId) {
        Favorite f = favoriteMapper.selectById(favoriteId);
        if (f == null || !userId.equals(f.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "收藏不存在");
        }
        favoriteMapper.deleteById(favoriteId);
    }

    @Override
    public UserStatsVO usageStats(Long userId) {
        Map<String, Object> counters = new LinkedHashMap<>();
        counters.put("userId", userId);
        Long total = usageRecordMapper.selectCount(
                new LambdaQueryWrapper<UsageRecord>().eq(UsageRecord::getUserId, userId));
        counters.put("totalUsageRecords", total != null ? total : 0L);

        QueryWrapper<UsageRecord> byType = new QueryWrapper<>();
        byType.select("type AS targetType", "COUNT(*) AS cnt")
                .eq("user_id", userId)
                .groupBy("type");
        List<Map<String, Object>> byTargetType = usageRecordMapper.selectMaps(byType);
        counters.put("byTargetType", byTargetType != null ? byTargetType : List.of());

        Map<String, Object> trends = new LinkedHashMap<>();
        LocalDate from = LocalDate.now().minusDays(7);
        QueryWrapper<UsageRecord> byDay = new QueryWrapper<>();
        byDay.select("DATE(create_time) AS day", "COUNT(*) AS cnt")
                .eq("user_id", userId)
                .ge("create_time", from.atStartOfDay())
                .groupBy("DATE(create_time)")
                .orderByAsc("DATE(create_time)");
        trends.put("last7Days", usageRecordMapper.selectMaps(byDay));

        return UserStatsVO.builder()
                .counters(counters)
                .trends(trends)
                .build();
    }

    /**
     * 我的 Agent 列表：从统一资源主表按创建人过滤。
     */
    @Override
    public List<Map<String, Object>> myAgents(Long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, resource_code, display_name, status, update_time FROM t_resource "
                        + "WHERE deleted = 0 AND resource_type = 'agent' AND created_by = ? ORDER BY update_time DESC",
                userId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", row.get("id"));
            r.put("agentName", row.get("resource_code"));
            r.put("displayName", row.get("display_name"));
            r.put("status", row.get("status"));
            r.put("agentType", "agent");
            r.put("updateTime", toDateTime(row.get("update_time")));
            out.add(r);
        }
        return out;
    }

    /**
     * 我的 Skill 列表：联合 Skill 扩展表补充类型和父级信息。
     */
    @Override
    public List<Map<String, Object>> mySkills(Long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT r.id, r.resource_code, r.display_name, r.status, r.update_time, ext.skill_type, ext.parent_resource_id "
                        + "FROM t_resource r LEFT JOIN t_resource_skill_ext ext ON r.id = ext.resource_id "
                        + "WHERE r.deleted = 0 AND r.resource_type = 'skill' AND r.created_by = ? ORDER BY r.update_time DESC",
                userId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", row.get("id"));
            r.put("agentName", row.get("resource_code"));
            r.put("displayName", row.get("display_name"));
            r.put("status", row.get("status"));
            r.put("packFormat", row.get("skill_type"));
            r.put("agentType", row.get("skill_type"));
            r.put("parentId", row.get("parent_resource_id"));
            r.put("updateTime", toDateTime(row.get("update_time")));
            out.add(r);
        }
        return out;
    }

    /**
     * 已授权技能分页：合并“本人创建 + 公共发布”并按最近使用排序。
     */
    @Override
    public PageResult<AuthorizedSkillVO> pageAuthorizedSkills(Long userId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(1, Math.min(pageSize, 100));

        List<Map<String, Object>> ownRows = jdbcTemplate.queryForList(
                "SELECT r.id, r.resource_code, r.display_name, r.description, r.status, r.update_time, ext.skill_type "
                        + "FROM t_resource r LEFT JOIN t_resource_skill_ext ext ON r.id = ext.resource_id "
                        + "WHERE r.deleted = 0 AND r.resource_type = 'skill' AND r.created_by = ? ORDER BY r.update_time DESC",
                userId);
        List<Map<String, Object>> publicRows = jdbcTemplate.queryForList(
                "SELECT r.id, r.resource_code, r.display_name, r.description, r.status, r.update_time, ext.skill_type "
                        + "FROM t_resource r LEFT JOIN t_resource_skill_ext ext ON r.id = ext.resource_id "
                        + "WHERE r.deleted = 0 AND r.resource_type = 'skill' AND COALESCE(ext.is_public,0)=1 AND r.status='published' ORDER BY r.update_time DESC");

        Map<String, LocalDateTime> lastUsedAtByAgentName = new HashMap<>();
        List<UsageRecord> usageRows = usageRecordMapper.selectList(
                new LambdaQueryWrapper<UsageRecord>()
                        .eq(UsageRecord::getUserId, userId)
                        .eq(UsageRecord::getType, "skill")
                        .orderByDesc(UsageRecord::getCreateTime));
        for (UsageRecord row : usageRows) {
            if (row.getAgentName() == null || lastUsedAtByAgentName.containsKey(row.getAgentName())) {
                continue;
            }
            lastUsedAtByAgentName.put(row.getAgentName(), row.getCreateTime());
        }

        List<AuthorizedSkillVO> merged = new ArrayList<>();
        Set<Long> added = new HashSet<>();
        for (Map<String, Object> row : ownRows) {
            Long id = longValue(row.get("id"));
            if (id == null || added.contains(id)) {
                continue;
            }
            merged.add(toAuthorizedSkillVO(row, "own", lastUsedAtByAgentName.get(str(row.get("resource_code")))));
            added.add(id);
        }
        for (Map<String, Object> row : publicRows) {
            Long id = longValue(row.get("id"));
            if (id == null || added.contains(id)) {
                continue;
            }
            merged.add(toAuthorizedSkillVO(row, "public", lastUsedAtByAgentName.get(str(row.get("resource_code")))));
            added.add(id);
        }

        merged.sort(Comparator.comparing(
                (AuthorizedSkillVO v) -> v.getLastUsedTime() != null ? v.getLastUsedTime() : v.getUpdateTime(),
                Comparator.nullsLast(Comparator.reverseOrder())));

        int from = (safePage - 1) * safePageSize;
        if (from >= merged.size()) {
            return PageResult.of(List.of(), merged.size(), safePage, safePageSize);
        }
        int to = Math.min(from + safePageSize, merged.size());
        return PageResult.of(merged.subList(from, to), merged.size(), safePage, safePageSize);
    }

    @Override
    public List<RecentUseVO> recentUse(Long userId, int limit, String type) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        LambdaQueryWrapper<UsageRecord> query = new LambdaQueryWrapper<UsageRecord>()
                .eq(UsageRecord::getUserId, userId)
                .orderByDesc(UsageRecord::getCreateTime)
                .last("LIMIT " + safeLimit);
        if (StringUtils.hasText(type)) {
            query.eq(UsageRecord::getType, type);
        }

        List<UsageRecord> rows = usageRecordMapper.selectList(query);
        List<RecentUseVO> result = new ArrayList<>(rows.size());
        for (UsageRecord row : rows) {
            result.add(RecentUseVO.builder()
                    .recordId(row.getId())
                    .type(row.getType())
                    .targetCode(row.getAgentName())
                    .targetName(row.getDisplayName())
                    .action(row.getAction())
                    .status(row.getStatus())
                    .tokenCost(row.getTokenCost())
                    .latencyMs(row.getLatencyMs())
                    .createTime(row.getCreateTime())
                    .build());
        }
        return result;
    }

    private static AuthorizedSkillVO toAuthorizedSkillVO(Map<String, Object> row, String source, LocalDateTime lastUsedTime) {
        String packFmt = str(row.get("skill_type"));
        return AuthorizedSkillVO.builder()
                .id(longValue(row.get("id")))
                .agentName(str(row.get("resource_code")))
                .displayName(str(row.get("display_name")))
                .description(str(row.get("description")))
                .agentType(packFmt)
                .packFormat(packFmt)
                .status(str(row.get("status")))
                .source(source)
                .updateTime(toDateTime(row.get("update_time")))
                .lastUsedTime(lastUsedTime)
                .build();
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static Long longValue(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.valueOf(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDateTime toDateTime(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDateTime ldt) return ldt;
        if (v instanceof Timestamp ts) return ts.toLocalDateTime();
        return null;
    }
}
