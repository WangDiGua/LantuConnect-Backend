package com.lantu.connect.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@Schema(description = "目录单项；列表接口分页返回")
public class ResourceCatalogItemVO {

    private String resourceType;

    private String resourceId;

    private String resourceCode;

    private String displayName;

    private String description;

    private String status;

    private String sourceType;

    /** 历史字段 {@code t_resource.access_policy} 的回显；网关不再据其做 per-resource Grant 裁决。 */
    @Schema(
            description = "历史兼容：`t_resource.access_policy`（新建/迁移后多为 open_platform）。invoke/resolve 以有效 X-Api-Key、scope、published 等为准，见 ResourceInvokeGrantService。",
            allowableValues = {"grant_required", "open_org", "open_platform"},
            example = "open_platform")
    private String accessPolicy;

    private LocalDateTime updateTime;

    /** {@code t_resource.created_by} */
    private Long createdBy;

    /** 创建者展示名（由 {@link com.lantu.connect.common.util.UserDisplayNameResolver} 批量解析） */
    private String createdByName;

    /** {@code t_review} 平均分，无评论时为 null */
    private Double ratingAvg;

    /** {@code t_review} 条数（{@code deleted=0}），无评论时为 null */
    private Long reviewCount;

    /** {@code t_call_log} 按 agent_id=资源 id 聚合计数；适用于 agent / mcp / app 等可走网关 invoke 的资源 */
    @Schema(description = "网关统一调用次数（t_call_log.agent_id）")
    private Long callCount;

    /** {@code t_usage_record}：type=app 且 action=invoke 的次数 */
    @Schema(description = "应用使用量（usage_record，app invoke）")
    private Long usageCount;

    /**
     * 预留字段；技能 zip 下载已下线，当前恒为 0。
     */
    @Schema(description = "下载量（预留；当前为 0）")
    private Long downloadCount;

    /** {@code t_resource.view_count}，详情 GET 成功时递增 */
    @Schema(description = "资源详情页累计浏览次数")
    private Long viewCount;

    /** 目录标签名（t_resource_tag_rel + t_tag），与市场筛选 tags 一致 */
    @Schema(description = "资源标签名列表（t_resource_tag_rel）；请求 `include` 含 tags 时与扩展块语义一致")
    private List<String> tags;

    /**
     * include=observability 时返回。
     */
    @Schema(description = "仅当请求包含 include=observability 时存在")
    private Map<String, Object> observability;

    /**
     * include=quality 时返回。
     */
    @Schema(description = "仅当请求包含 include=quality 时存在")
    private Map<String, Object> quality;

    /**
     * 携带有效 X-Api-Key 时为 true（仅表示「请求带了 Key」，**不是** per-resource Grant 判定结果；Grant 表已下线）。
     */
    @Schema(description = "携带有效 X-Api-Key 且能解析资源 id 时为 true；历史字段名保留。不表示是否存在资源级 Grant。")
    private Boolean hasGrantForKey;

    /**
     * 仅 {@code resourceType=skill} 时由目录列表批量填充：{@code context}。
     */
    @Schema(description = "技能：execution_mode（context）；非技能类型为 null")
    private String executionMode;
}
