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

    /** 目录标签 id（t_resource_tag_rel → t_tag），与 catalogTagNames 顺序一致（按标签名排序）。 */
    private List<Long> tagIds;

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

    /** agent：绑定的 MCP id */
    private List<Long> relatedMcpResourceIds;

    /** mcp：前置 skill id */
    private List<Long> relatedPreSkillResourceIds;

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

    // --- skill (t_resource_skill_ext)：Hosted Skill ---
    private String skillType;
    private Map<String, Object> manifest;
    private String entryDoc;
    /** skill：已废弃，API 恒为 null（技能包不挂载 MCP）。 */
    private Long parentResourceId;
    /** skill：已废弃，API 恒为 null。 */
    private String displayTemplate;
    private Map<String, Object> parametersSchema;

    /** skill：hosted */
    private String executionMode;
    private String hostedSystemPrompt;
    private String hostedUserTemplate;
    private String hostedDefaultModel;
    private Map<String, Object> hostedOutputSchema;
    private Double hostedTemperature;

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

    /** 目录侧标签名（t_resource_tag_rel）；全类型通用 */
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

    // --- published working draft (t_resource_draft) ---
    /** 已发布资源是否存在本地未提审草稿 */
    private Boolean hasWorkingDraft;
    private LocalDateTime workingDraftUpdatedAt;
    /** low / medium / high */
    private String workingDraftAuditTier;
    /** 是否存在待审核的「已发布资源变更」队列项 */
    private Boolean pendingPublishedUpdate;
}
