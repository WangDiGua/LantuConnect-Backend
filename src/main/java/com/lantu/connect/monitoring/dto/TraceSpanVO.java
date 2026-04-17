package com.lantu.connect.monitoring.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class TraceSpanVO {

    private String id;
    private String traceId;
    private String parentId;
    private String operationName;
    private String serviceName;
    private LocalDateTime startTime;
    private Integer duration;
    private String status;
    private Map<String, Object> tags;
    private List<TraceSpanLogVO> logs;
}
