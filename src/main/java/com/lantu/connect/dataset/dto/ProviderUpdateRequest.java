package com.lantu.connect.dataset.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class ProviderUpdateRequest {

    @Size(max = 64)
    private String providerCode;

    @Size(max = 128)
    private String providerName;

    @Pattern(regexp = "internal|partner|cloud", message = "providerType 须为 internal、partner 或 cloud")
    private String providerType;

    private String description;

    @Pattern(regexp = "api_key|oauth2|basic|none", message = "authType 须为 api_key、oauth2、basic 或 none")
    private String authType;

    private Map<String, Object> authConfig;

    @Size(max = 512)
    private String baseUrl;

    @Pattern(regexp = "active|inactive", message = "status 须为 active 或 inactive")
    private String status;
}
