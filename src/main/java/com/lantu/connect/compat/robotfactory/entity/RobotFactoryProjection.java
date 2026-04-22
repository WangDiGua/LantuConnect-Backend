package com.lantu.connect.compat.robotfactory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_robotfactory_projection")
public class RobotFactoryProjection {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long resourceId;
    private String resourceType;
    private String projectionCode;
    private Long schoolId;
    private Long corpId;
    private String scopeMode;
    private String agentName;
    private String displayName;
    private String description;
    private String displayNameOverride;
    private String descriptionOverride;
    private String displayTemplate;
    private String displayTemplateOverride;
    private String agentType;
    private String mode;
    private String runtimeRole;
    private String interactionMode;
    private String dispatchMode;
    private String specJson;
    private String specJsonOverride;
    private String parametersSchema;
    private String parametersSchemaOverride;
    private Boolean autoSyncEnabled;
    private Long externalAgentId;
    private String syncStatus;
    private String syncMessage;
    private LocalDateTime lastSyncedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
