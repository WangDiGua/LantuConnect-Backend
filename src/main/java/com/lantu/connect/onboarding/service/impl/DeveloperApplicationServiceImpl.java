package com.lantu.connect.onboarding.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lantu.connect.auth.entity.PlatformRole;
import com.lantu.connect.auth.entity.UserRoleRel;
import com.lantu.connect.auth.mapper.PlatformRoleMapper;
import com.lantu.connect.auth.mapper.UserRoleRelMapper;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.PageResults;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.util.ListQueryKeyword;
import com.lantu.connect.common.util.UserDisplayNameResolver;
import com.lantu.connect.notification.service.SystemNotificationFacade;
import com.lantu.connect.onboarding.dto.DeveloperApplicationCreateRequest;
import com.lantu.connect.onboarding.dto.DeveloperApplicationQueryRequest;
import com.lantu.connect.onboarding.entity.DeveloperApplication;
import com.lantu.connect.onboarding.mapper.DeveloperApplicationMapper;
import com.lantu.connect.onboarding.service.DeveloperApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeveloperApplicationServiceImpl implements DeveloperApplicationService {

    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_APPROVED = "approved";
    private static final String STATUS_REJECTED = "rejected";
    private static final String MSG_PENDING_DUPLICATE = "已有待审核申请，请勿重复提交";
    private static final String MSG_REVIEW_CONFLICT = "该申请已被其他审核员处理，请刷新列表";

    private final DeveloperApplicationMapper developerApplicationMapper;
    private final PlatformRoleMapper platformRoleMapper;
    private final UserRoleRelMapper userRoleRelMapper;
    private final UserDisplayNameResolver userDisplayNameResolver;
    private final SystemNotificationFacade systemNotificationFacade;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeveloperApplication submit(Long userId, DeveloperApplicationCreateRequest request) {
        long pendingCount = developerApplicationMapper.selectCount(
                new LambdaQueryWrapper<DeveloperApplication>()
                        .eq(DeveloperApplication::getUserId, userId)
                        .eq(DeveloperApplication::getStatus, STATUS_PENDING));
        if (pendingCount > 0) {
            throw new BusinessException(ResultCode.CONFLICT, MSG_PENDING_DUPLICATE);
        }

        DeveloperApplication row = new DeveloperApplication();
        row.setUserId(userId);
        row.setContactEmail(request.getContactEmail().trim());
        row.setContactPhone(StringUtils.hasText(request.getContactPhone()) ? request.getContactPhone().trim() : null);
        row.setCompanyName(StringUtils.hasText(request.getCompanyName()) ? request.getCompanyName().trim() : null);
        row.setApplyReason(request.getApplyReason().trim());
        row.setStatus(STATUS_PENDING);
        row.setCreateTime(LocalDateTime.now());
        row.setUpdateTime(LocalDateTime.now());
        try {
            developerApplicationMapper.insert(row);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ResultCode.CONFLICT, MSG_PENDING_DUPLICATE);
        }
        row.setUserName(userDisplayNameResolver.resolveDisplayName(userId));
        systemNotificationFacade.notifyOnboardingSubmitted(
                userId,
                row.getId(),
                row.getCompanyName(),
                row.getApplyReason());
        return row;
    }

    @Override
    public List<DeveloperApplication> myApplications(Long userId) {
        List<DeveloperApplication> result = developerApplicationMapper.selectList(
                new LambdaQueryWrapper<DeveloperApplication>()
                        .eq(DeveloperApplication::getUserId, userId)
                        .orderByDesc(DeveloperApplication::getCreateTime));
        enrichNames(result);
        return result;
    }

    @Override
    public PageResult<DeveloperApplication> list(DeveloperApplicationQueryRequest request) {
        int page = request.getPage() != null ? request.getPage() : 1;
        int pageSize = request.getPageSize() != null ? request.getPageSize() : 20;

        LambdaQueryWrapper<DeveloperApplication> q = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(request.getStatus())) {
            q.eq(DeveloperApplication::getStatus, request.getStatus().trim());
        }
        String kw = ListQueryKeyword.normalize(request.getKeyword());
        if (kw != null) {
            String likeParam = "%" + kw + "%";
            q.and(w -> {
                w.like(DeveloperApplication::getCompanyName, kw)
                        .or()
                        .like(DeveloperApplication::getApplyReason, kw)
                        .or()
                        .like(DeveloperApplication::getContactEmail, kw)
                        .or()
                        .like(DeveloperApplication::getContactPhone, kw)
                        .or()
                        .apply("CAST(user_id AS CHAR) LIKE {0}", likeParam)
                        .or()
                        .apply("EXISTS (SELECT 1 FROM t_user u WHERE u.user_id = t_developer_application.user_id"
                                + " AND u.deleted = 0 AND (u.username LIKE {0} OR u.real_name LIKE {0}))", likeParam);
                try {
                    long uid = Long.parseLong(kw);
                    w.or().eq(DeveloperApplication::getUserId, uid);
                } catch (NumberFormatException ignored) {
                    // ignore
                }
            });
        }
        q.orderByDesc(DeveloperApplication::getCreateTime);

        Page<DeveloperApplication> result = developerApplicationMapper.selectPage(new Page<>(page, pageSize), q);
        enrichNames(result.getRecords());
        return PageResults.from(result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, Long reviewerId, String reviewComment) {
        DeveloperApplication row = mustGet(id);
        if (!STATUS_PENDING.equals(row.getStatus())) {
            throw new BusinessException(ResultCode.ILLEGAL_STATE_TRANSITION, "仅待审核状态可审批");
        }

        PlatformRole devRole = platformRoleMapper.selectOne(
                new LambdaQueryWrapper<PlatformRole>().eq(PlatformRole::getRoleCode, "developer"));
        if (devRole == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "developer 角色不存在");
        }

        long relCount = userRoleRelMapper.selectCount(
                new LambdaQueryWrapper<UserRoleRel>()
                        .eq(UserRoleRel::getUserId, row.getUserId())
                        .eq(UserRoleRel::getRoleId, devRole.getId()));
        if (relCount == 0) {
            UserRoleRel rel = new UserRoleRel();
            rel.setUserId(row.getUserId());
            rel.setRoleId(devRole.getId());
            rel.setCreateTime(LocalDateTime.now());
            userRoleRelMapper.insert(rel);
        }

        String normalizedComment = StringUtils.hasText(reviewComment) ? reviewComment.trim() : "审核通过";
        LocalDateTime now = LocalDateTime.now();
        int updated = developerApplicationMapper.update(null, new UpdateWrapper<DeveloperApplication>()
                .eq("id", id)
                .eq("status", STATUS_PENDING)
                .set("status", STATUS_APPROVED)
                .set("review_comment", normalizedComment)
                .set("reviewed_by", reviewerId)
                .set("reviewed_at", now)
                .set("update_time", now));
        if (updated != 1) {
            throw new BusinessException(ResultCode.CONFLICT, MSG_REVIEW_CONFLICT);
        }

        row.setStatus(STATUS_APPROVED);
        row.setReviewComment(normalizedComment);
        row.setReviewedBy(reviewerId);
        row.setReviewedAt(now);
        row.setUpdateTime(now);
        systemNotificationFacade.notifyOnboardingReviewed(
                row.getUserId(),
                row.getId(),
                true,
                reviewerId,
                row.getReviewComment());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchApprove(List<Long> ids, Long reviewerId, String reviewComment) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            approve(id, reviewerId, reviewComment);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, Long reviewerId, String reviewComment) {
        DeveloperApplication row = mustGet(id);
        if (!STATUS_PENDING.equals(row.getStatus())) {
            throw new BusinessException(ResultCode.ILLEGAL_STATE_TRANSITION, "仅待审核状态可驳回");
        }
        if (!StringUtils.hasText(reviewComment)) {
            throw new BusinessException(ResultCode.REJECT_REASON_REQUIRED, "驳回原因不能为空");
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = developerApplicationMapper.update(null, new UpdateWrapper<DeveloperApplication>()
                .eq("id", id)
                .eq("status", STATUS_PENDING)
                .set("status", STATUS_REJECTED)
                .set("review_comment", reviewComment.trim())
                .set("reviewed_by", reviewerId)
                .set("reviewed_at", now)
                .set("update_time", now));
        if (updated != 1) {
            throw new BusinessException(ResultCode.CONFLICT, MSG_REVIEW_CONFLICT);
        }

        row.setStatus(STATUS_REJECTED);
        row.setReviewComment(reviewComment.trim());
        row.setReviewedBy(reviewerId);
        row.setReviewedAt(now);
        row.setUpdateTime(now);
        systemNotificationFacade.notifyOnboardingReviewed(
                row.getUserId(),
                row.getId(),
                false,
                reviewerId,
                row.getReviewComment());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchReject(List<Long> ids, Long reviewerId, String reviewComment) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            reject(id, reviewerId, reviewComment);
        }
    }

    private DeveloperApplication mustGet(Long id) {
        DeveloperApplication row = developerApplicationMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "申请记录不存在");
        }
        return row;
    }

    private void enrichNames(List<DeveloperApplication> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        java.util.Map<Long, String> names = userDisplayNameResolver.resolveDisplayNames(
                records.stream()
                        .flatMap(item -> java.util.stream.Stream.of(item.getUserId(), item.getReviewedBy()))
                        .toList());
        records.forEach(item -> {
            item.setUserName(names.get(item.getUserId()));
            item.setReviewedByName(names.get(item.getReviewedBy()));
        });
    }
}
