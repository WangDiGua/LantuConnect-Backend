package com.lantu.connect.gateway.service;

import com.lantu.connect.gateway.dto.SkillExternalCatalogItemVO;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 技能市场列表进程内缓存；超管保存配置后通过 {@link #invalidate()} 立即失效。
 */
@Component
public class SkillExternalCatalogCacheCoordinator {

    private volatile List<SkillExternalCatalogItemVO> cache = List.of();
    private volatile long cacheExpiresAtMillis = 0;
    private final Object cacheLock = new Object();

    public List<SkillExternalCatalogItemVO> getCache() {
        return cache;
    }

    public long getCacheExpiresAtMillis() {
        return cacheExpiresAtMillis;
    }

    public void setCache(List<SkillExternalCatalogItemVO> rows, long expiresAtMillis) {
        this.cache = rows;
        this.cacheExpiresAtMillis = expiresAtMillis;
    }

    public Object getCacheLock() {
        return cacheLock;
    }

    public void invalidate() {
        synchronized (cacheLock) {
            cache = List.of();
            cacheExpiresAtMillis = 0;
        }
    }
}
