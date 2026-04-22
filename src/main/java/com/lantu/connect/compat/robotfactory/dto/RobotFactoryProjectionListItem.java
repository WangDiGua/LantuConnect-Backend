package com.lantu.connect.compat.robotfactory.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RobotFactoryProjectionListItem {

    private Long id;
    private Long resourceId;
    private String resourceType;
    private String resourceCode;
    private String resourceStatus;
    private Long schoolId;
    private Long corpId;
    private String scopeMode;
    private String projectionCode;
    private String agentName;
    private String displayName;
    private String description;
    private String displayTemplate;
    private String agentType;
    private String mode;
    private String runtimeRole;
    private String interactionMode;
    private String dispatchMode;
    private Boolean autoSyncEnabled;
    private Long externalAgentId;
    private String syncStatus;
    private String syncMessage;
    private LocalDateTime lastSyncedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
