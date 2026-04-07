package com.lantu.connect.gateway.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.gateway.catalog.GitHubZipPackUrlMirror;
import com.lantu.connect.gateway.catalog.SkillExternalCatalogDedupeKeys;
import com.lantu.connect.gateway.config.SkillExternalCatalogProperties;
import com.lantu.connect.gateway.dto.SkillExternalCatalogItemVO;
import com.lantu.connect.gateway.entity.SkillExternalCatalogItem;
import com.lantu.connect.gateway.entity.SkillExternalCatalogSyncState;
import com.lantu.connect.gateway.dto.SkillExternalCatalogSyncStatusResponse;
import com.lantu.connect.gateway.mapper.SkillExternalCatalogItemMapper;
import com.lantu.connect.gateway.mapper.SkillExternalCatalogSyncStateMapper;
import com.lantu.connect.gateway.support.SkillExternalCatalogSyncDistributedLock;
import com.lantu.connect.gateway.support.SkillExternalCatalogSyncMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 技能在线市场远程目录库内镜像：按 dedupe_key upsert；远程失败时读库内快照。
 */
@Service
@Slf4j
public class SkillExternalCatalogPersistenceService {

    private static final Object SYNC_MONITOR = new Object();
    private static final int SYNC_STATE_ID = 1;

    /**
     * 全量同步单 JVM 单飞；与 Redis 锁并用时：持 Redis 锁路径不依赖本标志，Redis 降级时退回本标志。
     */
    private final AtomicBoolean catalogSyncInFlight = new AtomicBoolean(false);

    private final SkillExternalCatalogItemMapper itemMapper;
    private final SkillExternalCatalogSyncStateMapper syncStateMapper;
    private final SkillExternalCatalogSyncTxnBoundary syncTxnBoundary;
    private final SkillExternalCatalogPersistenceAsync asyncRunner;
    private final SkillExternalCatalogService skillExternalCatalogService;
    private final SkillExternalSkillMdService skillExternalSkillMdService;
    private final SkillExternalCatalogSyncDistributedLock catalogSyncDistributedLock;
    private final SkillExternalCatalogSyncMetrics catalogSyncMetrics;
    private final SkillExternalCatalogRuntimeConfigService runtimeConfigService;

    public SkillExternalCatalogPersistenceService(
            SkillExternalCatalogItemMapper itemMapper,
            SkillExternalCatalogSyncStateMapper syncStateMapper,
            SkillExternalCatalogSyncTxnBoundary syncTxnBoundary,
            SkillExternalCatalogPersistenceAsync asyncRunner,
            @Lazy SkillExternalCatalogService skillExternalCatalogService,
            @Lazy SkillExternalSkillMdService skillExternalSkillMdService,
            SkillExternalCatalogSyncDistributedLock catalogSyncDistributedLock,
            SkillExternalCatalogSyncMetrics catalogSyncMetrics,
            @Lazy SkillExternalCatalogRuntimeConfigService runtimeConfigService) {
        this.itemMapper = itemMapper;
        this.syncStateMapper = syncStateMapper;
        this.syncTxnBoundary = syncTxnBoundary;
        this.asyncRunner = asyncRunner;
        this.skillExternalCatalogService = skillExternalCatalogService;
        this.skillExternalSkillMdService = skillExternalSkillMdService;
        this.catalogSyncDistributedLock = catalogSyncDistributedLock;
        this.catalogSyncMetrics = catalogSyncMetrics;
        this.runtimeConfigService = runtimeConfigService;
    }

    public boolean usePersistence(SkillExternalCatalogProperties properties, String mode) {
        if (!properties.isPersistenceEnabled()) {
            return false;
        }
        if ("static".equalsIgnoreCase(mode)) {
            return false;
        }
        return "skillsmp".equalsIgnoreCase(mode) || "merge".equalsIgnoreCase(mode);
    }

    public boolean isTableEmpty() {
        return itemMapper.selectCount(null) == 0;
    }

