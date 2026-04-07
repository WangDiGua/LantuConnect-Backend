package com.lantu.connect.gateway.service;

import com.lantu.connect.gateway.config.SkillExternalCatalogProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 异步刷新技能市场库内镜像，避免阻塞 HTTP 读路径。
 */
@Component
@Slf4j
public class SkillExternalCatalogPersistenceAsync {

    private final SkillExternalCatalogPersistenceService persistenceService;

    public SkillExternalCatalogPersistenceAsync(
            @Lazy SkillExternalCatalogPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @Async
    public void runSync(SkillExternalCatalogProperties properties) {
        try {
            persistenceService.syncRemoteSnapshotBlocking(properties);
        } catch (Throwable t) {
            // 避免异步线程静默失败；事务回滚由 sync 方法抛出时由 Spring 处理，此处兜底未捕获错误
            log.error("技能市场异步全量同步失败", t);
        }
    }
}
