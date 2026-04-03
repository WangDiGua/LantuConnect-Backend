package com.lantu.connect.dashboard.service.impl;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.security.CasbinAuthorizationService;
import com.lantu.connect.dashboard.dto.OwnerDeveloperStatsVO;
import com.lantu.connect.dashboard.dto.OwnerResourceTypeInvokeCount;
import com.lantu.connect.dashboard.service.OwnerDeveloperStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OwnerDeveloperStatsServiceImpl implements OwnerDeveloperStatsService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final CasbinAuthorizationService casbinAuthorizationService;

    @Override
    public OwnerDeveloperStatsVO ownerResourceStats(Long operatorUserId, Long ownerUserId, int periodDays) {
        if (operatorUserId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未认证用户无法查看开发者统计");
        }
        int days = Math.min(365, Math.max(1, periodDays));
        long targetOwner = ownerUserId != null ? ownerUserId : operatorUserId;
        ensureMayViewOwnerStats(operatorUserId, targetOwner);

        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(days);

        Long gatewayTotal = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM t_call_log cl
                        INNER JOIN t_resource r ON r.id = CAST(cl.agent_id AS UNSIGNED) AND r.deleted = 0
                        WHERE r.created_by = ?
                          AND (cl.resource_type IS NULL OR LOWER(cl.resource_type) = LOWER(r.resource_type))
                          AND cl.create_time >= ? AND cl.create_time <= ?
                        """,
                Long.class, targetOwner, start, end);
        Long gatewaySuccess = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM t_call_log cl
                        INNER JOIN t_resource r ON r.id = CAST(cl.agent_id AS UNSIGNED) AND r.deleted = 0
                        WHERE r.created_by = ?
                          AND (cl.resource_type IS NULL OR LOWER(cl.resource_type) = LOWER(r.resource_type))
                          AND cl.create_time >= ? AND cl.create_time <= ?
                          AND cl.status = 'success'
                        """,
                Long.class, targetOwner, start, end);

        Long usageInvoke = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM t_usage_record ur
                        INNER JOIN t_resource r ON r.created_by = ? AND r.deleted = 0
                          AND (
                            ur.resource_id = r.id
                            OR (ur.resource_id IS NULL AND ur.agent_name = r.resource_code AND ur.type = r.resource_type)
                          )
                        WHERE ur.action = 'invoke'
                          AND ur.create_time >= ? AND ur.create_time <= ?
                        """,
                Long.class, targetOwner, start, end);

        Long skillDl = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM t_skill_pack_download_event
                        WHERE owner_user_id = ? AND create_time >= ? AND create_time <= ?
                        """,
                Long.class, targetOwner, start, end);

        List<OwnerResourceTypeInvokeCount> byType = jdbcTemplate.query("""
                        SELECT LOWER(COALESCE(NULLIF(TRIM(cl.resource_type), ''), r.resource_type)) AS rt,
                               COUNT(*) AS cnt,
                               SUM(CASE WHEN cl.status = 'success' THEN 1 ELSE 0 END) AS okcnt
                        FROM t_call_log cl
                        INNER JOIN t_resource r ON r.id = CAST(cl.agent_id AS UNSIGNED) AND r.deleted = 0
                        WHERE r.created_by = ?
                          AND (cl.resource_type IS NULL OR LOWER(cl.resource_type) = LOWER(r.resource_type))
                          AND cl.create_time >= ? AND cl.create_time <= ?
                        GROUP BY LOWER(COALESCE(NULLIF(TRIM(cl.resource_type), ''), r.resource_type))
                        ORDER BY cnt DESC
                        """,
                (rs, i) -> OwnerResourceTypeInvokeCount.builder()
                        .resourceType(rs.getString("rt"))
                        .invokeCount(rs.getLong("cnt"))
                        .successCount(rs.getLong("okcnt"))
                        .build(),
                targetOwner, start, end);

        return OwnerDeveloperStatsVO.builder()
                .ownerUserId(targetOwner)
                .periodDays(days)
                .periodStart(TS.format(start))
                .periodEnd(TS.format(end))
                .gatewayInvokeTotal(gatewayTotal != null ? gatewayTotal : 0L)
                .gatewayInvokeSuccess(gatewaySuccess != null ? gatewaySuccess : 0L)
                .usageRecordInvokeTotal(usageInvoke != null ? usageInvoke : 0L)
                .skillPackDownloadTotal(skillDl != null ? skillDl : 0L)
                .gatewayInvokesByResourceType(byType)
                .build();
    }

    private void ensureMayViewOwnerStats(Long operatorUserId, long targetOwnerUserId) {
        if (operatorUserId == targetOwnerUserId) {
            return;
        }
        if (casbinAuthorizationService.hasAnyRole(operatorUserId, new String[]{"platform_admin", "admin"})) {
            return;
        }
        if (casbinAuthorizationService.isDeptAdminOnly(operatorUserId)) {
            Long opMenu = casbinAuthorizationService.userDepartmentMenuId(operatorUserId);
            Long owMenu = casbinAuthorizationService.userDepartmentMenuId(targetOwnerUserId);
            if (opMenu != null && opMenu.equals(owMenu)) {
                return;
            }
        }
        throw new BusinessException(ResultCode.FORBIDDEN, "仅资源拥有者、同部门部门管理员或平台管理员可查看该开发者的统计");
    }
}
