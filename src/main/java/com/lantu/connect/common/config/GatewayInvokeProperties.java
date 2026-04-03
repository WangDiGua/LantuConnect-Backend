package com.lantu.connect.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "lantu.gateway")
public class GatewayInvokeProperties {

    private boolean invokeHttpStatusReflectsUpstream = true;
}
