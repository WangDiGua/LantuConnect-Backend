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
     * 历史字段（主表 {@code t_resource.access_policy}）。请求体若传入合法枚举仍可落库，但注册实现已将新建/更新统一为
     * {@link com.lantu.connect.gateway.model.ResourceAccessPolicy#OPEN_PLATFORM}；网关 invoke 不以本字段做 Grant 拦截，
     * 以 API Key scope、资源 published 与 {@link com.lantu.connect.gateway.security.ResourceInvokeGrantService} 行为为准。
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
    private String registrationProtocol;
    private String upstreamEndpoint;
    private String upstreamAgentId;
    private String credentialRef;
    private String transformProfile;
    private String modelAlias;
    private Boolean enabled;

    /**
     * 仅 skill 使用：形态标识（context_v1）。
     */
    private String skillType;
    /** 包 manifest（JSON），可选。 */
    private Map<String, Object> manifest;
    /** 入口文档相对路径（可选）。 */
    private String entryDoc;
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
     * 仅 app：关联资源 ID。agent 不再使用本字段绑 Skill。
     */
    private List<Long> relatedResourceIds;

    /**
     * agent / skill：绑定的 MCP 资源 ID（skill 为可选）。null 表示本次不修改；[] 清空。
     */
    private List<Long> relatedMcpResourceIds;

    /**
     * 仅 skill：固定为 {@code context}；请求可省略由服务端默认。
     */
    private String executionMode;

    /** skill：规范/提示词正文（落库 {@code t_resource_skill_ext.hosted_system_prompt}） */
    private String contextPrompt;
}

