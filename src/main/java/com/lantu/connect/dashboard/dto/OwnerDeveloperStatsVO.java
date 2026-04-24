package com.lantu.connect.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Owner 维度开发者统计：网关调用日志、invoke 使用记录（见 {@code PRODUCT_DEFINITION.md} §5）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerDeveloperStatsVO {

    private Long ownerUserId;
    private int periodDays;
    private String periodStart;
    private String periodEnd;

    /** {@code t_call_log}：归属该 owner 资源的网关 invoke / invoke-stream */
    private long gatewayInvokeTotal;
    private long gatewayInvokeSuccess;

    /**
     * {@code t_call_log}：{@code action=invoke} 且可归因到该 owner 资源的记录数。
     */
    private long usageRecordInvokeTotal;

    private List<OwnerResourceTypeInvokeCount> gatewayInvokesByResourceType;
}
