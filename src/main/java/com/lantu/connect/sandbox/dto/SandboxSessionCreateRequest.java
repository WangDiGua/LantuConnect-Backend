package com.lantu.connect.sandbox.dto;

import lombok.Data;

import java.util.List;

@Data
public class SandboxSessionCreateRequest {

    private Integer ttlMinutes;
    private Integer maxCalls;
    private Integer maxTimeoutSec;
    private List<String> allowedResourceTypes;
}
