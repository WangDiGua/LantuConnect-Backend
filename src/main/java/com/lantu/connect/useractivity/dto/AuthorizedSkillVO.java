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
    private String agentType;
    /** 技能包格式（对应 t_resource_skill_ext.skill_type）。 */
    private String status;
    private String source;
    private LocalDateTime updateTime;
    private LocalDateTime lastUsedTime;
}
