package com.lantu.connect.gateway.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.auth.entity.PlatformRole;
import com.lantu.connect.auth.mapper.PlatformRoleMapper;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.gateway.dto.ResourceManageVO;
import com.lantu.connect.gateway.dto.LifecycleTimelineVO;
import com.lantu.connect.gateway.dto.DegradationHintVO;
import com.lantu.connect.gateway.dto.ObservabilitySummaryVO;
import com.lantu.connect.gateway.dto.ResourceUpsertRequest;
import com.lantu.connect.gateway.dto.ResourceVersionCreateRequest;
import com.lantu.connect.gateway.dto.ResourceVersionVO;
import com.lantu.connect.gateway.model.ResourceAccessPolicy;
import com.lantu.connect.gateway.protocol.McpOutboundHeaderBuilder;
import com.lantu.connect.gateway.protocol.ProtocolInvokerRegistry;
import com.lantu.connect.gateway.service.ResourceRegistryService;
import com.lantu.connect.gateway.service.support.ResourceLifecycleStateMachine;
import com.lantu.connect.gateway.service.support.SkillPackSkillRootPath;
import com.lantu.connect.gateway.service.support.SkillPackValidationStatus;
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
import java.util.Objects;
import java.util.Set;
import java.util.LinkedHashSet;

@Service
@RequiredArgsConstructor
public class ResourceRegistryServiceImpl implements ResourceRegistryService {

    private static final Set<String> RESOURCE_TYPES = Set.of("agent", "skill", "mcp", "app", "dataset");

    private static final Set<String> APP_EMBED_TYPES = Set.of("iframe", "redirect", "micro_frontend");

    /** skill.skill_type：仅技能包格式；远程 MCP/HTTP 工具须用 resourceType=mcp。 */
    private static final Set<String> SKILL_PACK_FORMATS = Set.of("anthropic_v1", "folder_v1");

    private static final Set<String> FORBIDDEN_SKILL_PACK_TYPES = Set.of("mcp", "http_api");

    /** 含 service_detail_md 列的扩展表（用于统一 upsert / 读取逻辑） */
    private static final Set<String> SERVICE_DETAIL_EXT_TABLES = Set.of(
            "t_resource_mcp_ext",
            "t_resource_skill_ext",
            "t_resource_agent_ext",
            "t_resource_app_ext",
            "t_resource_dataset_ext");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PlatformRoleMapper platformRoleMapper;
    private final ProtocolInvokerRegistry protocolInvokerRegistry;
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
        ResourceAccessPolicy accessPolicy = ResourceAccessPolicy.parseRequestValue(request.getAccessPolicy());

