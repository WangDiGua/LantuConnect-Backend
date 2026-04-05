package com.lantu.connect.gateway.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ResourceManageVO {

    private Long id;
    private String resourceType;
    private String resourceCode;
    private String displayName;
    private String description;
    private String status;
    private String sourceType;
    private Long providerId;
    private Long categoryId;

    /** {@code grant_required} | {@code open_org} | {@code open_platform} */
    private String accessPolicy;

    private Long createdBy;
    private String createdByName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 当前默认版本号（t_resource_version.is_current = 1）；无版本记录时为 null。 */
    private String currentVersion;

    /** agent / skill 等：关联资源 ID（与入参 relatedResourceIds 一致）。 */
    private List<Long> relatedResourceIds;

    // --- agent (t_resource_agent_ext) ---
    private String agentType;
    private String mode;
    private Map<String, Object> spec;
    private Boolean isPublic;
    private Boolean hidden;
    private Integer maxConcurrency;
    private Integer maxSteps;
    private Double temperature;
    private String systemPrompt;

    // --- skill (t_resource_skill_ext)：Anthropic 式内容包 ---
    private String skillType;
    private String artifactUri;
    private String artifactSha256;
    private Map<String, Object> manifest;
    private String entryDoc;
    /** 技能 zip 校验状态：none / pending / valid / invalid */
    private String packValidationStatus;
    private LocalDateTime packValidatedAt;
    private String packValidationMessage;
    /** zip 内技能根（语义校验子树）；空表示整包 */
    private String skillRootPath;
    private Long parentResourceId;
    private String displayTemplate;
    private Map<String, Object> parametersSchema;

    // --- mcp (t_resource_mcp_ext) ---
    private String endpoint;
    private String protocol;
    private String authType;
    private Map<String, Object> authConfig;
    private String serviceDetailMd;

    // --- app (t_resource_app_ext) ---
    private String appUrl;
    private String embedType;
    private String icon;
    private List<String> screenshots;

    // --- dataset (t_resource_dataset_ext) ---
    private String dataType;
    private String format;
    private Long recordCount;
    private Long fileSize;
    /** 数据集扩展表中的自由文本标签（JSON 数组） */
    private List<String> tags;

    /** 目录侧标签名（t_resource_tag_rel）；全类型通用，与 categoryId 对应关系同步 */
    private List<String> catalogTagNames;

    // --- lifecycle / audit context ---
    private Long pendingAuditItemId;
    private String lastAuditStatus;
    private String lastRejectReason;
    private Long lastReviewerId;
    private LocalDateTime lastSubmitTime;
    private LocalDateTime lastReviewTime;
    /**
     * 前端可直接渲染的动作建议：update / submit / withdraw / deprecate / delete / createVersion / switchVersion
     */
    private List<String> allowedActions;
    private String statusHint;

    // --- observability / quality summary (optional on list/detail) ---
    private String healthStatus;
    private String circuitState;
    private String degradationCode;
    private String degradationHint;
    private Integer qualityScore;
    private Map<String, Object> qualityFactors;
}
