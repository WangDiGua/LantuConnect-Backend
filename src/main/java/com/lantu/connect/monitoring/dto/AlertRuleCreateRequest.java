package com.lantu.connect.monitoring.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 监控 AlertRuleCreateRequest 数据传输对象
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
public class AlertRuleCreateRequest {

    @NotBlank
    private String name;
    private String metric;
    private String conditionExpr;
    private Double threshold;
    /**
     * 比较算子，与 Dry-Run 一致：gt / gte / lt / lte / eq（也接受 &gt; &gt;= 等符号，服务端会规范化）
     */
    private String operator;
    /**
     * critical | warning | info（历史值 medium 会映射为 warning）
     */
    private String severity;
    /** 持续时间窗口，如 5m、1h */
    private String duration;
    /**
     * 已废弃：服务端忽略。告警仅通过站内消息与实时推送送达。
     */
    @Deprecated
    private List<String> notifyChannels;
    private Integer enabled;
}
