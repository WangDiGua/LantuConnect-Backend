package com.lantu.connect.gateway.capability.dto;

import lombok.Data;

import java.util.Map;

@Data
public class CapabilityInvokeRequest {

    private String version;

    private Integer timeoutSec = 30;

    private Map<String, Object> payload;
}
