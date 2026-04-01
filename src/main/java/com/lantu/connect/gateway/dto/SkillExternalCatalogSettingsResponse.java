package com.lantu.connect.gateway.dto;

import com.lantu.connect.gateway.config.SkillExternalCatalogProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 超管读取技能市场配置：不下发 SkillsMP API Key 明文，用 skillsmpApiKeyConfigured 表示是否已配置。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillExternalCatalogSettingsResponse {

    private SkillExternalCatalogProperties config;
    private boolean skillsmpApiKeyConfigured;
}
