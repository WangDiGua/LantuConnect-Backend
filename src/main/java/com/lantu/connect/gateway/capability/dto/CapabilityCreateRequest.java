package com.lantu.connect.gateway.capability.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CapabilityCreateRequest {

    @NotBlank
    private String source;

    @NotBlank
    private String detectedType;

    @NotBlank
    private String displayName;

    private String resourceCode;

    private String description;

    private String sourceType;

    private String runtimeMode;

    private Map<String, Object> inputSchema;

    private Map<String, Object> defaults;

    private Map<String, Object> authRefs;

    private List<Long> bindings;

    private Map<String, Object> capabilities;

    private Boolean submitForAudit;
}
