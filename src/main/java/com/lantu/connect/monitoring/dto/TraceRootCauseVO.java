package com.lantu.connect.monitoring.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TraceRootCauseVO {

    private String spanId;
    private String operationName;
    private String serviceName;
    private String message;
}
