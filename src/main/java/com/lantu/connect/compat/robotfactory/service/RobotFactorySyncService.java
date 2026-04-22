package com.lantu.connect.compat.robotfactory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.compat.robotfactory.entity.RobotFactoryProjection;
import com.lantu.connect.compat.robotfactory.entity.RobotFactorySyncLog;
import com.lantu.connect.compat.robotfactory.mapper.RobotFactoryProjectionMapper;
import com.lantu.connect.compat.robotfactory.mapper.RobotFactorySyncLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class RobotFactorySyncService {

    private final RobotFactoryProjectionService projectionService;
    private final RobotFactoryProjectionMapper projectionMapper;
    private final RobotFactorySyncLogMapper syncLogMapper;
    private final ObjectMapper objectMapper;
    private final RobotFactorySettingsService settingsService;

    @Transactional(rollbackFor = Exception.class)
    public RobotFactoryProjection manualSync(Long projectionId) {
        RobotFactoryProjection projection = projectionService.requireProjection(projectionId);
        projectionService.requirePublishedResourceForProjection(projection);
        boolean exists = externalExistsByAgentName(projection.getAgentName());
        String action = exists ? "update" : "manual_sync";
        return doUpsert(projection, action, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public RobotFactoryProjection deleteExternal(Long projectionId, String action) {
        RobotFactoryProjection projection = projectionService.requireProjection(projectionId);
        return doDeleteExternal(projection, action, true);
    }

    public void onResourcePublished(Long resourceId) {
        RobotFactoryProjection projection = projectionService.ensureProjectionForResource(resourceId);
        if (!Boolean.TRUE.equals(projection.getAutoSyncEnabled())) {
            return;
        }
        try {
            boolean exists = externalExistsByAgentName(projection.getAgentName());
            doUpsert(projection, exists ? "update" : "create", false);
        } catch (Exception e) {
            log.warn("robot-factory publish sync failed: resourceId={} msg={}", resourceId, e.getMessage());
        }
    }

    public void onResourceUpdated(Long resourceId) {
        RobotFactoryProjection projection = projectionService.ensureProjectionForResource(resourceId);
        if (!Boolean.TRUE.equals(projection.getAutoSyncEnabled())) {
            return;
        }
        try {
            doUpsert(projection, "update", false);
        } catch (Exception e) {
            log.warn("robot-factory update sync failed: resourceId={} msg={}", resourceId, e.getMessage());
        }
    }

    public void onResourceDeprecated(Long resourceId) {
        RobotFactoryProjection projection = projectionMapper.selectOne(new LambdaQueryWrapper<RobotFactoryProjection>()
                .eq(RobotFactoryProjection::getResourceId, resourceId)
                .last("LIMIT 1"));
        if (projection == null) {
            return;
        }
        if (!Boolean.TRUE.equals(projection.getAutoSyncEnabled())) {
            projectionService.updateProjectionSyncState(
                    projection.getId(),
                    "pending",
                    "资源已下线，自动同步未开启；如已注册到精灵平台，请手动删除外部记录",
                    projection.getExternalAgentId(),
                    projection.getLastSyncedAt());
            return;
        }
        try {
            doDeleteExternal(projection, "delete", false);
        } catch (Exception e) {
            log.warn("robot-factory delete sync failed: resourceId={} msg={}", resourceId, e.getMessage());
        }
    }

    private RobotFactoryProjection doUpsert(RobotFactoryProjection projection,
                                            String action,
                                            boolean throwOnError) {
        LocalDateTime now = LocalDateTime.now();
        projectionService.requirePublishedResourceForProjection(projection);
        Map<String, Object> payload = buildExternalPayload(projection);
        String requestJson = toJson(payload);
        try {
            JdbcTemplate externalJdbc = settingsService.newExternalJdbcTemplate();
            Long existingId = findExternalIdByAgentName(projection.getAgentName());
            Long externalId;
            String actualAction;
            if (existingId != null) {
                actualAction = "update";
                externalId = existingId;
                externalJdbc.update("""
                        UPDATE genie_external_agent
                        SET corp_id = ?, yn = ?, stop = ?, agent_name = ?, display_name = ?, description = ?, icon = ?,
                            display_template = ?, tags = ?, agent_type = ?, mode = ?, max_concurrency = ?, spec_json = ?,
                            workflow_source_json = NULL, intent_profile_json = NULL, intent_embedding_text = NULL,
                            intent_profile_hash = NULL, intent_profile_status = NULL, intent_profile_update_time = NULL,
                            allowed_tools = NULL, denied_tools = NULL, max_steps = NULL, temperature = NULL,
                            system_prompt = NULL, parameters_schema = ?, hidden = 0, sort_order = 0,
                            is_public = 1, allowed_roles = NULL, runtime_role = ?, interaction_mode = ?, dispatch_mode = ?,
                            quality_score = 0.5, avg_latency_ms = 0, success_rate = 1, avg_token_cost = 0, call_count = 0,
                            update_time = CURRENT_TIMESTAMP
                        WHERE id = ?
                        """,
                        payload.get("corp_id"),
                        payload.get("yn"),
                        payload.get("stop"),
                        payload.get("agent_name"),
                        payload.get("display_name"),
                        payload.get("description"),
                        payload.get("icon"),
                        payload.get("display_template"),
                        payload.get("tags"),
                        payload.get("agent_type"),
                        payload.get("mode"),
                        payload.get("max_concurrency"),
                        payload.get("spec_json"),
                        payload.get("parameters_schema"),
                        payload.get("runtime_role"),
                        payload.get("interaction_mode"),
                        payload.get("dispatch_mode"),
                        existingId);
            } else {
                actualAction = "create".equals(action) ? "create" : action;
                KeyHolder keyHolder = new GeneratedKeyHolder();
                externalJdbc.update(conn -> {
                    PreparedStatement ps = conn.prepareStatement("""
                            INSERT INTO genie_external_agent(
                                corp_id, yn, stop, agent_name, display_name, description, icon, display_template, tags,
                                agent_type, mode, max_concurrency, spec_json, workflow_source_json, intent_profile_json,
                                intent_embedding_text, intent_profile_hash, intent_profile_status, intent_profile_update_time,
                                allowed_tools, denied_tools, max_steps, temperature, system_prompt, parameters_schema,
                                hidden, sort_order, is_public, allowed_roles, runtime_role, interaction_mode, dispatch_mode,
                                quality_score, avg_latency_ms, success_rate, avg_token_cost, call_count, create_time, update_time
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
                                      NULL, NULL, NULL, ?, 0, 0, 1, NULL, ?, ?, ?, 0.5, 0, 1, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                            """, Statement.RETURN_GENERATED_KEYS);
                    ps.setObject(1, payload.get("corp_id"));
                    ps.setObject(2, payload.get("yn"));
                    ps.setObject(3, payload.get("stop"));
                    ps.setObject(4, payload.get("agent_name"));
                    ps.setObject(5, payload.get("display_name"));
                    ps.setObject(6, payload.get("description"));
                    ps.setObject(7, payload.get("icon"));
                    ps.setObject(8, payload.get("display_template"));
                    ps.setObject(9, payload.get("tags"));
                    ps.setObject(10, payload.get("agent_type"));
                    ps.setObject(11, payload.get("mode"));
                    ps.setObject(12, payload.get("max_concurrency"));
                    ps.setObject(13, payload.get("spec_json"));
                    ps.setObject(14, payload.get("parameters_schema"));
                    ps.setObject(15, payload.get("runtime_role"));
                    ps.setObject(16, payload.get("interaction_mode"));
                    ps.setObject(17, payload.get("dispatch_mode"));
                    return ps;
                }, keyHolder);
                externalId = keyHolder.getKey() == null
                        ? findExternalIdByAgentName(projection.getAgentName())
                        : keyHolder.getKey().longValue();
            }
            String message = "已同步到精灵平台注册表，需对方手动刷新缓存";
            projectionService.updateProjectionSyncState(projection.getId(), "synced", message, externalId, now);
            recordSyncLog(projection.getId(), projection.getResourceId(), actualAction, true, message, requestJson,
                    toJson(Map.of("externalAgentId", externalId, "action", actualAction)));
            return projectionMapper.selectById(projection.getId());
        } catch (Exception e) {
            String message = firstNonBlank(e.getMessage(), "精灵平台同步失败");
            projectionService.updateProjectionSyncState(projection.getId(), "failed", message, projection.getExternalAgentId(), now);
            recordSyncLog(projection.getId(), projection.getResourceId(), action, false, message, requestJson,
                    toJson(Map.of("error", message)));
            if (throwOnError) {
                throw wrapSyncException(message, e);
            }
            return projectionMapper.selectById(projection.getId());
        }
    }

    private RobotFactoryProjection doDeleteExternal(RobotFactoryProjection projection,
                                                    String action,
                                                    boolean throwOnError) {
        LocalDateTime now = LocalDateTime.now();
        try {
            JdbcTemplate externalJdbc = settingsService.newExternalJdbcTemplate();
            Long externalId = projection.getExternalAgentId();
            if (externalId != null) {
                externalJdbc.update("DELETE FROM genie_external_agent WHERE id = ?", externalId);
            } else {
                externalJdbc.update("DELETE FROM genie_external_agent WHERE agent_name = ?", projection.getAgentName());
            }
            String message = "已从精灵平台注册表删除，需对方手动刷新缓存";
            projectionService.updateProjectionSyncState(projection.getId(), "deleted", message, null, now);
            recordSyncLog(projection.getId(), projection.getResourceId(), action, true, message,
                    toJson(Map.of("agentName", projection.getAgentName(), "externalAgentId", externalId)),
                    toJson(Map.of("deleted", true)));
            return projectionMapper.selectById(projection.getId());
        } catch (Exception e) {
            String message = firstNonBlank(e.getMessage(), "精灵平台删除失败");
            projectionService.updateProjectionSyncState(projection.getId(), "failed", message, projection.getExternalAgentId(), now);
            recordSyncLog(projection.getId(), projection.getResourceId(), action, false, message,
                    toJson(Map.of("agentName", projection.getAgentName(), "externalAgentId", projection.getExternalAgentId())),
                    toJson(Map.of("error", message)));
            if (throwOnError) {
                throw wrapSyncException(message, e);
            }
            return projectionMapper.selectById(projection.getId());
        }
    }

    private Map<String, Object> buildExternalPayload(RobotFactoryProjection projection) {
        if (!"global".equalsIgnoreCase(projection.getScopeMode()) && projection.getCorpId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "当前投影未找到可用的 corp_id 映射，无法同步到精灵平台");
        }
        String effectiveSpecJson = projectionService.resolveEffectiveSpecJson(projection);
        if (!StringUtils.hasText(effectiveSpecJson)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "投影 spec_json 为空，无法同步到精灵平台");
        }
        validateExternalSpecJson(effectiveSpecJson);
        if (!Objects.equals(effectiveSpecJson, projection.getSpecJson())) {
            projection.setSpecJson(effectiveSpecJson);
            projectionMapper.updateById(projection);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("corp_id", "global".equalsIgnoreCase(projection.getScopeMode()) ? null : projection.getCorpId());
        payload.put("yn", 1);
        payload.put("stop", 0);
        payload.put("agent_name", projection.getAgentName());
        payload.put("display_name", projection.getDisplayName());
        payload.put("description", projection.getDescription());
        payload.put("icon", null);
        payload.put("display_template", projection.getDisplayTemplate());
        payload.put("tags", null);
        payload.put("agent_type", "mcp");
        payload.put("mode", "TOOL");
        payload.put("max_concurrency", 1);
        payload.put("spec_json", effectiveSpecJson);
        payload.put("parameters_schema", projection.getParametersSchema());
        payload.put("runtime_role", "tool");
        payload.put("interaction_mode", "sync");
        payload.put("dispatch_mode", "tool_sync");
        return payload;
    }

    private void validateExternalSpecJson(String specJson) {
        Map<String, Object> spec = projectionService.parseJsonMap(specJson);
        String url = firstNonBlank(spec.get("url") == null ? null : String.valueOf(spec.get("url")));
        if (!StringUtils.hasText(url)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "投影 spec_json.url 为空，无法同步到精灵平台");
        }
        String normalized = url.trim().toLowerCase(Locale.ROOT);
        if (!(normalized.startsWith("http://") || normalized.startsWith("https://"))) {
            throw new BusinessException(
                    ResultCode.PARAM_ERROR,
                    "投影 spec_json.url 不是可直连的绝对地址，请先在适配设置中填写对外访问地址，再重新同步");
        }
    }

    private Long findExternalIdByAgentName(String agentName) {
        try {
            return settingsService.newExternalJdbcTemplate().queryForObject(
                    "SELECT id FROM genie_external_agent WHERE agent_name = ? LIMIT 1",
                    Long.class,
                    agentName);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private boolean externalExistsByAgentName(String agentName) {
        return findExternalIdByAgentName(agentName) != null;
    }

    private void recordSyncLog(Long projectionId,
                               Long resourceId,
                               String action,
                               boolean success,
                               String message,
                               String requestSnapshotJson,
                               String responseSnapshotJson) {
        RobotFactorySyncLog logRow = new RobotFactorySyncLog();
        logRow.setProjectionId(projectionId);
        logRow.setResourceId(resourceId);
        logRow.setAction(normalizeAction(action));
        logRow.setSuccess(success);
        logRow.setMessage(message);
        logRow.setRequestSnapshotJson(requestSnapshotJson);
        logRow.setResponseSnapshotJson(responseSnapshotJson);
        logRow.setCreateTime(LocalDateTime.now());
        syncLogMapper.insert(logRow);
    }

    private RuntimeException wrapSyncException(String message, Exception e) {
        if (e instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new BusinessException(ResultCode.INTERNAL_ERROR, message);
    }

    private String toJson(Object payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return String.valueOf(payload);
        }
    }

    private static String normalizeAction(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "manual_sync";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
