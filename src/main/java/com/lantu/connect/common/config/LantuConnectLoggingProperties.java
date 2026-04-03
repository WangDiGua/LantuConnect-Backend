package com.lantu.connect.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "lantu.logging")
public class LantuConnectLoggingProperties {

    private boolean gatewayInvokeLogSuccess = false;
    private boolean gatewayInvokeLogPayloadPreview = false;
    private int gatewayInvokeBodyPreviewMax = 1024;
    private int gatewayInvokePayloadPreviewMax = 300;
    /** 与 {@link com.lantu.connect.common.filter.AccessLogFilter} 一致，YAML 未配置时由代码默认。 */
    private int accessLogErrorBodyMaxChars = 2048;
    private int accessLogErrorBodyMaxBytes = 262144;
    private String accessLogSkipErrorBodyPathFragments = "/auth/login,/auth/refresh,/auth/register";
}