    /**
     * 非空且超过 cacheTtl 则异步刷新（读请求仍返回当前库内数据）。
     * 库为空时不阻塞 HTTP：后台全量同步（含 SKILL.md 预取）可能长达数分钟，若在请求线程上
     * {@link #syncRemoteSnapshotBlocking} 会导致网关/浏览器超时；改触发异步同步，由列表接口走内存拉取回退。
     */
    public void ensureFresh(SkillExternalCatalogProperties properties) {
        if (!usePersistence(properties,
                properties.getProvider() == null ? "skillsmp" : properties.getProvider().trim())) {
            return;
        }
        long cnt = itemMapper.selectCount(null);
        if (cnt == 0) {
            log.info("技能市场镜像表为空，已触发异步全量同步（不阻塞当前请求）");
            asyncRunner.runSync(properties);
            return;
        }
        SkillExternalCatalogSyncState st = syncStateMapper.selectById(SYNC_STATE_ID);
        LocalDateTime last = st != null ? st.getLastSuccessAt() : null;
        long ttl = Math.max(60, properties.getCacheTtlSeconds());
        boolean stale = last == null || Duration.between(last, LocalDateTime.now()).getSeconds() >= ttl;
        if (stale) {
            log.debug("技能市场镜像超过 TTL，调度异步刷新");
            asyncRunner.runSync(properties);
        }
    }

    public void scheduleSyncAfterConfigSave(SkillExternalCatalogProperties properties) {
        if (!usePersistence(properties,
                properties.getProvider() == null ? "skillsmp" : properties.getProvider().trim())) {
            return;
        }
        asyncRunner.runSync(properties);
    }

    /**
     * 超管保存市场配置成功时置位，保证至少再跑一轮全量同步与 DB 对齐。
     */
    public void markPendingResyncWhenPersistenceEnabled(SkillExternalCatalogProperties properties) {
        if (!usePersistence(properties,
                properties.getProvider() == null ? "skillsmp" : properties.getProvider().trim())) {
            return;
        }
        syncTxnBoundary.markPendingResyncTrue();
    }

    public SkillExternalCatalogSyncStatusResponse getSyncStatusHint(SkillExternalCatalogProperties properties) {
        if (!usePersistence(properties,
                properties.getProvider() == null ? "skillsmp" : properties.getProvider().trim())) {
            return SkillExternalCatalogSyncStatusResponse.builder()
                    .pendingResync(false)
                    .syncInProgressHint(false)
                    .build();
        }
        SkillExternalCatalogSyncState st = syncStateMapper.selectById(SYNC_STATE_ID);
        boolean inFlight = catalogSyncInFlight.get();
        boolean redisHeld = false;
        if (properties.isSyncRedisLockEnabled()) {
            redisHeld = catalogSyncDistributedLock.exists();
        }
        return SkillExternalCatalogSyncStatusResponse.builder()
                .lastSuccessAt(st != null ? st.getLastSuccessAt() : null)
                .lastAttemptAt(st != null ? st.getLastAttemptAt() : null)
                .lastError(st != null ? st.getLastError() : null)
                .pendingResync(st != null && Boolean.TRUE.equals(st.getPendingResync()))
                .syncInProgressHint(inFlight || redisHeld)
                .build();
    }

    public List<SkillExternalCatalogItemVO> loadAllVosOrdered() {
        List<SkillExternalCatalogItem> rows = itemMapper.selectList(
                Wrappers.<SkillExternalCatalogItem>query().last("ORDER BY IFNULL(`stars`,0) DESC, `name` ASC"));
        List<SkillExternalCatalogItemVO> out = new ArrayList<>(rows.size());
        for (SkillExternalCatalogItem row : rows) {
            out.add(SkillExternalCatalogPersistenceService.toVo(row));
        }
        return out;
    }

