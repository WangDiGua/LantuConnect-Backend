package com.lantu.connect.compat.robotfactory.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class RobotFactoryResourceContext {

    private Long resourceId;
    private String resourceType;
    private String resourceCode;
    private String displayName;
    private String description;
    private String status;
    private Long createdBy;
    private Long schoolId;
    private String endpoint;
    private String protocol;
    private Map<String, Object> spec;
    private String serviceDetailMd;
}
