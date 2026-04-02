package com.lantu.connect.gateway.skillhub.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class SkillHubSearchResponse {

    private List<SkillHubSearchSkillJson> skills;
}
