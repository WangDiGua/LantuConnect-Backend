package com.lantu.connect.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
@Schema(description = "资源解析结果（详情 GET、POST resolve 等）")
public class ResourceResolveVO {

    private String resourceType;

    private String resourceId;

    private String version;

    private String resourceCode;

    private String displayName;

    private String status;

    /** {@code t_resource.created_by}，便于前端展示资源归属 */
    private Long createdBy;

    private String createdByName;

    /** {@code t_review} 平均分（含回复行时其 rating 为 0，与目录列表聚合一致）；无评论时为 null */
    private Double ratingAvg;

    /** {@code t_review} 条数（{@code deleted=0}，含回复），与目录列表一致；无评论时为 null */
    private Long reviewCount;

    private String invokeType;

    private String endpoint;

    private Map<String, Object> spec;

    /** MCP：注册时选填的服务详情 Markdown（目录 GET 展示用） */
    private String serviceDetailMd;

    /**
     * 应用类资源的短时启动令牌（仅 app 类型返回）。
     */
    private String launchToken;

    /**
     * 应用类资源的后端签发启动地址（仅 app 类型返回）。
     */
    private String launchUrl;

    /**
     * include=tags 时返回。
     */
    @Schema(description = "请求 include 含 tags 时填充")
    private List<String> tags;

    /**
     * include=observability 时返回。
     */
    @Schema(description = "请求 include 含 observability 时填充")
    private Map<String, Object> observability;

    /**
     * include=quality 时返回。
     */
    @Schema(description = "请求 include 含 quality 时填充")
    private Map<String, Object> quality;
}
