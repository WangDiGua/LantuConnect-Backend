package com.lantu.connect.sysconfig.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 系统配置 QuotaCreateRequest 数据传输对象
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
public class QuotaCreateRequest {

    @NotBlank
    private String subjectType;
    @NotBlank
    private String subjectId;
    private Long dailyLimit;
    private Long monthlyLimit;
}
