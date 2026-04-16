package com.lantu.connect.monitoring.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AlertSummaryVO {

    private long firing;
    private long acknowledged;
    private long silenced;
    private long resolvedToday;
    private long mine;
    private long enabledRules;
}
