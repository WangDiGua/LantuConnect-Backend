package com.lantu.connect.sysconfig.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.PageResults;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.util.ListQueryKeyword;
import com.lantu.connect.sysconfig.dto.QuotaRateLimitCreateRequest;
import com.lantu.connect.sysconfig.entity.QuotaRateLimit;
import com.lantu.connect.sysconfig.mapper.QuotaRateLimitMapper;
import com.lantu.connect.sysconfig.service.QuotaRateLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 系统配置QuotaRateLimit服务实现
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Service
@RequiredArgsConstructor
public class QuotaRateLimitServiceImpl implements QuotaRateLimitService {

    private final QuotaRateLimitMapper quotaRateLimitMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(QuotaRateLimitCreateRequest request) {
        QuotaRateLimit entity = new QuotaRateLimit();
        entity.setName(request.getRuleKey());
        entity.setTargetType("quota");
        entity.setTargetId(request.getQuotaId());
        entity.setTargetName("quota:" + request.getQuotaId());
        entity.setMaxRequestsPerMin(60);
        entity.setMaxRequestsPerHour(3600);
        entity.setMaxConcurrent(10);
        entity.setEnabled(request.getEnabled() == null || request.getEnabled() != 0);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        quotaRateLimitMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (quotaRateLimitMapper.selectById(id) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        quotaRateLimitMapper.deleteById(id);
    }

    @Override
    public QuotaRateLimit getById(Long id) {
        QuotaRateLimit row = quotaRateLimitMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return row;
    }

    @Override
    public PageResult<QuotaRateLimit> page(int page, int pageSize, Long quotaId, String keyword) {
        Page<QuotaRateLimit> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<QuotaRateLimit> q = new LambdaQueryWrapper<>();
        if (quotaId != null) {
            q.eq(QuotaRateLimit::getTargetId, quotaId).eq(QuotaRateLimit::getTargetType, "quota");
        }
        String kw = ListQueryKeyword.normalize(keyword);
        if (kw != null) {
            String likeParam = "%" + kw + "%";
            q.and(w -> w.like(QuotaRateLimit::getName, kw)
                    .or()
                    .like(QuotaRateLimit::getTargetName, kw)
                    .or()
                    .like(QuotaRateLimit::getTargetType, kw)
                    .or()
                    .apply("CAST(target_id AS CHAR) LIKE {0}", likeParam));
        }
        q.orderByDesc(QuotaRateLimit::getUpdateTime);
        Page<QuotaRateLimit> result = quotaRateLimitMapper.selectPage(p, q);
        return PageResults.from(result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggle(Long id, Integer enabled) {
        QuotaRateLimit existing = quotaRateLimitMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        existing.setEnabled(enabled != null && enabled != 0);
        existing.setUpdateTime(LocalDateTime.now());
        quotaRateLimitMapper.updateById(existing);
    }
}
