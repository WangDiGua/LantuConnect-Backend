package com.lantu.connect.sysconfig.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.sysconfig.entity.Quota;
import com.lantu.connect.sysconfig.mapper.QuotaMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuotaCheckService {

    private final QuotaMapper quotaMapper;

    private static String normCategory(String invokeResourceType) {
        if (!StringUtils.hasText(invokeResourceType)) {
            return "all";
        }
        return invokeResourceType.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 优先匹配与用户、资源大类一致的配额行；若无则回落到 resource_category=all。
     */
    private Quota resolveUserQuota(Long userId, String invokeResourceType) {
        String cat = normCategory(invokeResourceType);
        if (!"all".equals(cat)) {
            Quota specific = quotaMapper.selectOne(
                    new LambdaQueryWrapper<Quota>()
                            .eq(Quota::getTargetType, "user")
                            .eq(Quota::getTargetId, userId)
                            .eq(Quota::getResourceCategory, cat)
                            .last("LIMIT 1"));
            if (specific != null) {
                return specific;
            }
        }
        return quotaMapper.selectOne(
                new LambdaQueryWrapper<Quota>()
                        .eq(Quota::getTargetType, "user")
                        .eq(Quota::getTargetId, userId)
                        .eq(Quota::getResourceCategory, "all")
                        .last("LIMIT 1"));
    }

    @Transactional(rollbackFor = Exception.class)
    public void checkAndConsume(Long userId, int tokens, String invokeResourceType) {
        Quota quota = resolveUserQuota(userId, invokeResourceType);
        if (quota == null) {
            return;
        }
        if (!Boolean.TRUE.equals(quota.getEnabled())) {
            return;
        }
        String useCategory = quota.getResourceCategory();
        if (!StringUtils.hasText(useCategory)) {
            useCategory = "all";
        }
        if (quota.getDailyLimit() != null && quota.getDailyLimit() > 0) {
            int affected = quotaMapper.incrementDailyUsedIfWithinLimit(userId, tokens, useCategory);
            if (affected == 0) {
                int used = quota.getDailyUsed() != null ? quota.getDailyUsed() : 0;
                log.warn("用户 {} 日配额已用尽: 已用={}, 限额={}, 维度={}", userId, used, quota.getDailyLimit(), useCategory);
                throw new BusinessException(ResultCode.DAILY_QUOTA_EXHAUSTED);
            }
        } else {
            quotaMapper.incrementDailyUsed(userId, tokens, useCategory);
        }
        if (quota.getMonthlyLimit() != null && quota.getMonthlyLimit() > 0) {
            int affected = quotaMapper.incrementMonthlyUsedIfWithinLimit(userId, tokens, useCategory);
            if (affected == 0) {
                int used = quota.getMonthlyUsed() != null ? quota.getMonthlyUsed() : 0;
                log.warn("用户 {} 月配额已用尽: 已用={}, 限额={}, 维度={}", userId, used, quota.getMonthlyLimit(), useCategory);
                throw new BusinessException(ResultCode.MONTHLY_QUOTA_EXHAUSTED);
            }
        } else {
            quotaMapper.incrementMonthlyUsed(userId, tokens, useCategory);
        }
    }
}
