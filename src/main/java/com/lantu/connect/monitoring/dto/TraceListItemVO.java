package com.lantu.connect.monitoring.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TraceListItemVO {

    private String traceId;
    private String requestId;
    private String rootOperation;
    private String entryService;
    private String rootResourceType;
    private Long rootResourceId;
    private String rootResourceCode;
    private String rootDisplayName;
    private String status;
    private LocalDateTime startedAt;
    private Integer durationMs;
    private Integer spanCount;
    private Integer errorSpanCount;
    private String firstErrorMessage;
    private Long userId;
    private String ip;
}
