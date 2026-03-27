package com.lantu.connect.sysconfig.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 系统配置 AuditLogQueryRequest 数据传输对象
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
public class AuditLogQueryRequest {

    @Min(value = 1, message = "page 必须 >= 1")
    private int page = 1;

    @Min(value = 1, message = "pageSize 必须 >= 1")
    @Max(value = 200, message = "pageSize 不能超过 200")
    private int pageSize = 20;
    private String userId;
    private String action;
}
