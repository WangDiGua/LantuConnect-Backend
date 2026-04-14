package com.lantu.connect.realtime;

import com.lantu.connect.common.tx.TransactionCommitExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 审核待办数量变更后延迟合并推送，避免短时间多次写入导致 WebSocket 刷屏。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditPendingPushDebouncer {

    private static final int DEBOUNCE_SEC = 2;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "audit-pending-realtime-push");
        t.setDaemon(true);
        return t;
    });

    private final RealtimePushService realtimePushService;
    private volatile ScheduledFuture<?> pending;

    /**
     * 在事务提交后由调用方触发；多次调用会在安静 2s 后最多发送一条 pending_changed。
     */
    public synchronized void requestFlush() {
        TransactionCommitExecutor.runAfterCommitOrNow(this::scheduleFlush);
    }

    private synchronized void scheduleFlush() {
        if (pending != null) {
            pending.cancel(false);
        }
        pending = scheduler.schedule(() -> {
            try {
                realtimePushService.pushAuditPendingDigest();
            } catch (RuntimeException e) {
                log.debug("debounced audit pending push failed: {}", e.getMessage());
            } finally {
                synchronized (AuditPendingPushDebouncer.this) {
                    pending = null;
                }
            }
        }, DEBOUNCE_SEC, TimeUnit.SECONDS);
    }
}
