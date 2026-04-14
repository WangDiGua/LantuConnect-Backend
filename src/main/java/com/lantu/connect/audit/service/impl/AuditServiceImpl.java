package com.lantu.connect.audit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lantu.connect.audit.entity.AuditItem;
import com.lantu.connect.audit.mapper.AuditItemMapper;
import com.lantu.connect.audit.service.AuditService;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.PageResults;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.security.CasbinAuthorizationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.common.util.ListQueryKeyword;
import com.lantu.connect.common.util.UserDisplayNameResolver;
import com.lantu.connect.gateway.service.ResourceRegistryService;
import com.lantu.connect.gateway.service.support.ResourceLifecycleStateMachine;
import com.lantu.connect.gateway.security.ResourceInvokeGrantService;
import com.lantu.connect.gateway.security.AgentApiKeyService;
import com.lantu.connect.notification.service.NotificationEventCodes;
import com.lantu.connect.notification.service.SystemNotificationFacade;
import com.lantu.connect.realtime.AuditPendingPushDebouncer;
import com.lantu.connect.monitoring.service.ResourceHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 审核Audit服务实现：
 * 1. 审核员（reviewer）或平台超管执行 approve / reject（pending_review → testing / rejected）
 * 2. publish（testing → published）：资源 owner、全平台审核员（reviewer）或 platform_admin/admin（与 {@link com.lantu.connect.gateway.security.ResourceInvokeGrantService#ensureMayPublishAuditedResource} 一致）
 *
 * 审核队列不按部门隔离；具备审核角色的账号可查看全平台待审项。
 * 通过、驳回、发布对 {@code t_audit_item} 与 {@code t_resource} 使用「期望状态」条件更新（乐观并发控制），避免多名审核员重复处理同一条。
 *
 * <p>接口路径中的 {id} 支持两种含义（兼容前端）：① 资源主键 {@code t_audit_item.target_id}；
 * ② 审核队列表主键 {@code t_audit_item.id}。优先按资源 ID + 当前状态匹配最新一条。</p>
 */
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private static final String STATUS_PENDING = "pending_review";
    private static final String STATUS_TESTING = "testing";
    private static final String STATUS_PUBLISHED = "published";
    private static final String STATUS_REJECTED = "rejected";
    private static final String STATUS_MERGED_LIVE = "merged_live";
    private static final String AUDIT_KIND_PUBLISHED_UPDATE = "published_update";
    /** 平台强制下架后，关联审核队列表记状态（若有已 published 行） */
    private static final String STATUS_PLATFORM_FORCE_DEPRECATED = "platform_force_deprecated";
    private static final String TARGET_AGENT = "agent";
    private static final String TARGET_SKILL = "skill";
    private static final Set<String> SUPPORTED_TARGET_TYPES = Set.of("agent", "skill", "mcp", "app", "dataset");

    private static final String MSG_AUDIT_CONCURRENT = "该审核项已被其他审核员处理或状态已变更，请刷新列表";

    private final AuditItemMapper auditItemMapper;
    private final JdbcTemplate jdbcTemplate;
    private final SystemNotificationFacade systemNotificationFacade;
    private final UserDisplayNameResolver userDisplayNameResolver;
    private final ResourceInvokeGrantService resourceInvokeGrantService;
    private final AgentApiKeyService agentApiKeyService;
    private final CasbinAuthorizationService casbinAuthorizationService;
    private final ResourceRegistryService resourceRegistryService;
    private final ResourceHealthService resourceHealthService;
    private final ObjectMapper objectMapper;
    private final AuditPendingPushDebouncer auditPendingPushDebouncer;

    @Override
    public PageResult<AuditItem> pagePendingAgents(Long operatorUserId, int page, int pageSize) {
        return pagePendingResources(operatorUserId, TARGET_AGENT, null, null, page, pageSize);
    }

    @Override
    public PageResult<AuditItem> pagePendingSkills(Long operatorUserId, int page, int pageSize) {
        return pagePendingResources(operatorUserId, TARGET_SKILL, null, null, page, pageSize);
    }

    @Override
    public PageResult<AuditItem> pagePendingResources(Long operatorUserId, String resourceType, String status, String keyword, int page, int pageSize) {
        String normalizedType = normalizeTargetType(resourceType);

        Page<AuditItem> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<AuditItem> wrapper = new LambdaQueryWrapper<AuditItem>()
                .eq(StringUtils.hasText(normalizedType), AuditItem::getTargetType, normalizedType)
                .orderByDesc(AuditItem::getSubmitTime);

        if (!StringUtils.hasText(status)) {
            wrapper.eq(AuditItem::getStatus, STATUS_PENDING);
        } else if ("all".equalsIgnoreCase(status.trim())) {
            // 不按状态过滤
        } else {
            wrapper.eq(AuditItem::getStatus, status.trim().toLowerCase());
        }

        String kw = ListQueryKeyword.normalize(keyword);
        if (kw != null) {
            String likeParam = "%" + kw + "%";
            wrapper.and(q -> {
                q.like(AuditItem::getDisplayName, kw)
                        .or()
                        .like(AuditItem::getAgentName, kw)
                        .or()
                        .like(AuditItem::getDescription, kw)
                        .or()
                        .like(AuditItem::getTargetType, kw)
                        .or()
                        .like(AuditItem::getAgentType, kw)
                        .or()
                        .like(AuditItem::getSourceType, kw)
                        .or()
                        .like(AuditItem::getSubmitter, kw)
                        .or()
                        .apply("CAST(target_id AS CHAR) LIKE {0}", likeParam);
                try {
                    long id = Long.parseLong(kw);
                    q.or().eq(AuditItem::getId, id).or().eq(AuditItem::getTargetId, id);
                } catch (NumberFormatException ignored) {
                    // not numeric id search
                }
            });
        }

        Page<AuditItem> result = auditItemMapper.selectPage(p, wrapper);
        enrichNames(result.getRecords());
        return PageResults.from(result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveAgent(Long id, Long reviewerId) {
        approve(id, TARGET_AGENT, reviewerId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveSkill(Long id, Long reviewerId) {
        approve(id, TARGET_SKILL, reviewerId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveResource(Long id, Long reviewerId) {
        approve(id, null, reviewerId);
    }

    private void approve(Long id, String expectedTargetType, Long reviewerId) {
        AuditItem item = requirePending(id, expectedTargetType);
        if (AUDIT_KIND_PUBLISHED_UPDATE.equalsIgnoreCase(String.valueOf(item.getAuditKind()).trim())) {
            Map<String, Object> snap = parseSnapshotPayload(item.getPayloadJson());
            resourceRegistryService.applyPublishedUpdateFromAudit(reviewerId, item.getTargetId(), snap);
            int n = auditItemMapper.update(null, new LambdaUpdateWrapper<AuditItem>()
                    .eq(AuditItem::getId, item.getId())
                    .eq(AuditItem::getStatus, STATUS_PENDING)
                    .set(AuditItem::getStatus, STATUS_MERGED_LIVE)
                    .set(AuditItem::getReviewerId, reviewerId)
                    .set(AuditItem::getReviewTime, LocalDateTime.now()));
            if (n != 1) {
                throw new BusinessException(ResultCode.CONFLICT, MSG_AUDIT_CONCURRENT);
            }
            notifySubmitter(item, NotificationEventCodes.AUDIT_APPROVED,
                    "已发布资源变更已合并",
                    "您的资源「" + item.getDisplayName() + "」的配置变更已通过审核并合并至线上默认解析版本。");
            auditPendingPushDebouncer.requestFlush();
            return;
        }
        int n = auditItemMapper.update(null, new LambdaUpdateWrapper<AuditItem>()
                .eq(AuditItem::getId, item.getId())
                .eq(AuditItem::getStatus, STATUS_PENDING)
                .set(AuditItem::getStatus, STATUS_TESTING)
                .set(AuditItem::getReviewerId, reviewerId)
                .set(AuditItem::getReviewTime, LocalDateTime.now()));
        if (n != 1) {
            throw new BusinessException(ResultCode.CONFLICT, MSG_AUDIT_CONCURRENT);
        }
        syncResourceStatusIf(item, STATUS_TESTING, ResourceLifecycleStateMachine.STATUS_PENDING_REVIEW);

        notifySubmitter(item, NotificationEventCodes.AUDIT_APPROVED,
                "资源审核通过",
                "您的资源「" + item.getDisplayName() + "」已通过审核，请在资源中心对处于测试灰度（testing）的资源执行「发布上线」。");
        auditPendingPushDebouncer.requestFlush();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectAgent(Long id, String reason, Long reviewerId) {
        reject(id, TARGET_AGENT, reason, reviewerId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectSkill(Long id, String reason, Long reviewerId) {
        reject(id, TARGET_SKILL, reason, reviewerId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectResource(Long id, String reason, Long reviewerId) {
        reject(id, null, reason, reviewerId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishAgent(Long id, Long reviewerId) {
        publish(id, TARGET_AGENT, reviewerId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishSkill(Long id, Long reviewerId) {
        publish(id, TARGET_SKILL, reviewerId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishResource(Long id, Long reviewerId) {
        publish(id, null, reviewerId);
    }

    private void reject(Long id, String expectedTargetType, String reason, Long reviewerId) {
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException(ResultCode.REJECT_REASON_REQUIRED);
        }
        AuditItem item = requirePending(id, expectedTargetType);
        int n = auditItemMapper.update(null, new LambdaUpdateWrapper<AuditItem>()
                .eq(AuditItem::getId, item.getId())
                .eq(AuditItem::getStatus, STATUS_PENDING)
                .set(AuditItem::getStatus, STATUS_REJECTED)
                .set(AuditItem::getRejectReason, reason)
                .set(AuditItem::getReviewerId, reviewerId)
                .set(AuditItem::getReviewTime, LocalDateTime.now()));
        if (n != 1) {
            throw new BusinessException(ResultCode.CONFLICT, MSG_AUDIT_CONCURRENT);
        }
        if (AUDIT_KIND_PUBLISHED_UPDATE.equalsIgnoreCase(String.valueOf(item.getAuditKind()).trim())) {
            restorePublishedUpdateDraft(item);
            notifySubmitter(item, NotificationEventCodes.AUDIT_REJECTED,
                    "已发布资源变更被驳回",
                    "您的资源「" + item.getDisplayName() + "」配置变更未通过审核（线上未变），原因：" + reason
                            + "。草稿已恢复到资源中心，请修改后重新提交。");
            auditPendingPushDebouncer.requestFlush();
            return;
        }
        syncResourceStatusIf(item, STATUS_REJECTED, ResourceLifecycleStateMachine.STATUS_PENDING_REVIEW);

        notifySubmitter(item, NotificationEventCodes.AUDIT_REJECTED,
                "资源审核被驳回",
                "您的资源「" + item.getDisplayName() + "」审核未通过，原因：" + reason);
        auditPendingPushDebouncer.requestFlush();
    }

    private Map<String, Object> parseSnapshotPayload(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Map.of();
        }
        try {
            Map<String, Object> m = objectMapper.readValue(raw, new TypeReference<LinkedHashMap<String, Object>>() {});
            return m == null ? Map.of() : m;
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "审核载荷 JSON 解析失败");
        }
    }

    private void restorePublishedUpdateDraft(AuditItem item) {
        if (item.getTargetId() == null || !StringUtils.hasText(item.getPayloadJson())) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(parseSnapshotPayload(item.getPayloadJson()));
            jdbcTemplate.update("""
                            INSERT INTO t_resource_draft(resource_id, draft_json, audit_tier)
                            VALUES(?, CAST(? AS JSON), ?)
                            ON DUPLICATE KEY UPDATE draft_json = VALUES(draft_json), audit_tier = VALUES(audit_tier), update_time = CURRENT_TIMESTAMP
                            """,
                    item.getTargetId(), json, "medium");
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "恢复草稿失败");
        }
    }

    private AuditItem resolvePreferredAuditItem(Long pathId, String expectedTargetType, String requiredStatus) {
        if (pathId == null) {
            return null;
        }
        AuditItem byId = auditItemMapper.selectById(pathId);
        if (byId != null) {
            if (StringUtils.hasText(expectedTargetType) && !expectedTargetType.equals(byId.getTargetType())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "目标类型不匹配");
            }
            return requiredStatus.equals(byId.getStatus()) ? byId : null;
        }
        return resolveAuditItemByPathId(pathId, expectedTargetType, requiredStatus);
    }

    private AuditItem requirePending(Long id, String expectedTargetType) {
        AuditItem item = resolvePreferredAuditItem(id, expectedTargetType, STATUS_PENDING);
        if (item == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "审核项不存在");
        }
        normalizeTargetType(item.getTargetType());
        return item;
    }

    /**
     * 按路径 id 解析审核行：先 {@code target_id} + 期望状态（资源 ID），再回退 {@code t_audit_item.id}。
     */
    private AuditItem resolveAuditItemByPathId(Long pathId, String expectedTargetType, String requiredStatus) {
        if (pathId == null) {
            return null;
        }
        LambdaQueryWrapper<AuditItem> byTarget = new LambdaQueryWrapper<AuditItem>()
                .eq(AuditItem::getTargetId, pathId)
                .eq(AuditItem::getStatus, requiredStatus);
        if (StringUtils.hasText(expectedTargetType)) {
            byTarget.eq(AuditItem::getTargetType, expectedTargetType);
        }
        byTarget.orderByDesc(AuditItem::getSubmitTime).last("LIMIT 1");
        AuditItem item = auditItemMapper.selectOne(byTarget);
        if (item != null) {
            return item;
        }
        item = auditItemMapper.selectById(pathId);
        if (item == null) {
            return null;
        }
        if (StringUtils.hasText(expectedTargetType) && !expectedTargetType.equals(item.getTargetType())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "目标类型不匹配");
        }
        if (!requiredStatus.equals(item.getStatus())) {
            return null;
        }
        return item;
    }

    private void publish(Long id, String expectedTargetType, Long reviewerId) {
        AuditItem item = resolvePreferredAuditItem(id, expectedTargetType, STATUS_TESTING);
        if (item == null) {
            AuditItem hint = auditItemMapper.selectOne(new LambdaQueryWrapper<AuditItem>()
                    .eq(AuditItem::getTargetId, id)
                    .eq(StringUtils.hasText(expectedTargetType), AuditItem::getTargetType, expectedTargetType)
                    .orderByDesc(AuditItem::getSubmitTime)
                    .last("LIMIT 1"));
            if (hint == null) {
                hint = auditItemMapper.selectById(id);
            }
            if (hint != null) {
                if (StringUtils.hasText(expectedTargetType) && !expectedTargetType.equals(hint.getTargetType())) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "目标类型不匹配");
                }
                if (!STATUS_TESTING.equals(hint.getStatus())) {
                    throw new BusinessException(ResultCode.ILLEGAL_STATE_TRANSITION, "仅 testing 状态可发布");
                }
                item = hint;
            }
        }
        if (item == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "审核项不存在");
        }
        normalizeTargetType(item.getTargetType());
        resourceInvokeGrantService.ensureMayPublishAuditedResource(reviewerId, item.getTargetType(), item.getTargetId());
        int n = auditItemMapper.update(null, new LambdaUpdateWrapper<AuditItem>()
                .eq(AuditItem::getId, item.getId())
                .eq(AuditItem::getStatus, STATUS_TESTING)
                .set(AuditItem::getStatus, STATUS_PUBLISHED)
                .set(AuditItem::getReviewerId, reviewerId)
                .set(AuditItem::getReviewTime, LocalDateTime.now()));
        if (n != 1) {
            throw new BusinessException(ResultCode.CONFLICT, MSG_AUDIT_CONCURRENT);
        }
        syncResourceStatusIf(item, STATUS_PUBLISHED, ResourceLifecycleStateMachine.STATUS_TESTING);
        ensureDefaultVersion(item.getTargetId());
        if ("agent".equalsIgnoreCase(item.getTargetType())) {
            agentApiKeyService.ensureActiveKeyForAgent(item.getTargetId(), reviewerId);
        }

        boolean platformActor = casbinAuthorizationService.hasAnyRole(reviewerId, new String[]{"platform_admin", "admin"});
        String detail = platformActor
                ? "您的资源「" + item.getDisplayName() + "」已由平台管理员上线发布，现已进入平台可用资源池。"
                : "您的资源「" + item.getDisplayName() + "」已发布上线，现已进入平台可用资源池。";
        notifySubmitter(item, NotificationEventCodes.RESOURCE_PUBLISHED,
                "资源已上线发布",
                detail);
        auditPendingPushDebouncer.requestFlush();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void platformForceDeprecateResource(Long resourceId, Long operatorUserId, String reason) {
        if (resourceId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "资源 ID 不能为空");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, resource_type, status, created_by, display_name
                FROM t_resource
                WHERE id = ? AND deleted = 0
                LIMIT 1
                """, resourceId);
        if (rows.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "资源不存在");
        }
        Map<String, Object> row = rows.get(0);
        String currentStatus = String.valueOf(row.get("status"));
        ResourceLifecycleStateMachine.ensureTransitionAllowed(currentStatus, ResourceLifecycleStateMachine.STATUS_DEPRECATED);
        jdbcTemplate.update(
                "UPDATE t_resource SET status = ?, update_time = NOW() WHERE id = ? AND deleted = 0",
                ResourceLifecycleStateMachine.STATUS_DEPRECATED, resourceId);

        List<Map<String, Object>> auditRows = jdbcTemplate.queryForList(
                "SELECT id FROM t_audit_item WHERE target_id = ? AND status = ? ORDER BY submit_time DESC LIMIT 1",
                resourceId, STATUS_PUBLISHED);
        String reasonText = StringUtils.hasText(reason) ? reason.trim() : "平台强制下架";
        if (!auditRows.isEmpty()) {
            Object auditPk = auditRows.get(0).get("id");
            jdbcTemplate.update(
                    "UPDATE t_audit_item SET status = ?, reject_reason = ?, reviewer_id = ?, review_time = NOW() WHERE id = ?",
                    STATUS_PLATFORM_FORCE_DEPRECATED, reasonText, operatorUserId, auditPk);
        }

        Long ownerId = null;
        Object createdBy = row.get("created_by");
        if (createdBy != null) {
            try {
                ownerId = Long.parseLong(String.valueOf(createdBy));
            } catch (NumberFormatException ignored) {
            }
        }
        String resourceType = row.get("resource_type") != null ? String.valueOf(row.get("resource_type")) : "resource";
        if (ownerId != null) {
            systemNotificationFacade.notifyToUser(
                    ownerId,
                    NotificationEventCodes.PLATFORM_RESOURCE_FORCE_DEPRECATED,
                    "资源已被平台强制下架",
                    """
                            事件: 平台治理
                            结果: 强制下架
                            时间: %s
                            详情:
                            资源: %s/%s
                            说明: %s
                            操作人: %s
                            建议: 请登录资源中心查看状态；如有异议请联系平台管理员。
                            """.formatted(LocalDateTime.now(), resourceType, resourceId, reasonText,
                            operatorUserId != null ? String.valueOf(operatorUserId) : "-"),
                    resourceType,
                    String.valueOf(resourceId));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchApproveResources(List<Long> ids, Long reviewerId) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            approveResource(id, reviewerId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchRejectResources(List<Long> ids, String reason, Long reviewerId) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            rejectResource(id, reason, reviewerId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchPublishResources(List<Long> ids, Long reviewerId) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            publishResource(id, reviewerId);
        }
    }

    private void ensureDefaultVersion(Long resourceId) {
        if (resourceId == null) {
            return;
        }
        jdbcTemplate.update("""
                        INSERT INTO t_resource_version(resource_id, version, status, is_current, snapshot_json, create_time)
                        SELECT ?, 'v1', 'active', 1, NULL, NOW()
                        WHERE NOT EXISTS (
                            SELECT 1 FROM t_resource_version WHERE resource_id = ? AND version = 'v1'
                        )
                        """,
                resourceId, resourceId);
    }

    /**
     * 与 {@link #MSG_AUDIT_CONCURRENT} 一致：仅在资源仍处于期望状态时更新，防止与并发审核打架。
     */
    private void syncResourceStatusIf(AuditItem item, String newStatus, String expectedCurrentStatus) {
        if (item.getTargetId() == null || !StringUtils.hasText(item.getTargetType())) {
            return;
        }
        int n = jdbcTemplate.update(
                "UPDATE t_resource SET status = ?, update_time = NOW() WHERE id = ? AND resource_type = ? AND deleted = 0 AND status = ?",
                newStatus, item.getTargetId(), item.getTargetType(), expectedCurrentStatus);
        if (n != 1) {
            throw new BusinessException(ResultCode.CONFLICT, "资源状态已变更，请刷新后重试");
        }
        if (STATUS_PUBLISHED.equalsIgnoreCase(newStatus)) {
            resourceHealthService.ensurePolicyForResource(item.getTargetId());
        }
    }

    private void notifySubmitter(AuditItem item, String type, String title, String body) {
        if (item.getSubmitter() == null) {
            return;
        }
        try {
            Long submitterId = Long.valueOf(item.getSubmitter());
            String enrichedBody = """
                    事件: 资源审核
                    结果: %s
                    时间: %s
                    详情:
                    资源: %s/%s
                    说明: %s
                    建议: 请前往资源中心查看处理结果。
                    """.formatted(
                    title,
                    LocalDateTime.now(),
                    item.getTargetType(),
                    item.getTargetId(),
                    body);
            systemNotificationFacade.notifyToUser(
                    submitterId,
                    type,
                    title,
                    enrichedBody,
                    item.getTargetType(),
                    item.getTargetId() != null ? String.valueOf(item.getTargetId()) : null);
        } catch (NumberFormatException ignored) {
        }
    }

    private static String normalizeTargetType(String targetType) {
        if (!StringUtils.hasText(targetType)) {
            return null;
        }
        String normalized = targetType.trim().toLowerCase();
        if (!SUPPORTED_TARGET_TYPES.contains(normalized)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的审核目标类型: " + targetType);
        }
        return normalized;
    }

    private void enrichNames(List<AuditItem> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> userIds = records.stream()
                .flatMap(item -> java.util.stream.Stream.of(parseLong(item.getSubmitter()), item.getReviewerId()))
                .toList();
        Map<Long, String> names = userDisplayNameResolver.resolveDisplayNames(userIds);
        records.forEach(item -> {
            item.setSubmitterName(names.get(parseLong(item.getSubmitter())));
            item.setReviewerName(names.get(item.getReviewerId()));
        });
    }

    private static Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
