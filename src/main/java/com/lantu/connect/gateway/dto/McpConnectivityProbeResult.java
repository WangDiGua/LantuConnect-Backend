package com.lantu.connect.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpConnectivityProbeResult {
    private boolean ok;
    private int statusCode;
    private long latencyMs;
    private String message;
    /** 截断后的上游正文，便于排障 */
    private String bodyPreview;
}
