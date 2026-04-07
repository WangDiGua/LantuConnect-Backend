package com.lantu.connect.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 在线技能市场表格行：名称、简介、zip 链接、许可说明（及可选来源页）。
 * 聚合字段在列表/详情中由 {@link com.lantu.connect.gateway.service.SkillExternalEngagementService} 填充。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SkillExternalCatalogItemVO {

    private String id;
    private String name;
    private String summary;
    private String packUrl;
    private String licenseNote;
    private String sourceUrl;
    /** SkillsMP 等指标：星标，便于前端展示「热度」 */
    private Integer stars;

    /** 与 {@code t_skill_external_catalog_item.dedupe_key} 一致；便于前端详情路由 */
    private String itemKey;

    private Integer favoriteCount;
    private Long downloadCount;
    private Long viewCount;
    private Integer reviewCount;
    private Double ratingAvg;
    /** 当前用户是否已收藏（须请求带 X-User-Id） */
    private Boolean favoritedByMe;
}
