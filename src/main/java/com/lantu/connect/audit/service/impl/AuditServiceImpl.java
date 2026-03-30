package com.lantu.connect.audit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lantu.connect.audit.entity.AuditItem;
import com.lantu.connect.audit.mapper.AuditItemMapper;
import com.lantu.connect.audit.service.AuditService;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.PageResults;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.util.DeptScopeHelper;
import com.lantu.connect.common.util.ListQueryKeyword;
import com.lantu.connect.common.util.UserDisplayNameResolver;
import com.lantu.connect.notification.service.NotificationEventCodes;
import com.lantu.connect.notification.service.SystemNotificationFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 审核Audit服务实现 — 两级审核模型：
 * 1. dept_admin 执行 approve / reject（pending_review → testing / rejected）
 * 2. platform_admin 执行 publish（testing → published）
 *
 * dept_admin 只能看到本部门提交的审核项（按 submitter 的 menuId 过滤）。
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
    private static final String TARGET_AGENT = "agent";
    private static final String TARGET_SKILL = "skill";
    private static final Set<String> SUPPORTED_TARGET_TYPES = Set.of("agent", "skill", "mcp", "app", "dataset");

    private final AuditItemMapper auditItemMapper;
    private final JdbcTemplate jdbcTemplate;
    private final DeptScopeHelper deptScopeHelper;
    private final SystemNotificationFacade systemNotificationFacade;
    private final UserDisplayNameResolver userDisplayNameResolver;

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

        if (deptScopeHelper.isDeptAdminOnly(operatorUserId)) {
            List<String> deptUserIds = findDeptUserIds(operatorUserId);
            if (deptUserIds.isEmpty()) {
                return PageResult.empty(page, pageSize);
            }
            wrapper.in(AuditItem::getSubmitter, deptUserIds);
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
        item.setStatus(STATUS_TESTING);
        item.setReviewerId(reviewerId);
        item.setReviewTime(LocalDateTime.now());
        auditItemMapper.updateById(item);
        syncResourceStatus(item, STATUS_TESTING);

        notifySubmitter(item, NotificationEventCodes.AUDIT_APPROVED,
                "资源审核通过",
                "您的资源「" + item.getDisplayName() + "」已通过部门审核，等待平台管理员上线发布。");
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
        item.setStatus(STATUS_REJECTED);
        item.setRejectReason(reason);
        item.setReviewerId(reviewerId);
        item.setReviewTime(LocalDateTime.now());
        auditItemMapper.updateById(item);
        syncResourceStatus(item, STATUS_REJECTED);

        notifySubmitter(item, NotificationEventCodes.AUDIT_REJECTED,
                "资源审核被驳回",
                "您的资源「" + item.getDisplayName() + "」审核未通过，原因：" + reason);
    }

    private AuditItem requirePending(Long id, String expectedTargetType) {
        AuditItem item = resolveAuditItemByPathId(id, expectedTargetType, STATUS_PENDING);
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
        AuditItem item = resolveAuditItemByPathId(id, expectedTargetType, STATUS_TESTING);
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
        item.setStatus(STATUS_PUBLISHED);
        item.setReviewerId(reviewerId);
        item.setReviewTime(LocalDateTime.now());
        auditItemMapper.updateById(item);
        syncResourceStatus(item, STATUS_PUBLISHED);
        ensureDefaultVersion(item.getTargetId());

        notifySubmitter(item, NotificationEventCodes.RESOURCE_PUBLISHED,
                "资源已上线发布",
                "您的资源「" + item.getDisplayName() + "」已由平台管理员上线发布，现已进入平台可用资源池。");
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

    private void syncResourceStatus(AuditItem item, String status) {
        if (item.getTargetId() == null || !StringUtils.hasText(item.getTargetType())) {
            return;
        }
        jdbcTemplate.update(
                "UPDATE t_resource SET status = ?, update_time = NOW() WHERE id = ? AND resource_type = ? AND deleted = 0",
                status, item.getTargetId(), item.getTargetType());
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

    /**
     * Find user IDs in the same department (menuId) as the given operator.
     */
    private List<String> findDeptUserIds(Long operatorUserId) {
        Long menuId = deptScopeHelper.getCurrentUserMenuId();
        if (menuId == null) {
            return List.of(String.valueOf(operatorUserId));
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT user_id FROM t_user WHERE menu_id = ? AND deleted = 0", menuId);
        return rows.stream()
                .map(r -> String.valueOf(r.get("user_id")))
                .toList();
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
