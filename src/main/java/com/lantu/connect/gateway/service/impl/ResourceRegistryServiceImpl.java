package com.lantu.connect.gateway.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.auth.entity.PlatformRole;
import com.lantu.connect.auth.mapper.PlatformRoleMapper;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.gateway.dto.ResourceManageVO;
import com.lantu.connect.gateway.dto.ResourceUpsertRequest;
import com.lantu.connect.gateway.dto.ResourceVersionCreateRequest;
import com.lantu.connect.gateway.dto.ResourceVersionVO;
import com.lantu.connect.gateway.protocol.ProtocolInvokerRegistry;
import com.lantu.connect.gateway.service.ResourceRegistryService;
import com.lantu.connect.gateway.service.support.ResourceLifecycleStateMachine;
import com.lantu.connect.common.util.DeptScopeHelper;
import com.lantu.connect.common.util.UserDisplayNameResolver;
import com.lantu.connect.notification.service.NotificationEventCodes;
import com.lantu.connect.notification.service.NotificationService;
import com.lantu.connect.notification.service.SystemNotificationFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ResourceRegistryServiceImpl implements ResourceRegistryService {

    private static final Set<String> RESOURCE_TYPES = Set.of("agent", "skill", "mcp", "app", "dataset");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PlatformRoleMapper platformRoleMapper;
    private final ProtocolInvokerRegistry protocolInvokerRegistry;
    private final DeptScopeHelper deptScopeHelper;
    private final UserDisplayNameResolver userDisplayNameResolver;
    private final NotificationService notificationService;
    private final SystemNotificationFacade systemNotificationFacade;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceManageVO create(Long operatorUserId, ResourceUpsertRequest request) {
        ensureAuthenticated(operatorUserId);
        String type = normalizeType(request.getResourceType());
        validateByType(type, request);
        ensureUniqueCode(type, request.getResourceCode(), null);

        LocalDateTime now = LocalDateTime.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(conn -> {
            PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO t_resource(resource_type, resource_code, display_name, description, status, source_type, provider_id, category_id, created_by, deleted, create_time, update_time)
                    VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, type);
            ps.setString(2, request.getResourceCode().trim());
            ps.setString(3, request.getDisplayName().trim());
            ps.setString(4, request.getDescription());
            ps.setString(5, ResourceLifecycleStateMachine.STATUS_DRAFT);
            ps.setString(6, request.getSourceType());
            ps.setObject(7, request.getProviderId());
            ps.setObject(8, request.getCategoryId());
            ps.setLong(9, operatorUserId);
            ps.setTimestamp(10, Timestamp.valueOf(now));
            ps.setTimestamp(11, Timestamp.valueOf(now));
            return ps;
        }, keyHolder);
        Long resourceId = keyHolder.getKey() == null ? null : keyHolder.getKey().longValue();
        if (resourceId == null) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "创建资源失败");
        }
        upsertExtension(type, resourceId, request);
        syncResourceRelations(resourceId, type, request.getRelatedResourceIds());
        upsertDefaultVersion(resourceId, buildSnapshot(type, request), true);
        return findResource(resourceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceManageVO update(Long operatorUserId, Long resourceId, ResourceUpsertRequest request) {
        ensureAuthenticated(operatorUserId);
        ResourceRow row = requireManageableResource(operatorUserId, resourceId);
        ResourceLifecycleStateMachine.ensureEditable(row.status());
        String type = normalizeType(request.getResourceType());
        if (!row.resourceType().equals(type)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "resourceType 不允许变更");
        }
        validateByType(type, request);
        ensureUniqueCode(type, request.getResourceCode(), resourceId);

        jdbcTemplate.update("""
                        UPDATE t_resource
                        SET resource_code = ?, display_name = ?, description = ?, source_type = ?, provider_id = ?, category_id = ?, update_time = NOW()
                        WHERE id = ? AND deleted = 0
                        """,
                request.getResourceCode().trim(),
                request.getDisplayName().trim(),
                request.getDescription(),
                request.getSourceType(),
                request.getProviderId(),
                request.getCategoryId(),
                resourceId);
        upsertExtension(type, resourceId, request);
        syncResourceRelations(resourceId, type, request.getRelatedResourceIds());
        upsertCurrentVersionSnapshot(resourceId, buildSnapshot(type, request));
        return findResource(resourceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long operatorUserId, Long resourceId) {
        ensureAuthenticated(operatorUserId);
        ResourceRow row = requireManageableResource(operatorUserId, resourceId);
        ResourceLifecycleStateMachine.ensureDeletable(row.status());
        jdbcTemplate.update("UPDATE t_resource SET deleted = 1, update_time = NOW() WHERE id = ? AND deleted = 0", resourceId);
        jdbcTemplate.update("UPDATE t_resource_version SET status = 'inactive', is_current = 0 WHERE resource_id = ?", resourceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitForAudit(Long operatorUserId, Long resourceId) {
        ensureAuthenticated(operatorUserId);
        ResourceRow row = requireManageableResource(operatorUserId, resourceId);
        ResourceLifecycleStateMachine.ensureTransitionAllowed(row.status(), ResourceLifecycleStateMachine.STATUS_PENDING_REVIEW);

        Integer pending = jdbcTemplate.queryForObject("""
                        SELECT COUNT(1)
                        FROM t_audit_item
                        WHERE target_type = ? AND target_id = ? AND status = 'pending_review'
                        """,
                Integer.class,
                row.resourceType(),
                resourceId);
        if (pending != null && pending > 0) {
            throw new BusinessException(ResultCode.DUPLICATE_SUBMIT, "该资源已有待审核记录");
        }

        jdbcTemplate.update("UPDATE t_resource SET status = ?, update_time = NOW() WHERE id = ? AND deleted = 0",
                ResourceLifecycleStateMachine.STATUS_PENDING_REVIEW, resourceId);
        jdbcTemplate.update("""
                        INSERT INTO t_audit_item(target_type, target_id, display_name, agent_name, description, agent_type, source_type, submitter, submit_time, status, create_time)
                        VALUES(?, ?, ?, ?, ?, ?, ?, ?, NOW(), 'pending_review', NOW())
                        """,
                row.resourceType(),
                resourceId,
                row.displayName(),
                row.resourceCode(),
                row.description(),
                row.resourceType(),
                row.sourceType(),
                String.valueOf(operatorUserId));

        notifyDeptAdmins(operatorUserId, NotificationEventCodes.RESOURCE_SUBMITTED,
                "新资源待审核",
                """
                        事件: 资源提审
                        结果: 待审核
                        时间: %s
                        详情:
                        提交人: %s
                        资源: %s/%s
                        说明: 开发者提交了资源「%s」，请审核。
                        建议: 请前往审核列表处理。
                        """.formatted(
                        LocalDateTime.now(),
                        operatorUserId,
                        row.resourceType(),
                        resourceId,
                        row.displayName()),
                resourceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deprecate(Long operatorUserId, Long resourceId) {
        ensureAuthenticated(operatorUserId);
        ResourceRow row = requireManageableResource(operatorUserId, resourceId);
        ResourceLifecycleStateMachine.ensureTransitionAllowed(row.status(), ResourceLifecycleStateMachine.STATUS_DEPRECATED);
        jdbcTemplate.update("UPDATE t_resource SET status = ?, update_time = NOW() WHERE id = ? AND deleted = 0",
                ResourceLifecycleStateMachine.STATUS_DEPRECATED, resourceId);
        systemNotificationFacade.notifyResourceStateChange(
                operatorUserId,
                NotificationEventCodes.RESOURCE_DEPRECATED,
                "资源已下线弃用",
                row.resourceType(),
                resourceId,
                "状态已切换为 deprecated");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long operatorUserId, Long resourceId) {
        ensureAuthenticated(operatorUserId);
        ResourceRow row = requireManageableResource(operatorUserId, resourceId);
        ResourceLifecycleStateMachine.ensureTransitionAllowed(row.status(), ResourceLifecycleStateMachine.STATUS_DRAFT);
        jdbcTemplate.update("UPDATE t_resource SET status = ?, update_time = NOW() WHERE id = ? AND deleted = 0",
                ResourceLifecycleStateMachine.STATUS_DRAFT, resourceId);
        jdbcTemplate.update("UPDATE t_audit_item SET status = 'withdrawn' WHERE target_id = ? AND status = 'pending_review'",
                resourceId);
        systemNotificationFacade.notifyResourceStateChange(
                operatorUserId,
                NotificationEventCodes.RESOURCE_WITHDRAWN,
                "资源已撤回到草稿",
                row.resourceType(),
                resourceId,
                "状态已切换为 draft");
    }

    @Override
    public ResourceManageVO getById(Long operatorUserId, Long resourceId) {
        ensureAuthenticated(operatorUserId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM t_resource WHERE id = ? AND deleted = 0 LIMIT 1", resourceId);
        if (rows.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "资源不存在");
        }
        Map<String, Object> row = rows.get(0);
        Long createdBy = longValue(row.get("created_by"));
        boolean owner = createdBy != null && createdBy.equals(operatorUserId);
        if (!owner && !isAdmin(operatorUserId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅资源拥有者或管理员可查看");
        }
        ResourceManageVO vo = toManageVo(row);
        enrichCreatedByName(vo);
        return vo;
    }

    @Override
    public PageResult<ResourceManageVO> pageMine(Long operatorUserId, String resourceType, Integer page, Integer pageSize) {
        ensureAuthenticated(operatorUserId);
        int p = page == null ? 1 : Math.max(1, page);
        int ps = pageSize == null ? 20 : Math.min(100, Math.max(1, pageSize));
        int offset = (p - 1) * ps;
        String normalizedType = StringUtils.hasText(resourceType) ? normalizeType(resourceType) : null;
        boolean admin = isAdmin(operatorUserId);

        List<Object> countArgs = new ArrayList<>();
        List<Object> listArgs = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE deleted = 0 ");
        if (!admin) {
            where.append(" AND created_by = ? ");
            countArgs.add(operatorUserId);
            listArgs.add(operatorUserId);
        } else if (deptScopeHelper.isDeptAdminOnly(operatorUserId)) {
            Long menuId = deptScopeHelper.getCurrentUserMenuId();
            if (menuId != null) {
                where.append(" AND created_by IN (SELECT user_id FROM t_user WHERE menu_id = ? AND deleted = 0) ");
                countArgs.add(menuId);
                listArgs.add(menuId);
            }
        }
        if (StringUtils.hasText(normalizedType)) {
            where.append(" AND resource_type = ? ");
            countArgs.add(normalizedType);
            listArgs.add(normalizedType);
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM t_resource " + where, Long.class, countArgs.toArray());
        listArgs.add(ps);
        listArgs.add(offset);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, resource_type, resource_code, display_name, description, status, source_type, provider_id, category_id, created_by, create_time, update_time FROM t_resource "
                        + where + " ORDER BY update_time DESC LIMIT ? OFFSET ?",
                listArgs.toArray());
        List<ResourceManageVO> list = rows.stream().map(this::toManageVo).toList();
        enrichCreatedByNames(list);
        return PageResult.of(list, total == null ? 0L : total, p, ps);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceVersionVO createVersion(Long operatorUserId, Long resourceId, ResourceVersionCreateRequest request) {
        ensureAuthenticated(operatorUserId);
        ResourceRow row = requireManageableResource(operatorUserId, resourceId);
        String version = normalizeVersion(request.getVersion());
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_resource_version WHERE resource_id = ? AND version = ?",
                Integer.class,
                resourceId, version);
        if (exists != null && exists > 0) {
            throw new BusinessException(ResultCode.DUPLICATE_VERSION);
        }
        Map<String, Object> snapshot = request.getSnapshot() == null || request.getSnapshot().isEmpty()
                ? buildSnapshotFromDb(row.resourceType(), resourceId)
                : request.getSnapshot();
        boolean makeCurrent = Boolean.TRUE.equals(request.getMakeCurrent());
        if (makeCurrent) {
            jdbcTemplate.update("UPDATE t_resource_version SET is_current = 0 WHERE resource_id = ?", resourceId);
        }
        jdbcTemplate.update("""
                        INSERT INTO t_resource_version(resource_id, version, status, is_current, snapshot_json, create_time)
                        VALUES(?, ?, 'active', ?, CAST(? AS JSON), NOW())
                        """,
                resourceId, version, makeCurrent ? 1 : 0, writeJson(snapshot));
        return findVersion(resourceId, version);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void switchVersion(Long operatorUserId, Long resourceId, String version) {
        ensureAuthenticated(operatorUserId);
        ResourceRow row = requireManageableResource(operatorUserId, resourceId);
        String v = normalizeVersion(version);
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_resource_version WHERE resource_id = ? AND version = ? AND status = 'active'",
                Integer.class,
                resourceId, v);
        if (exists == null || exists == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "目标版本不存在或不可用");
        }
        jdbcTemplate.update("UPDATE t_resource_version SET is_current = 0 WHERE resource_id = ?", resourceId);
        jdbcTemplate.update("UPDATE t_resource_version SET is_current = 1 WHERE resource_id = ? AND version = ?",
                resourceId, v);
        systemNotificationFacade.notifyResourceStateChange(
                operatorUserId,
                NotificationEventCodes.RESOURCE_VERSION_SWITCHED,
                "资源默认版本已切换",
                row.resourceType(),
                resourceId,
                "当前版本: " + v);
    }

    @Override
    public List<ResourceVersionVO> listVersions(Long operatorUserId, Long resourceId) {
        ensureAuthenticated(operatorUserId);
        requireManageableResource(operatorUserId, resourceId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, resource_id, version, status, is_current, create_time
                FROM t_resource_version
                WHERE resource_id = ?
                ORDER BY is_current DESC, create_time DESC
                """, resourceId);
        return rows.stream().map(this::toVersionVo).toList();
    }

    private ResourceManageVO findResource(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, resource_type, resource_code, display_name, description, status, source_type, provider_id, category_id, created_by, create_time, update_time
                FROM t_resource
                WHERE id = ? AND deleted = 0
                LIMIT 1
                """, id);
        if (rows.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "资源不存在");
        }
        ResourceManageVO vo = toManageVo(rows.get(0));
        enrichCreatedByName(vo);
        return vo;
    }

    private ResourceVersionVO findVersion(Long resourceId, String version) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, resource_id, version, status, is_current, create_time
                FROM t_resource_version
                WHERE resource_id = ? AND version = ?
                LIMIT 1
                """, resourceId, version);
        if (rows.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "版本不存在");
        }
        return toVersionVo(rows.get(0));
    }

    private void upsertExtension(String type, Long resourceId, ResourceUpsertRequest request) {
        switch (type) {
            case "agent" -> upsertAgentExt(resourceId, request);
            case "skill" -> upsertSkillExt(resourceId, request);
            case "mcp" -> upsertMcpExt(resourceId, request);
            case "app" -> upsertAppExt(resourceId, request);
            case "dataset" -> upsertDatasetExt(resourceId, request);
            default -> throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的 resourceType: " + type);
        }
    }

    private void upsertAgentExt(Long resourceId, ResourceUpsertRequest request) {
        int updated = jdbcTemplate.update("""
                        UPDATE t_resource_agent_ext
                        SET agent_type = ?, mode = ?, spec_json = CAST(? AS JSON), is_public = ?, hidden = ?, max_concurrency = ?, max_steps = ?, temperature = ?, system_prompt = ?
                        WHERE resource_id = ?
                        """,
                request.getAgentType(),
                defaultString(request.getMode(), "SUBAGENT"),
                writeJson(defaultMap(request.getSpec())),
                toBoolNumber(request.getIsPublic()),
                toBoolNumber(request.getHidden()),
                request.getMaxConcurrency() == null ? 10 : request.getMaxConcurrency(),
                request.getMaxSteps(),
                request.getTemperature(),
                request.getSystemPrompt(),
                resourceId);
        if (updated == 0) {
            jdbcTemplate.update("""
                            INSERT INTO t_resource_agent_ext(resource_id, agent_type, mode, spec_json, is_public, hidden, max_concurrency, max_steps, temperature, system_prompt, featured, rating_avg, rating_count)
                            VALUES(?, ?, ?, CAST(? AS JSON), ?, ?, ?, ?, ?, ?, 0, 0.00, 0)
                            """,
                    resourceId,
                    request.getAgentType(),
                    defaultString(request.getMode(), "SUBAGENT"),
                    writeJson(defaultMap(request.getSpec())),
                    toBoolNumber(request.getIsPublic()),
                    toBoolNumber(request.getHidden()),
                    request.getMaxConcurrency() == null ? 10 : request.getMaxConcurrency(),
                    request.getMaxSteps(),
                    request.getTemperature(),
                    request.getSystemPrompt());
        }
    }

    private void upsertSkillExt(Long resourceId, ResourceUpsertRequest request) {
        int updated = jdbcTemplate.update("""
                        UPDATE t_resource_skill_ext
                        SET skill_type = ?, mode = ?, parent_resource_id = ?, display_template = ?, spec_json = CAST(? AS JSON), parameters_schema = CAST(? AS JSON), is_public = ?, max_concurrency = ?
                        WHERE resource_id = ?
                        """,
                request.getSkillType(),
                defaultString(request.getMode(), "TOOL"),
                request.getParentResourceId(),
                request.getDisplayTemplate(),
                writeJson(defaultMap(request.getSpec())),
                writeJson(defaultMap(request.getParametersSchema())),
                toBoolNumber(request.getIsPublic()),
                request.getMaxConcurrency() == null ? 10 : request.getMaxConcurrency(),
                resourceId);
        if (updated == 0) {
            jdbcTemplate.update("""
                            INSERT INTO t_resource_skill_ext(resource_id, skill_type, mode, parent_resource_id, display_template, spec_json, parameters_schema, is_public, max_concurrency)
                            VALUES(?, ?, ?, ?, ?, CAST(? AS JSON), CAST(? AS JSON), ?, ?)
                            """,
                    resourceId,
                    request.getSkillType(),
                    defaultString(request.getMode(), "TOOL"),
                    request.getParentResourceId(),
                    request.getDisplayTemplate(),
                    writeJson(defaultMap(request.getSpec())),
                    writeJson(defaultMap(request.getParametersSchema())),
                    toBoolNumber(request.getIsPublic()),
                    request.getMaxConcurrency() == null ? 10 : request.getMaxConcurrency());
        }
    }

    private void upsertMcpExt(Long resourceId, ResourceUpsertRequest request) {
        String protocol = defaultString(request.getProtocol(), "mcp").toLowerCase(Locale.ROOT);
        if (!protocolInvokerRegistry.isSupported(protocol)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "MCP 协议不可调用: " + protocol);
        }
        int updated = jdbcTemplate.update("""
                        UPDATE t_resource_mcp_ext
                        SET endpoint = ?, protocol = ?, auth_type = ?, auth_config = CAST(? AS JSON)
                        WHERE resource_id = ?
                        """,
                request.getEndpoint(),
                protocol,
                defaultString(request.getAuthType(), "none"),
                writeJson(defaultMap(request.getAuthConfig())),
                resourceId);
        if (updated == 0) {
            jdbcTemplate.update("""
                            INSERT INTO t_resource_mcp_ext(resource_id, endpoint, protocol, auth_type, auth_config)
                            VALUES(?, ?, ?, ?, CAST(? AS JSON))
                            """,
                    resourceId,
                    request.getEndpoint(),
                    protocol,
                    defaultString(request.getAuthType(), "none"),
                    writeJson(defaultMap(request.getAuthConfig())));
        }
    }

    private void upsertAppExt(Long resourceId, ResourceUpsertRequest request) {
        int updated = jdbcTemplate.update("""
                        UPDATE t_resource_app_ext
                        SET app_url = ?, embed_type = ?, icon = ?, screenshots = CAST(? AS JSON), is_public = ?
                        WHERE resource_id = ?
                        """,
                request.getAppUrl(),
                request.getEmbedType(),
                request.getIcon(),
                writeJson(defaultList(request.getScreenshots())),
                toBoolNumber(request.getIsPublic()),
                resourceId);
        if (updated == 0) {
            jdbcTemplate.update("""
                            INSERT INTO t_resource_app_ext(resource_id, app_url, embed_type, icon, screenshots, is_public)
                            VALUES(?, ?, ?, ?, CAST(? AS JSON), ?)
                            """,
                    resourceId,
                    request.getAppUrl(),
                    request.getEmbedType(),
                    request.getIcon(),
                    writeJson(defaultList(request.getScreenshots())),
                    toBoolNumber(request.getIsPublic()));
        }
    }

    private void upsertDatasetExt(Long resourceId, ResourceUpsertRequest request) {
        int updated = jdbcTemplate.update("""
                        UPDATE t_resource_dataset_ext
                        SET data_type = ?, format = ?, record_count = ?, file_size = ?, tags = CAST(? AS JSON), is_public = ?
                        WHERE resource_id = ?
                        """,
                request.getDataType(),
                request.getFormat(),
                request.getRecordCount() == null ? 0L : request.getRecordCount(),
                request.getFileSize() == null ? 0L : request.getFileSize(),
                writeJson(defaultList(request.getTags())),
                toBoolNumber(request.getIsPublic()),
                resourceId);
        if (updated == 0) {
            jdbcTemplate.update("""
                            INSERT INTO t_resource_dataset_ext(resource_id, data_type, format, record_count, file_size, tags, is_public)
                            VALUES(?, ?, ?, ?, ?, CAST(? AS JSON), ?)
                            """,
                    resourceId,
                    request.getDataType(),
                    request.getFormat(),
                    request.getRecordCount() == null ? 0L : request.getRecordCount(),
                    request.getFileSize() == null ? 0L : request.getFileSize(),
                    writeJson(defaultList(request.getTags())),
                    toBoolNumber(request.getIsPublic()));
        }
    }

    private void validateByType(String type, ResourceUpsertRequest request) {
        if (!StringUtils.hasText(request.getResourceCode())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "resourceCode 不能为空");
        }
        if (!StringUtils.hasText(request.getDisplayName())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "displayName 不能为空");
        }
        switch (type) {
            case "agent" -> {
                requireText(request.getAgentType(), "agentType 不能为空");
                if (request.getSpec() == null || request.getSpec().isEmpty()) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "agent spec 不能为空");
                }
            }
            case "skill" -> {
                requireText(request.getSkillType(), "skillType 不能为空");
                if (request.getSpec() == null || request.getSpec().isEmpty()) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "skill spec 不能为空");
                }
            }
            case "mcp" -> {
                requireText(request.getEndpoint(), "endpoint 不能为空");
                String protocol = defaultString(request.getProtocol(), "mcp");
                if (!protocolInvokerRegistry.isSupported(protocol)) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "MCP 协议不可调用: " + protocol);
                }
            }
            case "app" -> {
                requireText(request.getAppUrl(), "appUrl 不能为空");
                requireText(request.getEmbedType(), "embedType 不能为空");
            }
            case "dataset" -> {
                requireText(request.getDataType(), "dataType 不能为空");
                requireText(request.getFormat(), "format 不能为空");
            }
            default -> throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的 resourceType");
        }
    }

    private void ensureUniqueCode(String type, String resourceCode, Long excludeId) {
        String code = resourceCode == null ? null : resourceCode.trim();
        if (!StringUtils.hasText(code)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "resourceCode 不能为空");
        }
        Integer count;
        if (excludeId == null) {
            count = jdbcTemplate.queryForObject("""
                            SELECT COUNT(1)
                            FROM t_resource
                            WHERE deleted = 0 AND resource_type = ? AND resource_code = ?
                            """,
                    Integer.class, type, code);
        } else {
            count = jdbcTemplate.queryForObject("""
                            SELECT COUNT(1)
                            FROM t_resource
                            WHERE deleted = 0 AND resource_type = ? AND resource_code = ? AND id <> ?
                            """,
                    Integer.class, type, code, excludeId);
        }
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.DUPLICATE_NAME, "同类型资源编码已存在");
        }
    }

    private ResourceRow requireManageableResource(Long operatorUserId, Long resourceId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, resource_type, resource_code, display_name, description, status, source_type, created_by
                FROM t_resource
                WHERE id = ? AND deleted = 0
                LIMIT 1
                """, resourceId);
        if (rows.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "资源不存在");
        }
        Map<String, Object> row = rows.get(0);
        Long createdBy = longValue(row.get("created_by"));
        boolean owner = createdBy != null && createdBy.equals(operatorUserId);
        if (!owner && !isAdmin(operatorUserId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅资源拥有者或管理员可操作");
        }
        return new ResourceRow(
                longValue(row.get("id")),
                stringValue(row.get("resource_type")),
                stringValue(row.get("resource_code")),
                stringValue(row.get("display_name")),
                stringValue(row.get("description")),
                stringValue(row.get("status")),
                stringValue(row.get("source_type"))
        );
    }

    private boolean isAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        List<PlatformRole> roles = platformRoleMapper.selectRolesByUserId(userId);
        return roles.stream().map(PlatformRole::getRoleCode).anyMatch(code ->
                "platform_admin".equals(code) || "dept_admin".equals(code));
    }

    private void upsertDefaultVersion(Long resourceId, Map<String, Object> snapshot, boolean makeCurrent) {
        jdbcTemplate.update("UPDATE t_resource_version SET is_current = 0 WHERE resource_id = ? AND ? = 1",
                resourceId, makeCurrent ? 1 : 0);
        jdbcTemplate.update("""
                        INSERT INTO t_resource_version(resource_id, version, status, is_current, snapshot_json, create_time)
                        VALUES(?, 'v1', 'active', ?, CAST(? AS JSON), NOW())
                        ON DUPLICATE KEY UPDATE
                            status = VALUES(status),
                            is_current = VALUES(is_current),
                            snapshot_json = VALUES(snapshot_json)
                        """,
                resourceId,
                makeCurrent ? 1 : 0,
                writeJson(snapshot));
    }

    private void upsertCurrentVersionSnapshot(Long resourceId, Map<String, Object> snapshot) {
        Integer updated = jdbcTemplate.update("""
                        UPDATE t_resource_version
                        SET snapshot_json = CAST(? AS JSON)
                        WHERE resource_id = ? AND is_current = 1
                        """,
                writeJson(snapshot), resourceId);
        if (updated == null || updated == 0) {
            upsertDefaultVersion(resourceId, snapshot, true);
        }
    }

    private String normalizeType(String type) {
        if (!StringUtils.hasText(type)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "resourceType 不能为空");
        }
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        if (!RESOURCE_TYPES.contains(normalized)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的资源类型: " + type);
        }
        return normalized;
    }

    private static String normalizeVersion(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "version 不能为空");
        }
        String out = raw.trim();
        if (out.length() > 32) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "version 长度不能超过32");
        }
        return out;
    }

    private static void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, message);
        }
    }

    private static Integer toBoolNumber(Boolean value) {
        return Boolean.TRUE.equals(value) ? 1 : 0;
    }

    private static String defaultString(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private static Map<String, Object> defaultMap(Map<String, Object> value) {
        return value == null ? Map.of() : value;
    }

    private static List<String> defaultList(List<String> value) {
        return value == null ? List.of() : value;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "JSON 序列化失败");
        }
    }

    private Map<String, Object> buildSnapshot(String type, ResourceUpsertRequest request) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("resourceType", type);
        snapshot.put("resourceCode", request.getResourceCode());
        snapshot.put("displayName", request.getDisplayName());
        snapshot.put("description", request.getDescription());
        snapshot.put("status", ResourceLifecycleStateMachine.STATUS_DRAFT);
        switch (type) {
            case "agent" -> {
                snapshot.put("invokeType", "rest");
                snapshot.put("endpoint", request.getSpec() == null ? null : request.getSpec().get("url"));
                snapshot.put("spec", defaultMap(request.getSpec()));
            }
            case "skill" -> {
                snapshot.put("invokeType", "rest");
                snapshot.put("endpoint", request.getSpec() == null ? null : request.getSpec().get("url"));
                snapshot.put("spec", defaultMap(request.getSpec()));
            }
            case "mcp" -> {
                snapshot.put("invokeType", defaultString(request.getProtocol(), "mcp").toLowerCase(Locale.ROOT));
                snapshot.put("endpoint", request.getEndpoint());
                snapshot.put("spec", defaultMap(request.getAuthConfig()));
            }
            case "app" -> {
                snapshot.put("invokeType", "redirect");
                snapshot.put("endpoint", request.getAppUrl());
                snapshot.put("spec", Map.of("embedType", request.getEmbedType()));
            }
            case "dataset" -> {
                snapshot.put("invokeType", "metadata");
                snapshot.put("endpoint", null);
                Map<String, Object> spec = new HashMap<>();
                spec.put("dataType", request.getDataType());
                spec.put("format", request.getFormat());
                spec.put("recordCount", request.getRecordCount());
                spec.put("fileSize", request.getFileSize());
                spec.put("tags", defaultList(request.getTags()));
                snapshot.put("spec", spec);
            }
            default -> {
            }
        }
        return snapshot;
    }

    private Map<String, Object> buildSnapshotFromDb(String type, Long resourceId) {
        Map<String, Object> base = jdbcTemplate.queryForList("""
                SELECT resource_code, display_name, description, status
                FROM t_resource
                WHERE id = ? AND deleted = 0
                LIMIT 1
                """, resourceId).stream().findFirst().orElseThrow(() ->
                new BusinessException(ResultCode.NOT_FOUND, "资源不存在"));
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("resourceType", type);
        snapshot.put("resourceCode", stringValue(base.get("resource_code")));
        snapshot.put("displayName", stringValue(base.get("display_name")));
        snapshot.put("description", stringValue(base.get("description")));
        snapshot.put("status", stringValue(base.get("status")));
        switch (type) {
            case "agent" -> {
                Map<String, Object> ext = jdbcTemplate.queryForList("SELECT spec_json FROM t_resource_agent_ext WHERE resource_id = ? LIMIT 1", resourceId)
                        .stream().findFirst().orElse(Map.of());
                Map<String, Object> spec = parseJsonMap(ext.get("spec_json"));
                snapshot.put("invokeType", "rest");
                snapshot.put("endpoint", spec.get("url"));
                snapshot.put("spec", spec);
            }
            case "skill" -> {
                Map<String, Object> ext = jdbcTemplate.queryForList("SELECT spec_json FROM t_resource_skill_ext WHERE resource_id = ? LIMIT 1", resourceId)
                        .stream().findFirst().orElse(Map.of());
                Map<String, Object> spec = parseJsonMap(ext.get("spec_json"));
                snapshot.put("invokeType", "rest");
                snapshot.put("endpoint", spec.get("url"));
                snapshot.put("spec", spec);
            }
            case "mcp" -> {
                Map<String, Object> ext = jdbcTemplate.queryForList("SELECT endpoint, protocol, auth_config FROM t_resource_mcp_ext WHERE resource_id = ? LIMIT 1", resourceId)
                        .stream().findFirst().orElse(Map.of());
                snapshot.put("invokeType", stringValue(ext.get("protocol")));
                snapshot.put("endpoint", stringValue(ext.get("endpoint")));
                snapshot.put("spec", parseJsonMap(ext.get("auth_config")));
            }
            case "app" -> {
                Map<String, Object> ext = jdbcTemplate.queryForList("SELECT app_url, embed_type FROM t_resource_app_ext WHERE resource_id = ? LIMIT 1", resourceId)
                        .stream().findFirst().orElse(Map.of());
                snapshot.put("invokeType", "redirect");
                snapshot.put("endpoint", stringValue(ext.get("app_url")));
                snapshot.put("spec", Map.of("embedType", stringValue(ext.get("embed_type"))));
            }
            case "dataset" -> {
                Map<String, Object> ext = jdbcTemplate.queryForList("SELECT data_type, format, record_count, file_size, tags FROM t_resource_dataset_ext WHERE resource_id = ? LIMIT 1", resourceId)
                        .stream().findFirst().orElse(Map.of());
                Map<String, Object> spec = new LinkedHashMap<>();
                spec.put("dataType", stringValue(ext.get("data_type")));
                spec.put("format", stringValue(ext.get("format")));
                spec.put("recordCount", longValue(ext.get("record_count")));
                spec.put("fileSize", longValue(ext.get("file_size")));
                spec.put("tags", parseJsonList(ext.get("tags")));
                snapshot.put("invokeType", "metadata");
                snapshot.put("endpoint", null);
                snapshot.put("spec", spec);
            }
            default -> {
            }
        }
        return snapshot;
    }

    private Map<String, Object> parseJsonMap(Object raw) {
        try {
            if (raw == null) {
                return Map.of();
            }
            if (raw instanceof String s) {
                if (!StringUtils.hasText(s)) {
                    return Map.of();
                }
                return objectMapper.readValue(s, Map.class);
            }
            return objectMapper.convertValue(raw, Map.class);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private List<Object> parseJsonList(Object raw) {
        try {
            if (raw == null) {
                return List.of();
            }
            if (raw instanceof String s) {
                if (!StringUtils.hasText(s)) {
                    return List.of();
                }
                return objectMapper.readValue(s, List.class);
            }
            return objectMapper.convertValue(raw, List.class);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private ResourceManageVO toManageVo(Map<String, Object> row) {
        return ResourceManageVO.builder()
                .id(longValue(row.get("id")))
                .resourceType(stringValue(row.get("resource_type")))
                .resourceCode(stringValue(row.get("resource_code")))
                .displayName(stringValue(row.get("display_name")))
                .description(stringValue(row.get("description")))
                .status(stringValue(row.get("status")))
                .sourceType(stringValue(row.get("source_type")))
                .providerId(longValue(row.get("provider_id")))
                .categoryId(longValue(row.get("category_id")))
                .createdBy(longValue(row.get("created_by")))
                .createTime(toDateTime(row.get("create_time")))
                .updateTime(toDateTime(row.get("update_time")))
                .build();
    }

    private ResourceVersionVO toVersionVo(Map<String, Object> row) {
        return ResourceVersionVO.builder()
                .id(longValue(row.get("id")))
                .resourceId(longValue(row.get("resource_id")))
                .version(stringValue(row.get("version")))
                .status(stringValue(row.get("status")))
                .current(longValue(row.get("is_current")) != null && longValue(row.get("is_current")) == 1L)
                .createTime(toDateTime(row.get("create_time")))
                .build();
    }

    private static LocalDateTime toDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime time) {
            return time;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b ? 1L : 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }

    private static void ensureAuthenticated(Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未认证");
        }
    }

    private void syncResourceRelations(Long resourceId, String resourceType, List<Long> relatedResourceIds) {
        if (relatedResourceIds == null || relatedResourceIds.isEmpty()) {
            return;
        }
        if (!"agent".equals(resourceType) && !"app".equals(resourceType)) {
            return;
        }
        String relationType = "agent".equals(resourceType) ? "agent_depends_skill" : "app_depends_resource";

        jdbcTemplate.update("DELETE FROM t_resource_relation WHERE from_resource_id = ? AND relation_type = ?",
                resourceId, relationType);
        for (Long toId : relatedResourceIds) {
            if (toId == null || toId.equals(resourceId)) {
                continue;
            }
            jdbcTemplate.update("""
                    INSERT INTO t_resource_relation(from_resource_id, to_resource_id, relation_type, create_time)
                    VALUES(?, ?, ?, NOW())
                    """, resourceId, toId, relationType);
        }
    }

    private void notifyDeptAdmins(Long submitterUserId, String type, String title, String body, Long resourceId) {
        Long menuId = null;
        if (submitterUserId != null) {
            var user = jdbcTemplate.queryForList("SELECT menu_id FROM t_user WHERE user_id = ? AND deleted = 0 LIMIT 1", submitterUserId);
            if (!user.isEmpty() && user.get(0).get("menu_id") != null) {
                menuId = Long.valueOf(String.valueOf(user.get(0).get("menu_id")));
            }
        }
        String sql = menuId != null
                ? "SELECT ur.user_id FROM t_user_role_rel ur JOIN t_platform_role r ON ur.role_id = r.id JOIN t_user u ON ur.user_id = u.user_id WHERE r.role_code = 'dept_admin' AND u.menu_id = ? AND u.deleted = 0"
                : "SELECT ur.user_id FROM t_user_role_rel ur JOIN t_platform_role r ON ur.role_id = r.id WHERE r.role_code = 'dept_admin'";
        java.util.List<java.util.Map<String, Object>> admins = menuId != null
                ? jdbcTemplate.queryForList(sql, menuId)
                : jdbcTemplate.queryForList(sql);
        java.util.List<Long> adminIds = admins.stream()
                .map(r -> Long.valueOf(String.valueOf(r.get("user_id"))))
                .toList();
        if (!adminIds.isEmpty()) {
            notificationService.broadcast(adminIds, type, title, body, "resource", resourceId);
        }
    }

    private record ResourceRow(Long id, String resourceType, String resourceCode, String displayName, String description,
                               String status, String sourceType) {
    }

    private void enrichCreatedByName(ResourceManageVO vo) {
        if (vo == null || vo.getCreatedBy() == null) {
            return;
        }
        vo.setCreatedByName(userDisplayNameResolver.resolveDisplayName(vo.getCreatedBy()));
    }

    private void enrichCreatedByNames(List<ResourceManageVO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Map<Long, String> names = userDisplayNameResolver.resolveDisplayNames(
                records.stream().map(ResourceManageVO::getCreatedBy).toList());
        records.forEach(item -> item.setCreatedByName(names.get(item.getCreatedBy())));
    }
}

