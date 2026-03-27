package com.lantu.connect.sysconfig.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 系统配置 QuotaUpdateRequest 数据传输对象
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
public class QuotaUpdateRequest {

    @NotNull
    private Long id;
    private Long dailyLimit;
    private Long monthlyLimit;
    private Long dailyUsed;
    private Long monthlyUsed;
}
