package com.lantu.connect.monitoring.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlertRuleDryRunResult {

    private boolean wouldFire;

    private String operator;

    private BigDecimal threshold;

    private BigDecimal sampleValue;

    /** 人类可读说明 */
    private String detail;
}
