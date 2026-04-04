package com.lantu.connect.gateway.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.security.CasbinAuthorizationService;
import com.lantu.connect.common.util.ListQueryKeyword;
import com.lantu.connect.common.util.UserDisplayNameResolver;
import com.lantu.connect.gateway.dto.GrantApplicationRequest;
import com.lantu.connect.gateway.dto.GrantApplicationVO;
import com.lantu.connect.gateway.dto.ResourceGrantCreateRequest;
import com.lantu.connect.gateway.entity.ResourceGrantApplication;
import com.lantu.connect.gateway.mapper.ResourceGrantApplicationMapper;
import com.lantu.connect.gateway.security.ResourceInvokeGrantService;
import com.lantu.connect.gateway.service.GrantApplicationService;
import com.lantu.connect.notification.service.NotificationEventCodes;
import com.lantu.connect.notification.service.SystemNotificationFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GrantApplicationServiceImpl implements GrantApplicationService {

    private static final Set<String> ALLOWED_ACTIONS = Set.of("catalog", "resolve", "invoke", "*");

    private final ResourceGrantApplicationMapper applicationMapper;
    private final ResourceInvokeGrantService resourceInvokeGrantService;
    private final SystemNotificationFacade systemNotificationFacade;
    private final JdbcTemplate jdbcTemplate;
    private final UserDisplayNameResolver userDisplayNameResolver;
    private final CasbinAuthorizationService casbinAuthorizationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long apply(Long applicantUserId, GrantApplicationRequest request) {
        String resourceType = request.getResourceType().trim().toLowerCase(Locale.ROOT);
        Long resourceId = request.getResourceId();

        ensureResourcePublished(resourceType, resourceId);
        validateActions(request.getActions());

        Long existing = applicationMapper.selectCount(new LambdaQueryWrapper<ResourceGrantApplication>()
                .eq(ResourceGrantApplication::getApplicantId, applicantUserId)
                .eq(ResourceGrantApplication::getResourceType, resourceType)
                .eq(ResourceGrantApplication::getResourceId, resourceId)
                .eq(ResourceGrantApplication::getApiKeyId, request.getApiKeyId())
                .eq(ResourceGrantApplication::getStatus, "pending"));
        if (existing != null && existing > 0) {
            throw new BusinessException(ResultCode.GRANT_APPLICATION_DUPLICATE);
        }

        ResourceGrantApplication app = new ResourceGrantApplication();
        app.setApplicantId(applicantUserId);
        app.setResourceType(resourceType);
        app.setResourceId(resourceId);
        app.setApiKeyId(request.getApiKeyId());
        app.setActions(normalizeActions(request.getActions()));
        app.setUseCase(request.getUseCase());
        app.setCallFrequency(request.getCallFrequency());
        app.setStatus("pending");
        app.setExpiresAt(request.getExpiresAt());
        app.setCreateTime(LocalDateTime.now());
        app.setUpdateTime(LocalDateTime.now());
        applicationMapper.insert(app);

        notifyResourceOwnerNewGrantApplication(resourceType, resourceId, applicantUserId, request, app.getId());
        systemNotificationFacade.notifyPlatformAdmins(
                NotificationEventCodes.GRANT_APPLICATION_NEW,
                "新的资源授权申请待审批",
                """
                        事件: 资源授权申请
                        结果: 待审批
                        时间: %s
                        详情:
                        申请人: %s
                        资源: %s/%s
                        API Key: %s
                        诉求: %s
                        建议: 资源拥有者可审批；平台管理员可全量处理。
                        """.formatted(
                        LocalDateTime.now(),
                        applicantUserId,
                        resourceType,
                        resourceId,
                        request.getApiKeyId(),
                        request.getUseCase()),
                "grant_application",
                app.getId(),
                null);

        return app.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long reviewerUserId, Long applicationId) {
        ResourceGrantApplication app = requirePending(applicationId);
        resourceInvokeGrantService.ensureMayReviewGrantApplication(reviewerUserId, app.getResourceType(), app.getResourceId());
        app.setStatus("approved");
        app.setReviewerId(reviewerUserId);
        app.setReviewTime(LocalDateTime.now());
        app.setUpdateTime(LocalDateTime.now());
        applicationMapper.updateById(app);

        ResourceGrantCreateRequest grantReq = new ResourceGrantCreateRequest();
        grantReq.setResourceType(app.getResourceType());
        grantReq.setResourceId(app.getResourceId());
        grantReq.setGranteeApiKeyId(app.getApiKeyId());
        grantReq.setActions(app.getActions());
        grantReq.setExpiresAt(app.getExpiresAt());
        resourceInvokeGrantService.grant(reviewerUserId, grantReq);

        systemNotificationFacade.notifyToUser(
                app.getApplicantId(),
                NotificationEventCodes.GRANT_APPROVED,
                "资源授权申请已通过",
                """
                        事件: 资源授权申请
                        结果: 已通过
                        时间: %s
                        详情:
                        审批人: %s
                        资源: %s/%s
                        API Key: %s
                        建议: 可立即发起资源调用。
                        """.formatted(
                        LocalDateTime.now(),
                        reviewerUserId,
                        app.getResourceType(),
                        app.getResourceId(),
                        app.getApiKeyId()),
                "grant_application",
                String.valueOf(applicationId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long reviewerUserId, Long applicationId, String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException(ResultCode.REJECT_REASON_REQUIRED);
        }
        ResourceGrantApplication app = requirePending(applicationId);
        resourceInvokeGrantService.ensureMayReviewGrantApplication(reviewerUserId, app.getResourceType(), app.getResourceId());
        app.setStatus("rejected");
        app.setReviewerId(reviewerUserId);
        app.setRejectReason(reason);
        app.setReviewTime(LocalDateTime.now());
        app.setUpdateTime(LocalDateTime.now());
        applicationMapper.updateById(app);

        systemNotificationFacade.notifyToUser(
                app.getApplicantId(),
                NotificationEventCodes.GRANT_REJECTED,
                "资源授权申请被驳回",
                """
                        事件: 资源授权申请
                        结果: 已驳回
                        时间: %s
                        详情:
                        审批人: %s
                        资源: %s/%s
                        驳回原因: %s
                        建议: 根据驳回原因修正后重新提交。
                        """.formatted(
                        LocalDateTime.now(),
                        reviewerUserId,
                        app.getResourceType(),
                        app.getResourceId(),
                        reason),
                "grant_application",
                String.valueOf(applicationId));
    }

    @Override
    public PageResult<GrantApplicationVO> pageMyApplications(Long applicantUserId, String status, String keyword, int page, int pageSize) {
        Page<ResourceGrantApplication> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<ResourceGrantApplication> w = new LambdaQueryWrapper<ResourceGrantApplication>()
                .eq(ResourceGrantApplication::getApplicantId, applicantUserId);
        if (StringUtils.hasText(status) && !"all".equalsIgnoreCase(status.trim())) {
            w.eq(ResourceGrantApplication::getStatus, status.trim());
        }
        applyGrantApplicationKeyword(w, keyword);
        w.orderByDesc(ResourceGrantApplication::getCreateTime);
        Page<ResourceGrantApplication> result = applicationMapper.selectPage(p, w);
        Map<Long, String> names = resolveUserNames(result.getRecords());
        return PageResult.of(result.getRecords().stream().map(app -> toVO(app, names)).toList(),
                result.getTotal(), page, pageSize);
    }

    @Override
    public PageResult<GrantApplicationVO> pagePendingApplications(Long operatorUserId, String status, String keyword, int page, int pageSize) {
        if (operatorUserId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未认证用户无法查看待办");
        }
        Page<ResourceGrantApplication> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<ResourceGrantApplication> w = new LambdaQueryWrapper<ResourceGrantApplication>();
        if (StringUtils.hasText(status) && !"all".equalsIgnoreCase(status.trim())) {
            w.eq(ResourceGrantApplication::getStatus, status.trim().toLowerCase(Locale.ROOT));
        }
        applyGrantApplicationKeyword(w, keyword);
        applyPendingScopeForReviewer(w, operatorUserId);
        w.orderByDesc(ResourceGrantApplication::getCreateTime);
        Page<ResourceGrantApplication> result = applicationMapper.selectPage(p, w);
        Map<Long, String> names = resolveUserNames(result.getRecords());
        return PageResult.of(result.getRecords().stream().map(app -> toVO(app, names)).toList(),
                result.getTotal(), page, pageSize);
    }

    /**
     * platform_admin/admin/reviewer：全量待办；其余用户：仅自己拥有资源上的申请。
     */
    private void applyPendingScopeForReviewer(LambdaQueryWrapper<ResourceGrantApplication> w, Long operatorUserId) {
        if (casbinAuthorizationService.hasAnyRole(operatorUserId,
                new String[]{"platform_admin", "admin", "reviewer"})) {
            return;
        }
        applyResourceOwnerCreatedByEquals(w, operatorUserId);
    }

    private static void applyResourceOwnerCreatedByEquals(LambdaQueryWrapper<ResourceGrantApplication> w, Long ownerUserId) {
        w.apply("""
                EXISTS (SELECT 1 FROM t_resource r WHERE r.deleted = 0 \
                AND r.resource_type = t_resource_grant_application.resource_type \
                AND r.id = t_resource_grant_application.resource_id AND r.created_by = {0})\
                """, ownerUserId);
    }

    private void notifyResourceOwnerNewGrantApplication(String resourceType, Long resourceId, Long applicantUserId,
                                                        GrantApplicationRequest request, Long applicationId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT created_by FROM t_resource WHERE deleted = 0 AND resource_type = ? AND id = ? LIMIT 1",
                resourceType, resourceId);
        if (rows.isEmpty()) {
            return;
        }
        Object createdBy = rows.get(0).get("created_by");
        if (createdBy == null) {
            return;
        }
        long resourceOwnerId;
        try {
            resourceOwnerId = Long.parseLong(String.valueOf(createdBy));
        } catch (NumberFormatException ex) {
            return;
        }
        systemNotificationFacade.notifyToUser(
                resourceOwnerId,
                NotificationEventCodes.GRANT_APPLICATION_NEW,
                "你的资源有待处理的授权申请",
                """
                        事件: 资源授权申请
                        结果: 待你审批
                        时间: %s
                        详情:
                        申请人: %s
                        资源: %s/%s
                        API Key: %s
                        诉求: %s
                        建议: 请在「授权申请-待办」中通过或驳回。
                        """.formatted(
                        LocalDateTime.now(),
                        applicantUserId,
                        resourceType,
                        resourceId,
                        request.getApiKeyId(),
                        request.getUseCase()),
                "grant_application",
                String.valueOf(applicationId));
    }

    private void applyGrantApplicationKeyword(LambdaQueryWrapper<ResourceGrantApplication> w, String keyword) {
        String kw = ListQueryKeyword.normalize(keyword);
        if (kw == null) {
            return;
        }
        String likeParam = "%" + kw + "%";
        w.and(q -> {
            q.like(ResourceGrantApplication::getResourceType, kw)
                    .or()
                    .apply("CAST(resource_id AS CHAR) LIKE {0}", likeParam)
                    .or()
                    .like(ResourceGrantApplication::getApiKeyId, kw)
                    .or()
                    .like(ResourceGrantApplication::getUseCase, kw)
                    .or()
                    .like(ResourceGrantApplication::getCallFrequency, kw)
                    .or()
                    .apply("CAST(actions AS CHAR) LIKE {0}", likeParam)
                    .or()
                    .apply("EXISTS (SELECT 1 FROM t_user u WHERE u.user_id = t_resource_grant_application.applicant_id"
                            + " AND u.deleted = 0 AND (u.username LIKE {0} OR u.real_name LIKE {0}))", likeParam);
            try {
                long id = Long.parseLong(kw);
                q.or().eq(ResourceGrantApplication::getId, id);
            } catch (NumberFormatException ignored) {
                // not numeric
            }
        });
    }

    private ResourceGrantApplication requirePending(Long applicationId) {
        ResourceGrantApplication app = applicationMapper.selectById(applicationId);
        if (app == null) {
            throw new BusinessException(ResultCode.GRANT_APPLICATION_NOT_FOUND);
        }
        if (!"pending".equals(app.getStatus())) {
            throw new BusinessException(ResultCode.GRANT_APPLICATION_NOT_PENDING);
        }
        return app;
    }

    private void ensureResourcePublished(String resourceType, Long resourceId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT status FROM t_resource WHERE deleted = 0 AND resource_type = ? AND id = ? LIMIT 1",
                resourceType, resourceId);
        if (rows.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "资源不存在");
        }
        String status = String.valueOf(rows.get(0).get("status"));
        if (!"published".equals(status)) {
            throw new BusinessException(ResultCode.ILLEGAL_STATE_TRANSITION, "仅已上线资源可申请授权");
        }
    }

    private void validateActions(List<String> actions) {
        if (actions == null || actions.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "actions 不能为空");
        }
        for (String action : actions) {
            if (!ALLOWED_ACTIONS.contains(action.trim().toLowerCase(Locale.ROOT))) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "actions 仅支持 catalog/resolve/invoke/*");
            }
        }
    }

    private List<String> normalizeActions(List<String> actions) {
        return actions.stream()
                .map(a -> a.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private GrantApplicationVO toVO(ResourceGrantApplication app, Map<Long, String> names) {
        return GrantApplicationVO.builder()
                .id(app.getId())
                .applicantId(app.getApplicantId())
                .applicantName(names.get(app.getApplicantId()))
                .resourceType(app.getResourceType())
                .resourceId(app.getResourceId())
                .apiKeyId(app.getApiKeyId())
                .actions(app.getActions())
                .useCase(app.getUseCase())
                .callFrequency(app.getCallFrequency())
                .status(app.getStatus())
                .reviewerId(app.getReviewerId())
                .reviewerName(names.get(app.getReviewerId()))
                .rejectReason(app.getRejectReason())
                .reviewTime(app.getReviewTime())
                .expiresAt(app.getExpiresAt())
                .createTime(app.getCreateTime())
                .build();
    }

    private Map<Long, String> resolveUserNames(List<ResourceGrantApplication> records) {
        return userDisplayNameResolver.resolveDisplayNames(
                records.stream()
                        .flatMap(item -> java.util.stream.Stream.of(item.getApplicantId(), item.getReviewerId()))
                        .toList());
    }
}
