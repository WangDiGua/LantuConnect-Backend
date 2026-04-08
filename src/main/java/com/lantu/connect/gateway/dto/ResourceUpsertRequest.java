package com.lantu.connect.gateway.dto;

import com.lantu.connect.common.validation.ResourceCode;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 统一资源注册/更新请求。
 */
@Data
public class ResourceUpsertRequest {

    @NotBlank(message = "resourceType 不能为空")
    private String resourceType;

    @ResourceCode
    private String resourceCode;

    @NotBlank(message = "displayName 不能为空")
    private String displayName;

    private String description;

    private String sourceType;

    private Long providerId;

    /** 目录标签（t_tag.id），写入 t_resource_tag_rel；须与资源类型桶位匹配或为 general。 */
    private List<Long> tagIds;

    /**
     * 消费策略（主表 {@code t_resource.access_policy}）：{@code grant_required}（默认）、{@code open_org}、{@code open_platform}。
     * 网关是否按策略短路 Grant 见后续实现阶段。
     */
    private String accessPolicy;

    /**
     * 仅 agent 使用。
     */
    private String agentType;
    /** agent 运行模式；resourceType=skill 时服务端忽略。 */
    private String mode;
    private Map<String, Object> spec;
    private Boolean isPublic;
    private Boolean hidden;
    /** agent 等可调用资源使用；resourceType=skill 时服务端忽略。 */
    private Integer maxConcurrency;
    private Integer maxSteps;
    private Double temperature;
    private String systemPrompt;

    /**
     * 仅 skill 使用：技能包格式（如 anthropic_v1、folder_v1），非 MCP。
     */
    private String skillType;
    /** 技能包 URI；草稿可为空，上传 zip 或提审前须补齐。 */
    private String artifactUri;
    /** 技能包 SHA-256 hex，可选。 */
    private String artifactSha256;
    /** 包 manifest（JSON），可选。 */
    private Map<String, Object> manifest;
    /** 入口文档相对路径，如 SKILL.md。 */
    private String entryDoc;
    /** zip 内技能根目录（可选），与上传 skillRoot 一致。 */
    private String skillRootPath;
    /**
     * 已废弃：技能仅为技能包资源，不再挂载 MCP；服务端对 resourceType=skill 忽略此字段。
     */
    private Long parentResourceId;
    /** 已废弃：技能包资源不使用；服务端对 skill 忽略。 */
    private String displayTemplate;
    private Map<String, Object> parametersSchema;

    /**
     * 仅 mcp 使用。
     */
    private String endpoint;
    private String protocol;
    private String authType;
    private Map<String, Object> authConfig;
    /**
     * 各资源类型市场详情「介绍」Tab：Markdown，选填（agent/skill/mcp/app/dataset 扩展表 {@code service_detail_md}）。
     * 更新请求：非 null 按值写入（空串清空）；null 表示不修改（兼容未传字段的客户端）。
     */
    private String serviceDetailMd;

    /**
     * 仅 app 使用。
     */
    private String appUrl;
    private String embedType;
    private String icon;
    private List<String> screenshots;

    /**
     * 仅 dataset 使用。
     */
    private String dataType;
    private String format;
    private Long recordCount;
    private Long fileSize;
    private List<String> tags;

    /**
     * 关联资源 ID 列表（Agent 依赖的 Skills、App 依赖的 Agent/Skills 等）。
     */
    private List<Long> relatedResourceIds;
}

