package com.lantu.connect.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResourceResolveRequest {

    @NotBlank
    private String resourceType;

    @NotBlank
    private String resourceId;

    private String version;
}
