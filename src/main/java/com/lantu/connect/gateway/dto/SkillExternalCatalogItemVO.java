package com.lantu.connect.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 在线技能市场表格行：名称、简介、zip 链接、许可说明（及可选来源页）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SkillExternalCatalogItemVO {

    private String id;
    private String name;
    private String summary;
    private String packUrl;
    private String licenseNote;
    private String sourceUrl;
    /** SkillsMP 等指标：星标，便于前端展示「热度」 */
    private Integer stars;
}
