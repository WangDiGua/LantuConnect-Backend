package com.lantu.connect.monitoring.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.PageResults;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.util.UserDisplayNameResolver;
import com.lantu.connect.monitoring.dto.AlertActionNoteRequest;
import com.lantu.connect.monitoring.dto.AlertAssignRequest;
import com.lantu.connect.monitoring.dto.AlertBatchActionRequest;
import com.lantu.connect.monitoring.dto.AlertNotificationVO;
import com.lantu.connect.monitoring.dto.AlertRecordActionVO;
import com.lantu.connect.monitoring.dto.AlertRecordDetailVO;
import com.lantu.connect.monitoring.dto.AlertResolveRequest;
import com.lantu.connect.monitoring.dto.AlertRuleScopeOptionVO;
import com.lantu.connect.monitoring.dto.AlertRuleScopeResourceOptionVO;
import com.lantu.connect.monitoring.dto.AlertSilenceRequest;
import com.lantu.connect.monitoring.dto.AlertSummaryVO;
import com.lantu.connect.monitoring.dto.PageQuery;
import com.lantu.connect.monitoring.entity.AlertRecord;
import com.lantu.connect.monitoring.entity.AlertRecordAction;
import com.lantu.connect.monitoring.entity.AlertRule;
import com.lantu.connect.monitoring.mapper.AlertRecordActionMapper;
import com.lantu.connect.monitoring.mapper.AlertRecordMapper;
import com.lantu.connect.monitoring.mapper.AlertRuleMapper;
import com.lantu.connect.monitoring.service.AlertCenterService;
import com.lantu.connect.monitoring.service.AlertMetricSampler;
import com.lantu.connect.notification.entity.Notification;
import com.lantu.connect.notification.mapper.NotificationMapper;
import com.lantu.connect.notification.service.SystemNotificationFacade;
import com.lantu.connect.realtime.RealtimePushService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AlertCenterServiceImpl implements AlertCenterService {

    private static final Set<String> ACTIVE_STATUSES = Set.of("firing", "acknowledged", "silenced", "reopened");

    private final AlertRuleMapper alertRuleMapper;
    private final AlertRecordMapper alertRecordMapper;
    private final AlertRecordActionMapper alertRecordActionMapper;
    private final NotificationMapper notificationMapper;
    private final AlertMetricSampler alertMetricSampler;
    private final UserDisplayNameResolver userDisplayNameResolver;
    private final SystemNotificationFacade systemNotificationFacade;
    private final RealtimePushService realtimePushService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public PageResult<AlertRecord> pageEvents(PageQuery query, Long currentUserId) {
        Page<AlertRecord> page = new Page<>(query.getPage(), query.getPageSize());
        LambdaQueryWrapper<AlertRecord> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(AlertRecord::getRuleName, keyword)
                    .or()
                    .like(AlertRecord::getMessage, keyword)
                    .or()
                    .like(AlertRecord::getRuleId, keyword));
        }
        if (StringUtils.hasText(query.getSeverity()) && !"all".equalsIgnoreCase(query.getSeverity().trim())) {
            wrapper.eq(AlertRecord::getSeverity, query.getSeverity().trim().toLowerCase(Locale.ROOT));
        }
        if (StringUtils.hasText(query.getAlertStatus()) && !"all".equalsIgnoreCase(query.getAlertStatus().trim())) {
            wrapper.eq(AlertRecord::getStatus, query.getAlertStatus().trim().toLowerCase(Locale.ROOT));
        } else if (StringUtils.hasText(query.getStatus()) && !"all".equalsIgnoreCase(query.getStatus().trim())) {
            wrapper.eq(AlertRecord::getStatus, query.getStatus().trim().toLowerCase(Locale.ROOT));
        }
        if (StringUtils.hasText(query.getRuleId())) {
            wrapper.eq(AlertRecord::getRuleId, query.getRuleId().trim());
        }
        if (StringUtils.hasText(query.getAssignee())) {
            wrapper.eq(AlertRecord::getAssigneeUserId, parseLong(query.getAssignee()));
        }
        if (Boolean.TRUE.equals(query.getOnlyMine()) && currentUserId != null) {
            wrapper.eq(AlertRecord::getAssigneeUserId, currentUserId);
        }
        if (StringUtils.hasText(query.getScopeType()) && !"all".equalsIgnoreCase(query.getScopeType().trim())) {
            wrapper.apply("LOWER(JSON_UNQUOTE(JSON_EXTRACT(rule_snapshot_json, '$.scopeType'))) = {0}",
                    query.getScopeType().trim().toLowerCase(Locale.ROOT));
        }
        if (StringUtils.hasText(query.getResourceType()) && !"all".equalsIgnoreCase(query.getResourceType().trim())) {
            String resourceType = query.getResourceType().trim().toLowerCase(Locale.ROOT);
            wrapper.and(w -> w.apply(
                    "(LOWER(JSON_UNQUOTE(JSON_EXTRACT(labels, '$.resource_type'))) = {0} "
                            + "OR LOWER(JSON_UNQUOTE(JSON_EXTRACT(rule_snapshot_json, '$.scopeResourceType'))) = {0})",
                    resourceType));
        }
        wrapper.orderByDesc(AlertRecord::getFiredAt);
        Page<AlertRecord> result = alertRecordMapper.selectPage(page, wrapper);
        enrichRecords(result.getRecords());
        return PageResults.from(result);
    }

    @Override
    public AlertSummaryVO summary(Long currentUserId) {
        return AlertSummaryVO.builder()
                .firing(countByStatuses("firing", "reopened"))
                .acknowledged(countByStatuses("acknowledged"))
                .silenced(countByStatuses("silenced"))
                .resolvedToday(countResolvedToday())
                .mine(countMine(currentUserId))
                .enabledRules(countEnabledRules())
                .build();
    }

    @Override
    public AlertRecordDetailVO detail(String id) {
        AlertRecord record = requireRecord(id);
        enrichRecords(List.of(record));
        return toDetail(record);
    }

    @Override
    public List<AlertRecordActionVO> actions(String id) {
        requireRecord(id);
        return listActionVos(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ack(String id, Long operatorUserId, AlertActionNoteRequest request) {
        AlertRecord record = requireRecord(id);
        String previous = record.getStatus();
        if ("resolved".equals(previous)) {
            throw new BusinessException(ResultCode.CONFLICT, "已恢复事件不能认领");
        }
        LocalDateTime now = LocalDateTime.now();
        record.setStatus("acknowledged");
        record.setAckAt(now);
        record.setResolvedAt(null);
        alertRecordMapper.updateById(record);
        appendAction(record.getId(), "ack", operatorUserId, noteOf(request), previous, record.getStatus(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assign(String id, Long operatorUserId, AlertAssignRequest request) {
        AlertRecord record = requireRecord(id);
        String previous = record.getStatus();
        record.setAssigneeUserId(request.getAssigneeUserId());
        if ("firing".equals(previous) || "reopened".equals(previous)) {
            record.setStatus("acknowledged");
            record.setAckAt(LocalDateTime.now());
        }
        alertRecordMapper.updateById(record);
        Map<String, Object> extra = Map.of("assigneeUserId", request.getAssigneeUserId());
        appendAction(record.getId(), "assign", operatorUserId, request.getNote(), previous, record.getStatus(), extra);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void silence(String id, Long operatorUserId, AlertSilenceRequest request) {
        AlertRecord record = requireRecord(id);
        String previous = record.getStatus();
        if ("resolved".equals(previous)) {
            throw new BusinessException(ResultCode.CONFLICT, "已恢复事件不能静默");
        }
        record.setStatus("silenced");
        record.setSilencedAt(LocalDateTime.now());
        alertRecordMapper.updateById(record);
        appendAction(record.getId(), "silence", operatorUserId, request.getNote(), previous, record.getStatus(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resolve(String id, Long operatorUserId, AlertResolveRequest request) {
        AlertRecord record = requireRecord(id);
        String previous = record.getStatus();
        record.setStatus("resolved");
        record.setResolvedAt(LocalDateTime.now());
        alertRecordMapper.updateById(record);
        appendAction(record.getId(), "resolve", operatorUserId, request.getNote(), previous, record.getStatus(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reopen(String id, Long operatorUserId, AlertActionNoteRequest request) {
        AlertRecord record = requireRecord(id);
        String previous = record.getStatus();
        if (!"resolved".equals(previous)) {
            throw new BusinessException(ResultCode.CONFLICT, "仅已恢复事件可重新打开");
        }
        record.setStatus("reopened");
        record.setResolvedAt(null);
        record.setReopenedAt(LocalDateTime.now());
        resetRecoveryCounter(record);
        alertRecordMapper.updateById(record);
        appendAction(record.getId(), "reopen", operatorUserId, noteOf(request), previous, record.getStatus(), null);
        pushAlert(record, "manual_reopen");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchAction(Long operatorUserId, AlertBatchActionRequest request) {
        if (request == null || request.getIds() == null || request.getIds().isEmpty()) {
            return;
        }
        String action = request.getAction().trim().toLowerCase(Locale.ROOT);
        for (String id : request.getIds()) {
            switch (action) {
                case "ack" -> ack(id, operatorUserId, toNoteRequest(request.getNote()));
                case "silence" -> silence(id, operatorUserId, toSilenceRequest(request.getNote()));
                case "resolve" -> resolve(id, operatorUserId, toResolveRequest(request.getNote()));
                case "reopen" -> reopen(id, operatorUserId, toNoteRequest(request.getNote()));
                case "assign" -> {
                    AlertAssignRequest assignRequest = new AlertAssignRequest();
                    assignRequest.setAssigneeUserId(request.getAssigneeUserId());
                    assignRequest.setNote(request.getNote());
                    assign(id, operatorUserId, assignRequest);
                }
                default -> throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的批量动作: " + action);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void evaluateEnabledRules() {
        List<AlertRule> rules = alertRuleMapper.selectList(new LambdaQueryWrapper<AlertRule>()
                .eq(AlertRule::getEnabled, true));
        for (AlertRule rule : rules) {
            evaluateRule(rule);
        }
    }

    @Override
    public AlertRuleScopeOptionVO scopeOptions() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, resource_type, display_name
                FROM t_resource
                WHERE deleted = 0
                ORDER BY update_time DESC, id DESC
                LIMIT 300
                """);
        List<AlertRuleScopeResourceOptionVO> resources = rows.stream()
                .map(row -> AlertRuleScopeResourceOptionVO.builder()
                        .id(parseLong(row.get("id")))
                        .resourceType(text(row.get("resource_type")))
                        .displayName(text(row.get("display_name")))
                        .build())
                .toList();
        return AlertRuleScopeOptionVO.builder()
                .resourceTypes(List.of("agent", "skill", "mcp", "app", "dataset"))
                .resources(resources)
                .build();
    }

    private void evaluateRule(AlertRule rule) {
        AlertMetricSampler.AlertMetricSample sample = alertMetricSampler.sample(rule);
        boolean fire = alertMetricSampler.evaluate(sample.getSampleValue(), rule.getThreshold(), rule.getOperator());
        AlertRecord activeRecord = findActiveRecord(rule.getId());
        if (fire) {
            if (activeRecord == null) {
                AlertRecord latest = findLatestRecord(rule.getId());
                if (latest != null && "resolved".equalsIgnoreCase(latest.getStatus())) {
                    reopenResolvedRule(latest, rule, sample, true);
                } else {
                    createFiringRecord(rule, sample);
                }
            } else {
                refreshActiveRecord(activeRecord, rule, sample);
            }
            return;
        }
        if (activeRecord != null) {
            advanceRecoveryWindow(activeRecord);
        }
    }

    private void createFiringRecord(AlertRule rule, AlertMetricSampler.AlertMetricSample sample) {
        LocalDateTime now = LocalDateTime.now();
        AlertRecord record = new AlertRecord();
        record.setId(UUID.randomUUID().toString());
        record.setRuleId(rule.getId());
        record.setRuleName(rule.getName());
        record.setSeverity(rule.getSeverity());
        record.setStatus("firing");
        record.setMessage(buildMessage(rule, sample));
        record.setSource("alert-evaluator");
        record.setLabels(buildLabels(rule, sample));
        record.setLastSampleValue(sample.getSampleValue());
        record.setTriggerSnapshotJson(buildTriggerSnapshot(rule, sample, 0));
        record.setRuleSnapshotJson(buildRuleSnapshot(rule));
        record.setFiredAt(now);
        alertRecordMapper.insert(record);
        appendAction(record.getId(), "fire", null, "首次触发", null, "firing", sample.getSnapshot());
        pushAlert(record, "new_fire");
    }

    private void reopenResolvedRule(AlertRecord record,
                                    AlertRule rule,
                                    AlertMetricSampler.AlertMetricSample sample,
                                    boolean automatic) {
        String previous = record.getStatus();
        record.setStatus("reopened");
        record.setResolvedAt(null);
        record.setReopenedAt(LocalDateTime.now());
        record.setMessage(buildMessage(rule, sample));
        record.setSeverity(rule.getSeverity());
        record.setLabels(buildLabels(rule, sample));
        record.setLastSampleValue(sample.getSampleValue());
        record.setTriggerSnapshotJson(buildTriggerSnapshot(rule, sample, 0));
        record.setRuleSnapshotJson(buildRuleSnapshot(rule));
        alertRecordMapper.updateById(record);
        appendAction(record.getId(),
                automatic ? "auto_reopen" : "reopen",
                null,
                automatic ? "规则再次命中" : "人工重新打开",
                previous,
                "reopened",
                sample.getSnapshot());
        pushAlert(record, "reopened");
    }

    private void refreshActiveRecord(AlertRecord record,
                                     AlertRule rule,
                                     AlertMetricSampler.AlertMetricSample sample) {
        record.setMessage(buildMessage(rule, sample));
        record.setLabels(buildLabels(rule, sample));
        record.setLastSampleValue(sample.getSampleValue());
        record.setTriggerSnapshotJson(buildTriggerSnapshot(rule, sample, 0));
        record.setRuleSnapshotJson(buildRuleSnapshot(rule));
        alertRecordMapper.updateById(record);
    }

    private void advanceRecoveryWindow(AlertRecord record) {
        Map<String, Object> snapshot = copyMap(record.getTriggerSnapshotJson());
        int recoveryCount = intValue(snapshot.get("recoveryCount")) + 1;
        snapshot.put("recoveryCount", recoveryCount);
        snapshot.put("lastRecoveryCheckAt", LocalDateTime.now().toString());
        record.setTriggerSnapshotJson(snapshot);
        if (recoveryCount < 2) {
            alertRecordMapper.updateById(record);
            return;
        }
        String previous = record.getStatus();
        record.setStatus("resolved");
        record.setResolvedAt(LocalDateTime.now());
        snapshot.put("resolvedAt", LocalDateTime.now().toString());
        record.setTriggerSnapshotJson(snapshot);
        alertRecordMapper.updateById(record);
        appendAction(record.getId(), "auto_resolve", null, "连续两个窗口恢复正常", previous, "resolved", snapshot);
    }

    private AlertRecord requireRecord(String id) {
        AlertRecord record = alertRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return record;
    }

    private AlertRecord findActiveRecord(String ruleId) {
        return alertRecordMapper.selectOne(new LambdaQueryWrapper<AlertRecord>()
                .eq(AlertRecord::getRuleId, ruleId)
                .in(AlertRecord::getStatus, ACTIVE_STATUSES)
                .orderByDesc(AlertRecord::getFiredAt)
                .last("LIMIT 1"));
    }

    private AlertRecord findLatestRecord(String ruleId) {
        return alertRecordMapper.selectOne(new LambdaQueryWrapper<AlertRecord>()
                .eq(AlertRecord::getRuleId, ruleId)
                .orderByDesc(AlertRecord::getFiredAt)
                .last("LIMIT 1"));
    }

    private void appendAction(String recordId,
                              String actionType,
                              Long operatorUserId,
                              String note,
                              String previousStatus,
                              String nextStatus,
                              Map<String, Object> extra) {
        AlertRecordAction action = new AlertRecordAction();
        action.setRecordId(recordId);
        action.setActionType(actionType);
        action.setOperatorUserId(operatorUserId);
        action.setNote(StringUtils.hasText(note) ? note.trim() : null);
        action.setPreviousStatus(previousStatus);
        action.setNextStatus(nextStatus);
        action.setExtraJson(extra == null || extra.isEmpty() ? null : extra);
        action.setCreateTime(LocalDateTime.now());
        alertRecordActionMapper.insert(action);
    }

    private void enrichRecords(List<AlertRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Map<String, AlertRule> ruleMap = loadRules(records.stream().map(AlertRecord::getRuleId).toList());
        Map<Long, String> assigneeNames = loadUserNames(records.stream()
                .map(AlertRecord::getAssigneeUserId)
                .filter(Objects::nonNull)
                .toList());
        Map<Long, ResourceNameRef> resourceNames = loadResourceNames(records, ruleMap);
        Map<String, Integer> notificationCounts = loadNotificationCounts(records.stream().map(AlertRecord::getId).toList());
        for (AlertRecord record : records) {
            AlertRule rule = ruleMap.get(record.getRuleId());
            record.setAssigneeName(assigneeNames.get(record.getAssigneeUserId()));
            record.setNotificationCount(notificationCounts.getOrDefault(record.getId(), 0));
            enrichRuleProjection(record, rule, resourceNames);
            record.setTriggerReason(triggerReason(record));
            record.setActiveSeconds(resolveActiveSeconds(record));
        }
    }

    private Map<String, AlertRule> loadRules(Collection<String> ruleIds) {
        List<String> ids = ruleIds == null ? List.of() : ruleIds.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return alertRuleMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(AlertRule::getId, item -> item));
    }

    private Map<Long, String> loadUserNames(Collection<Long> userIds) {
        return userIds == null ? Map.of() : userDisplayNameResolver.resolveDisplayNames(userIds);
    }

    private Map<Long, ResourceNameRef> loadResourceNames(List<AlertRecord> records, Map<String, AlertRule> ruleMap) {
        Set<Long> ids = new LinkedHashSet<>();
        for (AlertRecord record : records) {
            AlertRule rule = ruleMap.get(record.getRuleId());
            Long resourceId = extractScopeResourceId(record, rule);
            if (resourceId != null) {
                ids.add(resourceId);
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, resource_type, display_name FROM t_resource WHERE id IN (" + placeholders + ")",
                ids.toArray());
        Map<Long, ResourceNameRef> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Long id = parseLong(row.get("id"));
            if (id != null) {
                result.put(id, new ResourceNameRef(text(row.get("resource_type")), text(row.get("display_name"))));
            }
        }
        return result;
    }

    private Map<String, Integer> loadNotificationCounts(List<String> recordIds) {
        List<String> ids = recordIds.stream().filter(StringUtils::hasText).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Object> args = new ArrayList<>();
        args.add("alert");
        args.addAll(ids);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT source_id, COUNT(*) AS cnt FROM t_notification WHERE source_type = ? AND source_id IN ("
                        + placeholders + ") GROUP BY source_id",
                args.toArray());
        Map<String, Integer> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            result.put(text(row.get("source_id")), intValue(row.get("cnt")));
        }
        return result;
    }

    private void enrichRuleProjection(AlertRecord record, AlertRule rule, Map<Long, ResourceNameRef> resourceNames) {
        Map<String, Object> ruleSnapshot = record.getRuleSnapshotJson();
        String scopeType = stringOrSnapshot(rule == null ? null : rule.getScopeType(), ruleSnapshot, "scopeType", "global");
        String scopeResourceType = stringOrSnapshot(rule == null ? null : rule.getScopeResourceType(), ruleSnapshot, "scopeResourceType", null);
        Long scopeResourceId = numberOrSnapshot(rule == null ? null : rule.getScopeResourceId(), ruleSnapshot, "scopeResourceId");
        ResourceNameRef resourceRef = scopeResourceId == null ? null : resourceNames.get(scopeResourceId);
        record.setScopeType(scopeType);
        record.setResourceType(scopeResourceType != null ? scopeResourceType : resourceRef == null ? null : resourceRef.resourceType());
        record.setResourceId(scopeResourceId);
        record.setResourceName(resourceRef == null ? null : resourceRef.displayName());
        record.setRuleMetric(stringOrSnapshot(rule == null ? null : rule.getMetric(), ruleSnapshot, "metric", null));
        record.setRuleOperator(stringOrSnapshot(rule == null ? null : rule.getOperator(), ruleSnapshot, "operator", null));
        record.setRuleThreshold(decimalOrSnapshot(rule == null ? null : rule.getThreshold(), ruleSnapshot, "threshold"));
        record.setRuleDuration(stringOrSnapshot(rule == null ? null : rule.getDuration(), ruleSnapshot, "duration", null));
        record.setRuleExpression(buildRuleExpression(record.getRuleMetric(), record.getRuleOperator(), record.getRuleThreshold()));
        record.setScopeLabel(buildScopeLabel(scopeType, record.getResourceType(), record.getResourceName(), record.getResourceId()));
    }

    private AlertRecordDetailVO toDetail(AlertRecord record) {
        return AlertRecordDetailVO.builder()
                .id(record.getId())
                .ruleId(record.getRuleId())
                .ruleName(record.getRuleName())
                .severity(record.getSeverity())
                .status(record.getStatus())
                .message(record.getMessage())
                .source(record.getSource())
                .assigneeUserId(record.getAssigneeUserId())
                .assigneeName(record.getAssigneeName())
                .ackAt(record.getAckAt())
                .silencedAt(record.getSilencedAt())
                .reopenedAt(record.getReopenedAt())
                .firedAt(record.getFiredAt())
                .resolvedAt(record.getResolvedAt())
                .lastSampleValue(record.getLastSampleValue())
                .scopeType(record.getScopeType())
                .scopeLabel(record.getScopeLabel())
                .resourceType(record.getResourceType())
                .resourceId(record.getResourceId())
                .resourceName(record.getResourceName())
                .metric(record.getRuleMetric())
                .operator(record.getRuleOperator())
                .threshold(record.getRuleThreshold())
                .duration(record.getRuleDuration())
                .ruleExpression(record.getRuleExpression())
                .triggerReason(record.getTriggerReason())
                .activeSeconds(record.getActiveSeconds())
                .notificationCount(record.getNotificationCount())
                .labels(record.getLabels())
                .triggerSnapshot(record.getTriggerSnapshotJson())
                .ruleSnapshot(record.getRuleSnapshotJson())
                .actions(listActionVos(record.getId()))
                .notifications(listNotifications(record.getId()))
                .build();
    }

    private List<AlertRecordActionVO> listActionVos(String recordId) {
        List<AlertRecordAction> actions = alertRecordActionMapper.selectList(new LambdaQueryWrapper<AlertRecordAction>()
                .eq(AlertRecordAction::getRecordId, recordId)
                .orderByDesc(AlertRecordAction::getCreateTime));
        Map<Long, String> operatorNames = userDisplayNameResolver.resolveDisplayNames(actions.stream()
                .map(AlertRecordAction::getOperatorUserId)
                .filter(Objects::nonNull)
                .toList());
        return actions.stream()
                .map(action -> AlertRecordActionVO.builder()
                        .id(action.getId())
                        .actionType(action.getActionType())
                        .operatorUserId(action.getOperatorUserId())
                        .operatorName(operatorNames.get(action.getOperatorUserId()))
                        .note(action.getNote())
                        .previousStatus(action.getPreviousStatus())
                        .nextStatus(action.getNextStatus())
                        .extra(action.getExtraJson())
                        .createTime(action.getCreateTime())
                        .build())
                .toList();
    }

    private List<AlertNotificationVO> listNotifications(String recordId) {
        List<Notification> notifications = notificationMapper.selectList(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getSourceType, "alert")
                .eq(Notification::getSourceId, recordId)
                .orderByDesc(Notification::getCreateTime));
        return notifications.stream()
                .map(notification -> AlertNotificationVO.builder()
                        .id(notification.getId())
                        .userId(notification.getUserId())
                        .title(notification.getTitle())
                        .body(notification.getBody())
                        .severity(notification.getSeverity())
                        .read(Boolean.TRUE.equals(notification.getIsRead()))
                        .createTime(notification.getCreateTime())
                        .build())
                .toList();
    }

    private void pushAlert(AlertRecord record, String reason) {
        realtimePushService.pushAlertFiring(
                record.getRuleId(),
                record.getRuleName(),
                record.getSeverity(),
                record.getId(),
                record.getMessage());
        String title = "系统告警触发: " + record.getRuleName();
        String body = "规则=%s\n级别=%s\n指标=%s\n阈值=%s\n样本=%s\n事件=%s".formatted(
                record.getRuleName(),
                record.getSeverity(),
                record.getRuleMetric(),
                record.getRuleThreshold() == null ? "-" : record.getRuleThreshold().toPlainString(),
                record.getLastSampleValue() == null ? "-" : record.getLastSampleValue().toPlainString(),
                record.getId());
        Set<Long> audience = new LinkedHashSet<>();
        audience.addAll(systemNotificationFacade.findRoleUserIds("platform_admin"));
        audience.addAll(systemNotificationFacade.findRoleUserIds("admin"));
        for (Long userId : audience) {
            systemNotificationFacade.notifyToUser(userId, "alert", title, body, "alert", record.getId());
        }
        appendAction(record.getId(), "notify", null, reason, record.getStatus(), record.getStatus(), Map.of("reason", reason));
    }

    private long countByStatuses(String... statuses) {
        Long count = alertRecordMapper.selectCount(new LambdaQueryWrapper<AlertRecord>()
                .in(AlertRecord::getStatus, List.of(statuses)));
        return count == null ? 0L : count;
    }

    private long countResolvedToday() {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        Long count = alertRecordMapper.selectCount(new LambdaQueryWrapper<AlertRecord>()
                .eq(AlertRecord::getStatus, "resolved")
                .ge(AlertRecord::getResolvedAt, start));
        return count == null ? 0L : count;
    }

    private long countMine(Long currentUserId) {
        if (currentUserId == null) {
            return 0L;
        }
        Long count = alertRecordMapper.selectCount(new LambdaQueryWrapper<AlertRecord>()
                .eq(AlertRecord::getAssigneeUserId, currentUserId)
                .in(AlertRecord::getStatus, ACTIVE_STATUSES));
        return count == null ? 0L : count;
    }

    private long countEnabledRules() {
        Long count = alertRuleMapper.selectCount(new LambdaQueryWrapper<AlertRule>().eq(AlertRule::getEnabled, true));
        return count == null ? 0L : count;
    }

    private Map<String, Object> buildTriggerSnapshot(AlertRule rule,
                                                     AlertMetricSampler.AlertMetricSample sample,
                                                     int recoveryCount) {
        Map<String, Object> snapshot = copyMap(sample.getSnapshot());
        snapshot.put("ruleName", rule.getName());
        snapshot.put("reason", buildRuleExpression(rule.getMetric(), rule.getOperator(), rule.getThreshold())
                + " / sample=" + sample.getSampleValue());
        snapshot.put("recoveryCount", recoveryCount);
        return snapshot;
    }

    private Map<String, Object> buildRuleSnapshot(AlertRule rule) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("scopeType", rule.getScopeType());
        snapshot.put("scopeResourceType", rule.getScopeResourceType());
        snapshot.put("scopeResourceId", rule.getScopeResourceId());
        snapshot.put("metric", rule.getMetric());
        snapshot.put("operator", rule.getOperator());
        snapshot.put("threshold", rule.getThreshold());
        snapshot.put("duration", rule.getDuration());
        snapshot.put("severity", rule.getSeverity());
        if (rule.getLabelFilters() != null && !rule.getLabelFilters().isEmpty()) {
            snapshot.put("labelFilters", rule.getLabelFilters());
        }
        return snapshot;
    }

    private Map<String, Object> buildLabels(AlertRule rule, AlertMetricSampler.AlertMetricSample sample) {
        Map<String, Object> labels = new LinkedHashMap<>();
        if (sample.getLabels() != null) {
            labels.putAll(sample.getLabels());
        }
        labels.put("rule_id", rule.getId());
        labels.put("rule_name", rule.getName());
        return labels;
    }

    private String buildMessage(AlertRule rule, AlertMetricSampler.AlertMetricSample sample) {
        return "规则触发: %s, metric=%s, sample=%s, threshold=%s".formatted(
                rule.getName(),
                sample.getMetric(),
                sample.getSampleValue(),
                rule.getThreshold());
    }

    private void resetRecoveryCounter(AlertRecord record) {
        Map<String, Object> snapshot = copyMap(record.getTriggerSnapshotJson());
        snapshot.put("recoveryCount", 0);
        record.setTriggerSnapshotJson(snapshot);
    }

    private String triggerReason(AlertRecord record) {
        Object reason = record.getTriggerSnapshotJson() == null ? null : record.getTriggerSnapshotJson().get("reason");
        if (StringUtils.hasText(text(reason))) {
            return text(reason);
        }
        return buildRuleExpression(record.getRuleMetric(), record.getRuleOperator(), record.getRuleThreshold());
    }

    private long resolveActiveSeconds(AlertRecord record) {
        LocalDateTime end = record.getResolvedAt() != null ? record.getResolvedAt() : LocalDateTime.now();
        LocalDateTime start = record.getFiredAt() != null ? record.getFiredAt() : end;
        return Math.max(0L, Duration.between(start, end).getSeconds());
    }

    private String buildRuleExpression(String metric, String operator, BigDecimal threshold) {
        String left = StringUtils.hasText(metric) ? metric : "-";
        String op = StringUtils.hasText(operator) ? operator : "gte";
        String right = threshold == null ? "0" : threshold.toPlainString();
        return left + " " + op + " " + right;
    }

    private String buildScopeLabel(String scopeType, String resourceType, String resourceName, Long resourceId) {
        if ("resource".equals(scopeType)) {
            return "%s / %s".formatted(
                    text(resourceType),
                    StringUtils.hasText(resourceName) ? resourceName : String.valueOf(resourceId));
        }
        if ("resource_type".equals(scopeType)) {
            return text(resourceType);
        }
        return "全平台";
    }

    private String stringOrSnapshot(String value, Map<String, Object> snapshot, String key, String fallback) {
        if (StringUtils.hasText(value)) {
            return value.trim();
        }
        if (snapshot != null && snapshot.get(key) != null) {
            return text(snapshot.get(key));
        }
        return fallback;
    }

    private Long numberOrSnapshot(Long value, Map<String, Object> snapshot, String key) {
        if (value != null) {
            return value;
        }
        if (snapshot == null) {
            return null;
        }
        return parseLong(snapshot.get(key));
    }

    private BigDecimal decimalOrSnapshot(BigDecimal value, Map<String, Object> snapshot, String key) {
        if (value != null) {
            return value;
        }
        if (snapshot == null || snapshot.get(key) == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(snapshot.get(key)));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long extractScopeResourceId(AlertRecord record, AlertRule rule) {
        if (rule != null && rule.getScopeResourceId() != null) {
            return rule.getScopeResourceId();
        }
        return numberOrSnapshot(null, record.getRuleSnapshotJson(), "scopeResourceId");
    }

    private AlertActionNoteRequest toNoteRequest(String note) {
        AlertActionNoteRequest request = new AlertActionNoteRequest();
        request.setNote(note);
        return request;
    }

    private AlertSilenceRequest toSilenceRequest(String note) {
        AlertSilenceRequest request = new AlertSilenceRequest();
        request.setNote(note);
        return request;
    }

    private AlertResolveRequest toResolveRequest(String note) {
        AlertResolveRequest request = new AlertResolveRequest();
        request.setNote(note);
        return request;
    }

    private String noteOf(AlertActionNoteRequest request) {
        return request == null ? null : request.getNote();
    }

    private Map<String, Object> copyMap(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(raw);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static Long parseLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record ResourceNameRef(String resourceType, String displayName) {
    }
}
