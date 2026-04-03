package com.lantu.connect.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.sysconfig.entity.SystemParam;
import com.lantu.connect.sysconfig.mapper.SystemParamMapper;
import com.lantu.connect.gateway.config.SkillExternalCatalogProperties;
import com.lantu.connect.gateway.dto.SkillExternalCatalogSettingsResponse;
import com.lantu.connect.gateway.skillhub.SkillHubTencentApiBaseSanitizer;
import org.springframework.context.annotation.Lazy;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 技能在线市场运行时配置：优先读库 {@code t_system_param.skill_external_catalog}（JSON），否则与 YAML/环境变量一致。
 */
@Service
@Slf4j
public class SkillExternalCatalogRuntimeConfigService {

    public static final String PARAM_KEY = "skill_external_catalog";

    private final SkillExternalCatalogProperties yamlDefaults;
    private final SystemParamMapper systemParamMapper;
    private final ObjectMapper objectMapper;
    private final SkillExternalCatalogCacheCoordinator cacheCoordinator;
    private final SkillExternalCatalogPersistenceService skillExternalCatalogPersistenceService;

    public SkillExternalCatalogRuntimeConfigService(
            SkillExternalCatalogProperties yamlDefaults,
            SystemParamMapper systemParamMapper,
            ObjectMapper objectMapper,
            SkillExternalCatalogCacheCoordinator cacheCoordinator,
            @Lazy SkillExternalCatalogPersistenceService skillExternalCatalogPersistenceService) {
        this.yamlDefaults = yamlDefaults;
        this.systemParamMapper = systemParamMapper;
        this.objectMapper = objectMapper;
        this.cacheCoordinator = cacheCoordinator;
        this.skillExternalCatalogPersistenceService = skillExternalCatalogPersistenceService;
    }

    /**
     * 当前生效配置（含超管覆盖）。
     */
    public SkillExternalCatalogProperties effective() {
        SystemParam row = systemParamMapper.selectById(PARAM_KEY);
        if (row != null && StringUtils.hasText(row.getValue())) {
            try {
                SkillExternalCatalogProperties p =
                        objectMapper.readValue(row.getValue().trim(), SkillExternalCatalogProperties.class);
                fillMissingNestedFromYamlDefaults(p);
                patchLegacySkillHubWebOnlyBaseUrl(p);
                return p;
            } catch (Exception e) {
                log.warn("skill_external_catalog JSON 无效，回退 YAML 默认: {}", e.getMessage());
            }
        }
        return cloneDefaults();
    }

    /** 兼容历史库内 JSON 缺字段（如早期无 skillhub）。 */
    private void fillMissingNestedFromYamlDefaults(SkillExternalCatalogProperties p) {
        if (!StringUtils.hasText(p.getRemoteCatalogMode())) {
            p.setRemoteCatalogMode("MERGED");
        }
        if (p.getSkillhub() == null) {
            p.setSkillhub(copySkillHubFromDefaults());
        }
        if (p.getSkillsmp() == null) {
            p.setSkillsmp(copySkillsMpFromDefaults());
        }
    }

    /**
     * 历史配置常把「官网根」当作 API base；运行时自动改为可返回 JSON 的根，避免读库后仍打 HTML。
     */
    private void patchLegacySkillHubWebOnlyBaseUrl(SkillExternalCatalogProperties p) {
        if (p.getSkillhub() == null) {
            return;
        }
        String raw = p.getSkillhub().getBaseUrl();
        if (SkillHubTencentApiBaseSanitizer.shouldReplaceWithAgentskillhubDev(raw)) {
            log.info(
                    "SkillHub baseUrl「{}」指向腾讯 SkillHub 官网域（含 /api/v1 亦多为 HTML）；已自动改用公开 JSON API「{}」。"
                            + "建议在「市场配置」中保存一次以更新数据库。",
                    raw != null ? raw.trim() : "",
                    SkillHubTencentApiBaseSanitizer.RECOMMENDED_JSON_API_ROOT);
            p.getSkillhub().setBaseUrl(SkillHubTencentApiBaseSanitizer.RECOMMENDED_JSON_API_ROOT);
        }
        String fb = p.getSkillhub().getFallbackBaseUrl();
        if (SkillHubTencentApiBaseSanitizer.shouldReplaceWithAgentskillhubDev(fb)) {
            log.info(
                    "SkillHub fallbackBaseUrl「{}」指向腾讯 SkillHub 官网域，已清空以免误用 HTML。",
                    fb.trim());
            p.getSkillhub().setFallbackBaseUrl("");
        }
    }

