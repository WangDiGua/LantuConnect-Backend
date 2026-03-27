package com.lantu.connect.monitoring.service;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.monitoring.dto.AlertRuleCreateRequest;
import com.lantu.connect.monitoring.dto.AlertRuleDryRunResult;
import com.lantu.connect.monitoring.dto.AlertRuleUpdateRequest;
import com.lantu.connect.monitoring.entity.AlertRule;

import java.math.BigDecimal;

/**
 * 监控AlertRule服务接口
 *
 * @author 王帝
 * @date 2026-03-21
 */
public interface AlertRuleService {

    String create(AlertRuleCreateRequest request);

    void update(AlertRuleUpdateRequest request);

    void delete(String id);

    AlertRule getById(String id);

    PageResult<AlertRule> page(int page, int pageSize, String name);

    /**
     * 试跑：用样本值与规则阈值、算子比较，判断是否会在该样本下触发告警
     */
    AlertRuleDryRunResult dryRun(String id, BigDecimal sampleValue);
}
