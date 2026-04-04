package com.lantu.connect.sysconfig.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.PageResults;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.sysconfig.dto.RateLimitRuleCreateRequest;
import com.lantu.connect.sysconfig.dto.RateLimitRuleUpdateRequest;
import com.lantu.connect.sysconfig.entity.RateLimitRule;
import com.lantu.connect.sysconfig.mapper.RateLimitRuleMapper;
import com.lantu.connect.sysconfig.service.PathRateLimitRuleCache;
import com.lantu.connect.sysconfig.service.RateLimitRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 系统配置RateLimitRule服务实现
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Service
@RequiredArgsConstructor
public class RateLimitRuleServiceImpl implements RateLimitRuleService {

    private final RateLimitRuleMapper rateLimitRuleMapper;
    private final PathRateLimitRuleCache pathRateLimitRuleCache;

    private static String normScope(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        return "all".equals(v) ? "all" : v;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(RateLimitRuleCreateRequest request) {
        RateLimitRule entity = new RateLimitRule();
        entity.setName(request.getName());

        boolean pathLegacy = StringUtils.hasText(request.getPathPattern())
                && !StringUtils.hasText(request.getTarget());
        boolean pathExplicit = "path".equalsIgnoreCase(String.valueOf(request.getTarget()));

        if (pathLegacy || pathExplicit) {
            entity.setTarget("path");
            entity.setTargetValue(request.getPathPattern() != null ? request.getPathPattern()
                    : request.getTargetValue() != null ? request.getTargetValue() : "/**");
            entity.setWindowMs(60_000L);
            entity.setMaxRequests(request.getLimitPerMinute() != null ? request.getLimitPerMinute() : 100);
            entity.setMaxTokens(request.getLimitPerDay() != null ? request.getLimitPerDay() : 0);
            entity.setBurstLimit(10);
            entity.setAction("reject");
            entity.setPriority(0);
            entity.setResourceScope(null);
        } else {
            String target = StringUtils.hasText(request.getTarget()) ? request.getTarget().trim().toLowerCase(Locale.ROOT) : "global";
            entity.setTarget(target);
            entity.setTargetValue(request.getTargetValue());
            entity.setWindowMs(request.getWindowMs() != null && request.getWindowMs() > 0 ? request.getWindowMs() : 60_000L);
            entity.setMaxRequests(request.getMaxRequests() != null && request.getMaxRequests() > 0 ? request.getMaxRequests() : 1000);
            entity.setMaxTokens(request.getMaxTokens());
            entity.setBurstLimit(request.getBurstLimit() != null && request.getBurstLimit() > 0 ? request.getBurstLimit() : 50);
            String action = StringUtils.hasText(request.getAction()) ? request.getAction().trim().toLowerCase(Locale.ROOT) : "reject";
            entity.setAction(action);
            entity.setPriority(request.getPriority() != null ? request.getPriority() : 0);
            String rs = normScope(request.getResourceScope());
            entity.setResourceScope(rs);
        }

        entity.setEnabled(request.getEnabled() == null || request.getEnabled() != 0);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        rateLimitRuleMapper.insert(entity);
        pathRateLimitRuleCache.invalidate();
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(RateLimitRuleUpdateRequest request) {
        RateLimitRule existing = rateLimitRuleMapper.selectById(request.getId());
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (request.getName() != null) {
            existing.setName(request.getName());
        }
        if (request.getTarget() != null) {
            existing.setTarget(request.getTarget().trim().toLowerCase(Locale.ROOT));
        }
        if (request.getPathPattern() != null) {
            existing.setTargetValue(request.getPathPattern());
            existing.setTarget("path");
        }
        if (request.getTargetValue() != null) {
            existing.setTargetValue(request.getTargetValue());
        }
        if (request.getWindowMs() != null && request.getWindowMs() > 0) {
            existing.setWindowMs(request.getWindowMs());
        }
        if (request.getLimitPerMinute() != null) {
            existing.setMaxRequests(request.getLimitPerMinute());
        }
        if (request.getMaxRequests() != null) {
            existing.setMaxRequests(request.getMaxRequests());
        }
        if (request.getLimitPerDay() != null) {
            existing.setMaxTokens(request.getLimitPerDay());
        }
        if (request.getMaxTokens() != null) {
            existing.setMaxTokens(request.getMaxTokens());
        }
        if (request.getBurstLimit() != null) {
            existing.setBurstLimit(request.getBurstLimit());
        }
        if (request.getAction() != null) {
            existing.setAction(request.getAction().trim().toLowerCase(Locale.ROOT));
        }
        if (request.getPriority() != null) {
            existing.setPriority(request.getPriority());
        }
        if (request.getResourceScope() != null) {
            String rs = normScope(request.getResourceScope());
            existing.setResourceScope(rs);
        }
        if (request.getEnabled() != null) {
            existing.setEnabled(request.getEnabled() != 0);
        }
        existing.setUpdateTime(LocalDateTime.now());
        rateLimitRuleMapper.updateById(existing);
        pathRateLimitRuleCache.invalidate();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        if (rateLimitRuleMapper.selectById(id) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        rateLimitRuleMapper.deleteById(id);
        pathRateLimitRuleCache.invalidate();
    }

    @Override
    public RateLimitRule getById(String id) {
        RateLimitRule rule = rateLimitRuleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return rule;
    }

    @Override
    public PageResult<RateLimitRule> page(int page, int pageSize, String name) {
        Page<RateLimitRule> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<RateLimitRule> q = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(name)) {
            q.like(RateLimitRule::getName, name);
        }
        q.orderByDesc(RateLimitRule::getUpdateTime);
        Page<RateLimitRule> result = rateLimitRuleMapper.selectPage(p, q);
        return PageResults.from(result);
    }
}
