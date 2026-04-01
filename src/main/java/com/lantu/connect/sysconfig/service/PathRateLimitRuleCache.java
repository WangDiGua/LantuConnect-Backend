package com.lantu.connect.sysconfig.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lantu.connect.sysconfig.entity.RateLimitRule;
import com.lantu.connect.sysconfig.mapper.RateLimitRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/** 缓存 {@code target = path} 的限流规则；规则增删改后应调用 {@link #invalidate()}。 */
@Service
@RequiredArgsConstructor
public class PathRateLimitRuleCache {

    private static final long TTL_MS = 30_000L;

    private final RateLimitRuleMapper rateLimitRuleMapper;

    private volatile List<RateLimitRule> cached = Collections.emptyList();
    private volatile long loadedAtMillis;

    public List<RateLimitRule> snapshot() {
        long now = System.currentTimeMillis();
        if (now - loadedAtMillis < TTL_MS) {
            return cached;
        }
        synchronized (this) {
            if (now - loadedAtMillis < TTL_MS) {
                return cached;
            }
            reloadLocked();
            return cached;
        }
    }

    public void invalidate() {
        synchronized (this) {
            loadedAtMillis = 0L;
            reloadLocked();
        }
    }

    private void reloadLocked() {
        LambdaQueryWrapper<RateLimitRule> q = new LambdaQueryWrapper<>();
        q.eq(RateLimitRule::getTarget, "path");
        q.and(w -> w.isNull(RateLimitRule::getEnabled).or().eq(RateLimitRule::getEnabled, true));
        q.orderByDesc(RateLimitRule::getPriority);
        cached = rateLimitRuleMapper.selectList(q);
        if (cached == null) {
            cached = Collections.emptyList();
        }
        loadedAtMillis = System.currentTimeMillis();
    }
}
