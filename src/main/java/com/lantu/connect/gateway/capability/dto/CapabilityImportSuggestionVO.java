package com.lantu.connect.gateway.capability.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class CapabilityImportSuggestionVO {

    private String detectedType;

    private String confidence;

    private String reason;

    private String displayName;

    private String resourceCode;

    private String description;

    private String runtimeMode;

    private Map<String, Object> inputSchema;

    private Map<String, Object> defaults;

    private Map<String, Object> authRefs;

    private List<Long> bindings;

    private Map<String, Object> capabilities;

    private Boolean requiresConfirmation;

    private List<String> warnings;
}
