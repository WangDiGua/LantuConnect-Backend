package com.lantu.connect.compat.robotfactory.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RobotFactoryProjectionCreateRequest {

    @NotNull
    private Long resourceId;

    private String scopeMode;
    private String displayName;
    private String description;
    private String displayTemplate;
    private String specJson;
    private String parametersSchema;
    private Boolean autoSyncEnabled;
}
