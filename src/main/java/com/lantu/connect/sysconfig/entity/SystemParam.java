package com.lantu.connect.sysconfig.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统参数实体
 *
 * @author 王帝
 * @date 2026-03-23
 */
@Data
@TableName("t_system_param")
public class SystemParam {

    @TableId(type = IdType.INPUT)
    private String key;

    private String value;
    private String type;
    private String description;
    private String category;
    private Boolean editable;
    private LocalDateTime updateTime;
}
