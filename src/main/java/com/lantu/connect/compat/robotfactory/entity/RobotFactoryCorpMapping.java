package com.lantu.connect.compat.robotfactory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_robotfactory_corp_mapping")
public class RobotFactoryCorpMapping {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long schoolId;
    private String schoolNameSnapshot;
    private Long corpId;
    private Boolean enabled;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
