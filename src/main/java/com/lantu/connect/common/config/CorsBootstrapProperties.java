package com.lantu.connect.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "cors")
public class CorsBootstrapProperties {

    /** 逗号分隔；空则仅依赖 relax-localhost 等。 */
    private String allowedOrigins = "";
    private boolean relaxLocalhost = true;
    private boolean allowAllOrigins = false;
}
