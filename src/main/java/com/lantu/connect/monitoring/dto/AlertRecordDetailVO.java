package com.lantu.connect.monitoring.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class AlertRecordDetailVO {

    private String id;
    private String ruleId;
    private String ruleName;
    private String severity;
    private String status;
    private String message;
    private String source;
    private Long assigneeUserId;
    private String assigneeName;
    private LocalDateTime ackAt;
    private LocalDateTime silencedAt;
    private LocalDateTime reopenedAt;
    private LocalDateTime firedAt;
    private LocalDateTime resolvedAt;
    private BigDecimal lastSampleValue;
    private String scopeType;
    private String scopeLabel;
    private String resourceType;
    private Long resourceId;
    private String resourceName;
    private String metric;
    private String operator;
    private BigDecimal threshold;
    private String duration;
    private String ruleExpression;
    private String triggerReason;
    private Long activeSeconds;
    private Integer notificationCount;
    private Map<String, Object> labels;
    private Map<String, Object> triggerSnapshot;
    private Map<String, Object> ruleSnapshot;
    private List<AlertRecordActionVO> actions;
    private List<AlertNotificationVO> notifications;
    private String traceId;
    private TraceSummaryVO trace;
    private ResourceHealthEvidenceVO resourceHealth;
    private List<CallLogEvidenceVO> relatedCallLogs;
}
