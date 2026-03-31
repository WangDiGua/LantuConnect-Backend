package com.lantu.connect.monitoring.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.PageResults;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.monitoring.dto.AlertRuleCreateRequest;
import com.lantu.connect.monitoring.dto.AlertRuleDryRunResult;
import com.lantu.connect.monitoring.dto.AlertRuleUpdateRequest;
import com.lantu.connect.monitoring.entity.AlertRule;
import com.lantu.connect.monitoring.mapper.AlertRuleMapper;
import com.lantu.connect.monitoring.service.AlertRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 监控AlertRule服务实现
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Service
@RequiredArgsConstructor
public class AlertRuleServiceImpl implements AlertRuleService {

    private final AlertRuleMapper alertRuleMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(AlertRuleCreateRequest request) {
        AlertRule entity = new AlertRule();
        entity.setName(request.getName());
        entity.setMetric(request.getMetric() != null ? request.getMetric() : "custom");
        entity.setDescription(request.getConditionExpr());
        entity.setOperator(normalizeOperator(request.getOperator()));
        entity.setDuration(normalizeDuration(request.getDuration()));
        entity.setSeverity(normalizeSeverity(request.getSeverity()));
        if (request.getThreshold() != null) {
            entity.setThreshold(BigDecimal.valueOf(request.getThreshold()));
        }
        entity.setNotifyChannels(request.getNotifyChannels());
        entity.setEnabled(request.getEnabled() == null || request.getEnabled() != 0);
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
        if (request.getName() != null) {
            existing.setName(request.getName());
        }
        if (request.getMetric() != null) {
            existing.setMetric(request.getMetric());
        }
        if (request.getConditionExpr() != null) {
            existing.setDescription(request.getConditionExpr());
        }
        if (request.getThreshold() != null) {
            existing.setThreshold(BigDecimal.valueOf(request.getThreshold()));
        }
        if (request.getNotifyChannels() != null) {
            existing.setNotifyChannels(request.getNotifyChannels());
        }
        if (request.getEnabled() != null) {
            existing.setEnabled(request.getEnabled() != 0);
        }
        if (request.getOperator() != null) {
            existing.setOperator(normalizeOperator(StringUtils.hasText(request.getOperator()) ? request.getOperator() : null));
        }
        if (request.getSeverity() != null) {
            existing.setSeverity(normalizeSeverity(StringUtils.hasText(request.getSeverity()) ? request.getSeverity() : null));
        }
        if (request.getDuration() != null) {
            existing.setDuration(normalizeDuration(StringUtils.hasText(request.getDuration()) ? request.getDuration() : null));
        }
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
    public PageResult<AlertRule> page(int page, int pageSize, String name) {
        Page<AlertRule> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<AlertRule> q = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(name)) {
            q.like(AlertRule::getName, name);
        }
        q.orderByDesc(AlertRule::getUpdateTime);
        Page<AlertRule> result = alertRuleMapper.selectPage(p, q);
        return PageResults.from(result);
    }

    @Override
    public AlertRuleDryRunResult dryRun(String id, java.math.BigDecimal sampleValue) {
        AlertRule rule = getById(id);
        String op = rule.getOperator() != null ? rule.getOperator().trim() : "gt";
        BigDecimal threshold = rule.getThreshold() != null ? rule.getThreshold() : BigDecimal.ZERO;
        boolean fire = evaluate(sampleValue, threshold, op);
        String detail = String.format("样本值=%s, 阈值=%s, 算子=%s => %s",
                sampleValue, threshold, op, fire ? "会触发" : "不触发");
        return new AlertRuleDryRunResult(fire, op, threshold, sampleValue, detail);
    }

    /**
     * 默认 gte；空串视为默认。与 {@link #evaluate} 支持的算子一致。
     */
    private static String normalizeOperator(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "gte";
        }
        String s = raw.trim().toLowerCase();
        return switch (s) {
            case ">", "gt" -> "gt";
            case ">=", "ge", "gte" -> "gte";
            case "<", "lt" -> "lt";
            case "<=", "le", "lte" -> "lte";
            case "=", "eq" -> "eq";
            default -> throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的算子: " + raw);
        };
    }

    private static String normalizeSeverity(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "warning";
        }
        String s = raw.trim().toLowerCase();
        if ("medium".equals(s)) {
            return "warning";
        }
        if ("critical".equals(s) || "warning".equals(s) || "info".equals(s)) {
            return s;
        }
        throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的严重级别: " + raw);
    }

    private static String normalizeDuration(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "5m";
        }
        return raw.trim();
    }

    private static boolean evaluate(BigDecimal value, BigDecimal threshold, String op) {
        String n = op.toLowerCase();
        int cmp = value.compareTo(threshold);
        return switch (n) {
            case "gt", ">" -> cmp > 0;
            case "gte", "ge", ">=" -> cmp >= 0;
            case "lt", "<" -> cmp < 0;
            case "lte", "le", "<=" -> cmp <= 0;
            case "eq", "=" -> cmp == 0;
            default -> throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的算子: " + op);
        };
    }
}
