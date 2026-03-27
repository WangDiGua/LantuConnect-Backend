package com.lantu.connect.sysconfig.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.PageResults;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.sysconfig.dto.QuotaCreateRequest;
import com.lantu.connect.sysconfig.dto.QuotaUpdateRequest;
import com.lantu.connect.sysconfig.entity.Quota;
import com.lantu.connect.sysconfig.mapper.QuotaMapper;
import com.lantu.connect.sysconfig.service.QuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 系统配置Quota服务实现
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Service
@RequiredArgsConstructor
public class QuotaServiceImpl implements QuotaService {

    private final QuotaMapper quotaMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(QuotaCreateRequest request) {
        Quota entity = new Quota();
        entity.setTargetType(request.getSubjectType());
        Long targetId = null;
        try {
            if (StringUtils.hasText(request.getSubjectId())) {
                targetId = Long.parseLong(request.getSubjectId().trim());
            }
        } catch (NumberFormatException ignored) {
            // 非数字则仅存名称语义
        }
        entity.setTargetId(targetId);
        entity.setTargetName(request.getSubjectId() != null ? request.getSubjectId() : "");
        entity.setDailyLimit(request.getDailyLimit() != null ? request.getDailyLimit().intValue() : 0);
        entity.setMonthlyLimit(request.getMonthlyLimit() != null ? request.getMonthlyLimit().intValue() : 0);
        entity.setDailyUsed(0);
        entity.setMonthlyUsed(0);
        entity.setEnabled(true);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        quotaMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(QuotaUpdateRequest request) {
        Quota existing = quotaMapper.selectById(request.getId());
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (request.getDailyLimit() != null) {
            existing.setDailyLimit(request.getDailyLimit().intValue());
        }
        if (request.getMonthlyLimit() != null) {
            existing.setMonthlyLimit(request.getMonthlyLimit().intValue());
        }
        if (request.getDailyUsed() != null) {
            existing.setDailyUsed(request.getDailyUsed().intValue());
        }
        if (request.getMonthlyUsed() != null) {
            existing.setMonthlyUsed(request.getMonthlyUsed().intValue());
        }
        existing.setUpdateTime(LocalDateTime.now());
        quotaMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (quotaMapper.selectById(id) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        quotaMapper.deleteById(id);
    }

    @Override
    public Quota getById(Long id) {
        Quota quota = quotaMapper.selectById(id);
        if (quota == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return quota;
    }

    @Override
    public PageResult<Quota> page(int page, int pageSize, String subjectType) {
        Page<Quota> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<Quota> q = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(subjectType)) {
            q.eq(Quota::getTargetType, subjectType);
        }
        q.orderByDesc(Quota::getUpdateTime);
        Page<Quota> result = quotaMapper.selectPage(p, q);
        return PageResults.from(result);
    }
}
