package com.lantu.connect.gateway.skillsmp.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class SkillsMpSkillJson {

    private String id;
    private String name;
    private String description;
    private Integer stars;
    private String author;
    @JsonAlias({"skill_url"})
    private String skillUrl;
    @JsonAlias({"github_url"})
    private String githubUrl;
    private Long updatedAt;
}
