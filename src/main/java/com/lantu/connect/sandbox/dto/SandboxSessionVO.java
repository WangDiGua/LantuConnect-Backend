package com.lantu.connect.sandbox.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class SandboxSessionVO {

    private String sessionToken;
    private String apiKeyPrefix;
    private Integer maxCalls;
    private Integer usedCalls;
    private Integer maxTimeoutSec;
    private List<String> allowedResourceTypes;
    private LocalDateTime expiresAt;
    private LocalDateTime lastInvokeAt;
    private String status;
}
