package com.lantu.connect.integrationpackage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_integration_package")
public class IntegrationPackage {

    @TableId(type = IdType.INPUT)
    private String id;

    private String name;

    private String description;

    /** active | disabled */
    private String status;

    private String createdBy;

    /** 用户自建套餐时必填；NULL 为迁移前历史数据 */
    private Long ownerUserId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
