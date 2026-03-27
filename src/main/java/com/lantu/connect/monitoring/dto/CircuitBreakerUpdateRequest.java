package com.lantu.connect.monitoring.dto;

import lombok.Data;

/**
 * 熔断器更新请求
 *
 * @author 王帝
 * @date 2026-03-22
 */
@Data
public class CircuitBreakerUpdateRequest {

    private String serviceKey;
    private Integer openDurationSeconds;
}
