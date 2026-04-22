package com.lantu.connect.compat.robotfactory.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RobotFactorySettingsHealthDTO {

    private boolean configured;
    private boolean databaseReachable;
    private boolean externalTableReady;
    private String status;
    private String message;
    private LocalDateTime checkedAt;
}
