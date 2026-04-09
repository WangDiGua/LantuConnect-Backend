package com.lantu.connect.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "file")
public class FileBootstrapProperties {

    private String uploadDir = "/data/nexusai/uploads";
    private int maxSizeMb = 50;
    private String allowedCategories = "document,avatar,image,attachment,temp,dataset";
}
