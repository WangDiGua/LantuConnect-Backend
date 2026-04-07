package com.lantu.connect.gateway.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lantu.connect.gateway.entity.SkillExternalCatalogItem;
import com.lantu.connect.gateway.entity.SkillExternalCatalogSyncState;
import com.lantu.connect.gateway.mapper.SkillExternalCatalogItemMapper;
import com.lantu.connect.gateway.mapper.SkillExternalCatalogSyncStateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 技能市场同步在 DB 上的事务边界：短操作使用 {@link Propagation#REQUIRES_NEW} 立即提交，
 * 避免与长时间远程拉取叠在同一大事务里锁住 {@code t_skill_external_catalog_sync}，
 * 导致并发路径上 {@code markPendingResyncTrue} 出现 Lock wait timeout。
 */
@Service
@RequiredArgsConstructor
public class SkillExternalCatalogSyncTxnBoundary {

    private static final int SYNC_STATE_ID = 1;
    private static final int UPSERT_CHUNK = 200;

    private final SkillExternalCatalogItemMapper itemMapper;
    private final SkillExternalCatalogSyncStateMapper syncStateMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void touchAttempt(LocalDateTime t) {
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

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markPendingResyncTrue() {
        ensureSyncStateRowExists();
        syncStateMapper.update(null, Wrappers.<SkillExternalCatalogSyncState>lambdaUpdate()
                .set(SkillExternalCatalogSyncState::getPendingResync, true)
                .eq(SkillExternalCatalogSyncState::getId, SYNC_STATE_ID));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markFailure(String msg) {
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

    /**
     * 大批量落库 + 同步状态一行；单独事务，不包含远程 HTTP。
     *
     * @return 若此前 {@code pending_resync} 为 true，同步成功后需再调度一轮跟进（afterCommit）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean persistRemoteSnapshotSuccess(LocalDateTime attemptAt,
                                                long nextBatch,
                                                List<SkillExternalCatalogItem> rows) {
        for (int i = 0; i < rows.size(); i += UPSERT_CHUNK) {
            int to = Math.min(i + UPSERT_CHUNK, rows.size());
            itemMapper.upsertBatch(rows.subList(i, to));
        }
        itemMapper.deleteWhereSyncBatchLt(nextBatch);

        SkillExternalCatalogSyncState state = syncStateMapper.selectById(SYNC_STATE_ID);
        if (state == null) {
            SkillExternalCatalogSyncState n = new SkillExternalCatalogSyncState();
            n.setId(SYNC_STATE_ID);
            n.setCurrentBatch(nextBatch);
            n.setLastSuccessAt(LocalDateTime.now());
            n.setLastError(null);
            n.setLastAttemptAt(attemptAt);
            n.setPendingResync(false);
            syncStateMapper.insert(n);
            return false;
        }
        boolean chainFollowup = Boolean.TRUE.equals(state.getPendingResync());
        state.setCurrentBatch(nextBatch);
        state.setLastSuccessAt(LocalDateTime.now());
        state.setLastError(null);
        state.setLastAttemptAt(attemptAt);
        state.setPendingResync(false);
        syncStateMapper.updateById(state);
        return chainFollowup;
    }

    private void ensureSyncStateRowExists() {
        SkillExternalCatalogSyncState s = syncStateMapper.selectById(SYNC_STATE_ID);
        if (s == null) {
            SkillExternalCatalogSyncState n = new SkillExternalCatalogSyncState();
            n.setId(SYNC_STATE_ID);
            n.setCurrentBatch(0L);
            n.setPendingResync(false);
            syncStateMapper.insert(n);
        }
    }
}
