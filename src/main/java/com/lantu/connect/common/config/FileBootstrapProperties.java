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
    private int skillPackMaxMb = 100;
    private String storageType = "local";
    private Minio minio = new Minio();
    private String allowedCategories = "document,avatar,image,attachment,temp,dataset";

    @Data
    public static class Minio {
        private String endpoint = "";
        private String accessKey = "";
        private String secretKey = "";
        private String bucket = "nexusai-connect";
    }
}
