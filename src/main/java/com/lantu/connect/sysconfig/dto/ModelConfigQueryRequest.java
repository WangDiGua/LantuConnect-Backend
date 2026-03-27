package com.lantu.connect.sysconfig.dto;

import lombok.Data;

/**
 * 系统配置 ModelConfigQueryRequest 数据传输对象
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
public class ModelConfigQueryRequest {

    private int page = 1;
    private int pageSize = 10;
    private String name;
    private String provider;
}
