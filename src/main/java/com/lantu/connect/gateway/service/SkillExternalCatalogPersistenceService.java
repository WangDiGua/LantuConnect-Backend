package com.lantu.connect.gateway.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.gateway.catalog.GitHubZipPackUrlMirror;
import com.lantu.connect.gateway.catalog.SkillExternalCatalogDedupeKeys;
import com.lantu.connect.gateway.config.SkillExternalCatalogProperties;
import com.lantu.connect.gateway.dto.SkillExternalCatalogItemVO;
import com.lantu.connect.gateway.entity.SkillExternalCatalogItem;
import com.lantu.connect.gateway.entity.SkillExternalCatalogSyncState;
import com.lantu.connect.gateway.mapper.SkillExternalCatalogItemMapper;
import com.lantu.connect.gateway.mapper.SkillExternalCatalogSyncStateMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 技能在线市场远程目录库内镜像：按 dedupe_key upsert；远程失败时读库内快照。
 */
@Service
@Slf4j
public class SkillExternalCatalogPersistenceService {

    private static final Object SYNC_MONITOR = new Object();
    private static final int UPSERT_CHUNK = 200;
    private static final int SYNC_STATE_ID = 1;

    private final SkillExternalCatalogItemMapper itemMapper;
    private final SkillExternalCatalogSyncStateMapper syncStateMapper;
    private final SkillExternalCatalogPersistenceAsync asyncRunner;
    private final SkillExternalCatalogService skillExternalCatalogService;

    public SkillExternalCatalogPersistenceService(
            SkillExternalCatalogItemMapper itemMapper,
            SkillExternalCatalogSyncStateMapper syncStateMapper,
            SkillExternalCatalogPersistenceAsync asyncRunner,
            @Lazy SkillExternalCatalogService skillExternalCatalogService) {
        this.itemMapper = itemMapper;
        this.syncStateMapper = syncStateMapper;
        this.asyncRunner = asyncRunner;
        this.skillExternalCatalogService = skillExternalCatalogService;
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
     * 库为空时阻塞同步；非空且超过 cacheTtl 则异步刷新（读请求仍返回当前库内数据）。
     */
    public void ensureFresh(SkillExternalCatalogProperties properties) {
        if (!usePersistence(properties,
                properties.getProvider() == null ? "skillsmp" : properties.getProvider().trim())) {
            return;
        }
        long cnt = itemMapper.selectCount(null);
        if (cnt == 0) {
            syncRemoteSnapshotBlocking(properties);
            return;
        }
        SkillExternalCatalogSyncState st = syncStateMapper.selectById(SYNC_STATE_ID);
        LocalDateTime last = st != null ? st.getLastSuccessAt() : null;
        long ttl = Math.max(60, properties.getCacheTtlSeconds());
        boolean stale = last == null || Duration.between(last, LocalDateTime.now()).getSeconds() >= ttl;
        if (stale) {
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

    public List<SkillExternalCatalogItemVO> loadAllVosOrdered() {
        List<SkillExternalCatalogItem> rows = itemMapper.selectList(
                Wrappers.<SkillExternalCatalogItem>query().last("ORDER BY IFNULL(`stars`,0) DESC, `name` ASC"));
        List<SkillExternalCatalogItemVO> out = new ArrayList<>(rows.size());
        for (SkillExternalCatalogItem row : rows) {
            out.add(SkillExternalCatalogPersistenceService.toVo(row));
        }
        return out;
    }

    public PageResult<SkillExternalCatalogItemVO> pageVos(String keyword, int page, int pageSize) {
        String q = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        int p = Math.max(1, page);
        int ps = Math.min(100, Math.max(1, pageSize));
        long total = itemMapper.countKeyword(q);
        long offset = (long) (p - 1) * ps;
        List<SkillExternalCatalogItem> rows = itemMapper.selectKeywordPage(q, offset, ps);
        List<SkillExternalCatalogItemVO> list =
                rows.stream().map(SkillExternalCatalogPersistenceService::toVo).collect(Collectors.toList());
        return PageResult.of(list, total, p, ps);
    }

    @Transactional(rollbackFor = Exception.class)
    public void syncRemoteSnapshotBlocking(SkillExternalCatalogProperties properties) {
        if (!usePersistence(properties,
                properties.getProvider() == null ? "skillsmp" : properties.getProvider().trim())) {
            return;
        }
        synchronized (SYNC_MONITOR) {
            LocalDateTime attemptAt = LocalDateTime.now();
            touchAttempt(attemptAt);
            List<SkillExternalCatalogItemVO> snap = skillExternalCatalogService.tryBuildRemoteSnapshotForDb(properties);
            if (snap.isEmpty()) {
                markFailure("远程快照为空或拉取失败");
                log.warn("技能市场库同步跳过：远程无数据");
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
            for (SkillExternalCatalogItemVO vo : snap) {
                rows.add(toRow(vo, nextBatch));
            }
            for (int i = 0; i < rows.size(); i += UPSERT_CHUNK) {
                int to = Math.min(i + UPSERT_CHUNK, rows.size());
                itemMapper.upsertBatch(rows.subList(i, to));
            }
            itemMapper.deleteWhereSyncBatchLt(nextBatch);
            state.setCurrentBatch(nextBatch);
            state.setLastSuccessAt(LocalDateTime.now());
            state.setLastError(null);
            state.setLastAttemptAt(attemptAt);
            syncStateMapper.updateById(state);
            log.info("技能市场库同步完成 batch={} rows={}", nextBatch, snap.size());
        }
    }

    private void touchAttempt(LocalDateTime t) {
        SkillExternalCatalogSyncState s = syncStateMapper.selectById(SYNC_STATE_ID);
        if (s == null) {
            SkillExternalCatalogSyncState n = new SkillExternalCatalogSyncState();
            n.setId(SYNC_STATE_ID);
            n.setCurrentBatch(0L);
            n.setLastAttemptAt(t);
            syncStateMapper.insert(n);
            return;
        }
        s.setLastAttemptAt(t);
        syncStateMapper.updateById(s);
    }

    private void markFailure(String msg) {
        SkillExternalCatalogSyncState s = syncStateMapper.selectById(SYNC_STATE_ID);
        if (s == null) {
            return;
        }
        String m = msg == null ? "" : msg;
        if (m.length() > 1000) {
            m = m.substring(0, 1000);
        }
        s.setLastError(m);
        s.setLastAttemptAt(LocalDateTime.now());
        syncStateMapper.updateById(s);
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
                .build();
    }
}