    /**
     * 按镜像表主键取一行；{@code key} 须与落库 dedupe_key 一致（通常小写）。
     */
    public SkillExternalCatalogItemVO findVoByDedupeKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        SkillExternalCatalogItem row = itemMapper.selectByDedupeKey(key.trim());
        if (row == null) {
            return null;
        }
        return SkillExternalCatalogPersistenceService.toVo(row);
    }

    public PageResult<SkillExternalCatalogItemVO> pageVos(String keyword,
                                                          int page,
                                                          int pageSize,
                                                          Integer minStars,
                                                          Integer maxStars,
                                                          String source) {
        String q = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        int p = Math.max(1, page);
        int ps = Math.min(100, Math.max(1, pageSize));
        long total = itemMapper.countKeyword(q, minStars, maxStars, source);
        long offset = (long) (p - 1) * ps;
        List<SkillExternalCatalogItem> rows = itemMapper.selectKeywordPage(q, offset, ps, minStars, maxStars, source);
        List<SkillExternalCatalogItemVO> list =
                rows.stream().map(SkillExternalCatalogPersistenceService::toVo).collect(Collectors.toList());
        return PageResult.of(list, total, p, ps);
    }

    public void syncRemoteSnapshotBlocking(SkillExternalCatalogProperties properties) {
        if (!usePersistence(properties,
                properties.getProvider() == null ? "skillsmp" : properties.getProvider().trim())) {
            return;
        }

        String redisToken = null;
        boolean redisLockHeld = false;
        boolean useJvmCas = !properties.isSyncRedisLockEnabled();

        if (properties.isSyncRedisLockEnabled()) {
            try {
                long ttlMs = Math.max(1L, (long) properties.getSyncRedisLockTtlMinutes()) * 60_000L;
                Optional<String> got = catalogSyncDistributedLock.tryAcquire(ttlMs);
                if (got.isEmpty()) {
                    log.warn("技能市场全量同步未获得 Redis 锁，已标记 pending_resync");
                    syncTxnBoundary.markPendingResyncTrue();
                    catalogSyncMetrics.recordSyncResult("lock_busy");
                    return;
                }
                redisToken = got.get();
                redisLockHeld = true;
                useJvmCas = false;
            } catch (RuntimeException redisEx) {
                if (SkillExternalCatalogSyncDistributedLock.isRedisLockLikelyFailure(redisEx)) {
                    log.error("技能市场同步 Redis 锁不可用，降级为 JVM 单飞: {}", redisEx.toString());
                    catalogSyncMetrics.recordSyncResult("redis_degraded");
                    useJvmCas = true;
                } else {
                    throw redisEx;
                }
            }
        }

        if (useJvmCas) {
            if (!catalogSyncInFlight.compareAndSet(false, true)) {
                log.info("技能市场全量同步已在执行中（JVM 单飞），已标记 pending_resync");
                syncTxnBoundary.markPendingResyncTrue();
                catalogSyncMetrics.recordSyncResult("lock_busy");
                return;
            }
        }

        var timer = catalogSyncMetrics.startSyncTimer();
        String timerResult = "error";
        boolean chainAfterCommit = false;
        try {
            synchronized (SYNC_MONITOR) {
                LocalDateTime attemptAt = LocalDateTime.now();
                syncTxnBoundary.touchAttempt(attemptAt);
                List<SkillExternalCatalogItemVO> snap = skillExternalCatalogService.tryBuildRemoteSnapshotForDb(properties);
                if (snap.isEmpty()) {
                    syncTxnBoundary.markFailure("远程快照为空或拉取失败");
                    log.warn("技能市场库同步跳过：远程无数据");
                    timerResult = "empty";
                    catalogSyncMetrics.recordSyncResult("empty");
                    return;
                }
                SkillExternalCatalogSyncState state = syncStateMapper.selectById(SYNC_STATE_ID);
                if (state == null) {
                    SkillExternalCatalogSyncState n = new SkillExternalCatalogSyncState();
                    n.setId(SYNC_STATE_ID);
                    n.setCurrentBatch(0L);
                    syncStateMapper.insert(n);
                    state = syncStateMapper.selectById(SYNC_STATE_ID);
                }
                long nextBatch = state.getCurrentBatch() + 1;
                List<SkillExternalCatalogItem> rows = new ArrayList<>(snap.size());
                int prefetchCap = Math.max(0, properties.getSkillMdPrefetchMaxPerSync());
                int prefetchAttempts = 0;
                for (SkillExternalCatalogItemVO vo : snap) {
                    SkillExternalCatalogItem row = toRow(vo, nextBatch);
                    if (properties.isSkillMdPrefetchOnSync() && (prefetchCap == 0 || prefetchAttempts < prefetchCap)) {
                        prefetchAttempts++;
                        Optional<SkillExternalSkillMdService.GithubSkillMdPrefetch> md =
                                skillExternalSkillMdService.prefetchGithubSkillMd(vo, properties);
                        if (md.isPresent()) {
                            SkillExternalSkillMdService.GithubSkillMdPrefetch p = md.get();
                            row.setSkillMd(p.markdown());
                            row.setSkillMdResolvedUrl(p.resolvedRawUrl());
                            row.setSkillMdTruncated(p.truncated());
                        }
                    }
                    rows.add(row);
                }
                chainAfterCommit = syncTxnBoundary.persistRemoteSnapshotSuccess(attemptAt, nextBatch, rows);
                log.info("技能市场库同步完成 batch={} rows={}", nextBatch, snap.size());
                timerResult = "success";
                catalogSyncMetrics.recordSyncResult("success");
            }
        } catch (RuntimeException ex) {
            syncTxnBoundary.markFailure(ex.getMessage());
            catalogSyncMetrics.recordSyncResult("error");
            throw ex;
        } finally {
            catalogSyncMetrics.stopSyncTimer(timer, timerResult);
            if (redisLockHeld && redisToken != null) {
                catalogSyncDistributedLock.releaseQuietly(redisToken);
            }
            if (useJvmCas) {
                catalogSyncInFlight.set(false);
            }
        }

        if (chainAfterCommit) {
            scheduleFollowupSyncAfterCommit();
        }
    }

    private void scheduleFollowupSyncAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    asyncRunner.runSync(runtimeConfigService.effective());
                }
            });
        } else {
            asyncRunner.runSync(runtimeConfigService.effective());
        }
    }

    private static SkillExternalCatalogItem toRow(SkillExternalCatalogItemVO vo, long batch) {
        SkillExternalCatalogItem e = new SkillExternalCatalogItem();
        e.setDedupeKey(SkillExternalCatalogDedupeKeys.fromVo(vo));
        e.setExternalId(vo.getId() != null ? vo.getId() : "");
        e.setName(vo.getName() != null ? vo.getName() : "");
        e.setSummary(vo.getSummary());
        e.setPackUrl(vo.getPackUrl() != null ? vo.getPackUrl() : "");
        e.setLicenseNote(vo.getLicenseNote());
        e.setSourceUrl(vo.getSourceUrl());
        e.setStars(vo.getStars());
        e.setSkillMdTruncated(false);
        e.setSyncBatch(batch);
        return e;
    }

    private static SkillExternalCatalogItemVO toVo(SkillExternalCatalogItem e) {
        String pack = e.getPackUrl() != null ? e.getPackUrl() : "";
        pack = GitHubZipPackUrlMirror.repairAccidentalRelativeMirrorPrefix(pack);
        return SkillExternalCatalogItemVO.builder()
                .id(e.getExternalId())
                .name(e.getName())
                .summary(e.getSummary())
                .packUrl(pack)
                .licenseNote(e.getLicenseNote())
                .sourceUrl(e.getSourceUrl())
                .stars(e.getStars())
                .itemKey(e.getDedupeKey())
                .build();
    }
}
