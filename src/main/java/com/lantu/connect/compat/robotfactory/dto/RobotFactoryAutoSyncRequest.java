package com.lantu.connect.compat.robotfactory.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RobotFactoryAutoSyncRequest {

    @NotNull
    private Boolean enabled;
}
