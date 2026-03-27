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

@Service
@RequiredArgsConstructor
@Slf4j
public class QuotaCheckService {

    private final QuotaMapper quotaMapper;

    @Transactional(rollbackFor = Exception.class)
    public void checkAndConsume(Long userId, int tokens) {
        Quota quota = quotaMapper.selectOne(
                new LambdaQueryWrapper<Quota>()
                        .eq(Quota::getTargetType, "user")
                        .eq(Quota::getTargetId, userId));
        if (quota == null) {
            return;
        }
        if (!Boolean.TRUE.equals(quota.getEnabled())) {
            return;
        }
        if (quota.getDailyLimit() != null && quota.getDailyLimit() > 0) {
            int affected = quotaMapper.incrementDailyUsedIfWithinLimit(userId, tokens);
            if (affected == 0) {
                int used = quota.getDailyUsed() != null ? quota.getDailyUsed() : 0;
                log.warn("用户 {} 日配额已用尽: 已用={}, 限额={}", userId, used, quota.getDailyLimit());
                throw new BusinessException(ResultCode.DAILY_QUOTA_EXHAUSTED);
            }
        } else {
            quotaMapper.incrementDailyUsed(userId, tokens);
        }
        if (quota.getMonthlyLimit() != null && quota.getMonthlyLimit() > 0) {
            int affected = quotaMapper.incrementMonthlyUsedIfWithinLimit(userId, tokens);
            if (affected == 0) {
                int used = quota.getMonthlyUsed() != null ? quota.getMonthlyUsed() : 0;
                log.warn("用户 {} 月配额已用尽: 已用={}, 限额={}", userId, used, quota.getMonthlyLimit());
                throw new BusinessException(ResultCode.MONTHLY_QUOTA_EXHAUSTED);
            }
        } else {
            quotaMapper.incrementMonthlyUsed(userId, tokens);
        }
    }
}
