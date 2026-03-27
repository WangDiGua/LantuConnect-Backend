package com.lantu.connect.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class InvokeRequest {

    @NotBlank
    private String resourceType;

    @NotBlank
    private String resourceId;

    private String version;

    private Integer timeoutSec = 30;

    private Map<String, Object> payload;
}
