package com.lantu.connect.compat.robotfactory.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RobotFactorySettingsUpsertRequest {

    private String dbUrl;
    private String dbUsername;
    private String dbPassword;
    private String dbDriverClassName;

    private String publicBaseUrl;
    private List<String> allowedIps = new ArrayList<>();

    private Integer sessionIdleMinutes;
    private Integer sessionMaxLifetimeMinutes;
    private Integer invokeTimeoutSeconds;
}
