package com.lantu.connect.monitoring.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class TraceSpanLogVO {

    private String timestamp;
    private String message;
    private Map<String, Object> context;
}
