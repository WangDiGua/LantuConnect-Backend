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
import com.lantu.connect.monitoring.service.ResourceHealthService;
import com.lantu.connect.monitoring.dto.ResourceHealthSnapshotVO;
import com.lantu.connect.gateway.service.support.ResourceLifecycleStateMachine;
import com.lantu.connect.common.util.UserDisplayNameResolver;
import com.lantu.connect.common.util.SensitiveDataEncryptor;
import com.lantu.connect.notification.service.NotificationEventCodes;
import com.lantu.connect.notification.service.SystemNotificationFacade;
import com.lantu.connect.realtime.AuditPendingPushDebouncer;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
    private static final Set<String> AGENT_REG_PROTOCOLS = Set.of(
            "bailian_compatible",
            "openai_compatible",
            "anthropic_messages",
            "gemini_generatecontent");

    /** skill.skill_type：仅 context_v1。 */
    private static final Set<String> SKILL_TYPE_ALLOWED = Set.of("context_v1");

    private static final String SKILL_EXEC_CONTEXT = "context";

    /** 含 service_detail_md 列的扩展表（用于统一 upsert / 读取逻辑） */
    private static final Set<String> SERVICE_DETAIL_EXT_TABLES = Set.of(
            "t_resource_mcp_ext",
            "t_resource_skill_ext",
            "t_resource_agent_ext",
            "t_resource_app_ext",
            "t_resource_dataset_ext");

    private static final String AUDIT_KIND_INITIAL = "initial";
    private static final String AUDIT_KIND_PUBLISHED_UPDATE = "published_update";

    private static final String AUDIT_TIER_LOW = "low";
    private static final String AUDIT_TIER_MEDIUM = "medium";
    private static final String AUDIT_TIER_HIGH = "high";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PlatformRoleMapper platformRoleMapper;
    private final ProtocolInvokerRegistry protocolInvokerRegistry;
    private final SensitiveDataEncryptor sensitiveDataEncryptor;
    private final UserDisplayNameResolver userDisplayNameResolver;
    private final SystemNotificationFacade systemNotificationFacade;
    private final AuditPendingPushDebouncer auditPendingPushDebouncer;
    private final ResourceHealthService resourceHealthService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceManageVO create(Long operatorUserId, ResourceUpsertRequest request) {
        ensureAuthenticated(operatorUserId);
        String type = normalizeType(request.getResourceType());
        validateByType(type, request);
        ensureUniqueCode(type, request.getResourceCode(), null);
        if ("agent".equals(type)) {
            ensureUniqueAgentModelAlias(operatorUserId, request.getModelAlias(), null);
        }
        ResourceAccessPolicy accessPolicy = ResourceAccessPolicy.OPEN_PLATFORM;

        LocalDateTime now = LocalDateTime.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(conn -> {
            PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO t_resource(resource_type, resource_code, display_name, description, status, source_type, access_policy, created_by, deleted, create_time, update_time)
                    VALUES(?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, type);
            ps.setString(2, request.getResourceCode().trim());
            ps.setString(3, request.getDisplayName().trim());
            ps.setString(4, request.getDescription());
            ps.setString(5, ResourceLifecycleStateMachine.STATUS_DRAFT);
            ps.setString(6, request.getSourceType());
            ps.setString(7, accessPolicy.wireValue());
            ps.setLong(8, operatorUserId);
            ps.setTimestamp(9, Timestamp.valueOf(now));
            ps.setTimestamp(10, Timestamp.valueOf(now));
            return ps;
        }, keyHolder);
        Long resourceId = keyHolder.getKey() == null ? null : keyHolder.getKey().longValue();
        if (resourceId == null) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "创建资源失败");
        }
        upsertExtension(type, resourceId, request);
        resourceHealthService.ensurePolicyForResource(resourceId);
        syncAllBindings(resourceId, type, request);
        syncResourceTagRels(resourceId, type, request);
        upsertDefaultVersion(resourceId, snapshotForVersion(type, resourceId, request, true), true);
        return findResource(resourceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceManageVO update(Long operatorUserId, Long resourceId, ResourceUpsertRequest request) {
        ensureAuthenticated(operatorUserId);
        ResourceRow row = requireManageableResource(operatorUserId, resourceId);
        String type = normalizeType(request.getResourceType());
        if (!row.resourceType().equals(type)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "resourceType 不允许变更");
        }
        validateByType(type, request);
        ensureUniqueCode(type, request.getResourceCode(), resourceId);
        if ("agent".equals(type)) {
            ensureUniqueAgentModelAlias(requireResourceCreatorForBindings(resourceId), request.getModelAlias(), resourceId);
        }

        String st = ResourceLifecycleStateMachine.normalizeStatus(row.status());
        if (ResourceLifecycleStateMachine.STATUS_PUBLISHED.equals(st)) {
            if (hasPendingPublishedUpdateAudit(resourceId)) {
                throw new BusinessException(ResultCode.DUPLICATE_SUBMIT, "该资源已有待审核的已发布变更，请等待审结或撤回");
            }
            ResourceAccessPolicy accessPolicy = resolveAccessPolicyForUpdate(resourceId, request);
            Map<String, Object> snap = buildSnapshot(type, request, accessPolicy);
            ResourceManageVO live = findResource(resourceId);
            String tier = computeDraftAuditTier(live, request, type);
            upsertWorkingDraft(resourceId, snap, tier);
            return getById(operatorUserId, resourceId);
        }

        ResourceLifecycleStateMachine.ensureEditable(row.status());
        persistUpsertToMainAndExtensions(resourceId, row.resourceType(), type, request);
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
        String st = ResourceLifecycleStateMachine.normalizeStatus(row.status());

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

        if (ResourceLifecycleStateMachine.STATUS_PUBLISHED.equals(st)) {
            Map<String, Object> draftMap = loadWorkingDraftMap(resourceId);
            if (draftMap == null || draftMap.isEmpty()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "请先在登记页保存草稿后再提交已发布资源变更");
            }
            ResourceManageVO live = findResource(resourceId);
            ResourceUpsertRequest merged = mergeSnapshotOntoCurrent(live, draftMap);
            String type = normalizeType(row.resourceType());
            validateByType(type, merged);
            ensureUniqueCode(type, merged.getResourceCode(), resourceId);
            ensureSkillReadyForSubmit(merged);
            ResourceAccessPolicy ap = resolveAccessPolicyForUpdate(resourceId, merged);
            Map<String, Object> freezeSnap = buildSnapshot(type, merged, ap);
            String tier = readWorkingDraftTier(resourceId);

            if (AUDIT_TIER_LOW.equals(tier) && mayAutoApplyLowTierPublishedUpdate(operatorUserId)) {
                applyPublishedUpdateFromAudit(operatorUserId, resourceId, freezeSnap);
                systemNotificationFacade.notifyAuditAudience(
                        NotificationEventCodes.RESOURCE_SUBMITTED,
                        "已发布资源变更已自动生效（低风险）",
                        """
                                事件: 已发布资源变更（自动合并）
                                时间: %s
                                资源: %s/%s
                                说明: 低风险修改已由具备权限的管理员直接合并上线，线上默认版本已更新。
                                """.formatted(LocalDateTime.now(), row.resourceType(), resourceId),
                        row.resourceType(),
                        resourceId,
                        null,
                        "/c/resource-audit",
                        "查看审核");
                notifyReviewers(
                        NotificationEventCodes.RESOURCE_SUBMITTED,
                        "已发布资源变更已自动生效（低风险）",
                        """
                                事件: 已发布资源变更（自动合并）
                                时间: %s
                                资源: %s/%s
                                说明: 低风险修改已由具备权限的管理员直接合并上线，线上解析版本已更新。
                                """.formatted(LocalDateTime.now(), row.resourceType(), resourceId),
                        resourceId);
                return getById(operatorUserId, resourceId);
            }

            try {
                jdbcTemplate.update("""
                                INSERT INTO t_audit_item(target_type, target_id, display_name, agent_name, description, \
                                agent_type, source_type, audit_kind, payload_json, submitter, submit_time, status, create_time) \
                                VALUES(?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), ?, NOW(), 'pending_review', NOW())
                                """,
                        row.resourceType(),
                        resourceId,
                        merged.getDisplayName() != null ? merged.getDisplayName() : row.displayName(),
                        merged.getResourceCode() != null ? merged.getResourceCode() : row.resourceCode(),
                        merged.getDescription() != null ? merged.getDescription() : row.description(),
                        row.resourceType(),
                        row.sourceType(),
                        AUDIT_KIND_PUBLISHED_UPDATE,
                        writeJson(freezeSnap),
                        String.valueOf(operatorUserId));
            } catch (DataIntegrityViolationException ex) {
                throw new BusinessException(ResultCode.DUPLICATE_SUBMIT, "该资源已有待审核的已发布变更，请等待审结或撤回");
            }
            deleteWorkingDraft(resourceId);

            notifyReviewers(NotificationEventCodes.RESOURCE_SUBMITTED,
                    "已发布资源变更待审核",
                    """
                            事件: 已发布资源变更提审
                            结果: 待审核（线上仍为当前版本）
                            时间: %s
                            提交人: %s
                            资源: %s/%s
                            说明: 「%s」提交了配置变更，审核通过后合并至线上默认解析版本。
                            """.formatted(
                            LocalDateTime.now(),
                            operatorUserId,
                            row.resourceType(),
                            resourceId,
                            merged.getDisplayName() != null ? merged.getDisplayName() : row.displayName()),
                    resourceId);
            systemNotificationFacade.notifyResourceSubmitted(
                    operatorUserId,
                    resourceId,
                    row.resourceType(),
                    merged.getDisplayName() != null ? merged.getDisplayName() : row.displayName(),
                    true);
            auditPendingPushDebouncer.requestFlush();
            return getById(operatorUserId, resourceId);
        }

        ResourceLifecycleStateMachine.ensureTransitionAllowed(row.status(), ResourceLifecycleStateMachine.STATUS_PENDING_REVIEW);

        if ("skill".equals(row.resourceType())) {
            List<Map<String, Object>> se = jdbcTemplate.queryForList(
                    "SELECT execution_mode, hosted_system_prompt FROM t_resource_skill_ext WHERE resource_id = ? LIMIT 1",
                    resourceId);
            if (se.isEmpty()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "技能扩展信息不存在");
            }
            String emRaw = stringValue(se.get(0).get("execution_mode"));
            String em = StringUtils.hasText(emRaw) ? emRaw.trim().toLowerCase(Locale.ROOT) : SKILL_EXEC_CONTEXT;
            if (!SKILL_EXEC_CONTEXT.equals(em)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "Skill 须为 context 模式");
            }
            if (!StringUtils.hasText(stringValue(se.get(0).get("hosted_system_prompt")))) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "请先填写 contextPrompt（规范正文）后再提审");
            }
        }

        int updated = jdbcTemplate.update("""
                        UPDATE t_resource
                        SET status = ?, update_time = NOW()
                        WHERE id = ? AND deleted = 0 AND LOWER(status) IN ('draft', 'rejected', 'deprecated')
                        """,
                ResourceLifecycleStateMachine.STATUS_PENDING_REVIEW,
                resourceId);
        if (updated != 1) {
            throw new BusinessException(ResultCode.CONFLICT, "资源状态已变更，请刷新后重试");
        }
        try {
            jdbcTemplate.update("""
                            INSERT INTO t_audit_item(target_type, target_id, display_name, agent_name, description, agent_type, source_type, audit_kind, submitter, submit_time, status, create_time)
                            VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), 'pending_review', NOW())
                            """,
                    row.resourceType(),
                    resourceId,
                    row.displayName(),
                    row.resourceCode(),
                    row.description(),
                    row.resourceType(),
                    row.sourceType(),
                    AUDIT_KIND_INITIAL,
                    String.valueOf(operatorUserId));
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ResultCode.DUPLICATE_SUBMIT, "该资源已有待审核记录");
        }

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
        systemNotificationFacade.notifyResourceSubmitted(
                operatorUserId,
                resourceId,
                row.resourceType(),
                row.displayName(),
                false);
        auditPendingPushDebouncer.requestFlush();
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
                "资源已暂停对外开放",
                row.resourceType(),
                resourceId,
                "状态已切换为 deprecated（暂停对外）");
        return findResource(resourceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceManageVO withdraw(Long operatorUserId, Long resourceId) {
        ensureAuthenticated(operatorUserId);
        ResourceRow row = requireManageableResource(operatorUserId, resourceId);
        String st = ResourceLifecycleStateMachine.normalizeStatus(row.status());

        if (ResourceLifecycleStateMachine.STATUS_PUBLISHED.equals(st)) {
            List<Map<String, Object>> pend = jdbcTemplate.queryForList("""
                            SELECT id, payload_json FROM t_audit_item
                            WHERE target_id = ? AND status = 'pending_review' AND audit_kind = ?
                            ORDER BY id DESC LIMIT 1
                            """,
                    resourceId, AUDIT_KIND_PUBLISHED_UPDATE);
            if (pend.isEmpty()) {
                throw new BusinessException(ResultCode.ILLEGAL_STATE_TRANSITION, "当前没有待审核的已发布变更可撤回");
            }
            Map<String, Object> payload = parseJsonMap(pend.get(0).get("payload_json"));
            if (!payload.isEmpty()) {
                upsertWorkingDraft(resourceId, payload, AUDIT_TIER_MEDIUM);
            }
            jdbcTemplate.update("UPDATE t_audit_item SET status = 'withdrawn' WHERE id = ? AND status = 'pending_review'",
                    longValue(pend.get(0).get("id")));
            return getById(operatorUserId, resourceId);
        }

        ResourceLifecycleStateMachine.ensureTransitionAllowed(row.status(), ResourceLifecycleStateMachine.STATUS_DRAFT);
        jdbcTemplate.update("UPDATE t_resource SET status = ?, update_time = NOW() WHERE id = ? AND deleted = 0",
                ResourceLifecycleStateMachine.STATUS_DRAFT, resourceId);
        jdbcTemplate.update("""
                        UPDATE t_audit_item SET status = 'withdrawn' \
                        WHERE target_id = ? AND status = 'pending_review' \
                        AND (audit_kind IS NULL OR TRIM(audit_kind) = '' OR LOWER(audit_kind) = 'initial')
                        """,
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
    @Transactional(rollbackFor = Exception.class)
    public void batchWithdraw(Long operatorUserId, List<Long> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return;
        }
        for (Long resourceId : resourceIds) {
            withdraw(operatorUserId, resourceId);
        }
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
        enrichWorkingDraftFields(vo, resourceId);
        applyDraftOverlayToPublishedDetail(vo, resourceId);
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
     * 消费策略统一为 {@link ResourceAccessPolicy#OPEN_PLATFORM}（不再提供 per-resource 授权策略）。
     */
    private Map<String, Object> snapshotForVersion(String type, Long resourceId, ResourceUpsertRequest request, boolean forCreate) {
        if ("skill".equals(type)) {
            return buildSnapshotFromDb(type, resourceId);
        }
        return buildSnapshot(type, request, resolveAccessPolicyForUpdate(resourceId, request));
    }

    private ResourceAccessPolicy resolveAccessPolicyForUpdate(Long resourceId, ResourceUpsertRequest request) {
        return ResourceAccessPolicy.OPEN_PLATFORM;
    }

    @Override
    public PageResult<ResourceManageVO> pageMine(Long operatorUserId, String resourceType, String status,
                                                 String keyword, String sortBy, String sortOrder,
                                                 Integer page, Integer pageSize, Long forResourceId) {
        ensureAuthenticated(operatorUserId);
        int p = page == null ? 1 : Math.max(1, page);
        int ps = pageSize == null ? 20 : Math.min(100, Math.max(1, pageSize));
        int offset = (p - 1) * ps;
        String normalizedType = StringUtils.hasText(resourceType) ? normalizeType(resourceType) : null;

        Long listOwnerUserId = operatorUserId;
        if (forResourceId != null) {
            requireManageableResource(operatorUserId, forResourceId);
            Long owner = jdbcTemplate.queryForObject(
                    "SELECT created_by FROM t_resource WHERE id = ? AND deleted = 0 LIMIT 1",
                    Long.class, forResourceId);
            if (owner == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "资源不存在");
            }
            listOwnerUserId = owner;
        }

        List<Object> countArgs = new ArrayList<>();
        List<Object> listArgs = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE deleted = 0 ");
        where.append(" AND created_by = ? ");
        countArgs.add(listOwnerUserId);
        listArgs.add(listOwnerUserId);
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
                        r.access_policy, r.created_by, r.create_time, r.update_time, \
                        (SELECT v.version FROM t_resource_version v WHERE v.resource_id = r.id AND v.is_current = 1 LIMIT 1) AS current_version \
                        FROM t_resource r \
                        """
                        + whereAliased + " ORDER BY " + orderColumn + " " + direction + " LIMIT ? OFFSET ?",
                listArgs.toArray());
        List<ResourceManageVO> list = rows.stream().map(this::toManageVo).toList();
        enrichCreatedByNames(list);
        enrichCatalogTagNamesBatch(list);
        enrichWorkingDraftFlagsBatch(list);
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
        String st = ResourceLifecycleStateMachine.normalizeStatus(row.status());
        if (ResourceLifecycleStateMachine.STATUS_PUBLISHED.equals(st)) {
            if (hasPendingPublishedUpdateAudit(resourceId)) {
                throw new BusinessException(ResultCode.DUPLICATE_SUBMIT, "该资源已有待审核的已发布变更，请等待审结或撤回");
            }
            ResourceAccessPolicy accessPolicy = resolveAccessPolicyForUpdate(resourceId, req);
            Map<String, Object> draftSnap = buildSnapshot(type, req, accessPolicy);
            String tier = computeDraftAuditTier(current, req, type);
            upsertWorkingDraft(resourceId, draftSnap, tier);
            return getById(operatorUserId, resourceId);
        }
        ResourceLifecycleStateMachine.ensureEditable(row.status());
        persistUpsertToMainAndExtensions(resourceId, row.resourceType(), type, req);
        return getById(operatorUserId, resourceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyPublishedUpdateFromAudit(Long reviewerUserId, Long resourceId, Map<String, Object> payloadSnapshot) {
        ensureAuthenticated(reviewerUserId);
        requireManageableResource(reviewerUserId, resourceId);
        ResourceRow row = requireRow(resourceId);
        String st = ResourceLifecycleStateMachine.normalizeStatus(row.status());
        if (!ResourceLifecycleStateMachine.STATUS_PUBLISHED.equals(st)) {
            throw new BusinessException(ResultCode.CONFLICT, "资源非已发布状态，无法应用已发布变更");
        }
        if (payloadSnapshot == null || payloadSnapshot.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "变更快照为空");
        }
        ResourceManageVO live = findResource(resourceId);
        ResourceUpsertRequest req = mergeSnapshotOntoCurrent(live, payloadSnapshot);
        String type = normalizeType(row.resourceType());
        if (!type.equals(normalizeType(req.getResourceType()))) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "快照 resourceType 与资源不一致");
        }
        validateByType(type, req);
        ensureUniqueCode(type, req.getResourceCode(), resourceId);
        persistUpsertToMainAndExtensions(resourceId, row.resourceType(), type, req);
        deleteWorkingDraft(resourceId);
    }

    private ResourceRow requireRow(Long resourceId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, resource_type, resource_code, display_name, description, status, source_type, created_by
                FROM t_resource WHERE id = ? AND deleted = 0 LIMIT 1
                """, resourceId);
        if (rows.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "资源不存在");
        }
        Map<String, Object> row = rows.get(0);
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

    private void persistUpsertToMainAndExtensions(Long resourceId, String rowResourceType, String normalizedType,
                                                  ResourceUpsertRequest request) {
        ResourceAccessPolicy accessPolicy = resolveAccessPolicyForUpdate(resourceId, request);
        jdbcTemplate.update("""
                        UPDATE t_resource
                        SET resource_code = ?, display_name = ?, description = ?, source_type = ?, access_policy = ?, update_time = NOW()
                        WHERE id = ? AND deleted = 0
                        """,
                request.getResourceCode().trim(),
                request.getDisplayName().trim(),
                request.getDescription(),
                request.getSourceType(),
                accessPolicy.wireValue(),
                resourceId);
        upsertExtension(normalizedType, resourceId, request);
        resourceHealthService.ensurePolicyForResource(resourceId);
        syncAllBindings(resourceId, normalizedType, request);
        syncResourceTagRels(resourceId, normalizedType, request);
        upsertCurrentVersionSnapshot(resourceId, snapshotForVersion(normalizedType, resourceId, request, false));
    }

    private void upsertWorkingDraft(Long resourceId, Map<String, Object> draftJson, String auditTier) {
        String tier = StringUtils.hasText(auditTier) ? auditTier.trim().toLowerCase(Locale.ROOT) : AUDIT_TIER_MEDIUM;
        if (!AUDIT_TIER_LOW.equals(tier) && !AUDIT_TIER_MEDIUM.equals(tier) && !AUDIT_TIER_HIGH.equals(tier)) {
            tier = AUDIT_TIER_MEDIUM;
        }
        jdbcTemplate.update("""
                        INSERT INTO t_resource_draft(resource_id, draft_json, audit_tier)
                        VALUES(?, CAST(? AS JSON), ?)
                        ON DUPLICATE KEY UPDATE draft_json = VALUES(draft_json), audit_tier = VALUES(audit_tier), update_time = CURRENT_TIMESTAMP
                        """,
                resourceId, writeJson(draftJson), tier);
    }

    private void deleteWorkingDraft(Long resourceId) {
        jdbcTemplate.update("DELETE FROM t_resource_draft WHERE resource_id = ?", resourceId);
    }

    private Map<String, Object> loadWorkingDraftMap(Long resourceId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT draft_json FROM t_resource_draft WHERE resource_id = ? LIMIT 1", resourceId);
        if (rows.isEmpty()) {
            return Map.of();
        }
        return parseJsonMap(rows.get(0).get("draft_json"));
    }

    private boolean hasPendingPublishedUpdateAudit(Long resourceId) {
        Integer n = jdbcTemplate.queryForObject("""
                        SELECT COUNT(1) FROM t_audit_item
                        WHERE target_id = ? AND status = 'pending_review' AND audit_kind = ?
                        """,
                Integer.class,
                resourceId, AUDIT_KIND_PUBLISHED_UPDATE);
        return n != null && n > 0;
    }

    private String readWorkingDraftTier(Long resourceId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT audit_tier FROM t_resource_draft WHERE resource_id = ? LIMIT 1", resourceId);
        if (rows.isEmpty()) {
            return AUDIT_TIER_MEDIUM;
        }
        return stringValue(rows.get(0).get("audit_tier"));
    }

    private void enrichWorkingDraftFields(ResourceManageVO vo, Long resourceId) {
        if (vo == null || resourceId == null) {
            return;
        }
        vo.setPendingPublishedUpdate(hasPendingPublishedUpdateAudit(resourceId));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT audit_tier, update_time FROM t_resource_draft WHERE resource_id = ? LIMIT 1
                """, resourceId);
        if (rows.isEmpty()) {
            vo.setHasWorkingDraft(false);
            vo.setWorkingDraftUpdatedAt(null);
            vo.setWorkingDraftAuditTier(null);
            return;
        }
        Map<String, Object> r = rows.get(0);
        vo.setHasWorkingDraft(true);
        vo.setWorkingDraftAuditTier(stringValue(r.get("audit_tier")));
        vo.setWorkingDraftUpdatedAt(toDateTime(r.get("update_time")));
    }

    private void enrichWorkingDraftFlagsBatch(List<ResourceManageVO> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<Long> ids = list.stream().map(ResourceManageVO::getId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        String in = ids.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(","));
        List<Object> argsDraft = new ArrayList<>(ids);
        List<Map<String, Object>> draftRows = jdbcTemplate.queryForList(
                "SELECT resource_id FROM t_resource_draft WHERE resource_id IN (" + in + ")", argsDraft.toArray());
        LinkedHashSet<Long> withDraft = new LinkedHashSet<>();
        for (Map<String, Object> r : draftRows) {
            Long rid = longValue(r.get("resource_id"));
            if (rid != null) {
                withDraft.add(rid);
            }
        }
        List<Object> argsPend = new ArrayList<>();
        argsPend.add(AUDIT_KIND_PUBLISHED_UPDATE);
        argsPend.addAll(ids);
        List<Map<String, Object>> pendRows = jdbcTemplate.queryForList(
                "SELECT target_id FROM t_audit_item WHERE status = 'pending_review' AND audit_kind = ? AND target_id IN ("
                        + in + ")", argsPend.toArray());
        LinkedHashSet<Long> pendingPu = new LinkedHashSet<>();
        for (Map<String, Object> r : pendRows) {
            Long tid = longValue(r.get("target_id"));
            if (tid != null) {
                pendingPu.add(tid);
            }
        }
        for (ResourceManageVO vo : list) {
            if (vo.getId() == null) {
                continue;
            }
            vo.setHasWorkingDraft(withDraft.contains(vo.getId()));
            vo.setPendingPublishedUpdate(pendingPu.contains(vo.getId()));
        }
    }

    private boolean mayAutoApplyLowTierPublishedUpdate(Long userId) {
        if (userId == null) {
            return false;
        }
        List<PlatformRole> roles = platformRoleMapper.selectRolesByUserId(userId);
        return roles.stream().map(PlatformRole::getRoleCode).anyMatch(code ->
                "platform_admin".equals(code) || "admin".equals(code) || "dept_admin".equals(code));
    }

    private String computeDraftAuditTier(ResourceManageVO live, ResourceUpsertRequest req, String normalizedType) {
        ResourceUpsertRequest base = voToUpsertRequest(live);
        String t = normalizeType(normalizedType);
        if (!trimEq(req.getResourceCode(), base.getResourceCode())) {
            return AUDIT_TIER_HIGH;
        }
        switch (t) {
            case "mcp" -> {
                if (!trimEq(req.getEndpoint(), base.getEndpoint())) {
                    return AUDIT_TIER_HIGH;
                }
                if (!shallowMapEquals(req.getAuthConfig(), base.getAuthConfig())) {
                    return AUDIT_TIER_HIGH;
                }
            }
            case "agent" -> {
                String u1 = req.getSpec() == null ? null : stringValue(req.getSpec().get("url"));
                String u2 = base.getSpec() == null ? null : stringValue(base.getSpec().get("url"));
                if (!trimEq(u1, u2)) {
                    return AUDIT_TIER_HIGH;
                }
            }
            case "skill" -> {
                if (!trimEq(req.getContextPrompt(), base.getContextPrompt())) {
                    return AUDIT_TIER_HIGH;
                }
            }
            case "app" -> {
                if (!trimEq(req.getAppUrl(), base.getAppUrl())) {
                    return AUDIT_TIER_HIGH;
                }
            }
            default -> {
            }
        }
        boolean onlyCosmetic =
                trimEq(req.getDisplayName(), base.getDisplayName())
                        && Objects.equals(
                        req.getDescription() == null ? "" : req.getDescription(),
                        base.getDescription() == null ? "" : base.getDescription())
                        && trimEq(
                        req.getServiceDetailMd() == null ? "" : req.getServiceDetailMd(),
                        base.getServiceDetailMd() == null ? "" : base.getServiceDetailMd());
        if (onlyCosmetic && t.equals("mcp")) {
            return AUDIT_TIER_LOW;
        }
        if (onlyCosmetic && t.equals("agent")) {
            return AUDIT_TIER_LOW;
        }
        if (onlyCosmetic && t.equals("app")) {
            return AUDIT_TIER_LOW;
        }
        if (onlyCosmetic && t.equals("dataset")) {
            return AUDIT_TIER_LOW;
        }
        if (onlyCosmetic && t.equals("skill")) {
            return AUDIT_TIER_LOW;
        }
        return AUDIT_TIER_MEDIUM;
    }

    private static boolean trimEq(String a, String b) {
        String x = a == null ? "" : a.trim();
        String y = b == null ? "" : b.trim();
        return x.equals(y);
    }

    private static boolean shallowMapEquals(Map<String, Object> a, Map<String, Object> b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return Objects.equals(new LinkedHashMap<>(a), new LinkedHashMap<>(b));
    }

    /**
     * 将合并后的请求体写回 VO（用于已发布资源读详情时展示草稿编辑态）。
     */
    private void copyUpsertOntoVo(ResourceManageVO vo, ResourceUpsertRequest r) {
        if (vo == null || r == null) {
            return;
        }
        vo.setResourceCode(r.getResourceCode());
        vo.setDisplayName(r.getDisplayName());
        vo.setDescription(r.getDescription());
        vo.setSourceType(r.getSourceType());
        vo.setTagIds(r.getTagIds() == null ? null : new ArrayList<>(r.getTagIds()));
        vo.setAccessPolicy(r.getAccessPolicy());
        vo.setRelatedResourceIds(r.getRelatedResourceIds() == null ? null : new ArrayList<>(r.getRelatedResourceIds()));
        vo.setRelatedMcpResourceIds(r.getRelatedMcpResourceIds() == null ? null : new ArrayList<>(r.getRelatedMcpResourceIds()));
        vo.setAgentType(r.getAgentType());
        vo.setMode(r.getMode());
        vo.setSpec(r.getSpec() == null || r.getSpec().isEmpty() ? null : shallowCopyMap(r.getSpec()));
        vo.setIsPublic(r.getIsPublic());
        vo.setHidden(r.getHidden());
        vo.setMaxConcurrency(r.getMaxConcurrency());
        vo.setMaxSteps(r.getMaxSteps());
        vo.setTemperature(r.getTemperature());
        vo.setSystemPrompt(r.getSystemPrompt());
        vo.setServiceDetailMd(r.getServiceDetailMd());
        vo.setSkillType(r.getSkillType());
        vo.setManifest(r.getManifest() == null || r.getManifest().isEmpty() ? null : shallowCopyMap(r.getManifest()));
        vo.setEntryDoc(r.getEntryDoc());
        vo.setParentResourceId(r.getParentResourceId());
        vo.setDisplayTemplate(r.getDisplayTemplate());
        vo.setParametersSchema(r.getParametersSchema() == null || r.getParametersSchema().isEmpty()
                ? null
                : shallowCopyMap(r.getParametersSchema()));
        vo.setExecutionMode(r.getExecutionMode());
        vo.setContextPrompt(r.getContextPrompt());
        vo.setEndpoint(r.getEndpoint());
        vo.setProtocol(r.getProtocol());
        vo.setAuthType(r.getAuthType());
        vo.setAuthConfig(r.getAuthConfig() == null || r.getAuthConfig().isEmpty()
                ? null
                : shallowCopyMap(r.getAuthConfig()));
        vo.setAppUrl(r.getAppUrl());
        vo.setEmbedType(r.getEmbedType());
        vo.setIcon(r.getIcon());
        vo.setScreenshots(r.getScreenshots() == null ? null : new ArrayList<>(r.getScreenshots()));
        vo.setDataType(r.getDataType());
        vo.setFormat(r.getFormat());
        vo.setRecordCount(r.getRecordCount());
        vo.setFileSize(r.getFileSize());
        vo.setTags(r.getTags() == null ? null : new ArrayList<>(r.getTags()));
        if ("skill".equalsIgnoreCase(normalizeType(vo.getResourceType()))) {
            vo.setMode(null);
            vo.setParentResourceId(null);
            vo.setDisplayTemplate(null);
            vo.setMaxConcurrency(null);
        }
    }

    private void applyDraftOverlayToPublishedDetail(ResourceManageVO vo, Long resourceId) {
        if (vo == null || resourceId == null) {
            return;
        }
        if (!ResourceLifecycleStateMachine.STATUS_PUBLISHED.equals(ResourceLifecycleStateMachine.normalizeStatus(vo.getStatus()))) {
            return;
        }
        Map<String, Object> draft = loadWorkingDraftMap(resourceId);
        if (draft.isEmpty()) {
            return;
        }
        ResourceUpsertRequest merged = mergeSnapshotOntoCurrent(vo, draft);
        copyUpsertOntoVo(vo, merged);
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
                    .title("资源已暂停对外开放")
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
        Map<String, Object> quality = computeQuality(resourceId, normalizedType);
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
                .qualityFactors(extractQualityFactors(quality))
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
     * 将当前 VO 转为更新请求体，供「快照写回」与 {@link #mergeSnapshotOntoCurrent} 叠加；目录标签依赖 tagIds（t_resource_tag_rel），关联资源显式拷贝以免被 sync 误删语义依赖 null。
     */
    private ResourceUpsertRequest voToUpsertRequest(ResourceManageVO vo) {
        ResourceUpsertRequest req = new ResourceUpsertRequest();
        req.setResourceType(vo.getResourceType());
        req.setResourceCode(vo.getResourceCode());
        req.setDisplayName(vo.getDisplayName());
        req.setDescription(vo.getDescription());
        req.setSourceType(vo.getSourceType());
        if (vo.getTagIds() != null) {
            req.setTagIds(new ArrayList<>(vo.getTagIds()));
        }
        req.setAccessPolicy(vo.getAccessPolicy());
        if (vo.getRelatedResourceIds() != null) {
            req.setRelatedResourceIds(new ArrayList<>(vo.getRelatedResourceIds()));
        }
        if (vo.getRelatedMcpResourceIds() != null) {
            req.setRelatedMcpResourceIds(new ArrayList<>(vo.getRelatedMcpResourceIds()));
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
        req.setManifest(vo.getManifest() == null || vo.getManifest().isEmpty() ? null : shallowCopyMap(vo.getManifest()));
        req.setEntryDoc(vo.getEntryDoc());
        req.setParentResourceId(vo.getParentResourceId());
        req.setDisplayTemplate(vo.getDisplayTemplate());
        req.setParametersSchema(vo.getParametersSchema() == null || vo.getParametersSchema().isEmpty()
                ? null
                : shallowCopyMap(vo.getParametersSchema()));
        req.setExecutionMode(vo.getExecutionMode());
        req.setContextPrompt(vo.getContextPrompt());
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
        if ("skill".equalsIgnoreCase(normalizeType(req.getResourceType()))) {
            req.setMode(null);
            req.setParentResourceId(null);
            req.setDisplayTemplate(null);
            req.setMaxConcurrency(null);
        }
        return req;
    }

    /**
     * 以当前主资源为基准合并版本快照；保留 {@code tagIds}、关联 ID、快照未给出的扩展字段。
     */
    private ResourceUpsertRequest mergeSnapshotOntoCurrent(ResourceManageVO current, Map<String, Object> snap) {
        ResourceUpsertRequest req = voToUpsertRequest(current);
        req.setResourceType(current.getResourceType());
        if (current.getTagIds() != null) {
            req.setTagIds(new ArrayList<>(current.getTagIds()));
        }
        if (current.getRelatedResourceIds() != null) {
            req.setRelatedResourceIds(new ArrayList<>(current.getRelatedResourceIds()));
        }
        if (current.getRelatedMcpResourceIds() != null) {
            req.setRelatedMcpResourceIds(new ArrayList<>(current.getRelatedMcpResourceIds()));
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
                    req.setUpstreamEndpoint(stringValue(snap.get("endpoint")).trim());
                }
                if (StringUtils.hasText(stringValue(snap.get("registrationProtocol")))) {
                    req.setRegistrationProtocol(stringValue(snap.get("registrationProtocol")).trim().toLowerCase(Locale.ROOT));
                } else if (StringUtils.hasText(stringValue(snap.get("invokeType")))) {
                    req.setRegistrationProtocol(stringValue(snap.get("invokeType")).trim().toLowerCase(Locale.ROOT));
                }
                if (snap.containsKey("upstreamAgentId")) {
                    req.setUpstreamAgentId(stringValue(snap.get("upstreamAgentId")));
                }
                if (snap.containsKey("credentialRef")) {
                    req.setCredentialRef(stringValue(snap.get("credentialRef")));
                }
                if (snap.containsKey("transformProfile")) {
                    req.setTransformProfile(stringValue(snap.get("transformProfile")));
                }
                if (StringUtils.hasText(stringValue(snap.get("modelAlias")))) {
                    req.setModelAlias(stringValue(snap.get("modelAlias")).trim());
                }
                if (snap.containsKey("enabled")) {
                    req.setEnabled(boolObject(snap.get("enabled")));
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
                if (snap.containsKey("executionMode") && StringUtils.hasText(stringValue(snap.get("executionMode")))) {
                    req.setExecutionMode(stringValue(snap.get("executionMode")).trim().toLowerCase(Locale.ROOT));
                }
                if (snap.containsKey("contextPrompt")) {
                    req.setContextPrompt(stringValue(snap.get("contextPrompt")));
                } else if (snap.containsKey("hostedSystemPrompt")) {
                    req.setContextPrompt(stringValue(snap.get("hostedSystemPrompt")));
                }
                if (snap.containsKey("parametersSchema")) {
                    req.setParametersSchema(shallowCopyMap(parseJsonMap(snap.get("parametersSchema"))));
                }
                Map<String, Object> specSnap = parseJsonMap(snap.get("spec"));
                if (!specSnap.isEmpty()) {
                    if (specSnap.containsKey("manifest")) {
                        req.setManifest(shallowCopyMap(parseJsonMap(specSnap.get("manifest"))));
                    }
                    if (specSnap.containsKey("entryDoc")) {
                        req.setEntryDoc(stringValue(specSnap.get("entryDoc")));
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
        if ("skill".equalsIgnoreCase(type)) {
            req.setMode(null);
            req.setParentResourceId(null);
            req.setDisplayTemplate(null);
            req.setMaxConcurrency(null);
        }
        return req;
    }

    private ResourceManageVO findResource(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, resource_type, resource_code, display_name, description, status, source_type, access_policy, created_by, create_time, update_time
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
        enrichCatalogTagIds(vo, id);
        enrichWorkingDraftFields(vo, id);
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
        vo.setAllowedActions(suggestActions(vo, viewerUserId));
        vo.setStatusHint(statusHintForVo(vo));
    }

    private List<String> suggestActions(ResourceManageVO vo, Long viewerUserId) {
        Long ownerId = vo.getCreatedBy();
        boolean owner = ownerId != null && viewerUserId != null && ownerId.equals(viewerUserId);
        String status = vo.getStatus();
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
                if (owner) {
                    actions.add("update");
                    actions.add("submit");
                    if (Boolean.TRUE.equals(vo.getPendingPublishedUpdate())) {
                        actions.add("withdraw");
                    }
                }
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

    private String statusHintForVo(ResourceManageVO vo) {
        if (vo == null) {
            return "状态待确认";
        }
        String s = vo.getStatus() == null ? "" : vo.getStatus().trim().toLowerCase(Locale.ROOT);
        if ("published".equals(s) && Boolean.TRUE.equals(vo.getPendingPublishedUpdate())) {
            return "已提交已发布变更审核：完成前线上仍为当前默认解析版本；可撤回后继续编辑草稿";
        }
        if ("published".equals(s) && Boolean.TRUE.equals(vo.getHasWorkingDraft())) {
            return "当前正在编辑草稿：保存后不会立即影响线上解析，请提交审核通过后方合并至默认版本";
        }
        return statusHintStatic(s, vo.getLastRejectReason());
    }

    private static String statusHintStatic(String status, String rejectReason) {
        String s = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "draft" -> "草稿态，可编辑后重新提审";
            case "pending_review" -> "审核进行中，可撤回到草稿";
            case "testing" -> "测试阶段，建议观察稳定性后发布";
            case "published" -> "已上架：可在平台内检索并经网关调用；不代表上游实例当前已启动，连通性以「运行健康」或健康检查/试调用为准";
            case "rejected" -> StringUtils.hasText(rejectReason) ? "已驳回：" + rejectReason : "已驳回，请修改后重提";
            case "deprecated" -> "已暂停对外开放，可修复后重新提审";
            default -> "状态待确认";
        };
    }

    private void enrichObservabilityFields(ResourceManageVO vo) {
        if (vo == null || vo.getId() == null) {
            return;
        }
        Map<String, Object> quality = computeQuality(vo.getId(), vo.getResourceType());
        vo.setHealthStatus(stringValue(quality.get("healthStatus")));
        vo.setCircuitState(stringValue(quality.get("circuitState")));
        vo.setCallabilityState(stringValue(quality.get("callabilityState")));
        vo.setCallabilityReason(stringValue(quality.get("callabilityReason")));
        vo.setCallable(Boolean.valueOf(stringValue(quality.get("callable"))));
        vo.setLastProbeAt(toDateTime(quality.get("lastProbeAt")));
        vo.setLastSuccessAt(toDateTime(quality.get("lastSuccessAt")));
        vo.setLastFailureAt(toDateTime(quality.get("lastFailureAt")));
        vo.setLastFailureReason(stringValue(quality.get("lastFailureReason")));
        vo.setConsecutiveSuccess(longValue(quality.get("consecutiveSuccess")));
        vo.setConsecutiveFailure(longValue(quality.get("consecutiveFailure")));
        vo.setProbeLatencyMs(longValue(quality.get("probeLatencyMs")));
        vo.setProbePayloadSummary(stringValue(quality.get("probePayloadSummary")));
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
        applyObservabilityDegradationHints(vo, quality);
    }

    /**
     * 熔断优先；最近一次网关调用若失败，写入 degradation 提示（供运营视图）。
     * 注意：不在此处把 {@code healthStatus} 打成 down——否则会与 {@link #computeQuality} 中
     * 「仅记录 lastInvokeFailureHint、不篡改健康探针结果」的策略冲突，导致广场列表长期「暂不可调用」。
     */
    private void applyObservabilityDegradationHints(ResourceManageVO vo, Map<String, Object> quality) {
        if (vo == null || quality == null) {
            return;
        }
        String invokeHint = stringValue(quality.get("lastInvokeFailureHint"));
        DegradationHintVO circuitOrHealth = buildDegradationHint(vo.getHealthStatus(), vo.getCircuitState());
        String c = vo.getCircuitState() == null ? "" : vo.getCircuitState().trim().toLowerCase(Locale.ROOT);
        if (circuitOrHealth != null && "open".equals(c)) {
            vo.setDegradationCode(circuitOrHealth.getDegradationCode());
            vo.setDegradationHint(circuitOrHealth.getUserFacingHint());
            return;
        }
        if (StringUtils.hasText(invokeHint)) {
            vo.setDegradationCode("RECENT_GATEWAY_INVOKE_FAILED");
            vo.setDegradationHint(invokeHint);
            return;
        }
        if (circuitOrHealth != null) {
            vo.setDegradationCode(circuitOrHealth.getDegradationCode());
            vo.setDegradationHint(circuitOrHealth.getUserFacingHint());
        }
    }

    private Map<String, Object> computeQuality(Long resourceId, String resourceType) {
        ResourceHealthSnapshotVO healthSnapshot = resourceHealthService.getSnapshot(resourceId);
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

        String health = healthSnapshot == null ? "unknown" : stringValue(healthSnapshot.getHealthStatus());
        String circuitState = healthSnapshot == null ? "unknown" : stringValue(healthSnapshot.getCircuitState());
        String callabilityState = healthSnapshot == null ? "unknown" : stringValue(healthSnapshot.getCallabilityState());
        String callabilityReason = healthSnapshot == null ? null : healthSnapshot.getCallabilityReason();

        Map<String, Object> factors = new LinkedHashMap<>();
        factors.put("successRate", successRate);
        factors.put("avgLatencyMs", avgLatencyMs);
        factors.put("totalCalls7d", totalCalls);
        factors.put("latencyFactor", latencyFactor);

        String lastInvokeFailureHint = null;
        List<Map<String, Object>> lastInvokeRows = jdbcTemplate.queryForList("""
                SELECT status, error_message, create_time
                FROM t_call_log
                WHERE agent_id = ?
                ORDER BY create_time DESC
                LIMIT 1
                """, String.valueOf(resourceId));
        if (!lastInvokeRows.isEmpty()) {
            Map<String, Object> li = lastInvokeRows.get(0);
            String st = stringValue(li.get("status"));
            if (StringUtils.hasText(st) && !"success".equalsIgnoreCase(st.trim())) {
                // 不因单次历史失败覆盖 t_resource_runtime_policy 健康状态：否则 MCP 已恢复仍会一直显示「暂不可调用」
                // （前端 isCatalogMcpCallable 视 healthStatus=down 为禁入）。提示见 lastInvokeFailureHint。
                String em = stringValue(li.get("error_message"));
                if (StringUtils.hasText(em)) {
                    String clip = em.length() > 220 ? em.substring(0, 217) + "..." : em;
                    lastInvokeFailureHint = "最近网关调用未成功：" + clip;
                } else {
                    lastInvokeFailureHint = "最近网关调用未成功（详见监控中心调用日志）";
                }
                factors.put("lastGatewayInvokeStatus", st);
                if (StringUtils.hasText(em)) {
                    factors.put("lastGatewayInvokeErrorPreview", em.length() > 500 ? em.substring(0, 497) + "..." : em);
                }
            } else if (StringUtils.hasText(st)) {
                factors.put("lastGatewayInvokeStatus", st);
            }
        }

        Map<String, Object> qualityBlock = new LinkedHashMap<>();
        qualityBlock.put("healthStatus", health != null ? health : "unknown");
        qualityBlock.put("circuitState", circuitState != null ? circuitState : "unknown");
        qualityBlock.put("callabilityState", callabilityState != null ? callabilityState : "unknown");
        qualityBlock.put("callabilityReason", callabilityReason);
        qualityBlock.put("qualityScore", qualityScore);
        qualityBlock.put("qualityFactors", factors);
        qualityBlock.put("lastInvokeFailureHint", lastInvokeFailureHint);
        if (healthSnapshot != null) {
            qualityBlock.put("lastProbeAt", healthSnapshot.getLastProbeAt());
            qualityBlock.put("lastSuccessAt", healthSnapshot.getLastSuccessAt());
            qualityBlock.put("lastFailureAt", healthSnapshot.getLastFailureAt());
            qualityBlock.put("lastFailureReason", healthSnapshot.getLastFailureReason());
            qualityBlock.put("probeLatencyMs", healthSnapshot.getProbeLatencyMs());
            qualityBlock.put("probePayloadSummary", healthSnapshot.getProbePayloadSummary());
            qualityBlock.put("consecutiveSuccess", healthSnapshot.getConsecutiveSuccess());
            qualityBlock.put("consecutiveFailure", healthSnapshot.getConsecutiveFailure());
            qualityBlock.put("probeStrategy", healthSnapshot.getProbeStrategy());
            qualityBlock.put("callable", healthSnapshot.getCallable());
        }
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
        String encryptedCredentialRef = resolveCredentialRefForUpsert(resourceId, request.getCredentialRef());
        int updated = jdbcTemplate.update("""
                        UPDATE t_resource_agent_ext
                        SET agent_type = ?, mode = ?, spec_json = CAST(? AS JSON), is_public = ?, hidden = ?, max_concurrency = ?, max_steps = ?, temperature = ?, system_prompt = ?, service_detail_md = ?,
                            registration_protocol = ?, upstream_endpoint = ?, upstream_agent_id = ?, credential_ref = ?, transform_profile = ?, model_alias = ?, enabled = ?
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
                request.getRegistrationProtocol(),
                request.getUpstreamEndpoint(),
                request.getUpstreamAgentId(),
                encryptedCredentialRef,
                request.getTransformProfile(),
                request.getModelAlias(),
                toBoolNumber(request.getEnabled()),
                resourceId);
        if (updated == 0) {
            jdbcTemplate.update("""
                            INSERT INTO t_resource_agent_ext(resource_id, agent_type, mode, spec_json, is_public, hidden, max_concurrency, max_steps, temperature, system_prompt, service_detail_md, registration_protocol, upstream_endpoint, upstream_agent_id, credential_ref, transform_profile, model_alias, enabled, featured, rating_avg, rating_count)
                            VALUES(?, ?, ?, CAST(? AS JSON), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0.00, 0)
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
                    serviceMd,
                    request.getRegistrationProtocol(),
                    request.getUpstreamEndpoint(),
                    request.getUpstreamAgentId(),
                    encryptedCredentialRef,
                    request.getTransformProfile(),
                    request.getModelAlias(),
                    toBoolNumber(request.getEnabled()));
        }
    }

    private void upsertSkillExt(Long resourceId, ResourceUpsertRequest request) {
        String manifestJson = writeJson(defaultMap(request.getManifest()));
        String serviceMd = resolveExtServiceDetailMd("t_resource_skill_ext", resourceId, request.getServiceDetailMd());
        String ctx = request.getContextPrompt() == null ? "" : request.getContextPrompt();
        int updated = jdbcTemplate.update("""
                        UPDATE t_resource_skill_ext
                        SET skill_type = ?, execution_mode = 'context',
                            manifest_json = CAST(? AS JSON), entry_doc = ?,
                            mode = NULL, parent_resource_id = NULL, display_template = NULL,
                            spec_json = CAST(? AS JSON), parameters_schema = CAST(? AS JSON), is_public = ?, max_concurrency = NULL,
                            service_detail_md = ?,
                            hosted_system_prompt = ?, hosted_user_template = NULL, hosted_default_model = NULL,
                            hosted_output_schema = CAST('{}' AS JSON), hosted_temperature = NULL
                        WHERE resource_id = ?
                        """,
                request.getSkillType().trim().toLowerCase(Locale.ROOT),
                manifestJson,
                request.getEntryDoc(),
                writeJson(defaultMap(request.getSpec())),
                writeJson(defaultMap(request.getParametersSchema())),
                toBoolNumber(request.getIsPublic()),
                serviceMd,
                ctx,
                resourceId);
        if (updated == 0) {
            jdbcTemplate.update("""
                            INSERT INTO t_resource_skill_ext(resource_id, skill_type, execution_mode, manifest_json, entry_doc, mode, parent_resource_id, display_template, spec_json, parameters_schema, is_public, max_concurrency, service_detail_md, hosted_system_prompt, hosted_user_template, hosted_default_model, hosted_output_schema, hosted_temperature)
                            VALUES(?, ?, 'context', CAST(? AS JSON), ?, NULL, NULL, NULL, CAST(? AS JSON), CAST(? AS JSON), ?, NULL, ?, ?, NULL, NULL, CAST('{}' AS JSON), NULL)
                            """,
                    resourceId,
                    request.getSkillType().trim().toLowerCase(Locale.ROOT),
                    manifestJson,
                    request.getEntryDoc(),
                    writeJson(defaultMap(request.getSpec())),
                    writeJson(defaultMap(request.getParametersSchema())),
                    toBoolNumber(request.getIsPublic()),
                    serviceMd,
                    ctx);
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

    /**
     * Skill 绑定的 MCP 须已存在、已发布、类型为 mcp，且与 Skill 同一创建者。
     */
    private void validateSkillBoundMcpsPublished(List<Long> mcpIds, Long ownerUserId) {
        if (mcpIds == null || mcpIds.isEmpty()) {
            return;
        }
        if (ownerUserId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "无法校验 Skill 绑定 MCP：缺少创建者");
        }
        for (Long mid : mcpIds) {
            if (mid == null || mid <= 0) {
                continue;
            }
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                            SELECT r.resource_type, r.status, r.created_by
                            FROM t_resource r
                            WHERE r.id = ? AND r.deleted = 0
                            LIMIT 1
                            """,
                    mid);
            if (rows.isEmpty()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "绑定的 MCP 不存在: " + mid);
            }
            Map<String, Object> row = rows.get(0);
            if (!"mcp".equalsIgnoreCase(stringValue(row.get("resource_type")))) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "绑定目标须为 MCP 资源: " + mid);
            }
            if (!ResourceLifecycleStateMachine.STATUS_PUBLISHED.equals(
                    ResourceLifecycleStateMachine.normalizeStatus(stringValue(row.get("status"))))) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "绑定的 MCP 须为已发布（published）状态: " + mid);
            }
            Long cb = longValue(row.get("created_by"));
            if (cb == null || !cb.equals(ownerUserId)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "仅能绑定本人登记的 MCP: " + mid);
            }
        }
    }

    private void ensureSkillReadyForSubmit(ResourceUpsertRequest merged) {
        if (merged == null || !"skill".equalsIgnoreCase(normalizeType(merged.getResourceType()))) {
            return;
        }
        String exec = merged.getExecutionMode() == null ? SKILL_EXEC_CONTEXT : merged.getExecutionMode().trim().toLowerCase(Locale.ROOT);
        if (!SKILL_EXEC_CONTEXT.equals(exec)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Skill 须为 context 模式");
        }
        if (!StringUtils.hasText(merged.getContextPrompt())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "contextPrompt（规范正文）不能为空");
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
                if (!StringUtils.hasText(request.getAgentType())) {
                    request.setAgentType("http_api");
                } else {
                    request.setAgentType(request.getAgentType().trim());
                }
                if (!StringUtils.hasText(request.getMode())) {
                    request.setMode("SUBAGENT");
                }
                if (request.getSpec() == null) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "agent spec 不能为 null（可传 {}）");
                }
                String protocol = defaultString(request.getRegistrationProtocol(), "openai_compatible")
                        .trim().toLowerCase(Locale.ROOT);
                if (!AGENT_REG_PROTOCOLS.contains(protocol)) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "registrationProtocol 不支持: " + protocol);
                }
                request.setRegistrationProtocol(protocol);
                if (!StringUtils.hasText(request.getUpstreamEndpoint())
                        && request.getSpec() != null
                        && StringUtils.hasText(stringValue(request.getSpec().get("url")))) {
                    request.setUpstreamEndpoint(stringValue(request.getSpec().get("url")).trim());
                }
                requireText(request.getUpstreamEndpoint(), "upstreamEndpoint 不能为空");
                if (!isHttpOrHttpsUrl(request.getUpstreamEndpoint().trim())) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "upstreamEndpoint 必须是 http(s) URL");
                }
                request.setUpstreamEndpoint(request.getUpstreamEndpoint().trim());
                if (!StringUtils.hasText(request.getModelAlias()) && StringUtils.hasText(request.getResourceCode())) {
                    request.setModelAlias(request.getResourceCode().trim());
                }
                requireText(request.getModelAlias(), "modelAlias 不能为空");
                request.setModelAlias(request.getModelAlias().trim());
                if (request.getEnabled() == null) {
                    request.setEnabled(Boolean.TRUE);
                }
            }
            case "skill" -> {
                String exec = request.getExecutionMode() == null ? SKILL_EXEC_CONTEXT : request.getExecutionMode().trim().toLowerCase(Locale.ROOT);
                if (!SKILL_EXEC_CONTEXT.equals(exec)) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "平台 Skill 仅支持 context 模式");
                }
                requireText(request.getContextPrompt(), "须填写 contextPrompt（规范正文）");
                if (StringUtils.hasText(request.getSkillType())) {
                    String st = request.getSkillType().trim().toLowerCase(Locale.ROOT);
                    if (!SKILL_TYPE_ALLOWED.contains(st)) {
                        throw new BusinessException(ResultCode.PARAM_ERROR, "skillType 仅支持 context_v1");
                    }
                }
                request.setSkillType("context_v1");
                request.setExecutionMode(SKILL_EXEC_CONTEXT);
                if (StringUtils.hasText(request.getEntryDoc())) {
                    request.setEntryDoc(request.getEntryDoc().trim());
                }
            }
            case "mcp" -> {
                Map<String, Object> ac = request.getAuthConfig() == null ? Map.of() : request.getAuthConfig();
                String transport = stringValue(ac.get("transport"));
                if ("stdio".equalsIgnoreCase(transport)) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "MCP 注册不再支持 stdio，请先将服务暴露为 http(s)/ws(s) 地址");
                }
                requireText(request.getEndpoint(), "endpoint 不能为空");
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

    private void ensureUniqueAgentModelAlias(Long ownerUserId, String modelAlias, Long excludeResourceId) {
        if (!StringUtils.hasText(modelAlias) || ownerUserId == null) {
            return;
        }
        String alias = modelAlias.trim();
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(1)
                        FROM t_resource r
                        JOIN t_resource_agent_ext ae ON ae.resource_id = r.id
                        WHERE r.deleted = 0
                          AND r.resource_type = 'agent'
                          AND r.created_by = ?
                          AND ae.model_alias = ?
                          AND (? IS NULL OR r.id <> ?)
                        """,
                Integer.class,
                ownerUserId,
                alias,
                excludeResourceId,
                excludeResourceId);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.DUPLICATE_NAME, "当前租户下 modelAlias 已存在");
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
                snapshot.put("invokeType", request.getRegistrationProtocol());
                snapshot.put("endpoint", request.getUpstreamEndpoint());
                snapshot.put("spec", defaultMap(request.getSpec()));
                snapshot.put("registrationProtocol", request.getRegistrationProtocol());
                snapshot.put("upstreamEndpoint", request.getUpstreamEndpoint());
                snapshot.put("upstreamAgentId", request.getUpstreamAgentId());
                snapshot.put("credentialRef", maskCredentialRef(request.getCredentialRef()));
                snapshot.put("transformProfile", request.getTransformProfile());
                snapshot.put("modelAlias", request.getModelAlias());
                snapshot.put("enabled", request.getEnabled());
                if (StringUtils.hasText(request.getServiceDetailMd())) {
                    snapshot.put("serviceDetailMd", request.getServiceDetailMd().trim());
                }
            }
            case "skill" -> {
                snapshot.put("invokeType", "portal_context");
                snapshot.put("endpoint", null);
                snapshot.put("executionMode", SKILL_EXEC_CONTEXT);
                snapshot.put("contextPrompt", request.getContextPrompt());
                snapshot.put("parametersSchema", defaultMap(request.getParametersSchema()));
                Map<String, Object> spec = new LinkedHashMap<>();
                if (request.getManifest() != null && !request.getManifest().isEmpty()) {
                    spec.put("manifest", request.getManifest());
                }
                if (StringUtils.hasText(request.getEntryDoc())) {
                    spec.put("entryDoc", request.getEntryDoc());
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
                                "SELECT spec_json, service_detail_md, registration_protocol, upstream_endpoint, upstream_agent_id, credential_ref, transform_profile, model_alias, enabled FROM t_resource_agent_ext WHERE resource_id = ? LIMIT 1", resourceId)
                        .stream().findFirst().orElse(Map.of());
                Map<String, Object> spec = parseJsonMap(ext.get("spec_json"));
                snapshot.put("invokeType", stringValue(ext.get("registration_protocol")));
                snapshot.put("endpoint", stringValue(ext.get("upstream_endpoint")));
                snapshot.put("spec", spec);
                snapshot.put("registrationProtocol", stringValue(ext.get("registration_protocol")));
                snapshot.put("upstreamEndpoint", stringValue(ext.get("upstream_endpoint")));
                snapshot.put("upstreamAgentId", stringValue(ext.get("upstream_agent_id")));
                snapshot.put("credentialRef", maskCredentialRef(stringValue(ext.get("credential_ref"))));
                snapshot.put("transformProfile", stringValue(ext.get("transform_profile")));
                snapshot.put("modelAlias", stringValue(ext.get("model_alias")));
                snapshot.put("enabled", boolObject(ext.get("enabled")));
                if (StringUtils.hasText(stringValue(ext.get("service_detail_md")))) {
                    snapshot.put("serviceDetailMd", stringValue(ext.get("service_detail_md")).trim());
                }
            }
            case "skill" -> {
                Map<String, Object> ext = jdbcTemplate.queryForList("""
                                SELECT skill_type, execution_mode, manifest_json, entry_doc, spec_json, service_detail_md,
                                hosted_system_prompt, parameters_schema
                                FROM t_resource_skill_ext WHERE resource_id = ? LIMIT 1
                                """, resourceId)
                        .stream().findFirst().orElse(Map.of());
                snapshot.put("invokeType", "portal_context");
                snapshot.put("endpoint", null);
                snapshot.put("executionMode", SKILL_EXEC_CONTEXT);
                snapshot.put("contextPrompt", stringValue(ext.get("hosted_system_prompt")));
                snapshot.put("parametersSchema", parseJsonMap(ext.get("parameters_schema")));
                Map<String, Object> spec = new LinkedHashMap<>();
                Map<String, Object> manifest = parseJsonMap(ext.get("manifest_json"));
                if (!manifest.isEmpty()) {
                    spec.put("manifest", manifest);
                }
                if (StringUtils.hasText(stringValue(ext.get("entry_doc")))) {
                    spec.put("entryDoc", stringValue(ext.get("entry_doc")));
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

    @SuppressWarnings("unchecked")
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

    @SuppressWarnings("unchecked")
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractQualityFactors(Map<String, Object> quality) {
        Object raw = quality.get("qualityFactors");
        if (raw == null) {
            return Map.of();
        }
        if (raw instanceof Map) {
            return (Map<String, Object>) raw;
        }
        return Map.of();
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
        switch (type) {
            case "agent" -> {
                List<Long> mcps = loadRelationTargetIds(resourceId, "agent_depends_mcp");
                if (!mcps.isEmpty()) {
                    vo.setRelatedMcpResourceIds(mcps);
                }
            }
            case "skill" -> {
                List<Long> skillMcps = loadRelationTargetIds(resourceId, "skill_depends_mcp");
                if (!skillMcps.isEmpty()) {
                    vo.setRelatedMcpResourceIds(skillMcps);
                }
            }
            case "app" -> {
                List<Long> rel = loadRelationTargetIds(resourceId, "app_depends_resource");
                if (!rel.isEmpty()) {
                    vo.setRelatedResourceIds(rel);
                }
            }
            default -> {
            }
        }
    }

    private List<Long> loadRelationTargetIds(Long resourceId, String relationType) {
        return jdbcTemplate.query(
                "SELECT to_resource_id FROM t_resource_relation WHERE from_resource_id = ? AND relation_type = ? ORDER BY to_resource_id",
                (rs, rowNum) -> rs.getLong("to_resource_id"),
                resourceId,
                relationType);
    }

    private void enrichAgentFields(ResourceManageVO vo, Long resourceId) {
        var rows = jdbcTemplate.queryForList("""
                        SELECT agent_type, mode, spec_json, is_public, hidden, max_concurrency, max_steps, temperature, system_prompt, service_detail_md,
                               registration_protocol, upstream_endpoint, upstream_agent_id, credential_ref, transform_profile, model_alias, enabled
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
        vo.setRegistrationProtocol(stringValue(r.get("registration_protocol")));
        vo.setUpstreamEndpoint(stringValue(r.get("upstream_endpoint")));
        vo.setUpstreamAgentId(stringValue(r.get("upstream_agent_id")));
        vo.setCredentialRef(maskCredentialRef(stringValue(r.get("credential_ref"))));
        vo.setTransformProfile(stringValue(r.get("transform_profile")));
        vo.setModelAlias(stringValue(r.get("model_alias")));
        vo.setEnabled(boolObject(r.get("enabled")));
        vo.setServiceDetailMd(stringValue(r.get("service_detail_md")));
    }

    private void enrichSkillFields(ResourceManageVO vo, Long resourceId) {
        var rows = jdbcTemplate.queryForList("""
                        SELECT skill_type, execution_mode, manifest_json, entry_doc, mode, parent_resource_id, display_template, spec_json, parameters_schema, is_public, max_concurrency,
                               service_detail_md,
                               hosted_system_prompt
                        FROM t_resource_skill_ext WHERE resource_id = ? LIMIT 1
                        """,
                resourceId);
        if (rows.isEmpty()) {
            return;
        }
        Map<String, Object> r = rows.get(0);
        vo.setSkillType(stringValue(r.get("skill_type")));
        vo.setManifest(parseJsonMap(r.get("manifest_json")));
        vo.setEntryDoc(stringValue(r.get("entry_doc")));
        vo.setSpec(parseJsonMap(r.get("spec_json")));
        vo.setParametersSchema(parseJsonMap(r.get("parameters_schema")));
        vo.setIsPublic(boolObject(r.get("is_public")));
        vo.setServiceDetailMd(stringValue(r.get("service_detail_md")));
        String em = stringValue(r.get("execution_mode"));
        vo.setExecutionMode(StringUtils.hasText(em) ? em.trim().toLowerCase(Locale.ROOT) : SKILL_EXEC_CONTEXT);
        vo.setContextPrompt(stringValue(r.get("hosted_system_prompt")));
        vo.setMode(null);
        vo.setParentResourceId(null);
        vo.setDisplayTemplate(null);
        vo.setMaxConcurrency(null);
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

    private String encryptCredentialRef(String credentialRef) {
        if (!StringUtils.hasText(credentialRef)) {
            return null;
        }
        return sensitiveDataEncryptor.encrypt(credentialRef.trim());
    }

    private String resolveCredentialRefForUpsert(Long resourceId, String credentialRefRaw) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT credential_ref FROM t_resource_agent_ext WHERE resource_id = ? LIMIT 1",
                resourceId);
        String existing = rows.isEmpty() ? null : stringValue(rows.get(0).get("credential_ref"));
        if (!StringUtils.hasText(credentialRefRaw)) {
            return existing;
        }
        String v = credentialRefRaw.trim();
        if (v.contains("****")) {
            return existing;
        }
        return encryptCredentialRef(v);
    }

    private String maskCredentialRef(String encryptedCredentialRef) {
        if (!StringUtils.hasText(encryptedCredentialRef)) {
            return null;
        }
        String plain;
        try {
            plain = sensitiveDataEncryptor.decrypt(encryptedCredentialRef);
        } catch (RuntimeException ex) {
            plain = encryptedCredentialRef;
        }
        if (!StringUtils.hasText(plain)) {
            return "****";
        }
        String p = plain.trim();
        if (p.length() <= 8) {
            return "****";
        }
        return p.substring(0, 4) + "****" + p.substring(p.length() - 2);
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
     * 维护 t_resource_tag_rel：{@code request.tagIds} 须经存在性校验且标签桶位为当前 resourceType 或 general；
     * dataset 另可按 {@code request.tags} 名称解析已存在标签（不自动建标签）。
     */
    private void syncResourceTagRels(Long resourceId, String resourceType, ResourceUpsertRequest request) {
        jdbcTemplate.update("DELETE FROM t_resource_tag_rel WHERE resource_id = ? AND resource_type = ?",
                resourceId, resourceType);
        LinkedHashSet<Long> collected = new LinkedHashSet<>();
        if (request.getTagIds() != null) {
            for (Long candidate : request.getTagIds()) {
                if (candidate == null) {
                    continue;
                }
                if (tagAllowedForResourceType(resourceType, candidate)) {
                    collected.add(candidate);
                }
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
                    collected.add(found.get(0));
                }
            }
        }
        for (Long tagId : collected) {
            jdbcTemplate.update(
                    "INSERT INTO t_resource_tag_rel(resource_type, resource_id, tag_id) VALUES(?, ?, ?)",
                    resourceType, resourceId, tagId);
        }
    }

    /** t_tag.category 为 general 或与资源类型一致时才写入 rel，避免跨桶误关联。 */
    private boolean tagAllowedForResourceType(String resourceType, Long tagId) {
        Integer cnt = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM t_tag WHERE id = ?",
                Integer.class, tagId);
        if (cnt == null || cnt == 0) {
            return false;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT category FROM t_tag WHERE id = ? LIMIT 1", tagId);
        if (rows.isEmpty()) {
            return false;
        }
        String cat = stringValue(rows.get(0).get("category"));
        if (!StringUtils.hasText(cat)) {
            return false;
        }
        String c = cat.trim().toLowerCase(Locale.ROOT);
        return "general".equals(c) || c.equals(normalizeType(resourceType));
    }

    private void enrichCatalogTagIds(ResourceManageVO vo, Long resourceId) {
        if (vo == null || resourceId == null || !StringUtils.hasText(vo.getResourceType())) {
            return;
        }
        List<Long> ids = jdbcTemplate.query(
                "SELECT t.id FROM t_resource_tag_rel rt INNER JOIN t_tag t ON t.id = rt.tag_id "
                        + "WHERE rt.resource_id = ? AND rt.resource_type = ? ORDER BY t.name",
                (rs, i) -> rs.getLong(1),
                resourceId, vo.getResourceType());
        vo.setTagIds(ids.isEmpty() ? List.of() : ids);
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

    private void syncAllBindings(Long resourceId, String resourceType, ResourceUpsertRequest request) {
        String rt = normalizeType(resourceType);
        Long bindingOwnerUserId = requireResourceCreatorForBindings(resourceId);
        if ("agent".equals(rt)) {
            if (request.getRelatedMcpResourceIds() != null) {
                assertBindingTargetsOwnedBy(resourceId, bindingOwnerUserId, request.getRelatedMcpResourceIds(), "mcp");
                replaceRelations(resourceId, "agent_depends_mcp", request.getRelatedMcpResourceIds());
            }
        } else if ("skill".equals(rt)) {
            if (request.getRelatedMcpResourceIds() != null) {
                validateSkillBoundMcpsPublished(request.getRelatedMcpResourceIds(), bindingOwnerUserId);
                assertBindingTargetsOwnedBy(resourceId, bindingOwnerUserId, request.getRelatedMcpResourceIds(), "mcp");
                replaceRelations(resourceId, "skill_depends_mcp", request.getRelatedMcpResourceIds());
            }
        } else if ("app".equals(rt)) {
            if (request.getRelatedResourceIds() != null) {
                assertBindingTargetsOwnedBy(resourceId, bindingOwnerUserId, request.getRelatedResourceIds(), null);
                replaceRelations(resourceId, "app_depends_resource", request.getRelatedResourceIds());
            }
        }
    }

    /**
     * 关联绑定仅允许指向与<strong>当前资源</strong>同一 {@code created_by} 的登记资源（「本人资源」），
     * 与操作者是否为管理员无关，避免代操作时绑到操作者名下资源。
     */
    private Long requireResourceCreatorForBindings(Long resourceId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT created_by FROM t_resource WHERE id = ? AND deleted = 0 LIMIT 1
                """, resourceId);
        if (rows.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "资源不存在");
        }
        Long createdBy = longValue(rows.get(0).get("created_by"));
        if (createdBy == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "资源缺少创建者信息，无法校验关联绑定");
        }
        return createdBy;
    }

    /**
     * @param expectedResourceType 为 {@code null} 时不校验目标类型（如应用泛型关联）
     */
    private void assertBindingTargetsOwnedBy(Long fromResourceId, Long ownerUserId, List<Long> toIds,
                                            String expectedResourceType) {
        if (toIds == null || toIds.isEmpty()) {
            return;
        }
        for (Long toId : toIds) {
            if (toId == null) {
                continue;
            }
            if (fromResourceId != null && toId.equals(fromResourceId)) {
                continue;
            }
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT id, resource_type, created_by FROM t_resource WHERE id = ? AND deleted = 0 LIMIT 1
                    """, toId);
            if (rows.isEmpty()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "绑定目标资源不存在: " + toId);
            }
            Map<String, Object> r = rows.get(0);
            if (expectedResourceType != null) {
                String trt = normalizeType(stringValue(r.get("resource_type")));
                if (!expectedResourceType.equalsIgnoreCase(trt)) {
                    throw new BusinessException(ResultCode.PARAM_ERROR,
                            "绑定目标类型不符合（期望 " + expectedResourceType + "）: " + toId);
                }
            }
            Long targetCreator = longValue(r.get("created_by"));
            if (targetCreator == null || !targetCreator.equals(ownerUserId)) {
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        "仅可绑定与当前资源同一创建者登记的资源（本人资源）: " + toId);
            }
        }
    }

    private void replaceRelations(Long fromResourceId, String relationType, List<Long> toIds) {
        jdbcTemplate.update("DELETE FROM t_resource_relation WHERE from_resource_id = ? AND relation_type = ?",
                fromResourceId, relationType);
        if (toIds == null || toIds.isEmpty()) {
            return;
        }
        for (Long toId : toIds) {
            if (toId == null || toId.equals(fromResourceId)) {
                continue;
            }
            jdbcTemplate.update("""
                    INSERT INTO t_resource_relation(from_resource_id, to_resource_id, relation_type, create_time)
                    VALUES(?, ?, ?, NOW())
                    """, fromResourceId, toId, relationType);
        }
    }

    /** 向所有审核员推送通知（不按部门过滤；待审项不独占派给某人，全体 reviewer 共用同一全平台队列）。 */
    private void notifyReviewers(String type, String title, String body, Long resourceId) {
        // legacy shim: audit submission notifications are now routed through
        // SystemNotificationFacade so submitters and all audit recipients share
        // the same after-commit delivery path.
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

