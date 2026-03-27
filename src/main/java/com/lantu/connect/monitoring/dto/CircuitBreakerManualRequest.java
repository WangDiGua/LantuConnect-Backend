package com.lantu.connect.monitoring.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 监控 CircuitBreakerManualRequest 数据传输对象
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
public class CircuitBreakerManualRequest {

    @NotBlank
    private String serviceKey;
    private Integer openDurationSeconds;
}
