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
    /** 目录标签 id（t_resource_tag_rel → t_tag），与 catalogTagNames 顺序一致（按标签名排序）。 */
    private List<Long> tagIds;

    /** 历史：`t_resource.access_policy` 回显；invoke 不以该值做 per-resource Grant 裁决。 */
    private String accessPolicy;

    private Long createdBy;
    private String createdByName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 当前默认版本号（t_resource_version.is_current = 1）；无版本记录时为 null。 */
    private String currentVersion;

    /** app 等：关联资源 ID。agent 不再绑 Skill。 */
    private List<Long> relatedResourceIds;

    /** agent / skill：绑定的 MCP id */
    private List<Long> relatedMcpResourceIds;

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
    private String registrationProtocol;
    private String upstreamEndpoint;
    private String upstreamAgentId;
    private String credentialRef;
    private String transformProfile;
    private String modelAlias;
    private Boolean enabled;

    // --- skill (t_resource_skill_ext)：Context Skill ---
    private String skillType;
    private Map<String, Object> manifest;
    private String entryDoc;
    /** skill：已废弃，Context Skill 不使用父级资源。 */
    private Long parentResourceId;
    /** skill：已废弃，Context Skill 不使用展示模板。 */
    private String displayTemplate;
    private Map<String, Object> parametersSchema;

    /** skill：context */
    private String executionMode;
    /** 规范/提示词正文 */
    private String contextPrompt;

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
    private String callabilityState;
    private String callabilityReason;
    private Boolean callable;
    private LocalDateTime lastProbeAt;
    private LocalDateTime lastSuccessAt;
    private LocalDateTime lastFailureAt;
    private String lastFailureReason;
    private Long consecutiveSuccess;
    private Long consecutiveFailure;
    private Long probeLatencyMs;
    private String probePayloadSummary;
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
