package com.lantu.connect.monitoring.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.PageResults;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.monitoring.dto.AlertRuleCreateRequest;
import com.lantu.connect.monitoring.dto.AlertRuleDryRunRequest;
import com.lantu.connect.monitoring.dto.AlertRuleDryRunResult;
import com.lantu.connect.monitoring.dto.AlertRuleMetricOptionVO;
import com.lantu.connect.monitoring.dto.AlertRuleUpdateRequest;
import com.lantu.connect.monitoring.entity.AlertRule;
import com.lantu.connect.monitoring.mapper.AlertRuleMapper;
import com.lantu.connect.monitoring.service.AlertMetricSampler;
import com.lantu.connect.monitoring.service.AlertRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AlertRuleServiceImpl implements AlertRuleService {

    private final AlertRuleMapper alertRuleMapper;
    private final AlertMetricSampler alertMetricSampler;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(AlertRuleCreateRequest request) {
        AlertRule entity = new AlertRule();
        applyWritableFields(entity, request);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        alertRuleMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(AlertRuleUpdateRequest request) {
        AlertRule existing = alertRuleMapper.selectById(request.getId());
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        applyWritableFields(existing, request);
        existing.setUpdateTime(LocalDateTime.now());
        alertRuleMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        if (alertRuleMapper.selectById(id) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        alertRuleMapper.deleteById(id);
    }

    @Override
    public AlertRule getById(String id) {
        AlertRule rule = alertRuleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return rule;
    }

    @Override
    public PageResult<AlertRule> page(int page,
                                      int pageSize,
                                      String keyword,
                                      String scopeType,
                                      String resourceType,
                                      Boolean enabled,
                                      String severity) {
        Page<AlertRule> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<AlertRule> q = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            q.and(w -> w.like(AlertRule::getName, kw)
                    .or().like(AlertRule::getMetric, kw)
                    .or().like(AlertRule::getDescription, kw));
        }
        if (StringUtils.hasText(scopeType) && !"all".equalsIgnoreCase(scopeType.trim())) {
            q.eq(AlertRule::getScopeType, normalizeScopeType(scopeType));
        }
        if (StringUtils.hasText(resourceType) && !"all".equalsIgnoreCase(resourceType.trim())) {
            q.eq(AlertRule::getScopeResourceType, normalizeResourceType(resourceType));
        }
        if (enabled != null) {
            q.eq(AlertRule::getEnabled, enabled);
        }
        if (StringUtils.hasText(severity) && !"all".equalsIgnoreCase(severity.trim())) {
            q.eq(AlertRule::getSeverity, normalizeSeverity(severity));
        }
        q.orderByDesc(AlertRule::getUpdateTime);
        return PageResults.from(alertRuleMapper.selectPage(p, q));
    }

    @Override
    public AlertRuleDryRunResult dryRun(String id, AlertRuleDryRunRequest request) {
        AlertRule rule = getById(id);
        BigDecimal sampleValue = request != null ? request.getSampleValue() : null;
        String mode = request == null ? null : request.getMode();
        AlertMetricSampler.AlertMetricSample sample;
        if ("preview".equalsIgnoreCase(mode) || sampleValue == null) {
            sample = alertMetricSampler.sample(rule);
            sampleValue = sample.getSampleValue();
        } else {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("metric", alertMetricSampler.normalizeMetric(rule.getMetric()));
            snapshot.put("sampleValue", sampleValue);
            snapshot.put("mode", "sample");
            sample = AlertMetricSampler.AlertMetricSample.builder()
                    .metric(alertMetricSampler.normalizeMetric(rule.getMetric()))
                    .sampleValue(sampleValue)
                    .sampleSource("manual_sample")
                    .summary("manual sample")
                    .snapshot(snapshot)
                    .labels(Map.of("metric", alertMetricSampler.normalizeMetric(rule.getMetric())))
                    .build();
        }
        boolean fire = alertMetricSampler.evaluate(sampleValue, rule.getThreshold(), rule.getOperator());
        String expression = "%s %s %s".formatted(sample.getMetric(), rule.getOperator(), rule.getThreshold());
        String detail = "sample=%s, expr=%s => %s".formatted(sampleValue, expression, fire ? "fire" : "stable");
        return new AlertRuleDryRunResult(
                fire,
                alertMetricSampler.normalizeOperator(rule.getOperator()),
                rule.getThreshold(),
                sampleValue,
                detail,
                sample.getSampleSource(),
                sample.getSummary(),
                !fire,
                sample.getSnapshot());
    }

    @Override
    public List<AlertRuleMetricOptionVO> metricOptions() {
        return alertMetricSampler.metricOptions();
    }

    private void applyWritableFields(AlertRule entity, AlertRuleCreateRequest request) {
        entity.setName(requireText(request.getName(), "name 不能为空"));
        entity.setMetric(alertMetricSampler.normalizeMetric(request.getMetric()));
        entity.setDescription(trimToNull(request.getConditionExpr()));
        entity.setOperator(alertMetricSampler.normalizeOperator(request.getOperator()));
        entity.setDuration(normalizeDuration(request.getDuration()));
        entity.setSeverity(normalizeSeverity(request.getSeverity()));
        entity.setThreshold(request.getThreshold() == null ? BigDecimal.ZERO : BigDecimal.valueOf(request.getThreshold()));
        entity.setEnabled(request.getEnabled() == null || request.getEnabled() != 0);
        entity.setNotifyChannels(Collections.emptyList());
        entity.setScopeType(normalizeScopeType(request.getScopeType()));
        entity.setScopeResourceType(normalizeScopeResourceType(request.getScopeResourceType(), entity.getScopeType()));
        entity.setScopeResourceId(normalizeScopeResourceId(request.getScopeResourceId(), entity.getScopeType()));
        entity.setLabelFilters(normalizeLabelFilters(request.getLabelFilters()));
    }

    private void applyWritableFields(AlertRule entity, AlertRuleUpdateRequest request) {
        if (request.getName() != null) {
            entity.setName(requireText(request.getName(), "name 不能为空"));
        }
        if (request.getMetric() != null) {
            entity.setMetric(alertMetricSampler.normalizeMetric(request.getMetric()));
        }
        if (request.getConditionExpr() != null) {
            entity.setDescription(trimToNull(request.getConditionExpr()));
        }
        if (request.getOperator() != null) {
            entity.setOperator(alertMetricSampler.normalizeOperator(request.getOperator()));
        }
        if (request.getDuration() != null) {
            entity.setDuration(normalizeDuration(request.getDuration()));
        }
        if (request.getSeverity() != null) {
            entity.setSeverity(normalizeSeverity(request.getSeverity()));
        }
        if (request.getThreshold() != null) {
            entity.setThreshold(BigDecimal.valueOf(request.getThreshold()));
        }
        if (request.getEnabled() != null) {
            entity.setEnabled(request.getEnabled() != 0);
        }
        entity.setNotifyChannels(Collections.emptyList());
        if (request.getScopeType() != null || request.getScopeResourceType() != null || request.getScopeResourceId() != null) {
            String scopeType = request.getScopeType() == null ? entity.getScopeType() : request.getScopeType();
            entity.setScopeType(normalizeScopeType(scopeType));
            String rawResourceType = request.getScopeResourceType() == null ? entity.getScopeResourceType() : request.getScopeResourceType();
            Long rawResourceId = request.getScopeResourceId() == null ? entity.getScopeResourceId() : request.getScopeResourceId();
            entity.setScopeResourceType(normalizeScopeResourceType(rawResourceType, entity.getScopeType()));
            entity.setScopeResourceId(normalizeScopeResourceId(rawResourceId, entity.getScopeType()));
        }
        if (request.getLabelFilters() != null) {
            entity.setLabelFilters(normalizeLabelFilters(request.getLabelFilters()));
        }
    }

    private String normalizeDuration(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "5m";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeSeverity(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "warning";
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if ("medium".equals(value)) {
            return "warning";
        }
        if ("critical".equals(value) || "warning".equals(value) || "info".equals(value)) {
            return value;
        }
        throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的告警级别: " + raw);
    }

    private String normalizeScopeType(String raw) {
        return alertMetricSampler.normalizeScopeType(raw);
    }

    private String normalizeScopeResourceType(String raw, String scopeType) {
        if ("global".equals(scopeType)) {
            return null;
        }
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "scopeResourceType 不能为空");
        }
        return normalizeResourceType(raw);
    }

    private Long normalizeScopeResourceId(Long raw, String scopeType) {
        if (!"resource".equals(scopeType)) {
            return null;
        }
        if (raw == null || raw <= 0L) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "scopeResourceId 不能为空");
        }
        return raw;
    }

    private String normalizeResourceType(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if ("agent".equals(value) || "skill".equals(value) || "mcp".equals(value) || "app".equals(value) || "dataset".equals(value)) {
            return value;
        }
        throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的资源类型: " + raw);
    }

    private Map<String, String> normalizeLabelFilters(Map<String, String> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        raw.forEach((key, value) -> {
            if (StringUtils.hasText(key) && StringUtils.hasText(value)) {
                normalized.put(key.trim(), value.trim());
            }
        });
        return normalized.isEmpty() ? null : normalized;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, message);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