        LocalDateTime now = LocalDateTime.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(conn -> {
            PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO t_resource(resource_type, resource_code, display_name, description, status, source_type, provider_id, category_id, access_policy, created_by, deleted, create_time, update_time)
                    VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, type);
            ps.setString(2, request.getResourceCode().trim());
            ps.setString(3, request.getDisplayName().trim());
            ps.setString(4, request.getDescription());
            ps.setString(5, ResourceLifecycleStateMachine.STATUS_DRAFT);
            ps.setString(6, request.getSourceType());
            ps.setObject(7, request.getProviderId());
            ps.setObject(8, request.getCategoryId());
            ps.setString(9, accessPolicy.wireValue());
            ps.setLong(10, operatorUserId);
            ps.setTimestamp(11, Timestamp.valueOf(now));
            ps.setTimestamp(12, Timestamp.valueOf(now));
            return ps;
        }, keyHolder);
        Long resourceId = keyHolder.getKey() == null ? null : keyHolder.getKey().longValue();
        if (resourceId == null) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "创建资源失败");
        }
        upsertExtension(type, resourceId, request);
        syncResourceRelations(resourceId, type, request.getRelatedResourceIds());
        syncResourceTagRels(resourceId, type, request);
        upsertDefaultVersion(resourceId, snapshotForVersion(type, resourceId, request, true), true);
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
        ResourceAccessPolicy accessPolicy = resolveAccessPolicyForUpdate(resourceId, request);

        jdbcTemplate.update("""
                        UPDATE t_resource
                        SET resource_code = ?, display_name = ?, description = ?, source_type = ?, provider_id = ?, category_id = ?, access_policy = ?, update_time = NOW()
                        WHERE id = ? AND deleted = 0
                        """,
                request.getResourceCode().trim(),
                request.getDisplayName().trim(),
                request.getDescription(),
                request.getSourceType(),
                request.getProviderId(),
                request.getCategoryId(),
                accessPolicy.wireValue(),
                resourceId);
        upsertExtension(type, resourceId, request);
        syncResourceRelations(resourceId, type, request.getRelatedResourceIds());
        syncResourceTagRels(resourceId, type, request);
        upsertCurrentVersionSnapshot(resourceId, snapshotForVersion(type, resourceId, request, false));
        return findResource(resourceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long operatorUserId, Long resourceId) {
        ensureAuthenticated(operatorUserId);
        ResourceRow row = requireManageableResource(operatorUserId, resourceId);
        ResourceLifecycleStateMachine.ensureDeletable(row.status());
        jdbcTemplate.update("DELETE FROM t_resource_tag_rel WHERE resource_id = ? AND resource_type = ?",
                resourceId, row.resourceType());
        jdbcTemplate.update("UPDATE t_resource SET deleted = 1, update_time = NOW() WHERE id = ? AND deleted = 0", resourceId);
        jdbcTemplate.update("UPDATE t_resource_version SET status = 'inactive', is_current = 0 WHERE resource_id = ?", resourceId);
    }

    /**
     * 开发者提审：写入全局审核队列 {@code t_audit_item}（无部门字段、不按组织派单）。
     * 所有绑定 {@code reviewer} 的账号拉取的是同一份全平台待审列表；通知会推送给每一位审核员。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceManageVO submitForAudit(Long operatorUserId, Long resourceId) {
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

        if ("skill".equals(row.resourceType())) {
            List<Map<String, Object>> se = jdbcTemplate.queryForList(
                    "SELECT artifact_uri FROM t_resource_skill_ext WHERE resource_id = ? LIMIT 1",
                    resourceId);
            if (se.isEmpty()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "技能扩展信息不存在");
            }
            if (!StringUtils.hasText(stringValue(se.get(0).get("artifact_uri")))) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "artifactUri 不能为空，请先上传技能包");
            }
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

        notifyReviewers(NotificationEventCodes.RESOURCE_SUBMITTED,
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
        return findResource(resourceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceManageVO deprecate(Long operatorUserId, Long resourceId) {
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
        return findResource(resourceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceManageVO withdraw(Long operatorUserId, Long resourceId) {
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
        return findResource(resourceId);
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
        enrichExtensionFields(vo, resourceId);
        enrichCurrentVersionLabel(vo, resourceId);
        enrichCatalogTagNames(vo, resourceId);
        enrichLifecycleContext(vo, operatorUserId);
        enrichObservabilityFields(vo);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recomputeCurrentVersionSnapshot(Long operatorUserId, Long resourceId) {
        ensureAuthenticated(operatorUserId);
        ResourceRow row = requireManageableResource(operatorUserId, resourceId);
        upsertCurrentVersionSnapshot(resourceId, buildSnapshotFromDb(row.resourceType(), resourceId));
    }

    /**
     * @param forCreate true：缺省 accessPolicy 视为 grant_required；false：更新时未传字段则保留库中值
     */
    private Map<String, Object> snapshotForVersion(String type, Long resourceId, ResourceUpsertRequest request, boolean forCreate) {
        if ("skill".equals(type)) {
            return buildSnapshotFromDb(type, resourceId);
        }
        ResourceAccessPolicy ap = forCreate
                ? ResourceAccessPolicy.parseRequestValue(request.getAccessPolicy())
                : resolveAccessPolicyForUpdate(resourceId, request);
        return buildSnapshot(type, request, ap);
    }

    private ResourceAccessPolicy resolveAccessPolicyForUpdate(Long resourceId, ResourceUpsertRequest request) {
        if (StringUtils.hasText(request.getAccessPolicy())) {
            return ResourceAccessPolicy.parseRequestValue(request.getAccessPolicy());
        }
        List<Map<String, Object>> cur = jdbcTemplate.queryForList(
                "SELECT access_policy FROM t_resource WHERE id = ? AND deleted = 0 LIMIT 1", resourceId);
        if (cur.isEmpty()) {
            return ResourceAccessPolicy.GRANT_REQUIRED;
        }
        return ResourceAccessPolicy.fromStored(cur.get(0).get("access_policy"));
    }

    private static Timestamp toSqlTimestamp(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Timestamp ts) {
            return ts;
        }
        if (raw instanceof LocalDateTime ldt) {
            return Timestamp.valueOf(ldt);
        }
        if (raw instanceof java.util.Date d) {
            return new Timestamp(d.getTime());
        }
        return null;
    }

    @Override
    public PageResult<ResourceManageVO> pageMine(Long operatorUserId, String resourceType, String status,
                                                 String keyword, String sortBy, String sortOrder,
                                                 Integer page, Integer pageSize) {
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
        }
        if (StringUtils.hasText(normalizedType)) {
            where.append(" AND resource_type = ? ");
            countArgs.add(normalizedType);
            listArgs.add(normalizedType);
        }
        if (StringUtils.hasText(status)) {
            where.append(" AND status = ? ");
            countArgs.add(status.trim().toLowerCase(Locale.ROOT));
            listArgs.add(status.trim().toLowerCase(Locale.ROOT));
        }
        if (StringUtils.hasText(keyword)) {
            where.append(" AND (display_name LIKE ? OR resource_code LIKE ? OR description LIKE ?) ");
            String kw = "%" + keyword.trim() + "%";
            countArgs.add(kw);
            countArgs.add(kw);
            countArgs.add(kw);
            listArgs.add(kw);
            listArgs.add(kw);
            listArgs.add(kw);
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM t_resource " + where, Long.class, countArgs.toArray());
        listArgs.add(ps);
        listArgs.add(offset);
        String whereAliased = where.toString()
                .replace(" WHERE deleted = 0 ", " WHERE r.deleted = 0 ")
                .replace(" AND created_by IN (", " AND r.created_by IN (")
                .replace(" AND created_by = ? ", " AND r.created_by = ? ")
                .replace(" AND resource_type = ? ", " AND r.resource_type = ? ")
                .replace(" AND status = ? ", " AND r.status = ? ")
                .replace("display_name LIKE ?", "r.display_name LIKE ?")
                .replace("resource_code LIKE ?", "r.resource_code LIKE ?")
                .replace("description LIKE ?", "r.description LIKE ?");
        String orderColumn = switch (StringUtils.hasText(sortBy) ? sortBy.trim().toLowerCase(Locale.ROOT) : "") {
            case "create_time" -> "r.create_time";
            case "display_name" -> "r.display_name";
            case "status" -> "r.status";
            default -> "r.update_time";
        };
        String direction = "asc".equalsIgnoreCase(sortOrder) ? "ASC" : "DESC";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                        SELECT r.id, r.resource_type, r.resource_code, r.display_name, r.description, r.status, r.source_type, \
                        r.provider_id, r.category_id, r.access_policy, r.created_by, r.create_time, r.update_time, \
                        (SELECT v.version FROM t_resource_version v WHERE v.resource_id = r.id AND v.is_current = 1 LIMIT 1) AS current_version \
                        FROM t_resource r \
                        """
                        + whereAliased + " ORDER BY " + orderColumn + " " + direction + " LIMIT ? OFFSET ?",
                listArgs.toArray());
        List<ResourceManageVO> list = rows.stream().map(this::toManageVo).toList();
        enrichCreatedByNames(list);
        enrichCatalogTagNamesBatch(list);
        list.forEach(vo -> enrichLifecycleContext(vo, operatorUserId));
        list.forEach(this::enrichObservabilityFields);
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
    public ResourceManageVO switchVersion(Long operatorUserId, Long resourceId, String version) {
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
        return findResource(resourceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceManageVO applyVersionSnapshotToWorkingCopy(Long operatorUserId, Long resourceId, String version) {
        ensureAuthenticated(operatorUserId);
        ResourceRow row = requireManageableResource(operatorUserId, resourceId);
        ResourceLifecycleStateMachine.ensureEditable(row.status());
        String v = normalizeVersion(version);
        List<Map<String, Object>> verRows = jdbcTemplate.queryForList("""
                        SELECT snapshot_json FROM t_resource_version
                        WHERE resource_id = ? AND version = ? AND status = 'active'
                        LIMIT 1
                        """,
                resourceId, v);
        if (verRows.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "版本不存在或不可用");
        }
        Map<String, Object> snap = parseJsonMap(verRows.get(0).get("snapshot_json"));
        if (snap.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "版本快照为空");
        }
        ResourceManageVO current = findResource(resourceId);
        ResourceUpsertRequest req = mergeSnapshotOntoCurrent(current, snap);
        String type = normalizeType(row.resourceType());
        if (!type.equals(normalizeType(req.getResourceType()))) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "快照 resourceType 与资源不一致");
        }
        validateByType(type, req);
        ensureUniqueCode(type, req.getResourceCode(), resourceId);
        ResourceAccessPolicy accessPolicy = resolveAccessPolicyForUpdate(resourceId, req);

        jdbcTemplate.update("""
                        UPDATE t_resource
                        SET resource_code = ?, display_name = ?, description = ?, source_type = ?, provider_id = ?, category_id = ?, access_policy = ?, update_time = NOW()
                        WHERE id = ? AND deleted = 0
                        """,
                req.getResourceCode().trim(),
                req.getDisplayName().trim(),
                req.getDescription(),
                req.getSourceType(),
                req.getProviderId(),
                req.getCategoryId(),
                accessPolicy.wireValue(),
                resourceId);
        upsertExtension(type, resourceId, req);
        syncResourceRelations(resourceId, type, req.getRelatedResourceIds());
        syncResourceTagRels(resourceId, type, req);
        upsertCurrentVersionSnapshot(resourceId, snapshotForVersion(type, resourceId, req, false));
        return getById(operatorUserId, resourceId);
    }

    @Override
    public LifecycleTimelineVO lifecycleTimeline(Long operatorUserId, Long resourceId) {
        ResourceManageVO detail = getById(operatorUserId, resourceId);
        List<LifecycleTimelineVO.Event> events = new ArrayList<>();
        List<Map<String, Object>> audits = jdbcTemplate.queryForList("""
                SELECT submit_time, review_time, status, submitter, reviewer_id, reject_reason, create_time
                FROM t_audit_item
                WHERE target_id = ?
                ORDER BY COALESCE(review_time, submit_time, create_time) ASC
                """, resourceId);
        events.add(LifecycleTimelineVO.Event.builder()
                .eventType("created")
                .title("资源创建")
                .status(ResourceLifecycleStateMachine.STATUS_DRAFT)
                .actor(detail.getCreatedByName())
                .eventTime(detail.getCreateTime())
                .build());
        for (Map<String, Object> item : audits) {
            LocalDateTime submitTime = toDateTime(item.get("submit_time"));
            if (submitTime == null) {
                submitTime = toDateTime(item.get("create_time"));
            }
            events.add(LifecycleTimelineVO.Event.builder()
                    .eventType("submitted")
                    .title("提交审核")
                    .status("pending_review")
                    .actor(stringValue(item.get("submitter")))
                    .eventTime(submitTime)
                    .build());
            String st = stringValue(item.get("status"));
            LocalDateTime reviewTime = toDateTime(item.get("review_time"));
            if (reviewTime != null && StringUtils.hasText(st) && !"pending_review".equalsIgnoreCase(st)) {
                events.add(LifecycleTimelineVO.Event.builder()
                        .eventType(st)
                        .title("审核结果: " + st)
                        .status(st)
                        .actor(stringValue(item.get("reviewer_id")))
                        .reason(stringValue(item.get("reject_reason")))
                        .eventTime(reviewTime)
                        .build());
            }
        }
        if ("published".equalsIgnoreCase(detail.getStatus())) {
            events.add(LifecycleTimelineVO.Event.builder()
                    .eventType("published")
                    .title("资源已发布")
                    .status("published")
                    .eventTime(detail.getUpdateTime())
                    .build());
        } else if ("deprecated".equalsIgnoreCase(detail.getStatus())) {
            events.add(LifecycleTimelineVO.Event.builder()
                    .eventType("deprecated")
                    .title("资源已下线")
                    .status("deprecated")
                    .eventTime(detail.getUpdateTime())
                    .build());
        }
        return LifecycleTimelineVO.builder()
                .resourceId(detail.getId())
                .resourceType(detail.getResourceType())
                .resourceCode(detail.getResourceCode())
                .displayName(detail.getDisplayName())
                .currentStatus(detail.getStatus())
                .events(events.stream().sorted((a, b) -> {
                    LocalDateTime at = a.getEventTime();
                    LocalDateTime bt = b.getEventTime();
                    if (at == null && bt == null) {
                        return 0;
                    }
                    if (at == null) {
                        return -1;
                    }
                    if (bt == null) {
                        return 1;
                    }
                    return at.compareTo(bt);
                }).toList())
                .build();
    }

    @Override
    public ObservabilitySummaryVO observabilitySummary(Long operatorUserId, String resourceType, Long resourceId) {
        ResourceManageVO detail = getById(operatorUserId, resourceId);
        String normalizedType = normalizeType(resourceType);
        if (!normalizedType.equals(detail.getResourceType())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "resource type 与 id 不匹配");
        }
        Map<String, Object> quality = computeQuality(resourceId);
        DegradationHintVO hint = buildDegradationHint(
                stringValue(quality.get("healthStatus")),
                stringValue(quality.get("circuitState")));
        return ObservabilitySummaryVO.builder()
                .resourceId(detail.getId())
                .resourceType(detail.getResourceType())
                .resourceCode(detail.getResourceCode())
                .displayName(detail.getDisplayName())
                .healthStatus(stringValue(quality.get("healthStatus")))
                .circuitState(stringValue(quality.get("circuitState")))
                .qualityScore((Integer) quality.get("qualityScore"))
                .qualityFactors((Map<String, Object>) quality.get("qualityFactors"))
                .degradationHint(hint)
                .generatedAt(LocalDateTime.now())
                .build();
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

    private static LinkedHashMap<String, Object> shallowCopyMap(Map<String, Object> src) {
        if (src == null) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(src);
    }

    /**
     * 将当前 VO 转为更新请求体，供「快照写回」与 {@link #mergeSnapshotOntoCurrent} 叠加；目录标签依赖 categoryId，关联资源显式拷贝以免被 sync 误删语义依赖 null。
     */
    private ResourceUpsertRequest voToUpsertRequest(ResourceManageVO vo) {
        ResourceUpsertRequest req = new ResourceUpsertRequest();
        req.setResourceType(vo.getResourceType());
        req.setResourceCode(vo.getResourceCode());
        req.setDisplayName(vo.getDisplayName());
        req.setDescription(vo.getDescription());
        req.setSourceType(vo.getSourceType());
        req.setProviderId(vo.getProviderId());
        req.setCategoryId(vo.getCategoryId());
        req.setAccessPolicy(vo.getAccessPolicy());
        if (vo.getRelatedResourceIds() != null) {
            req.setRelatedResourceIds(new ArrayList<>(vo.getRelatedResourceIds()));
        }
        req.setAgentType(vo.getAgentType());
        req.setMode(vo.getMode());
        req.setSpec(vo.getSpec() == null || vo.getSpec().isEmpty() ? null : shallowCopyMap(vo.getSpec()));
        req.setIsPublic(vo.getIsPublic());
        req.setHidden(vo.getHidden());
        req.setMaxConcurrency(vo.getMaxConcurrency());
        req.setMaxSteps(vo.getMaxSteps());
        req.setTemperature(vo.getTemperature());
        req.setSystemPrompt(vo.getSystemPrompt());
        req.setServiceDetailMd(vo.getServiceDetailMd());
        req.setSkillType(vo.getSkillType());
        req.setArtifactUri(vo.getArtifactUri());
        req.setArtifactSha256(vo.getArtifactSha256());
        req.setManifest(vo.getManifest() == null || vo.getManifest().isEmpty() ? null : shallowCopyMap(vo.getManifest()));
        req.setEntryDoc(vo.getEntryDoc());
        req.setSkillRootPath(vo.getSkillRootPath());
        req.setParentResourceId(vo.getParentResourceId());
        req.setDisplayTemplate(vo.getDisplayTemplate());
        req.setParametersSchema(vo.getParametersSchema() == null || vo.getParametersSchema().isEmpty()
                ? null
                : shallowCopyMap(vo.getParametersSchema()));
        req.setEndpoint(vo.getEndpoint());
        req.setProtocol(vo.getProtocol());
        req.setAuthType(vo.getAuthType());
        req.setAuthConfig(vo.getAuthConfig() == null || vo.getAuthConfig().isEmpty()
                ? null
                : shallowCopyMap(vo.getAuthConfig()));
        req.setAppUrl(vo.getAppUrl());
        req.setEmbedType(vo.getEmbedType());
        req.setIcon(vo.getIcon());
        if (vo.getScreenshots() != null) {
            req.setScreenshots(new ArrayList<>(vo.getScreenshots()));
        }
        req.setDataType(vo.getDataType());
        req.setFormat(vo.getFormat());
        req.setRecordCount(vo.getRecordCount());
        req.setFileSize(vo.getFileSize());
        if (vo.getTags() != null) {
            req.setTags(new ArrayList<>(vo.getTags()));
        }
        return req;
    }

    /**
     * 以当前主资源为基准合并版本快照；保留 {@code categoryId}、关联 ID、快照未给出的扩展字段。
     */
    private ResourceUpsertRequest mergeSnapshotOntoCurrent(ResourceManageVO current, Map<String, Object> snap) {
        ResourceUpsertRequest req = voToUpsertRequest(current);
        req.setResourceType(current.getResourceType());
        req.setCategoryId(current.getCategoryId());
        if (current.getRelatedResourceIds() != null) {
            req.setRelatedResourceIds(new ArrayList<>(current.getRelatedResourceIds()));
        }
        if (snap.containsKey("resourceCode") && StringUtils.hasText(stringValue(snap.get("resourceCode")))) {
            req.setResourceCode(stringValue(snap.get("resourceCode")).trim());
        }
        if (snap.containsKey("displayName") && StringUtils.hasText(stringValue(snap.get("displayName")))) {
            req.setDisplayName(stringValue(snap.get("displayName")).trim());
        }
        if (snap.containsKey("description")) {
            Object d = snap.get("description");
            req.setDescription(d == null ? null : String.valueOf(d));
        }
        if (snap.containsKey("accessPolicy") && snap.get("accessPolicy") != null
                && StringUtils.hasText(stringValue(snap.get("accessPolicy")))) {
            req.setAccessPolicy(ResourceAccessPolicy.parseRequestValue(stringValue(snap.get("accessPolicy"))).wireValue());
        }
        String type = normalizeType(current.getResourceType());
        switch (type) {
            case "agent" -> {
                if (snap.containsKey("spec") && snap.get("spec") != null) {
                    req.setSpec(shallowCopyMap(parseJsonMap(snap.get("spec"))));
                }
                if (StringUtils.hasText(stringValue(snap.get("endpoint")))) {
                    String ep = stringValue(snap.get("endpoint")).trim();
                    Map<String, Object> sp = req.getSpec() == null ? new LinkedHashMap<>() : shallowCopyMap(req.getSpec());
                    sp.put("url", ep);
                    req.setSpec(sp);
                }
                if (snap.containsKey("serviceDetailMd")) {
                    Object raw = snap.get("serviceDetailMd");
                    req.setServiceDetailMd(raw == null ? null : String.valueOf(raw));
                }
            }
            case "skill" -> {
                if (StringUtils.hasText(stringValue(snap.get("packFormat")))) {
                    req.setSkillType(stringValue(snap.get("packFormat")).trim().toLowerCase(Locale.ROOT));
                }
                if (StringUtils.hasText(stringValue(snap.get("endpoint")))) {
                    req.setArtifactUri(stringValue(snap.get("endpoint")).trim());
                }
                if (snap.containsKey("artifactSha256") && StringUtils.hasText(stringValue(snap.get("artifactSha256")))) {
                    req.setArtifactSha256(stringValue(snap.get("artifactSha256")).trim().toLowerCase(Locale.ROOT));
                }
                Map<String, Object> specSnap = parseJsonMap(snap.get("spec"));
                if (!specSnap.isEmpty()) {
                    if (specSnap.containsKey("manifest")) {
                        req.setManifest(shallowCopyMap(parseJsonMap(specSnap.get("manifest"))));
                    }
                    if (specSnap.containsKey("entryDoc")) {
                        req.setEntryDoc(stringValue(specSnap.get("entryDoc")));
                    }
                    if (specSnap.containsKey("skillRootPath")) {
                        req.setSkillRootPath(stringValue(specSnap.get("skillRootPath")));
                    }
                    if (specSnap.containsKey("extra")) {
                        req.setSpec(shallowCopyMap(parseJsonMap(specSnap.get("extra"))));
                    }
                }
                if (snap.containsKey("serviceDetailMd")) {
                    Object raw = snap.get("serviceDetailMd");
                    req.setServiceDetailMd(raw == null ? null : String.valueOf(raw));
                }
            }
            case "mcp" -> {
                LinkedHashMap<String, Object> specSnap = shallowCopyMap(parseJsonMap(snap.get("spec")));
                String at = stringValue(specSnap.remove(McpOutboundHeaderBuilder.REGISTRY_AUTH_TYPE_KEY));
                if (StringUtils.hasText(at)) {
                    req.setAuthType(at.trim().toLowerCase(Locale.ROOT));
                }
                req.setAuthConfig(specSnap);
                if (StringUtils.hasText(stringValue(snap.get("endpoint")))) {
                    req.setEndpoint(stringValue(snap.get("endpoint")).trim());
                }
                if (StringUtils.hasText(stringValue(snap.get("invokeType")))) {
                    req.setProtocol(stringValue(snap.get("invokeType")).trim().toLowerCase(Locale.ROOT));
                }
                if (snap.containsKey("serviceDetailMd")) {
                    Object raw = snap.get("serviceDetailMd");
                    req.setServiceDetailMd(raw == null ? null : String.valueOf(raw));
                }
            }
            case "app" -> {
                if (StringUtils.hasText(stringValue(snap.get("endpoint")))) {
                    req.setAppUrl(stringValue(snap.get("endpoint")).trim());
                }
                Map<String, Object> appSpec = parseJsonMap(snap.get("spec"));
                if (!appSpec.isEmpty()) {
                    if (appSpec.containsKey("embedType") && StringUtils.hasText(stringValue(appSpec.get("embedType")))) {
                        req.setEmbedType(stringValue(appSpec.get("embedType")).trim().toLowerCase(Locale.ROOT));
                    }
                    if (appSpec.containsKey("icon")) {
                        req.setIcon(stringValue(appSpec.get("icon")));
                    }
                    if (appSpec.containsKey("screenshots")) {
                        req.setScreenshots(toStringListFromJsonColumn(appSpec.get("screenshots")));
                    }
                }
                if (snap.containsKey("serviceDetailMd")) {
                    Object raw = snap.get("serviceDetailMd");
                    req.setServiceDetailMd(raw == null ? null : String.valueOf(raw));
                }
            }
            case "dataset" -> {
                Map<String, Object> dspec = parseJsonMap(snap.get("spec"));
                if (!dspec.isEmpty()) {
                    if (dspec.containsKey("dataType") && StringUtils.hasText(stringValue(dspec.get("dataType")))) {
                        req.setDataType(stringValue(dspec.get("dataType")).trim());
                    }
                    if (dspec.containsKey("format") && StringUtils.hasText(stringValue(dspec.get("format")))) {
                        req.setFormat(stringValue(dspec.get("format")).trim());
                    }
                    if (dspec.containsKey("recordCount")) {
                        req.setRecordCount(longObject(dspec.get("recordCount")));
                    }
                    if (dspec.containsKey("fileSize")) {
                        req.setFileSize(longObject(dspec.get("fileSize")));
                    }
                    if (dspec.containsKey("tags")) {
                        req.setTags(parseJsonList(dspec.get("tags")).stream().map(String::valueOf).toList());
                    }
                }
                if (snap.containsKey("serviceDetailMd")) {
                    Object raw = snap.get("serviceDetailMd");
                    req.setServiceDetailMd(raw == null ? null : String.valueOf(raw));
                }
            }
            default -> {
            }
        }
        if ("dataset".equals(type) && !snap.containsKey("spec")) {
            req.setTags(current.getTags() == null ? null : new ArrayList<>(current.getTags()));
        }
        return req;
    }

    private ResourceManageVO findResource(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, resource_type, resource_code, display_name, description, status, source_type, provider_id, category_id, access_policy, created_by, create_time, update_time
                FROM t_resource
                WHERE id = ? AND deleted = 0
                LIMIT 1
                """, id);
        if (rows.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "资源不存在");
        }
        ResourceManageVO vo = toManageVo(rows.get(0));
        enrichCreatedByName(vo);
        enrichExtensionFields(vo, id);
        enrichCurrentVersionLabel(vo, id);
        enrichCatalogTagNames(vo, id);
        enrichLifecycleContext(vo, null);
        enrichObservabilityFields(vo);
        return vo;
    }

    private void enrichLifecycleContext(ResourceManageVO vo, Long viewerUserId) {
        if (vo == null || vo.getId() == null) {
            return;
        }
        List<Map<String, Object>> audits = jdbcTemplate.queryForList("""
                SELECT id, status, reject_reason, reviewer_id, submit_time, review_time
                FROM t_audit_item
                WHERE target_id = ?
                ORDER BY id DESC
                LIMIT 1
                """, vo.getId());
        if (!audits.isEmpty()) {
            Map<String, Object> a = audits.get(0);
            vo.setPendingAuditItemId(longValue(a.get("id")));
            vo.setLastAuditStatus(stringValue(a.get("status")));
            vo.setLastRejectReason(stringValue(a.get("reject_reason")));
            vo.setLastReviewerId(longValue(a.get("reviewer_id")));
            vo.setLastSubmitTime(toDateTime(a.get("submit_time")));
            vo.setLastReviewTime(toDateTime(a.get("review_time")));
        }
        vo.setAllowedActions(suggestActions(vo.getStatus(), vo.getCreatedBy(), viewerUserId));
        vo.setStatusHint(statusHint(vo.getStatus(), vo.getLastRejectReason()));
    }

    private static List<String> suggestActions(String status, Long ownerId, Long viewerUserId) {
        boolean owner = ownerId != null && viewerUserId != null && ownerId.equals(viewerUserId);
        String s = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
        List<String> actions = new ArrayList<>();
        switch (s) {
            case "draft" -> {
                actions.add("update");
                actions.add("submit");
                actions.add("delete");
                actions.add("createVersion");
            }
            case "pending_review" -> actions.add("withdraw");
            case "testing" -> {
                actions.add("withdraw");
                actions.add("deprecate");
            }
            case "published" -> {
                actions.add("createVersion");
                actions.add("switchVersion");
                actions.add("deprecate");
            }
            case "rejected" -> {
                actions.add("update");
                actions.add("submit");
            }
            case "deprecated" -> {
                actions.add("update");
                actions.add("submit");
            }
            default -> {
            }
        }
        if (!owner) {
            actions.remove("delete");
        }
        return actions;
    }

    private static String statusHint(String status, String rejectReason) {
        String s = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "draft" -> "草稿态，可编辑后重新提审";
            case "pending_review" -> "审核进行中，可撤回到草稿";
            case "testing" -> "测试阶段，建议观察稳定性后发布";
            case "published" -> "线上可用，建议持续关注质量指标";
            case "rejected" -> StringUtils.hasText(rejectReason) ? "已驳回：" + rejectReason : "已驳回，请修改后重提";
            case "deprecated" -> "已下线，可修复后重新提审";
            default -> "状态待确认";
        };
    }

    private void enrichObservabilityFields(ResourceManageVO vo) {
        if (vo == null || vo.getId() == null) {
            return;
        }
        Map<String, Object> quality = computeQuality(vo.getId());
        vo.setHealthStatus(stringValue(quality.get("healthStatus")));
        vo.setCircuitState(stringValue(quality.get("circuitState")));
        vo.setQualityScore(intObject(quality.get("qualityScore")));
        Object qf = quality.get("qualityFactors");
        if (qf instanceof Map<?, ?> rawFactors) {
            Map<String, Object> factors = new LinkedHashMap<>();
            for (Map.Entry<?, ?> ent : rawFactors.entrySet()) {
                if (ent.getKey() != null) {
                    factors.put(String.valueOf(ent.getKey()), ent.getValue());
                }
            }
            vo.setQualityFactors(factors);
        }
        DegradationHintVO hint = buildDegradationHint(vo.getHealthStatus(), vo.getCircuitState());
        if (hint != null) {
            vo.setDegradationCode(hint.getDegradationCode());
            vo.setDegradationHint(hint.getUserFacingHint());
        }
    }

    private Map<String, Object> computeQuality(Long resourceId) {
        List<Map<String, Object>> callRows = jdbcTemplate.queryForList("""
                SELECT
                    COUNT(1) AS totalCalls,
                    SUM(CASE WHEN status = 'success' THEN 1 ELSE 0 END) AS successCalls,
                    ROUND(AVG(latency_ms), 2) AS avgLatencyMs
                FROM t_call_log
                WHERE agent_id = ?
                AND create_time >= DATE_SUB(NOW(), INTERVAL 7 DAY)
                """, String.valueOf(resourceId));
        long totalCalls = 0L;
        long successCalls = 0L;
        double avgLatencyMs = 0D;
        if (!callRows.isEmpty()) {
            Map<String, Object> c = callRows.get(0);
            totalCalls = longValue(c.get("totalCalls")) == null ? 0L : longValue(c.get("totalCalls"));
            successCalls = longValue(c.get("successCalls")) == null ? 0L : longValue(c.get("successCalls"));
            avgLatencyMs = doubleObject(c.get("avgLatencyMs")) == null ? 0D : doubleObject(c.get("avgLatencyMs"));
        }
        double successRate = totalCalls <= 0 ? 1D : ((double) successCalls / (double) totalCalls);
        double latencyFactor = Math.max(0D, 1D - (avgLatencyMs / 8000D));
        int qualityScore = (int) Math.round(successRate * 70D + latencyFactor * 30D);
        qualityScore = Math.max(0, Math.min(100, qualityScore));

        List<Map<String, Object>> hc = jdbcTemplate.queryForList(
                "SELECT health_status FROM t_resource_health_config WHERE resource_id = ? LIMIT 1",
                resourceId);
        String health = hc.isEmpty() ? "unknown" : stringValue(hc.get(0).get("health_status"));
        List<Map<String, Object>> cb = jdbcTemplate.queryForList(
                "SELECT current_state FROM t_resource_circuit_breaker WHERE resource_id = ? LIMIT 1",
                resourceId);
        String circuitState = cb.isEmpty() ? "unknown" : stringValue(cb.get(0).get("current_state"));

        Map<String, Object> factors = new LinkedHashMap<>();
        factors.put("successRate", successRate);
        factors.put("avgLatencyMs", avgLatencyMs);
        factors.put("totalCalls7d", totalCalls);
        factors.put("latencyFactor", latencyFactor);
        Map<String, Object> qualityBlock = new LinkedHashMap<>();
        qualityBlock.put("healthStatus", health != null ? health : "unknown");
        qualityBlock.put("circuitState", circuitState != null ? circuitState : "unknown");
        qualityBlock.put("qualityScore", qualityScore);
        qualityBlock.put("qualityFactors", factors);
        return qualityBlock;
    }

    private static DegradationHintVO buildDegradationHint(String healthStatus, String circuitState) {
        String h = healthStatus == null ? "" : healthStatus.trim().toLowerCase(Locale.ROOT);
        String c = circuitState == null ? "" : circuitState.trim().toLowerCase(Locale.ROOT);
        if ("open".equals(c)) {
            return DegradationHintVO.builder()
                    .degradationCode("CIRCUIT_OPEN")
                    .userFacingHint("当前资源暂时不可用，请稍后重试")
                    .opsHint("检查上游错误率、恢复后可尝试半开探测")
                    .build();
        }
        if ("degraded".equals(h)) {
            return DegradationHintVO.builder()
                    .degradationCode("HEALTH_DEGRADED")
                    .userFacingHint("当前资源响应不稳定，建议稍后再试")
                    .opsHint("检查健康检查地址、超时阈值与依赖状态")
                    .build();
        }
        return null;
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
        String serviceMd = resolveExtServiceDetailMd("t_resource_agent_ext", resourceId, request.getServiceDetailMd());
        int updated = jdbcTemplate.update("""
                        UPDATE t_resource_agent_ext
                        SET agent_type = ?, mode = ?, spec_json = CAST(? AS JSON), is_public = ?, hidden = ?, max_concurrency = ?, max_steps = ?, temperature = ?, system_prompt = ?, service_detail_md = ?
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
                serviceMd,
                resourceId);
        if (updated == 0) {
            jdbcTemplate.update("""
                            INSERT INTO t_resource_agent_ext(resource_id, agent_type, mode, spec_json, is_public, hidden, max_concurrency, max_steps, temperature, system_prompt, service_detail_md, featured, rating_avg, rating_count)
                            VALUES(?, ?, ?, CAST(? AS JSON), ?, ?, ?, ?, ?, ?, ?, 0, 0.00, 0)
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
                    request.getSystemPrompt(),
                    serviceMd);
        }
    }

    private void upsertSkillExt(Long resourceId, ResourceUpsertRequest request) {
        List<Map<String, Object>> existingRows = jdbcTemplate.queryForList("""
                        SELECT artifact_uri, artifact_sha256, pack_validation_status, pack_validated_at, pack_validation_message, skill_root_path
                        FROM t_resource_skill_ext WHERE resource_id = ? LIMIT 1
                        """,
                resourceId);

        String newUri = StringUtils.hasText(request.getArtifactUri()) ? request.getArtifactUri().trim() : null;
        String newSha = StringUtils.hasText(request.getArtifactSha256()) ? request.getArtifactSha256().trim().toLowerCase(Locale.ROOT) : null;

        boolean artifactChanged = true;
        String packStatus = SkillPackValidationStatus.NONE;
        Timestamp packValidatedAt = null;
        String packValidationMessage = null;
        String existingSkillRoot = null;
        if (!existingRows.isEmpty()) {
            Map<String, Object> er = existingRows.get(0);
            String oldUri = stringValue(er.get("artifact_uri"));
            String oldSha = stringValue(er.get("artifact_sha256"));
            String oldShaNorm = StringUtils.hasText(oldSha) ? oldSha.trim().toLowerCase(Locale.ROOT) : null;
            artifactChanged = !Objects.equals(StringUtils.hasText(oldUri) ? oldUri.trim() : null, newUri)
                    || !Objects.equals(oldShaNorm, newSha);
            if (!artifactChanged) {
                String ps = stringValue(er.get("pack_validation_status"));
                packStatus = StringUtils.hasText(ps) ? ps.trim().toLowerCase(Locale.ROOT) : SkillPackValidationStatus.NONE;
                packValidatedAt = toSqlTimestamp(er.get("pack_validated_at"));
                packValidationMessage = stringValue(er.get("pack_validation_message"));
            }
            String erRoot = stringValue(er.get("skill_root_path"));
            existingSkillRoot = StringUtils.hasText(erRoot) ? erRoot.trim() : null;
        }

        String skillRootPathVal = existingSkillRoot;
        if (request.getSkillRootPath() != null) {
            skillRootPathVal = SkillPackSkillRootPath.normalizeOrNull(request.getSkillRootPath());
        } else if (artifactChanged && !existingRows.isEmpty()) {
            skillRootPathVal = null;
        }

        String manifestJson = writeJson(defaultMap(request.getManifest()));
        String serviceMd = resolveExtServiceDetailMd("t_resource_skill_ext", resourceId, request.getServiceDetailMd());
        int updated = jdbcTemplate.update("""
                        UPDATE t_resource_skill_ext
                        SET skill_type = ?, artifact_uri = ?, artifact_sha256 = ?, manifest_json = CAST(? AS JSON), entry_doc = ?,
                            mode = ?, parent_resource_id = ?, display_template = ?, spec_json = CAST(? AS JSON), parameters_schema = CAST(? AS JSON), is_public = ?, max_concurrency = ?,
                            pack_validation_status = ?, pack_validated_at = ?, pack_validation_message = ?, skill_root_path = ?, service_detail_md = ?
                        WHERE resource_id = ?
                        """,
                request.getSkillType().trim().toLowerCase(Locale.ROOT),
                newUri,
                newSha,
                manifestJson,
                request.getEntryDoc(),
                defaultString(request.getMode(), "TOOL"),
                request.getParentResourceId(),
                request.getDisplayTemplate(),
                writeJson(defaultMap(request.getSpec())),
                writeJson(defaultMap(request.getParametersSchema())),
                toBoolNumber(request.getIsPublic()),
                request.getMaxConcurrency() == null ? 10 : request.getMaxConcurrency(),
                packStatus,
                packValidatedAt,
                packValidationMessage,
                skillRootPathVal,
                serviceMd,
                resourceId);
        if (updated == 0) {
            jdbcTemplate.update("""
                            INSERT INTO t_resource_skill_ext(resource_id, skill_type, artifact_uri, artifact_sha256, manifest_json, entry_doc, mode, parent_resource_id, display_template, spec_json, parameters_schema, is_public, max_concurrency, pack_validation_status, pack_validated_at, pack_validation_message, skill_root_path, service_detail_md)
                            VALUES(?, ?, ?, ?, CAST(? AS JSON), ?, ?, ?, ?, CAST(? AS JSON), CAST(? AS JSON), ?, ?, ?, ?, ?, ?, ?)
                            """,
                    resourceId,
                    request.getSkillType().trim().toLowerCase(Locale.ROOT),
                    newUri,
                    newSha,
                    manifestJson,
                    request.getEntryDoc(),
                    defaultString(request.getMode(), "TOOL"),
                    request.getParentResourceId(),
                    request.getDisplayTemplate(),
                    writeJson(defaultMap(request.getSpec())),
                    writeJson(defaultMap(request.getParametersSchema())),
                    toBoolNumber(request.getIsPublic()),
                    request.getMaxConcurrency() == null ? 10 : request.getMaxConcurrency(),
                    SkillPackValidationStatus.NONE,
                    null,
                    null,
                    SkillPackSkillRootPath.normalizeOrNull(request.getSkillRootPath()),
                    serviceMd);
        }
    }

    private void upsertMcpExt(Long resourceId, ResourceUpsertRequest request) {
        String protocol = defaultString(request.getProtocol(), "mcp").toLowerCase(Locale.ROOT);
        if (!protocolInvokerRegistry.isSupported(protocol)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "MCP 协议不可调用: " + protocol);
        }
        String serviceMd = resolveExtServiceDetailMd("t_resource_mcp_ext", resourceId, request.getServiceDetailMd());
        int updated = jdbcTemplate.update("""
                        UPDATE t_resource_mcp_ext
                        SET endpoint = ?, protocol = ?, auth_type = ?, auth_config = CAST(? AS JSON), service_detail_md = ?
                        WHERE resource_id = ?
                        """,
                request.getEndpoint(),
                protocol,
                defaultString(request.getAuthType(), "none"),
                writeJson(defaultMap(request.getAuthConfig())),
                serviceMd,
                resourceId);
        if (updated == 0) {
            jdbcTemplate.update("""
                            INSERT INTO t_resource_mcp_ext(resource_id, endpoint, protocol, auth_type, auth_config, service_detail_md)
                            VALUES(?, ?, ?, ?, CAST(? AS JSON), ?)
                            """,
                    resourceId,
                    request.getEndpoint(),
                    protocol,
                    defaultString(request.getAuthType(), "none"),
                    writeJson(defaultMap(request.getAuthConfig())),
                    serviceMd);
        }
    }

    /**
     * incoming 非 null：按提交值更新（空串视为清空）；incoming 为 null：保留原行（兼容未传字段的客户端）。
     *
     * @param extTable 须为 {@link #SERVICE_DETAIL_EXT_TABLES} 之一
     */
    private String resolveExtServiceDetailMd(String extTable, Long resourceId, String incoming) {
        if (!SERVICE_DETAIL_EXT_TABLES.contains(extTable)) {
            throw new IllegalArgumentException("invalid ext table: " + extTable);
        }
        if (incoming != null) {
            String t = incoming.trim();
            if (t.isEmpty()) {
                return null;
            }
            if (t.length() > 200_000) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "服务详情 Markdown 过长（上限约 200KB）");
            }
            return t;
        }
        var prev = jdbcTemplate.queryForList(
                "SELECT service_detail_md FROM " + extTable + " WHERE resource_id = ? LIMIT 1", resourceId);
        if (prev.isEmpty()) {
            return null;
        }
        Object v = prev.get(0).get("service_detail_md");
        return v == null ? null : String.valueOf(v);
    }

    private void upsertAppExt(Long resourceId, ResourceUpsertRequest request) {
        String serviceMd = resolveExtServiceDetailMd("t_resource_app_ext", resourceId, request.getServiceDetailMd());
        int updated = jdbcTemplate.update("""
                        UPDATE t_resource_app_ext
                        SET app_url = ?, embed_type = ?, icon = ?, screenshots = CAST(? AS JSON), is_public = ?, service_detail_md = ?
                        WHERE resource_id = ?
                        """,
                request.getAppUrl(),
                request.getEmbedType(),
                request.getIcon(),
                writeJson(defaultList(request.getScreenshots())),
                toBoolNumber(request.getIsPublic()),
                serviceMd,
                resourceId);
        if (updated == 0) {
            jdbcTemplate.update("""
                            INSERT INTO t_resource_app_ext(resource_id, app_url, embed_type, icon, screenshots, is_public, service_detail_md)
                            VALUES(?, ?, ?, ?, CAST(? AS JSON), ?, ?)
                            """,
                    resourceId,
                    request.getAppUrl(),
                    request.getEmbedType(),
                    request.getIcon(),
                    writeJson(defaultList(request.getScreenshots())),
                    toBoolNumber(request.getIsPublic()),
                    serviceMd);
        }
    }

    private void upsertDatasetExt(Long resourceId, ResourceUpsertRequest request) {
        String serviceMd = resolveExtServiceDetailMd("t_resource_dataset_ext", resourceId, request.getServiceDetailMd());
        int updated = jdbcTemplate.update("""
                        UPDATE t_resource_dataset_ext
                        SET data_type = ?, format = ?, record_count = ?, file_size = ?, tags = CAST(? AS JSON), is_public = ?, service_detail_md = ?
                        WHERE resource_id = ?
                        """,
                request.getDataType(),
                request.getFormat(),
                request.getRecordCount() == null ? 0L : request.getRecordCount(),
                request.getFileSize() == null ? 0L : request.getFileSize(),
                writeJson(defaultList(request.getTags())),
                toBoolNumber(request.getIsPublic()),
                serviceMd,
                resourceId);
        if (updated == 0) {
            jdbcTemplate.update("""
                            INSERT INTO t_resource_dataset_ext(resource_id, data_type, format, record_count, file_size, tags, is_public, service_detail_md)
                            VALUES(?, ?, ?, ?, ?, CAST(? AS JSON), ?, ?)
                            """,
                    resourceId,
                    request.getDataType(),
                    request.getFormat(),
                    request.getRecordCount() == null ? 0L : request.getRecordCount(),
                    request.getFileSize() == null ? 0L : request.getFileSize(),
                    writeJson(defaultList(request.getTags())),
                    toBoolNumber(request.getIsPublic()),
                    serviceMd);
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
                requireText(request.getSkillType(), "skillType（技能包格式）不能为空");
                String packFmt = request.getSkillType().trim().toLowerCase(Locale.ROOT);
                if (FORBIDDEN_SKILL_PACK_TYPES.contains(packFmt)) {
                    throw new BusinessException(ResultCode.PARAM_ERROR,
                            "skillType 不可为 mcp/http_api；可远程调用的工具请使用 resourceType=mcp 注册");
                }
                if (!SKILL_PACK_FORMATS.contains(packFmt)) {
                    throw new BusinessException(ResultCode.PARAM_ERROR,
                            "skillType 必须是 anthropic_v1 或 folder_v1");
                }
                if (StringUtils.hasText(request.getArtifactUri())) {
                    request.setArtifactUri(request.getArtifactUri().trim());
                } else {
                    request.setArtifactUri(null);
                }
                if (StringUtils.hasText(request.getArtifactSha256())) {
                    request.setArtifactSha256(request.getArtifactSha256().trim().toLowerCase(Locale.ROOT));
                } else {
                    request.setArtifactSha256(null);
                }
                if (StringUtils.hasText(request.getEntryDoc())) {
                    request.setEntryDoc(request.getEntryDoc().trim());
                }
            }
            case "mcp" -> {
                Map<String, Object> ac = request.getAuthConfig() == null ? Map.of() : request.getAuthConfig();
                String transport = stringValue(ac.get("transport"));
                if ("stdio".equalsIgnoreCase(transport)) {
                    requireText(request.getEndpoint(), "stdio MCP 须将本机边车 HTTP(S) 地址填入 endpoint");
                    if (!isHttpOrHttpsUrl(request.getEndpoint().trim())) {
                        throw new BusinessException(ResultCode.PARAM_ERROR, "stdio 边车 endpoint 须为 http(s) URL");
                    }
                } else {
                    requireText(request.getEndpoint(), "endpoint 不能为空");
                }
                String protocol = defaultString(request.getProtocol(), "mcp");
                if (!protocolInvokerRegistry.isSupported(protocol)) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "MCP 协议不可调用: " + protocol);
                }
                String at = defaultString(request.getAuthType(), "none");
                if ("oauth2_client".equalsIgnoreCase(at)) {
                    requireText(stringValue(ac.get("tokenUrl")), "oauth2_client 需要 auth_config.tokenUrl");
                    requireText(stringValue(ac.get("clientId")), "oauth2_client 需要 auth_config.clientId");
                    boolean hasSecret = StringUtils.hasText(stringValue(ac.get("clientSecret")))
                            || StringUtils.hasText(stringValue(ac.get("clientSecretRef")));
                    if (!hasSecret) {
                        throw new BusinessException(ResultCode.PARAM_ERROR,
                                "oauth2_client 需要 auth_config.clientSecret 或 clientSecretRef");
                    }
                }
            }
            case "app" -> {
                requireText(request.getAppUrl(), "appUrl 不能为空");
                String appUrl = request.getAppUrl().trim();
                if (!isHttpOrHttpsUrl(appUrl)) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "appUrl 须为 http:// 或 https:// URL");
                }
                requireText(request.getEmbedType(), "embedType 不能为空");
                String et = request.getEmbedType().trim().toLowerCase(Locale.ROOT);
                if (!APP_EMBED_TYPES.contains(et)) {
                    throw new BusinessException(ResultCode.PARAM_ERROR,
                            "embedType 必须是 iframe、redirect 或 micro_frontend 之一");
                }
                request.setAppUrl(appUrl);
                request.setEmbedType(et);
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
                "platform_admin".equals(code) || "admin".equals(code) || "reviewer".equals(code));
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

    private static boolean isHttpOrHttpsUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        String u = url.trim().toLowerCase(Locale.ROOT);
        return u.startsWith("http://") || u.startsWith("https://");
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

    private Map<String, Object> buildSnapshot(String type, ResourceUpsertRequest request, ResourceAccessPolicy accessPolicy) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("resourceType", type);
        snapshot.put("resourceCode", request.getResourceCode());
        snapshot.put("displayName", request.getDisplayName());
        snapshot.put("description", request.getDescription());
        snapshot.put("status", ResourceLifecycleStateMachine.STATUS_DRAFT);
        snapshot.put("accessPolicy", accessPolicy.wireValue());
        switch (type) {
            case "agent" -> {
                snapshot.put("invokeType", "rest");
                snapshot.put("endpoint", request.getSpec() == null ? null : request.getSpec().get("url"));
                snapshot.put("spec", defaultMap(request.getSpec()));
                if (StringUtils.hasText(request.getServiceDetailMd())) {
                    snapshot.put("serviceDetailMd", request.getServiceDetailMd().trim());
                }
            }
            case "skill" -> {
                snapshot.put("packFormat", request.getSkillType().trim().toLowerCase(Locale.ROOT));
                snapshot.put("invokeType", "artifact");
                snapshot.put("endpoint", request.getArtifactUri());
                if (StringUtils.hasText(request.getArtifactSha256())) {
                    snapshot.put("artifactSha256", request.getArtifactSha256());
                }
                Map<String, Object> spec = new LinkedHashMap<>();
                if (request.getManifest() != null && !request.getManifest().isEmpty()) {
                    spec.put("manifest", request.getManifest());
                }
                if (StringUtils.hasText(request.getEntryDoc())) {
                    spec.put("entryDoc", request.getEntryDoc());
                }
                if (StringUtils.hasText(request.getSkillRootPath())) {
                    String root = SkillPackSkillRootPath.normalizeOrNull(request.getSkillRootPath());
                    if (root != null) {
                        spec.put("skillRootPath", root);
                    }
                }
                if (request.getSpec() != null && !request.getSpec().isEmpty()) {
                    spec.put("extra", request.getSpec());
                }
                snapshot.put("spec", spec);
                if (StringUtils.hasText(request.getServiceDetailMd())) {
                    snapshot.put("serviceDetailMd", request.getServiceDetailMd().trim());
                }
            }
            case "mcp" -> {
                snapshot.put("invokeType", defaultString(request.getProtocol(), "mcp").toLowerCase(Locale.ROOT));
                snapshot.put("endpoint", request.getEndpoint());
                snapshot.put("spec", defaultMap(request.getAuthConfig()));
                if (StringUtils.hasText(request.getServiceDetailMd())) {
                    snapshot.put("serviceDetailMd", request.getServiceDetailMd().trim());
                }
            }
            case "app" -> {
                snapshot.put("invokeType", "redirect");
                snapshot.put("endpoint", request.getAppUrl());
                Map<String, Object> appSpec = new LinkedHashMap<>();
                appSpec.put("embedType", request.getEmbedType());
                if (StringUtils.hasText(request.getIcon())) {
                    appSpec.put("icon", request.getIcon().trim());
                }
                appSpec.put("screenshots", defaultList(request.getScreenshots()));
                snapshot.put("spec", appSpec);
                if (StringUtils.hasText(request.getServiceDetailMd())) {
                    snapshot.put("serviceDetailMd", request.getServiceDetailMd().trim());
                }
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
                if (StringUtils.hasText(request.getServiceDetailMd())) {
                    snapshot.put("serviceDetailMd", request.getServiceDetailMd().trim());
                }
            }
            default -> {
            }
        }
        return snapshot;
    }

    private Map<String, Object> buildSnapshotFromDb(String type, Long resourceId) {
        Map<String, Object> base = jdbcTemplate.queryForList("""
                SELECT resource_code, display_name, description, status, access_policy
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
        snapshot.put("accessPolicy", ResourceAccessPolicy.fromStored(base.get("access_policy")).wireValue());
        switch (type) {
            case "agent" -> {
                Map<String, Object> ext = jdbcTemplate.queryForList(
                                "SELECT spec_json, service_detail_md FROM t_resource_agent_ext WHERE resource_id = ? LIMIT 1", resourceId)
                        .stream().findFirst().orElse(Map.of());
                Map<String, Object> spec = parseJsonMap(ext.get("spec_json"));
                snapshot.put("invokeType", "rest");
                snapshot.put("endpoint", spec.get("url"));
                snapshot.put("spec", spec);
                if (StringUtils.hasText(stringValue(ext.get("service_detail_md")))) {
                    snapshot.put("serviceDetailMd", stringValue(ext.get("service_detail_md")).trim());
                }
            }
            case "skill" -> {
                Map<String, Object> ext = jdbcTemplate.queryForList("""
                                SELECT skill_type, artifact_uri, artifact_sha256, manifest_json, entry_doc, spec_json, pack_validation_status, skill_root_path, service_detail_md
                                FROM t_resource_skill_ext WHERE resource_id = ? LIMIT 1
                                """, resourceId)
                        .stream().findFirst().orElse(Map.of());
                String packFmt = stringValue(ext.get("skill_type"));
                snapshot.put("packFormat", packFmt);
                snapshot.put("invokeType", "artifact");
                snapshot.put("endpoint", stringValue(ext.get("artifact_uri")));
                if (StringUtils.hasText(stringValue(ext.get("artifact_sha256")))) {
                    snapshot.put("artifactSha256", stringValue(ext.get("artifact_sha256")).trim().toLowerCase(Locale.ROOT));
                }
                Map<String, Object> spec = new LinkedHashMap<>();
                Map<String, Object> manifest = parseJsonMap(ext.get("manifest_json"));
                if (!manifest.isEmpty()) {
                    spec.put("manifest", manifest);
                }
                if (StringUtils.hasText(stringValue(ext.get("entry_doc")))) {
                    spec.put("entryDoc", stringValue(ext.get("entry_doc")));
                }
                if (StringUtils.hasText(stringValue(ext.get("pack_validation_status")))) {
                    spec.put("packValidationStatus", stringValue(ext.get("pack_validation_status")).trim().toLowerCase(Locale.ROOT));
                }
                if (StringUtils.hasText(stringValue(ext.get("skill_root_path")))) {
                    spec.put("skillRootPath", stringValue(ext.get("skill_root_path")).trim());
                }
                Map<String, Object> extra = parseJsonMap(ext.get("spec_json"));
                if (!extra.isEmpty()) {
                    spec.put("extra", extra);
                }
                snapshot.put("spec", spec);
                if (StringUtils.hasText(stringValue(ext.get("service_detail_md")))) {
                    snapshot.put("serviceDetailMd", stringValue(ext.get("service_detail_md")).trim());
                }
            }
            case "mcp" -> {
                Map<String, Object> ext = jdbcTemplate.queryForList(
                                "SELECT endpoint, protocol, auth_type, auth_config, service_detail_md FROM t_resource_mcp_ext WHERE resource_id = ? LIMIT 1", resourceId)
                        .stream().findFirst().orElse(Map.of());
                snapshot.put("invokeType", stringValue(ext.get("protocol")));
                snapshot.put("endpoint", stringValue(ext.get("endpoint")));
                Map<String, Object> spec = parseJsonMap(ext.get("auth_config"));
                spec = spec == null ? new LinkedHashMap<>() : new LinkedHashMap<>(spec);
                String at = stringValue(ext.get("auth_type"));
                if (StringUtils.hasText(at)) {
                    spec.put(McpOutboundHeaderBuilder.REGISTRY_AUTH_TYPE_KEY, at.trim().toLowerCase(Locale.ROOT));
                }
                snapshot.put("spec", spec);
                if (StringUtils.hasText(stringValue(ext.get("service_detail_md")))) {
                    snapshot.put("serviceDetailMd", stringValue(ext.get("service_detail_md")).trim());
                }
            }
            case "app" -> {
                Map<String, Object> ext = jdbcTemplate.queryForList(
                                "SELECT app_url, embed_type, icon, screenshots, service_detail_md FROM t_resource_app_ext WHERE resource_id = ? LIMIT 1", resourceId)
                        .stream().findFirst().orElse(Map.of());
                snapshot.put("invokeType", "redirect");
                snapshot.put("endpoint", stringValue(ext.get("app_url")));
                Map<String, Object> appSpec = new LinkedHashMap<>();
                appSpec.put("embedType", stringValue(ext.get("embed_type")));
                if (StringUtils.hasText(stringValue(ext.get("icon")))) {
                    appSpec.put("icon", stringValue(ext.get("icon")));
                }
                appSpec.put("screenshots", toStringListFromJsonColumn(ext.get("screenshots")));
                snapshot.put("spec", appSpec);
                if (StringUtils.hasText(stringValue(ext.get("service_detail_md")))) {
                    snapshot.put("serviceDetailMd", stringValue(ext.get("service_detail_md")).trim());
                }
            }
            case "dataset" -> {
                Map<String, Object> ext = jdbcTemplate.queryForList(
                                "SELECT data_type, format, record_count, file_size, tags, service_detail_md FROM t_resource_dataset_ext WHERE resource_id = ? LIMIT 1", resourceId)
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
                if (StringUtils.hasText(stringValue(ext.get("service_detail_md")))) {
                    snapshot.put("serviceDetailMd", stringValue(ext.get("service_detail_md")).trim());
                }
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

    private void enrichExtensionFields(ResourceManageVO vo, Long resourceId) {
        if (vo == null || resourceId == null) {
            return;
        }
        String type = vo.getResourceType() == null ? null : vo.getResourceType().trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(type)) {
            return;
        }
        switch (type) {
            case "agent" -> enrichAgentFields(vo, resourceId);
            case "skill" -> enrichSkillFields(vo, resourceId);
            case "mcp" -> enrichMcpFields(vo, resourceId);
            case "app" -> enrichAppFields(vo, resourceId);
            case "dataset" -> enrichDatasetFields(vo, resourceId);
            default -> {
            }
        }
        List<Long> rel = loadRelatedResourceIds(resourceId, type);
        if (!rel.isEmpty()) {
            vo.setRelatedResourceIds(rel);
        }
    }

    private List<Long> loadRelatedResourceIds(Long resourceId, String resourceType) {
        String relationType = switch (resourceType) {
            case "agent" -> "agent_depends_skill";
            case "app" -> "app_depends_resource";
            default -> null;
        };
        if (relationType == null) {
            return List.of();
        }
        return jdbcTemplate.query(
                "SELECT to_resource_id FROM t_resource_relation WHERE from_resource_id = ? AND relation_type = ? ORDER BY to_resource_id",
                (rs, rowNum) -> rs.getLong("to_resource_id"),
                resourceId,
                relationType);
    }

    private void enrichAgentFields(ResourceManageVO vo, Long resourceId) {
        var rows = jdbcTemplate.queryForList("""
                        SELECT agent_type, mode, spec_json, is_public, hidden, max_concurrency, max_steps, temperature, system_prompt, service_detail_md
                        FROM t_resource_agent_ext WHERE resource_id = ? LIMIT 1
                        """,
                resourceId);
        if (rows.isEmpty()) {
            return;
        }
        Map<String, Object> r = rows.get(0);
        vo.setAgentType(stringValue(r.get("agent_type")));
        vo.setMode(stringValue(r.get("mode")));
        vo.setSpec(parseJsonMap(r.get("spec_json")));
        vo.setIsPublic(boolObject(r.get("is_public")));
        vo.setHidden(boolObject(r.get("hidden")));
        vo.setMaxConcurrency(intObject(r.get("max_concurrency")));
        vo.setMaxSteps(intObject(r.get("max_steps")));
        vo.setTemperature(doubleObject(r.get("temperature")));
        vo.setSystemPrompt(stringValue(r.get("system_prompt")));
        vo.setServiceDetailMd(stringValue(r.get("service_detail_md")));
    }

    private void enrichSkillFields(ResourceManageVO vo, Long resourceId) {
        var rows = jdbcTemplate.queryForList("""
                        SELECT skill_type, artifact_uri, artifact_sha256, manifest_json, entry_doc, mode, parent_resource_id, display_template, spec_json, parameters_schema, is_public, max_concurrency,
                               pack_validation_status, pack_validated_at, pack_validation_message, skill_root_path, service_detail_md
                        FROM t_resource_skill_ext WHERE resource_id = ? LIMIT 1
                        """,
                resourceId);
        if (rows.isEmpty()) {
            return;
        }
        Map<String, Object> r = rows.get(0);
        vo.setSkillType(stringValue(r.get("skill_type")));
        vo.setArtifactUri(stringValue(r.get("artifact_uri")));
        vo.setArtifactSha256(stringValue(r.get("artifact_sha256")));
        vo.setManifest(parseJsonMap(r.get("manifest_json")));
        vo.setEntryDoc(stringValue(r.get("entry_doc")));
        vo.setPackValidationStatus(stringValue(r.get("pack_validation_status")));
        vo.setPackValidatedAt(toDateTime(r.get("pack_validated_at")));
        vo.setPackValidationMessage(stringValue(r.get("pack_validation_message")));
        vo.setSkillRootPath(stringValue(r.get("skill_root_path")));
        vo.setMode(stringValue(r.get("mode")));
        vo.setParentResourceId(longObject(r.get("parent_resource_id")));
        vo.setDisplayTemplate(stringValue(r.get("display_template")));
        vo.setSpec(parseJsonMap(r.get("spec_json")));
        vo.setParametersSchema(parseJsonMap(r.get("parameters_schema")));
        vo.setIsPublic(boolObject(r.get("is_public")));
        vo.setMaxConcurrency(intObject(r.get("max_concurrency")));
        vo.setServiceDetailMd(stringValue(r.get("service_detail_md")));
    }

    private void enrichMcpFields(ResourceManageVO vo, Long resourceId) {
        var rows = jdbcTemplate.queryForList(
                "SELECT endpoint, protocol, auth_type, auth_config, service_detail_md FROM t_resource_mcp_ext WHERE resource_id = ? LIMIT 1",
                resourceId);
        if (rows.isEmpty()) {
            return;
        }
        Map<String, Object> r = rows.get(0);
        vo.setEndpoint(stringValue(r.get("endpoint")));
        vo.setProtocol(stringValue(r.get("protocol")));
        vo.setAuthType(stringValue(r.get("auth_type")));
        vo.setAuthConfig(parseJsonMap(r.get("auth_config")));
        vo.setServiceDetailMd(stringValue(r.get("service_detail_md")));
    }

    private void enrichAppFields(ResourceManageVO vo, Long resourceId) {
        var rows = jdbcTemplate.queryForList(
                "SELECT app_url, embed_type, icon, screenshots, is_public, service_detail_md FROM t_resource_app_ext WHERE resource_id = ? LIMIT 1",
                resourceId);
        if (rows.isEmpty()) {
            return;
        }
        Map<String, Object> r = rows.get(0);
        vo.setAppUrl(stringValue(r.get("app_url")));
        vo.setEmbedType(stringValue(r.get("embed_type")));
        vo.setIcon(stringValue(r.get("icon")));
        vo.setScreenshots(toStringListFromJsonColumn(r.get("screenshots")));
        vo.setIsPublic(boolObject(r.get("is_public")));
        vo.setServiceDetailMd(stringValue(r.get("service_detail_md")));
    }

    private void enrichDatasetFields(ResourceManageVO vo, Long resourceId) {
        var rows = jdbcTemplate.queryForList(
                "SELECT data_type, format, record_count, file_size, tags, is_public, service_detail_md FROM t_resource_dataset_ext WHERE resource_id = ? LIMIT 1",
                resourceId);
        if (rows.isEmpty()) {
            return;
        }
        Map<String, Object> r = rows.get(0);
        vo.setDataType(stringValue(r.get("data_type")));
        vo.setFormat(stringValue(r.get("format")));
        vo.setRecordCount(longObject(r.get("record_count")));
        vo.setFileSize(longObject(r.get("file_size")));
        vo.setTags(toStringListFromJsonColumn(r.get("tags")));
        vo.setIsPublic(boolObject(r.get("is_public")));
        vo.setServiceDetailMd(stringValue(r.get("service_detail_md")));
    }

    private List<String> toStringListFromJsonColumn(Object raw) {
        return parseJsonList(raw).stream().map(String::valueOf).toList();
    }

    private static Boolean boolObject(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Boolean b) {
            return b;
        }
        if (o instanceof Number n) {
            return n.intValue() != 0;
        }
        return null;
    }

    private static Integer intObject(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception ex) {
            return null;
        }
    }

    private static Long longObject(Object o) {
        if (o == null) {
            return null;
        }
        return longValue(o);
    }

    private static Double doubleObject(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (Exception ex) {
            return null;
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
                .accessPolicy(ResourceAccessPolicy.fromStored(row.get("access_policy")).wireValue())
                .createdBy(longValue(row.get("created_by")))
                .createTime(toDateTime(row.get("create_time")))
                .updateTime(toDateTime(row.get("update_time")))
                .currentVersion(stringValue(row.get("current_version")))
                .build();
    }

    /** 详情等场景主查询未带 current_version 列时补齐。 */
    private void enrichCurrentVersionLabel(ResourceManageVO vo, Long resourceId) {
        if (vo == null || resourceId == null) {
            return;
        }
        List<String> found = jdbcTemplate.query(
                "SELECT version FROM t_resource_version WHERE resource_id = ? AND is_current = 1 LIMIT 1",
                (rs, i) -> rs.getString(1),
                resourceId);
        if (!found.isEmpty() && StringUtils.hasText(found.get(0))) {
            vo.setCurrentVersion(found.get(0));
        }
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

    /**
     * 与目录按标签名筛选一致：维护 t_resource_tag_rel。categoryId 为 t_tag.id；dataset 的 request.tags 仅当名称在 t_tag 中存在时写入（不自动建标签）。
     */
    private void syncResourceTagRels(Long resourceId, String resourceType, ResourceUpsertRequest request) {
        jdbcTemplate.update("DELETE FROM t_resource_tag_rel WHERE resource_id = ? AND resource_type = ?",
                resourceId, resourceType);
        LinkedHashSet<Long> tagIds = new LinkedHashSet<>();
        if (request.getCategoryId() != null) {
            Integer cnt = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM t_tag WHERE id = ?",
                    Integer.class, request.getCategoryId());
            if (cnt != null && cnt > 0) {
                tagIds.add(request.getCategoryId());
            }
        }
        if ("dataset".equals(resourceType) && request.getTags() != null) {
            for (String raw : request.getTags()) {
                if (!StringUtils.hasText(raw)) {
                    continue;
                }
                String label = raw.trim();
                List<Long> found = jdbcTemplate.query(
                        "SELECT id FROM t_tag WHERE name = ? LIMIT 2",
                        (rs, i) -> rs.getLong(1),
                        label);
                if (found.size() == 1) {
                    tagIds.add(found.get(0));
                }
            }
        }
        for (Long tagId : tagIds) {
            jdbcTemplate.update(
                    "INSERT INTO t_resource_tag_rel(resource_type, resource_id, tag_id) VALUES(?, ?, ?)",
                    resourceType, resourceId, tagId);
        }
    }

    private void enrichCatalogTagNames(ResourceManageVO vo, Long resourceId) {
        if (vo == null || resourceId == null || !StringUtils.hasText(vo.getResourceType())) {
            return;
        }
        List<String> names = jdbcTemplate.query(
                "SELECT t.name FROM t_resource_tag_rel rt INNER JOIN t_tag t ON t.id = rt.tag_id "
                        + "WHERE rt.resource_id = ? AND rt.resource_type = ? ORDER BY t.name",
                (rs, i) -> rs.getString(1),
                resourceId, vo.getResourceType());
        vo.setCatalogTagNames(names.isEmpty() ? List.of() : names);
    }

    private void enrichCatalogTagNamesBatch(List<ResourceManageVO> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<Long> ids = list.stream().map(ResourceManageVO::getId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        String placeholders = ids.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(","));
        List<Object> args = new ArrayList<>(ids);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT rt.resource_id, t.name FROM t_resource_tag_rel rt "
                        + "INNER JOIN t_tag t ON t.id = rt.tag_id WHERE rt.resource_id IN ("
                        + placeholders + ") ORDER BY rt.resource_id, t.name",
                args.toArray());
        Map<Long, List<String>> byId = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long rid = longValue(row.get("resource_id"));
            String n = stringValue(row.get("name"));
            if (rid != null && StringUtils.hasText(n)) {
                byId.computeIfAbsent(rid, k -> new ArrayList<>()).add(n);
            }
        }
        for (ResourceManageVO vo : list) {
            if (vo.getId() != null) {
                vo.setCatalogTagNames(byId.getOrDefault(vo.getId(), List.of()));
            }
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

    /** 向所有审核员推送通知（不按部门过滤；待审项不独占派给某人，全体 reviewer 共用同一全平台队列）。 */
    private void notifyReviewers(String type, String title, String body, Long resourceId) {
        String sql = "SELECT ur.user_id FROM t_user_role_rel ur JOIN t_platform_role r ON ur.role_id = r.id WHERE r.role_code = 'reviewer'";
        java.util.List<java.util.Map<String, Object>> reviewers = jdbcTemplate.queryForList(sql);
        java.util.List<Long> reviewerIds = reviewers.stream()
                .map(r -> Long.valueOf(String.valueOf(r.get("user_id"))))
                .toList();
        if (!reviewerIds.isEmpty()) {
            notificationService.broadcast(reviewerIds, type, title, body, "resource", resourceId);
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

