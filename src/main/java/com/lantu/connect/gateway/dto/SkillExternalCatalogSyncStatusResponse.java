package com.lantu.connect.gateway.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 技能在线市场库同步只读状态（供前端轮询提示等）。
 */
@Data
@Builder
public class SkillExternalCatalogSyncStatusResponse {

    private LocalDateTime lastSuccessAt;
    private LocalDateTime lastAttemptAt;
    private String lastError;
    private boolean pendingResync;
    /**
     * 提示用：本机 JVM 同步进行中或 Redis 锁存在时为 true；多实例下后者更准确。
     */
    private boolean syncInProgressHint;
}
