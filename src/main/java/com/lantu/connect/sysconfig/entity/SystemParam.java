package com.lantu.connect.sysconfig.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统参数实体
 *
 * @author 王帝
 * @date 2026-03-23
 */
@Data
public class SystemParam {

    private String key;

    private String value;
    private String type;
    private String description;
    private String category;
    private Boolean editable;
    private LocalDateTime updateTime;
}
