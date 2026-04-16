package com.lantu.connect.monitoring.service;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.monitoring.dto.AlertRuleCreateRequest;
import com.lantu.connect.monitoring.dto.AlertRuleDryRunRequest;
import com.lantu.connect.monitoring.dto.AlertRuleDryRunResult;
import com.lantu.connect.monitoring.dto.AlertRuleMetricOptionVO;
import com.lantu.connect.monitoring.dto.AlertRuleUpdateRequest;
import com.lantu.connect.monitoring.entity.AlertRule;

import java.util.List;

public interface AlertRuleService {

    String create(AlertRuleCreateRequest request);

    void update(AlertRuleUpdateRequest request);

    void delete(String id);

    AlertRule getById(String id);

    PageResult<AlertRule> page(int page,
                               int pageSize,
                               String keyword,
                               String scopeType,
                               String resourceType,
                               Boolean enabled,
                               String severity);

    AlertRuleDryRunResult dryRun(String id, AlertRuleDryRunRequest request);

    List<AlertRuleMetricOptionVO> metricOptions();
}
