package com.lantu.connect.sysconfig.service;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.sysconfig.dto.QuotaRateLimitCreateRequest;
import com.lantu.connect.sysconfig.entity.QuotaRateLimit;

/**
 * 系统配置QuotaRateLimit服务接口
 *
 * @author 王帝
 * @date 2026-03-21
 */
public interface QuotaRateLimitService {

    Long create(QuotaRateLimitCreateRequest request);

    void delete(Long id);

    QuotaRateLimit getById(Long id);

    PageResult<QuotaRateLimit> page(int page, int pageSize, Long quotaId, String keyword);

    void toggle(Long id, Integer enabled);
}
