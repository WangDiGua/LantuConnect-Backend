package com.lantu.connect.gateway.support;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * 技能市场库全量同步 Micrometer 指标：{@code skill.catalog.sync}、{@code skill.catalog.sync.duration}。
 */
@Component
public class SkillExternalCatalogSyncMetrics {

    private final MeterRegistry registry;

    public SkillExternalCatalogSyncMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordSyncResult(String result) {
        registry.counter("skill.catalog.sync", "result", result).increment();
    }

    public Timer.Sample startSyncTimer() {
        return Timer.start(registry);
    }

    public void stopSyncTimer(Timer.Sample sample, String result) {
        if (sample == null) {
            return;
        }
        sample.stop(registry.timer("skill.catalog.sync.duration", "result", result));
    }
}
