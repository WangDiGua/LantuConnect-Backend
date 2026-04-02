package com.lantu.connect.gateway.skillhub.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * SkillHub 公开搜索 API 单条（与 agentskillhub.dev / skillhub.tencent.com 协议兼容）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class SkillHubSearchSkillJson {

    private String slug;
    private String name;
    private String description;
    private Integer totalInstalls;
    private String sourceIdentifier;
}
