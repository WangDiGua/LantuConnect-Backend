package com.lantu.connect.monitoring.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 告警规则试跑：传入当前样本指标值，与规则阈值比较
 */
@Data
public class AlertRuleDryRunRequest {

    private BigDecimal sampleValue;

    private String mode;
}
