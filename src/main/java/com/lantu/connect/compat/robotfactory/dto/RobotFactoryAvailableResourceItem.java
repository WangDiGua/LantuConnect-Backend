package com.lantu.connect.compat.robotfactory.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RobotFactoryAvailableResourceItem {

    private Long resourceId;
    private String resourceCode;
    private String displayName;
    private String description;
    private Long schoolId;
}
