package com.lantu.connect.task;

import com.lantu.connect.gateway.config.SkillExternalCatalogProperties;
import com.lantu.connect.gateway.service.SkillExternalCatalogPersistenceAsync;
import com.lantu.connect.gateway.service.SkillExternalCatalogPersistenceService;
import com.lantu.connect.gateway.service.SkillExternalCatalogRuntimeConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时对齐远程技能目录（与 cache-ttl 互补：整点触发异步同步，读路径仍优先走库内快照）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SkillExternalCatalogMirrorSyncTask {

    private final SkillExternalCatalogRuntimeConfigService runtimeConfigService;
    private final SkillExternalCatalogPersistenceAsync persistenceAsync;
    private final SkillExternalCatalogPersistenceService persistenceService;

    @Scheduled(cron = "0 0 * * * ?")
    public void hourly() {
        SkillExternalCatalogProperties p = runtimeConfigService.effective();
        String mode = p.getProvider() == null ? "skillsmp" : p.getProvider().trim();
        if (!persistenceService.usePersistence(p, mode)) {
            return;
        }
        persistenceAsync.runSync(p);
    }
}
