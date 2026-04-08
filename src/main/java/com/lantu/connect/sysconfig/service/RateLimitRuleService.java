package com.lantu.connect.sysconfig.service;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.sysconfig.dto.RateLimitRuleBatchPatchRequest;
import com.lantu.connect.sysconfig.dto.RateLimitRuleCreateRequest;
import com.lantu.connect.sysconfig.dto.RateLimitRuleUpdateRequest;
import com.lantu.connect.sysconfig.entity.RateLimitRule;

import java.util.List;

/**
 * 系统配置RateLimitRule服务接口
 *
 * @author 王帝
 * @date 2026-03-21
 */
public interface RateLimitRuleService {

    String create(RateLimitRuleCreateRequest request);

    void update(RateLimitRuleUpdateRequest request);

    void batchPatch(RateLimitRuleBatchPatchRequest body);

    void delete(String id);

    void batchDelete(List<String> ids);

    RateLimitRule getById(String id);

    PageResult<RateLimitRule> page(int page, int pageSize, String name);
}
