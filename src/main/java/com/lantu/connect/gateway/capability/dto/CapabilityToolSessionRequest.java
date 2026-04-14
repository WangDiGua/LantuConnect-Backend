package com.lantu.connect.gateway.capability.dto;

import lombok.Data;

import java.util.Map;

@Data
public class CapabilityToolSessionRequest {

    private String action;

    private String version;

    private Integer timeoutSec = 45;

    private String toolName;

    private Map<String, Object> arguments;
}
