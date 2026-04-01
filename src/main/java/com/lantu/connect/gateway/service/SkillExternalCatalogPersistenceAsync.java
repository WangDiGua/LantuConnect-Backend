package com.lantu.connect.gateway.service;

import com.lantu.connect.gateway.config.SkillExternalCatalogProperties;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 异步刷新技能市场库内镜像，避免阻塞 HTTP 读路径。
 */
@Component
public class SkillExternalCatalogPersistenceAsync {

    private final SkillExternalCatalogPersistenceService persistenceService;

    public SkillExternalCatalogPersistenceAsync(
            @Lazy SkillExternalCatalogPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @Async
    public void runSync(SkillExternalCatalogProperties properties) {
        persistenceService.syncRemoteSnapshotBlocking(properties);
    }
}
