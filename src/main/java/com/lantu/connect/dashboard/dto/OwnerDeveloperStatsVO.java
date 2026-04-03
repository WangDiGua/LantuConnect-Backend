package com.lantu.connect.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Owner 维度开发者统计：网关调用日志、invoke 使用记录、技能包下载（见 {@code PRODUCT_DEFINITION.md} §5）。
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
     * {@code t_usage_record}：{@code action=invoke} 且可归因到该 owner 资源的记录数（与 call_log 可能同源重复计数，仅供对照）
     */
    private long usageRecordInvokeTotal;

    /** {@code t_skill_pack_download_event}：他人下载该 owner 技能包次数 */
    private long skillPackDownloadTotal;

    private List<OwnerResourceTypeInvokeCount> gatewayInvokesByResourceType;
}
