package com.lantu.connect.compat.robotfactory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.PageResults;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.compat.robotfactory.dto.RobotFactoryAvailableResourceItem;
import com.lantu.connect.compat.robotfactory.dto.RobotFactoryCorpMappingUpsertRequest;
import com.lantu.connect.compat.robotfactory.dto.RobotFactoryProjectionCreateRequest;
import com.lantu.connect.compat.robotfactory.dto.RobotFactoryProjectionListItem;
import com.lantu.connect.compat.robotfactory.dto.RobotFactoryProjectionUpdateRequest;
import com.lantu.connect.compat.robotfactory.dto.RobotFactoryResourceContext;
import com.lantu.connect.compat.robotfactory.entity.RobotFactoryCorpMapping;
import com.lantu.connect.compat.robotfactory.entity.RobotFactoryProjection;
import com.lantu.connect.compat.robotfactory.entity.RobotFactorySyncLog;
import com.lantu.connect.compat.robotfactory.mapper.RobotFactoryCorpMappingMapper;
import com.lantu.connect.compat.robotfactory.mapper.RobotFactoryProjectionMapper;
import com.lantu.connect.compat.robotfactory.mapper.RobotFactorySyncLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RobotFactoryProjectionService {

    private static final String TYPE_MCP = "mcp";
    private static final String STATUS_PUBLISHED = "published";
    private static final Set<String> ACTIVE_SYNC_STATUSES = Set.of("synced");

    private final RobotFactoryCorpMappingMapper corpMappingMapper;
    private final RobotFactoryProjectionMapper projectionMapper;
    private final RobotFactorySyncLogMapper syncLogMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RobotFactorySettingsService settingsService;

    @Value("${server.servlet.context-path:/regis}")
    private String servletContextPath;

    public List<RobotFactoryCorpMapping> listCorpMappings() {
        return corpMappingMapper.selectList(new LambdaQueryWrapper<RobotFactoryCorpMapping>()
                .orderByAsc(RobotFactoryCorpMapping::getSchoolId));
    }

    @Transactional(rollbackFor = Exception.class)
    public RobotFactoryCorpMapping createCorpMapping(RobotFactoryCorpMappingUpsertRequest request) {
        RobotFactoryCorpMapping existing = findCorpMappingBySchoolId(request.getSchoolId());
        if (existing != null) {
            throw new BusinessException(ResultCode.CONFLICT, "该 school_id 已存在软件工厂 Corp 映射");
        }
        RobotFactoryCorpMapping entity = new RobotFactoryCorpMapping();
        copyCorpMapping(request, entity);
        corpMappingMapper.insert(entity);
        return corpMappingMapper.selectById(entity.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public RobotFactoryCorpMapping updateCorpMapping(Long id, RobotFactoryCorpMappingUpsertRequest request) {
        RobotFactoryCorpMapping entity = corpMappingMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Corp 映射不存在");
        }
        RobotFactoryCorpMapping sameSchool = findCorpMappingBySchoolId(request.getSchoolId());
        if (sameSchool != null && !Objects.equals(sameSchool.getId(), id)) {
            throw new BusinessException(ResultCode.CONFLICT, "该 school_id 已存在软件工厂 Corp 映射");
        }
        copyCorpMapping(request, entity);
        corpMappingMapper.updateById(entity);
        refreshProjectionCorpIds(request.getSchoolId());
        return corpMappingMapper.selectById(id);
    }

    public PageResult<RobotFactoryProjectionListItem> pageProjections(int page,
                                                                      int pageSize,
                                                                      String syncStatus,
                                                                      Boolean autoSyncEnabled,
                                                                      Long schoolId,
                                                                      String keyword) {
        Page<RobotFactoryProjection> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<RobotFactoryProjection> wrapper = new LambdaQueryWrapper<RobotFactoryProjection>()
                .eq(StringUtils.hasText(syncStatus), RobotFactoryProjection::getSyncStatus, normalize(syncStatus))
                .eq(autoSyncEnabled != null, RobotFactoryProjection::getAutoSyncEnabled, autoSyncEnabled)
                .eq(schoolId != null, RobotFactoryProjection::getSchoolId, schoolId)
                .orderByDesc(RobotFactoryProjection::getUpdateTime);
        if (StringUtils.hasText(keyword)) {
            String q = keyword.trim();
            wrapper.and(w -> w.like(RobotFactoryProjection::getDisplayName, q)
                    .or().like(RobotFactoryProjection::getAgentName, q)
                    .or().like(RobotFactoryProjection::getProjectionCode, q));
        }
        Page<RobotFactoryProjection> result = projectionMapper.selectPage(p, wrapper);
        List<RobotFactoryProjectionListItem> rows = toProjectionList(result.getRecords());
        return PageResult.of(rows, result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    public RobotFactoryProjectionListItem getProjection(Long id) {
        RobotFactoryProjection entity = requireProjection(id);
        return toProjectionList(List.of(entity)).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "投影不存在"));
    }

    @Transactional(rollbackFor = Exception.class)
    public RobotFactoryProjectionListItem createProjection(RobotFactoryProjectionCreateRequest request) {
        if (projectionMapper.selectOne(new LambdaQueryWrapper<RobotFactoryProjection>()
                .eq(RobotFactoryProjection::getResourceId, request.getResourceId())) != null) {
            throw new BusinessException(ResultCode.CONFLICT, "该 MCP 资源已存在软件工厂投影");
        }
        RobotFactoryResourceContext resource = requirePublishedMcpResource(request.getResourceId());
        RobotFactoryProjection entity = buildProjection(resource, request, null);
        projectionMapper.insert(entity);
        return getProjection(entity.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public RobotFactoryProjectionListItem updateProjection(Long id, RobotFactoryProjectionUpdateRequest request) {
        RobotFactoryProjection entity = requireProjection(id);
        RobotFactoryResourceContext resource = requireMcpResource(entity.getResourceId());
        applyProjectionOverrides(entity, resource, request);
        projectionMapper.updateById(entity);
        return getProjection(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public RobotFactoryProjection setAutoSync(Long id, boolean enabled) {
        RobotFactoryProjection entity = requireProjection(id);
        entity.setAutoSyncEnabled(enabled);
        entity.setUpdateTime(LocalDateTime.now());
        projectionMapper.updateById(entity);
        return projectionMapper.selectById(id);
    }

    public PageResult<RobotFactorySyncLog> pageSyncLogs(int page,
                                                        int pageSize,
                                                        Long projectionId,
                                                        Boolean success,
                                                        String action) {
        Page<RobotFactorySyncLog> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<RobotFactorySyncLog> wrapper = new LambdaQueryWrapper<RobotFactorySyncLog>()
                .eq(projectionId != null, RobotFactorySyncLog::getProjectionId, projectionId)
                .eq(success != null, RobotFactorySyncLog::getSuccess, success)
                .eq(StringUtils.hasText(action), RobotFactorySyncLog::getAction, normalize(action))
                .orderByDesc(RobotFactorySyncLog::getCreateTime);
        return PageResults.from(syncLogMapper.selectPage(p, wrapper));
    }

    public List<RobotFactoryAvailableResourceItem> listAvailablePublishedResources(String keyword, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        StringBuilder sql = new StringBuilder("""
                SELECT r.id,
                       r.resource_code,
                       r.display_name,
                       r.description,
                       u.school_id
                FROM t_resource r
                LEFT JOIN t_user u ON u.user_id = r.created_by
                WHERE r.deleted = 0
                  AND LOWER(r.resource_type) = 'mcp'
                  AND LOWER(r.status) = 'published'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM t_robotfactory_projection p
                      WHERE p.resource_id = r.id
                  )
                """);
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword.trim() + "%";
            sql.append("""
                     AND (
                         r.display_name LIKE ?
                         OR r.resource_code LIKE ?
                         OR r.description LIKE ?
                     )
                    """);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY r.update_time DESC, r.id DESC LIMIT ?");
        args.add(safeLimit);
        return jdbcTemplate.query(sql.toString(), args.toArray(), (rs, rowNum) -> RobotFactoryAvailableResourceItem.builder()
                .resourceId(rs.getLong("id"))
                .resourceCode(rs.getString("resource_code"))
                .displayName(rs.getString("display_name"))
                .description(rs.getString("description"))
                .schoolId((Long) rs.getObject("school_id", Long.class))
                .build());
    }

    @Transactional(rollbackFor = Exception.class)
    public RobotFactoryProjection ensureProjectionForResource(Long resourceId) {
        RobotFactoryProjection existing = projectionMapper.selectOne(new LambdaQueryWrapper<RobotFactoryProjection>()
                .eq(RobotFactoryProjection::getResourceId, resourceId));
        RobotFactoryResourceContext resource = requireMcpResource(resourceId);
        if (existing == null) {
            RobotFactoryProjectionCreateRequest createRequest = new RobotFactoryProjectionCreateRequest();
            createRequest.setResourceId(resourceId);
            createRequest.setScopeMode("school");
            RobotFactoryProjection created = buildProjection(resource, createRequest, null);
            created.setAutoSyncEnabled(Boolean.FALSE);
            projectionMapper.insert(created);
            return created;
        }
        applyProjectionOverrides(existing, resource, null);
        projectionMapper.updateById(existing);
        return existing;
    }

    public RobotFactoryProjection requireProjection(Long id) {
        RobotFactoryProjection entity = projectionMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "投影不存在");
        }
        return entity;
    }

    public RobotFactoryProjection requireProjectionByCode(String projectionCode) {
        RobotFactoryProjection entity = projectionMapper.selectOne(new LambdaQueryWrapper<RobotFactoryProjection>()
                .eq(RobotFactoryProjection::getProjectionCode, projectionCode));
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "投影不存在");
        }
        return entity;
    }

    public RobotFactoryProjection requireSyncedProjectionByCode(String projectionCode) {
        RobotFactoryProjection entity = requireProjectionByCode(projectionCode);
        if (!ACTIVE_SYNC_STATUSES.contains(normalize(entity.getSyncStatus()))) {
            throw new BusinessException(ResultCode.ILLEGAL_STATE_TRANSITION, "投影尚未同步到软件工厂");
        }
        return entity;
    }

    public RobotFactoryResourceContext requirePublishedResourceForProjection(RobotFactoryProjection projection) {
        RobotFactoryResourceContext resource = requirePublishedMcpResource(projection.getResourceId());
        if (!TYPE_MCP.equalsIgnoreCase(resource.getResourceType())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "当前投影仅支持 MCP 资源");
        }
        return resource;
    }

    public RobotFactoryResourceContext requireMcpResource(Long resourceId) {
        RobotFactoryResourceContext resource = loadResourceContext(resourceId);
        if (resource == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "资源不存在");
        }
        if (!TYPE_MCP.equalsIgnoreCase(resource.getResourceType())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "仅支持 MCP 资源接入软件工厂");
        }
        return resource;
    }

    public RobotFactoryResourceContext requirePublishedMcpResource(Long resourceId) {
        RobotFactoryResourceContext resource = requireMcpResource(resourceId);
        if (!STATUS_PUBLISHED.equalsIgnoreCase(resource.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "仅允许为 published 的 MCP 资源创建投影");
        }
        return resource;
    }

    public RobotFactoryCorpMapping findCorpMappingBySchoolId(Long schoolId) {
        if (schoolId == null) {
            return null;
        }
        return corpMappingMapper.selectOne(new LambdaQueryWrapper<RobotFactoryCorpMapping>()
                .eq(RobotFactoryCorpMapping::getSchoolId, schoolId)
                .last("LIMIT 1"));
    }

    public void updateProjectionSyncState(Long projectionId,
                                          String syncStatus,
                                          String syncMessage,
                                          Long externalAgentId,
                                          LocalDateTime lastSyncedAt) {
        RobotFactoryProjection entity = requireProjection(projectionId);
        entity.setSyncStatus(syncStatus);
        entity.setSyncMessage(syncMessage);
        entity.setExternalAgentId(externalAgentId);
        entity.setLastSyncedAt(lastSyncedAt);
        projectionMapper.updateById(entity);
    }

    public String toJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "软件工厂适配 JSON 序列化失败");
        }
    }

    public Map<String, Object> parseJsonMap(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(raw, new TypeReference<LinkedHashMap<String, Object>>() {
            });
            return parsed == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parsed);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private void copyCorpMapping(RobotFactoryCorpMappingUpsertRequest request, RobotFactoryCorpMapping entity) {
        entity.setSchoolId(request.getSchoolId());
        entity.setSchoolNameSnapshot(StringUtils.hasText(request.getSchoolNameSnapshot())
                ? request.getSchoolNameSnapshot().trim()
                : "school-" + request.getSchoolId());
        entity.setCorpId(request.getCorpId());
        entity.setEnabled(request.getEnabled() == null || Boolean.TRUE.equals(request.getEnabled()));
        entity.setRemark(StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : null);
    }

    private void refreshProjectionCorpIds(Long schoolId) {
        List<RobotFactoryProjection> projections = projectionMapper.selectList(new LambdaQueryWrapper<RobotFactoryProjection>()
                .eq(RobotFactoryProjection::getSchoolId, schoolId));
        RobotFactoryCorpMapping mapping = findCorpMappingBySchoolId(schoolId);
        for (RobotFactoryProjection projection : projections) {
            if ("global".equalsIgnoreCase(projection.getScopeMode())) {
                projection.setCorpId(null);
            } else {
                projection.setCorpId(mapping != null && Boolean.TRUE.equals(mapping.getEnabled()) ? mapping.getCorpId() : null);
            }
            projectionMapper.updateById(projection);
        }
    }

    private RobotFactoryProjection buildProjection(RobotFactoryResourceContext resource,
                                                   RobotFactoryProjectionCreateRequest createRequest,
                                                   RobotFactoryProjectionUpdateRequest updateRequest) {
        RobotFactoryProjection entity = new RobotFactoryProjection();
        entity.setResourceId(resource.getResourceId());
        entity.setResourceType(TYPE_MCP);
        entity.setProjectionCode(buildProjectionCode(resource.getResourceId()));
        entity.setAgentName(buildAgentName(resource.getResourceId()));
        entity.setAgentType(TYPE_MCP);
        entity.setMode("TOOL");
        entity.setRuntimeRole("tool");
        entity.setInteractionMode("sync");
        entity.setDispatchMode("tool_sync");
        entity.setAutoSyncEnabled(createRequest == null || createRequest.getAutoSyncEnabled() == null
                ? Boolean.FALSE : createRequest.getAutoSyncEnabled());
        entity.setSyncStatus("pending");
        entity.setSyncMessage("待同步到软件工厂");
        applyProjectionOverrides(entity, resource, updateRequest != null ? updateRequest : toUpdateRequest(createRequest));
        return entity;
    }

    private RobotFactoryProjectionUpdateRequest toUpdateRequest(RobotFactoryProjectionCreateRequest request) {
        if (request == null) {
            return null;
        }
        RobotFactoryProjectionUpdateRequest update = new RobotFactoryProjectionUpdateRequest();
        update.setScopeMode(request.getScopeMode());
        update.setDisplayName(request.getDisplayName());
        update.setDescription(request.getDescription());
        update.setDisplayTemplate(request.getDisplayTemplate());
        update.setSpecJson(request.getSpecJson());
        update.setParametersSchema(request.getParametersSchema());
        update.setAutoSyncEnabled(request.getAutoSyncEnabled());
        return update;
    }

    private void applyProjectionOverrides(RobotFactoryProjection entity,
                                          RobotFactoryResourceContext resource,
                                          RobotFactoryProjectionUpdateRequest request) {
        String scopeMode = normalizeScopeMode(request != null && StringUtils.hasText(request.getScopeMode())
                ? request.getScopeMode()
                : entity.getScopeMode());
        entity.setScopeMode(scopeMode);
        entity.setSchoolId(resource.getSchoolId());
        entity.setCorpId(resolveCorpId(resource.getSchoolId(), scopeMode));

        if (request != null && request.getAutoSyncEnabled() != null) {
            entity.setAutoSyncEnabled(request.getAutoSyncEnabled());
        }
        if (request != null) {
            if (request.getDisplayName() != null) {
                entity.setDisplayNameOverride(trimToNull(request.getDisplayName()));
            }
            if (request.getDescription() != null) {
                entity.setDescriptionOverride(trimToNull(request.getDescription()));
            }
            if (request.getDisplayTemplate() != null) {
                entity.setDisplayTemplateOverride(trimToNull(request.getDisplayTemplate()));
            }
            if (request.getSpecJson() != null) {
                entity.setSpecJsonOverride(trimToNull(request.getSpecJson()));
            }
            if (request.getParametersSchema() != null) {
                entity.setParametersSchemaOverride(trimToNull(request.getParametersSchema()));
            }
        }

        entity.setDisplayName(firstNonBlank(entity.getDisplayNameOverride(), resource.getDisplayName(), entity.getAgentName()));
        entity.setDescription(firstNonBlank(entity.getDescriptionOverride(), resource.getDescription(), resource.getResourceCode()));
        entity.setDisplayTemplate(firstNonBlank(entity.getDisplayTemplateOverride(), inferDisplayTemplate(resource), null));
        entity.setSpecJson(buildSpecJson(entity.getProjectionCode(), entity.getSpecJsonOverride()));
        entity.setParametersSchema(firstNonBlank(entity.getParametersSchemaOverride(), resolveDefaultParametersSchema(resource), null));
    }

    private String buildProjectionCode(Long resourceId) {
        return "rfp_mcp_" + resourceId;
    }

    private String buildAgentName(Long resourceId) {
        return "rf_mcp_" + resourceId;
    }

    private Long resolveCorpId(Long schoolId, String scopeMode) {
        if ("global".equalsIgnoreCase(scopeMode)) {
            return null;
        }
        RobotFactoryCorpMapping mapping = findCorpMappingBySchoolId(schoolId);
        if (mapping == null || !Boolean.TRUE.equals(mapping.getEnabled())) {
            return null;
        }
        return mapping.getCorpId();
    }

    private String resolveDefaultParametersSchema(RobotFactoryResourceContext resource) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT snapshot_json
                FROM t_resource_version
                WHERE resource_id = ? AND is_current = 1
                ORDER BY create_time DESC
                LIMIT 1
                """, resource.getResourceId());
        if (rows.isEmpty()) {
            return null;
        }
        Object raw = rows.get(0).get("snapshot_json");
        Map<String, Object> snapshot = parseJsonMap(raw == null ? null : String.valueOf(raw));
        Object schema = snapshot.get("parametersSchema");
        if (schema == null) {
            Object spec = snapshot.get("spec");
            if (spec instanceof Map<?, ?> map) {
                schema = map.get("parametersSchema");
            }
        }
        return schema == null ? null : toJson(schema);
    }

    private String buildSpecJson(String projectionCode, String overrideSpecJson) {
        if (StringUtils.hasText(overrideSpecJson)) {
            return overrideSpecJson.trim();
        }
        String publicBaseUrl = settingsService.getPublicBaseUrl();
        String base = StringUtils.hasText(publicBaseUrl) ? trimTrailingSlash(publicBaseUrl) : "";
        String ctx = normalizeContextPath(servletContextPath);
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("url", base + ctx + "/compat/robot-factory/mcp/" + projectionCode + "/sse");
        return toJson(spec);
    }

    private String inferDisplayTemplate(RobotFactoryResourceContext resource) {
        String corpus = String.join(" ",
                firstNonBlank(resource.getDisplayName(), ""),
                firstNonBlank(resource.getResourceCode(), ""),
                firstNonBlank(resource.getDescription(), ""))
                .toLowerCase(Locale.ROOT);
        if (corpus.contains("image") || corpus.contains("图片") || corpus.contains("图像")) {
            return "image";
        }
        if (corpus.contains("search") || corpus.contains("搜索") || corpus.contains("检索")) {
            return "search_web";
        }
        if (corpus.contains("word")
                || corpus.contains("excel")
                || corpus.contains("ppt")
                || corpus.contains("pdf")
                || corpus.contains("mindmap")
                || corpus.contains("文档")
                || corpus.contains("文件")) {
            return "file";
        }
        return null;
    }

    private List<RobotFactoryProjectionListItem> toProjectionList(List<RobotFactoryProjection> projections) {
        if (projections == null || projections.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Map<String, Object>> resourceRows = loadResourceRows(
                projections.stream().map(RobotFactoryProjection::getResourceId).collect(Collectors.toSet()));
        List<RobotFactoryProjectionListItem> rows = new ArrayList<>();
        for (RobotFactoryProjection item : projections) {
            Map<String, Object> resource = resourceRows.get(item.getResourceId());
            rows.add(RobotFactoryProjectionListItem.builder()
                    .id(item.getId())
                    .resourceId(item.getResourceId())
                    .resourceType(item.getResourceType())
                    .resourceCode(stringValue(resource == null ? null : resource.get("resource_code")))
                    .resourceStatus(stringValue(resource == null ? null : resource.get("status")))
                    .schoolId(item.getSchoolId())
                    .corpId(item.getCorpId())
                    .scopeMode(item.getScopeMode())
                    .projectionCode(item.getProjectionCode())
                    .agentName(item.getAgentName())
                    .displayName(item.getDisplayName())
                    .description(item.getDescription())
                    .displayTemplate(item.getDisplayTemplate())
                    .agentType(item.getAgentType())
                    .mode(item.getMode())
                    .runtimeRole(item.getRuntimeRole())
                    .interactionMode(item.getInteractionMode())
                    .dispatchMode(item.getDispatchMode())
                    .autoSyncEnabled(Boolean.TRUE.equals(item.getAutoSyncEnabled()))
                    .externalAgentId(item.getExternalAgentId())
                    .syncStatus(item.getSyncStatus())
                    .syncMessage(item.getSyncMessage())
                    .lastSyncedAt(item.getLastSyncedAt())
                    .createTime(item.getCreateTime())
                    .updateTime(item.getUpdateTime())
                    .build());
        }
        return rows;
    }

    private Map<Long, Map<String, Object>> loadResourceRows(Set<Long> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String placeholders = resourceIds.stream().map(x -> "?").collect(Collectors.joining(","));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, resource_code, status FROM t_resource WHERE id IN (" + placeholders + ")",
                resourceIds.toArray());
        Map<Long, Map<String, Object>> out = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long id = longValue(row.get("id"));
            if (id != null) {
                out.put(id, row);
            }
        }
        return out;
    }

    private RobotFactoryResourceContext loadResourceContext(Long resourceId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT r.id,
                       r.resource_type,
                       r.resource_code,
                       r.display_name,
                       r.description,
                       r.status,
                       r.created_by,
                       u.school_id,
                       ext.endpoint,
                       ext.protocol,
                       ext.auth_type,
                       ext.auth_config,
                       ext.service_detail_md
                FROM t_resource r
                LEFT JOIN t_user u ON u.user_id = r.created_by
                LEFT JOIN t_resource_mcp_ext ext ON ext.resource_id = r.id
                WHERE r.id = ? AND r.deleted = 0
                LIMIT 1
                """, resourceId);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        Map<String, Object> spec = parseJsonMap(stringValue(row.get("auth_config")));
        if (StringUtils.hasText(stringValue(row.get("auth_type")))) {
            spec.put("authType", stringValue(row.get("auth_type")).trim().toLowerCase(Locale.ROOT));
        }
        return RobotFactoryResourceContext.builder()
                .resourceId(longValue(row.get("id")))
                .resourceType(stringValue(row.get("resource_type")))
                .resourceCode(stringValue(row.get("resource_code")))
                .displayName(stringValue(row.get("display_name")))
                .description(stringValue(row.get("description")))
                .status(stringValue(row.get("status")))
                .createdBy(longValue(row.get("created_by")))
                .schoolId(longValue(row.get("school_id")))
                .endpoint(stringValue(row.get("endpoint")))
                .protocol(firstNonBlank(stringValue(row.get("protocol")), TYPE_MCP))
                .spec(spec)
                .serviceDetailMd(stringValue(row.get("service_detail_md")))
                .build();
    }

    private static String normalizeScopeMode(String raw) {
        String value = normalize(raw);
        return "global".equals(value) ? "global" : "school";
    }

    private static String normalize(String raw) {
        return raw == null ? null : raw.trim().toLowerCase(Locale.ROOT);
    }

    private static String trimToNull(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim();
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

    private static String trimTrailingSlash(String raw) {
        String value = raw == null ? "" : raw.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String normalizeContextPath(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String value = raw.trim();
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        if ("/".equals(value)) {
            return "";
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String stringValue(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }

    private static Long longValue(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
