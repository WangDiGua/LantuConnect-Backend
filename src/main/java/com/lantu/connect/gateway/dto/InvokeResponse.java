package com.lantu.connect.gateway.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InvokeResponse {

    private String requestId;

    private String traceId;

    private String resourceType;

    private String resourceId;

    private Integer statusCode;

    private String status;

    private Long latencyMs;

    private String body;
}
