package com.lantu.connect.useractivity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 最近使用视图
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecentUseVO {

    private Long recordId;
    private String type;
    private String targetCode;
    private String targetName;
    private String action;
    private String status;
    private Integer tokenCost;
    private Integer latencyMs;
    private LocalDateTime createTime;
}
