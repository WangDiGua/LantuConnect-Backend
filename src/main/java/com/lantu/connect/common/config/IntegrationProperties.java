package com.lantu.connect.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * {@code lantu.integration.*} 默认值来自 YAML；可被 {@code t_system_param.runtime_app_config} 覆盖。
 */
@Data
@Component
@ConfigurationProperties(prefix = "lantu.integration")
public class IntegrationProperties {

    private String networkApiUrl = "";
    private String aclApiUrl = "";
    private boolean mcpAllowHttp = false;
    private boolean mcpAllowLocalTargets = false;
    private boolean mcpAllowInsecureWs = false;
    private String mcpHttpAccept = "application/json, text/event-stream";
    private int mcpMaxRedirects = 3;
    private int appLaunchTokenTtlSeconds = 300;
    private int mcpSessionTtlMinutes = 45;
    private int mcpInvokeRetries = 1;
    private Oauth2 oauth2 = new Oauth2();

    @Data
    public static class Oauth2 {
        private int connectTimeoutSec = 15;
    }
}
