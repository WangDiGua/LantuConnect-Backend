package com.lantu.connect.monitoring.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertEvidenceVO {

    private String id;
    private String ruleId;
    private String ruleName;
    private String severity;
    private String status;
    private String message;
    private LocalDateTime firedAt;
}
