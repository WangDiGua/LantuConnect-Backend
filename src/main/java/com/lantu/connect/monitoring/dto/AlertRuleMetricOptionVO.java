package com.lantu.connect.monitoring.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AlertRuleMetricOptionVO {

    private String value;
    private String label;
    private String description;
    private String unit;
}
