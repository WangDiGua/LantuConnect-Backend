package com.lantu.connect.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.sysconfig.entity.SystemParam;
import com.lantu.connect.sysconfig.mapper.SystemParamMapper;
import com.lantu.connect.gateway.config.SkillExternalCatalogProperties;
import com.lantu.connect.gateway.dto.SkillExternalCatalogSettingsResponse;
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
                return objectMapper.readValue(row.getValue().trim(), SkillExternalCatalogProperties.class);
            } catch (Exception e) {
                log.warn("skill_external_catalog JSON 无效，回退 YAML 默认: {}", e.getMessage());
            }
        }
        return cloneDefaults();
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
        if (incoming.getSkillsmp() == null) {
            incoming.setSkillsmp(new SkillExternalCatalogProperties.SkillsMp());
        }
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
            n.setDescription("技能在线市场（超管可编辑，覆盖 skill-external-catalog.yml 默认值）");
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
            return objectMapper.readValue(
                    objectMapper.writeValueAsString(yamlDefaults),
                    SkillExternalCatalogProperties.class);
        } catch (Exception e) {
            throw new IllegalStateException("无法克隆默认技能市场配置", e);
        }
    }
}
