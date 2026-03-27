package com.lantu.connect.gateway.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "t_resource_grant_application", autoResultMap = true)
public class ResourceGrantApplication {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long applicantId;
    private String resourceType;
    private Long resourceId;
    private String apiKeyId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> actions;

    private String useCase;
    private String callFrequency;
    private String status;
    private Long reviewerId;
    private String rejectReason;
    private LocalDateTime reviewTime;
    private LocalDateTime expiresAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
