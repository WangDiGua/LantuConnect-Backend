package com.lantu.connect.gateway.health;

import com.lantu.connect.gateway.config.SkillExternalCatalogProperties;
import com.lantu.connect.gateway.entity.SkillExternalCatalogSyncState;
import com.lantu.connect.gateway.mapper.SkillExternalCatalogSyncStateMapper;
import com.lantu.connect.gateway.service.SkillExternalCatalogRuntimeConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 技能在线市场库镜像同步健康：最近成功时间、pending_resync、last_error。
 */
@Component("skillExternalCatalogSync")
@RequiredArgsConstructor
public class SkillExternalCatalogSyncHealthIndicator implements HealthIndicator {

    private static final int SYNC_STATE_ID = 1;

    private final SkillExternalCatalogSyncStateMapper syncStateMapper;
    private final SkillExternalCatalogRuntimeConfigService runtimeConfigService;

    @Override
    public Health health() {
        SkillExternalCatalogProperties p = runtimeConfigService.effective();
        String mode = p.getProvider() == null ? "skillsmp" : p.getProvider().trim();
        if (!p.isPersistenceEnabled() || "static".equalsIgnoreCase(mode)) {
            return Health.up()
                    .withDetail("persistence", "off or static provider — sync state not applicable")
                    .build();
        }
        if (!"skillsmp".equalsIgnoreCase(mode) && !"merge".equalsIgnoreCase(mode)) {
            return Health.up().withDetail("persistence", "provider does not use mirror table").build();
        }

        SkillExternalCatalogSyncState st = syncStateMapper.selectById(SYNC_STATE_ID);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("pendingResync", st != null && Boolean.TRUE.equals(st.getPendingResync()));
        details.put("lastSuccessAt", st != null ? String.valueOf(st.getLastSuccessAt()) : null);
        details.put("lastAttemptAt", st != null ? String.valueOf(st.getLastAttemptAt()) : null);
        String err = st != null ? st.getLastError() : null;
        details.put("lastError", err != null && err.length() > 200 ? err.substring(0, 200) + "…" : err);

        long ttlSec = Math.max(60, p.getCacheTtlSeconds());
        long staleSec = ttlSec * 2;
        LocalDateTime lastOk = st != null ? st.getLastSuccessAt() : null;
        boolean stale = lastOk == null
                || Duration.between(lastOk, LocalDateTime.now()).getSeconds() > staleSec;
        boolean degraded = stale
                || (st != null && Boolean.TRUE.equals(st.getPendingResync()))
                || (err != null && !err.isBlank());

        if (degraded) {
            return Health.status("DEGRADED")
                    .withDetails(details)
                    .build();
        }
        return Health.up().withDetails(details).build();
    }
}
