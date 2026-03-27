package com.lantu.connect.common.integration;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 网络下发结果
 *
 * @author 王帝
 * @date 2026-03-23
 */
@Data
@Builder
public class NetworkApplyResult {

    private boolean success;

    private String taskId;

    private String message;

    private LocalDateTime appliedAt;

    private String networkId;

    private String status;
}
