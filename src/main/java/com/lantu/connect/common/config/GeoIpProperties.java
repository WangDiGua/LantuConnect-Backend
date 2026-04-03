package com.lantu.connect.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "geoip")
public class GeoIpProperties {

    private boolean enabled = true;
    private int timeoutMs = 2000;
}
