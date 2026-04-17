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
public class CallLogEvidenceVO {

    private String id;
    private String traceId;
    private String resourceType;
    private String resourceName;
    private String method;
    private String status;
    private Integer statusCode;
    private Integer latencyMs;
    private String errorMessage;
    private LocalDateTime createdAt;
}
