package com.lantu.connect.monitoring.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlertRuleDryRunResult {

    private boolean wouldFire;

    private String operator;

    private BigDecimal threshold;

    private BigDecimal sampleValue;

    private String detail;

    private String sampleSource;

    private String reason;

    private Boolean recoveryCandidate;

    private Map<String, Object> snapshot;
}
