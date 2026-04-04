package com.lantu.connect.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 登记前 MCP 连通性探测（不创建资源、不托管上游）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class McpConnectivityProbeRequest {

    @NotBlank
    private String endpoint;

    /** 与 {@link ResourceUpsertRequest#authType} 同源：none / api_key / bearer / basic / oauth2_client */
    private String authType;

    /** 与资源 auth_config JSON 同源，可含 method、headers、token 等 */
    private Map<String, Object> authConfig;

    /** http | websocket | stdio（与登记表单 transport 一致） */
    private String transport;
}
