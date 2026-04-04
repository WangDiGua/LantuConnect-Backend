package com.lantu.connect.sysconfig.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

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

    /**
     * 按统一资源大类过滤：匹配 action 或 resource 字段中包含该关键字（如 agent、skill、mcp）。
     */
    private String resourceType;

    /** 对 operator/username、action、resource、详情、ip 等做 OR 模糊匹配 */
    private String keyword;

    /** true 时仅返回 result=failure */
    private Boolean onlyFailure;

    /** success 或 failure；若与 onlyFailure 同时传入，以本字段为准 */
    private String result;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime timeFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime timeTo;
}
