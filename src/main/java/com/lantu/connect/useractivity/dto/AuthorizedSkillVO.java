package com.lantu.connect.useractivity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 已授权技能视图
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorizedSkillVO {

    private Long id;
    private String agentName;
    private String displayName;
    private String description;
    /**
     * 历史字段：与 {@link #packFormat} 同源，值为技能包格式（anthropic_v1 等）。
     */
    private String agentType;
    /** 技能包格式（对应 t_resource_skill_ext.skill_type）。 */
    private String packFormat;
    private String status;
    private String source;
    private LocalDateTime updateTime;
    private LocalDateTime lastUsedTime;
}
