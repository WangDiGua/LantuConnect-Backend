package com.lantu.connect.compat.robotfactory.dto;

import lombok.Data;

@Data
public class RobotFactoryProjectionUpdateRequest {

    private String scopeMode;
    private String displayName;
    private String description;
    private String displayTemplate;
    private String specJson;
    private String parametersSchema;
    private Boolean autoSyncEnabled;
}
