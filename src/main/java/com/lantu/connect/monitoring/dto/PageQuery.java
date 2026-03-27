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
}
