package com.lantu.connect.monitoring.dto;

import lombok.Data;

@Data
public class PageQuery {

    private int page = 1;
    private int pageSize = 10;
    private String keyword;
    private String status;
    private String severity;
    private String alertStatus;
    private String resourceType;
    private Long resourceId;
    private String scopeType;
    private String assignee;
    private String ruleId;
    private Boolean onlyMine;
    private Boolean enabled;
}
