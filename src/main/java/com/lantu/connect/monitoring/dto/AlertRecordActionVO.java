package com.lantu.connect.monitoring.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class AlertRecordActionVO {

    private Long id;
    private String actionType;
    private Long operatorUserId;
    private String operatorName;
    private String note;
    private String previousStatus;
    private String nextStatus;
    private Map<String, Object> extra;
    private LocalDateTime createTime;
}
