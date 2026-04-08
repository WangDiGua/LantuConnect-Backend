package com.lantu.connect.monitoring;

import com.lantu.connect.realtime.RealtimePushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 健康探活与资源级 DB 熔断联动：探针确认恢复后关闭 OPEN/HALF_OPEN，避免「健康已 green 但熔断仍开」长期不可调用。
 * 不触碰 {@code FORCED_OPEN}（视为运维强制停顿）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ResourceCircuitHealthBridge {

    private final JdbcTemplate jdbcTemplate;
    private final RealtimePushService realtimePushService;

    /**
     * 在健康探针写入 {@code healthy} 后调用：若熔断处于 OPEN 或 HALF_OPEN，则收回为 CLOSED 并推送（若开启）。
     */
    public void resetOpenOrHalfOpenAfterHealthyProbe(String resourceType, long resourceId) {
        if (!StringUtils.hasText(resourceType)) {
            return;
        }
        String rt = resourceType.trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> resRows = jdbcTemplate.queryForList("""
                        SELECT resource_code, display_name FROM t_resource
                        WHERE id = ? AND deleted = 0 LIMIT 1
                        """,
                resourceId);
        if (resRows.isEmpty()) {
            return;
        }
        Map<String, Object> res = resRows.get(0);
        List<Map<String, Object>> cbRows = jdbcTemplate.queryForList("""
                        SELECT current_state FROM t_resource_circuit_breaker
                        WHERE resource_type = ? AND resource_id = ? LIMIT 1
                        """,
                rt, resourceId);
        if (cbRows.isEmpty()) {
            return;
        }
        String prev = String.valueOf(cbRows.get(0).get("current_state")).trim();
        String pu = prev.toUpperCase(Locale.ROOT);
        if (!"OPEN".equals(pu) && !"HALF_OPEN".equals(pu)) {
            return;
        }
        int n = jdbcTemplate.update("""
                        UPDATE t_resource_circuit_breaker
                        SET current_state = 'CLOSED', success_count = 0, failure_count = 0, update_time = NOW()
                        WHERE resource_type = ? AND resource_id = ? AND UPPER(TRIM(current_state)) IN ('OPEN', 'HALF_OPEN')
                        """,
                rt, resourceId);
        if (n <= 0) {
            return;
        }
        log.info("[熔断-探活联动 {}] resource_type={} resource_id={} {} -> CLOSED", resourceType, rt, resourceId, prev);
        Object rc = res.get("resource_code");
        Object dname = res.get("display_name");
        String code = rc == null ? "" : String.valueOf(rc);
        String dn = (dname == null || !StringUtils.hasText(String.valueOf(dname))) ? code : String.valueOf(dname);
        realtimePushService.pushCircuitStateChanged(resourceId, rt, code, dn, "CLOSED", prev);
    }
}
