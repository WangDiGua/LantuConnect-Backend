package com.lantu.connect.monitoring.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 监控 AlertRuleUpdateRequest 数据传输对象
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
public class AlertRuleUpdateRequest {

    @NotBlank
    private String id;
    private String name;
    private String metric;
    private String conditionExpr;
    private Double threshold;
    private String operator;
    private String severity;
    private String duration;
    private List<String> notifyChannels;
    private Integer enabled;
}
