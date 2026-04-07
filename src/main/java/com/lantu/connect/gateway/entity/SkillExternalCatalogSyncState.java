package com.lantu.connect.gateway.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_skill_external_catalog_sync")
public class SkillExternalCatalogSyncState {

    @TableId(value = "id", type = IdType.INPUT)
    private Integer id;

    @TableField("last_success_at")
    private LocalDateTime lastSuccessAt;

    @TableField("last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @TableField("last_error")
    private String lastError;

    /**
     * 保存配置或跨实例抢锁失败等场景置 1；成功同步并消费后清 0，必要时再调度一次异步同步。
     */
    @TableField("pending_resync")
    private Boolean pendingResync;

    @TableField("current_batch")
    private Long currentBatch;
}
