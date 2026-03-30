package com.lantu.connect.monitoring.dto;

import lombok.Data;

/**
 * 监控 PageQuery
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
public class PageQuery {

    private int page = 1;
    private int pageSize = 10;
    private String keyword;

    /** 调用日志状态：success、error、timeout；前端占位 all 时不筛选 */
    private String status;

    /** 告警级别：critical、warning、info */
    private String severity;

    /** 告警记录状态：firing、resolved、silenced；前端占位 all 时不筛选 */
    private String alertStatus;
}
