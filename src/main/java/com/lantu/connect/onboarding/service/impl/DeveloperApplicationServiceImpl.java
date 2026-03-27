package com.lantu.connect.onboarding.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lantu.connect.auth.entity.PlatformRole;
import com.lantu.connect.auth.entity.UserRoleRel;
import com.lantu.connect.auth.mapper.PlatformRoleMapper;
import com.lantu.connect.auth.mapper.UserRoleRelMapper;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.PageResults;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.util.UserDisplayNameResolver;
import com.lantu.connect.onboarding.dto.DeveloperApplicationCreateRequest;
import com.lantu.connect.onboarding.dto.DeveloperApplicationQueryRequest;
import com.lantu.connect.onboarding.entity.DeveloperApplication;
import com.lantu.connect.onboarding.mapper.DeveloperApplicationMapper;
import com.lantu.connect.onboarding.service.DeveloperApplicationService;
import com.lantu.connect.notification.service.SystemNotificationFacade;
import lombok.RequiredArgsConstructor;
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
            throw new BusinessException(ResultCode.CONFLICT, "已有待审核申请，请勿重复提交");
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
        developerApplicationMapper.insert(row);
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
        if (StringUtils.hasText(request.getKeyword())) {
            String keyword = request.getKeyword().trim();
            q.and(w -> w.like(DeveloperApplication::getCompanyName, keyword)
                    .or()
                    .like(DeveloperApplication::getApplyReason, keyword)
                    .or()
                    .like(DeveloperApplication::getContactEmail, keyword));
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

        row.setStatus(STATUS_APPROVED);
        row.setReviewComment(StringUtils.hasText(reviewComment) ? reviewComment.trim() : "审核通过");
        row.setReviewedBy(reviewerId);
        row.setReviewedAt(LocalDateTime.now());
        row.setUpdateTime(LocalDateTime.now());
        developerApplicationMapper.updateById(row);
        systemNotificationFacade.notifyOnboardingReviewed(
                row.getUserId(),
                row.getId(),
                true,
                reviewerId,
                row.getReviewComment());
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
        row.setStatus(STATUS_REJECTED);
        row.setReviewComment(reviewComment.trim());
        row.setReviewedBy(reviewerId);
        row.setReviewedAt(LocalDateTime.now());
        row.setUpdateTime(LocalDateTime.now());
        developerApplicationMapper.updateById(row);
        systemNotificationFacade.notifyOnboardingReviewed(
                row.getUserId(),
                row.getId(),
                false,
                reviewerId,
                row.getReviewComment());
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
