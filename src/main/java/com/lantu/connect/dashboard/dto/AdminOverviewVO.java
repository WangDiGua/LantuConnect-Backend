package com.lantu.connect.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 仪表盘 AdminOverviewVO 数据传输对象
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminOverviewVO {

    private Map<String, Object> summary;

    private List<Map<String, Object>> charts;

    private Map<String, Object> extras;
}
