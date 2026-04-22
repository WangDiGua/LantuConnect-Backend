package com.lantu.connect.compat.robotfactory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_robotfactory_sync_log")
public class RobotFactorySyncLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectionId;
    private Long resourceId;
    private String action;
    private Boolean success;
    private String message;
    private String requestSnapshotJson;
    private String responseSnapshotJson;
    private LocalDateTime createTime;
}