    private SkillExternalCatalogProperties.SkillHub copySkillHubFromDefaults() {
        try {
            SkillExternalCatalogProperties d = objectMapper.readValue(
                    objectMapper.writeValueAsString(yamlDefaults), SkillExternalCatalogProperties.class);
            return d.getSkillhub() != null ? d.getSkillhub() : new SkillExternalCatalogProperties.SkillHub();
        } catch (Exception e) {
            return new SkillExternalCatalogProperties.SkillHub();
        }
    }

    private SkillExternalCatalogProperties.SkillsMp copySkillsMpFromDefaults() {
        try {
            SkillExternalCatalogProperties d = objectMapper.readValue(
                    objectMapper.writeValueAsString(yamlDefaults), SkillExternalCatalogProperties.class);
            return d.getSkillsmp() != null ? d.getSkillsmp() : new SkillExternalCatalogProperties.SkillsMp();
        } catch (Exception e) {
            return new SkillExternalCatalogProperties.SkillsMp();
        }
    }

    public SkillExternalCatalogSettingsResponse getForAdmin() {
        SkillExternalCatalogProperties p = effective();
        boolean keyConfigured = p.getSkillsmp() != null && StringUtils.hasText(p.getSkillsmp().getApiKey());
        if (p.getSkillsmp() != null) {
            p.getSkillsmp().setApiKey("");
        }
        return SkillExternalCatalogSettingsResponse.builder()
                .config(p)
                .skillsmpApiKeyConfigured(keyConfigured)
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveFromAdmin(Long operatorUserId, @Valid SkillExternalCatalogProperties incoming) {
        if (incoming.getSkillhub() == null) {
            incoming.setSkillhub(new SkillExternalCatalogProperties.SkillHub());
        }
        if (incoming.getSkillsmp() == null) {
            incoming.setSkillsmp(new SkillExternalCatalogProperties.SkillsMp());
        }
        patchLegacySkillHubWebOnlyBaseUrl(incoming);
        SkillExternalCatalogProperties current = effective();
        if (!StringUtils.hasText(incoming.getSkillsmp().getApiKey())) {
            String keep = current.getSkillsmp() != null ? current.getSkillsmp().getApiKey() : null;
            incoming.getSkillsmp().setApiKey(keep);
        }
        LocalDateTime now = LocalDateTime.now();
        String json;
        try {
            json = objectMapper.writeValueAsString(incoming);
        } catch (Exception e) {
            throw new IllegalStateException("配置序列化失败: " + e.getMessage(), e);
        }
        SystemParam existing = systemParamMapper.selectById(PARAM_KEY);
        if (existing == null) {
            SystemParam n = new SystemParam();
            n.setKey(PARAM_KEY);
            n.setValue(json);
            n.setType("json");
            n.setCategory("skill-catalog");
            n.setDescription("技能在线市场（超管可编辑，覆盖 application.yml 中 lantu.skill-external-catalog 默认值）");
            n.setEditable(true);
            n.setUpdateTime(now);
            systemParamMapper.insert(n);
        } else {
            existing.setValue(json);
            existing.setUpdateTime(now);
            systemParamMapper.updateById(existing);
        }
        cacheCoordinator.invalidate();
        skillExternalCatalogPersistenceService.scheduleSyncAfterConfigSave(incoming);
    }

    private SkillExternalCatalogProperties cloneDefaults() {
        try {
            SkillExternalCatalogProperties p = objectMapper.readValue(
                    objectMapper.writeValueAsString(yamlDefaults),
                    SkillExternalCatalogProperties.class);
            patchLegacySkillHubWebOnlyBaseUrl(p);
            return p;
        } catch (Exception e) {
            throw new IllegalStateException("无法克隆默认技能市场配置", e);
        }
    }
}
