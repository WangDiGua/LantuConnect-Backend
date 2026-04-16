package com.lantu.connect.monitoring.controller;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.R;
import com.lantu.connect.common.security.RequirePermission;
import com.lantu.connect.common.security.RequireRole;
import com.lantu.connect.monitoring.dto.AlertRuleCreateRequest;
import com.lantu.connect.monitoring.dto.AlertActionNoteRequest;
import com.lantu.connect.monitoring.dto.AlertAssignRequest;
import com.lantu.connect.monitoring.dto.AlertBatchActionRequest;
import com.lantu.connect.monitoring.dto.AlertRecordActionVO;
import com.lantu.connect.monitoring.dto.AlertRecordDetailVO;
import com.lantu.connect.monitoring.dto.AlertResolveRequest;
import com.lantu.connect.monitoring.dto.AlertRuleDryRunRequest;
import com.lantu.connect.monitoring.dto.AlertRuleDryRunResult;
import com.lantu.connect.monitoring.dto.AlertRuleMetricOptionVO;
import com.lantu.connect.monitoring.dto.AlertRuleScopeOptionVO;
import com.lantu.connect.monitoring.dto.AlertSilenceRequest;
import com.lantu.connect.monitoring.dto.AlertSummaryVO;
import com.lantu.connect.monitoring.dto.AlertRuleUpdateRequest;
import com.lantu.connect.monitoring.dto.KpiMetric;
import com.lantu.connect.monitoring.dto.PageQuery;
import com.lantu.connect.monitoring.dto.PerformanceAnalysisVO;
import com.lantu.connect.monitoring.dto.QualityHistoryPointVO;
import com.lantu.connect.monitoring.entity.AlertRecord;
import com.lantu.connect.monitoring.entity.AlertRule;
import com.lantu.connect.monitoring.entity.CallLog;
import com.lantu.connect.monitoring.entity.TraceSpan;
import com.lantu.connect.monitoring.service.AlertCenterService;
import com.lantu.connect.monitoring.service.AlertRuleService;
import com.lantu.connect.monitoring.service.MonitoringService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 监控 Monitoring 控制器
 *
 * @author 王帝
 * @date 2026-03-21
 */
@RestController
@RequestMapping("/monitoring")
@RequiredArgsConstructor
public class MonitoringController {

    private final MonitoringService monitoringService;
    private final AlertRuleService alertRuleService;
    private final AlertCenterService alertCenterService;

    @GetMapping("/kpis")
    @RequirePermission({"monitor:view"})
    public R<List<KpiMetric>> kpis() {
        return R.ok(monitoringService.kpis());
    }

    @GetMapping("/resources/{type}/{id}/quality-history")
    @RequirePermission({"monitor:view"})
    public R<List<QualityHistoryPointVO>> qualityHistory(@PathVariable String type,
                                                          @PathVariable Long id,
                                                          @RequestParam(required = false) String from,
                                                          @RequestParam(required = false) String to) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime fromTime = StringUtils.hasText(from) ? LocalDateTime.parse(from.trim(), fmt) : LocalDateTime.now().minusDays(7);
        LocalDateTime toTime = StringUtils.hasText(to) ? LocalDateTime.parse(to.trim(), fmt) : LocalDateTime.now();
        return R.ok(monitoringService.qualityHistory(type, id, fromTime, toTime));
    }

    @GetMapping("/performance")
    @RequirePermission({"monitor:view"})
    public R<List<Map<String, Object>>> performance(@RequestParam(required = false) String resourceType) {
        return R.ok(monitoringService.performance(resourceType));
    }

    @GetMapping("/performance-analysis")
    @RequirePermission({"monitor:view"})
    public R<PerformanceAnalysisVO> performanceAnalysis(@RequestParam(required = false, defaultValue = "24h") String window,
                                                        @RequestParam(required = false) String resourceType,
                                                        @RequestParam(required = false) Long resourceId) {
        return R.ok(monitoringService.performanceAnalysis(window, resourceType, resourceId));
    }

    /**
     * 监控概览：近 N 小时按统一 resource_type（含 unknown）聚合的调用量与错误数。
     */
    @GetMapping("/call-summary-by-resource")
    @RequirePermission({"monitor:view"})
    public R<List<Map<String, Object>>> callSummaryByResource(
            @RequestParam(required = false, defaultValue = "24") int hours) {
        return R.ok(monitoringService.callSummaryByResource(hours));
    }

    @GetMapping("/call-logs")
    @RequirePermission({"monitor:view"})
    public R<PageResult<CallLog>> callLogs(PageQuery query) {
        return R.ok(monitoringService.callLogs(query));
    }

    @GetMapping("/alerts")
    @RequirePermission({"monitor:view"})
    public R<PageResult<AlertRecord>> alerts(PageQuery query,
                                             @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return R.ok(alertCenterService.pageEvents(query, userId));
    }

    @GetMapping("/alerts/summary")
    @RequirePermission({"monitor:view"})
    public R<AlertSummaryVO> alertSummary(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return R.ok(alertCenterService.summary(userId));
    }

    @GetMapping("/alerts/{id}")
    @RequirePermission({"monitor:view"})
    public R<AlertRecordDetailVO> alertDetail(@PathVariable String id) {
        return R.ok(alertCenterService.detail(id));
    }

    @GetMapping("/alerts/{id}/actions")
    @RequirePermission({"monitor:view"})
    public R<List<AlertRecordActionVO>> alertActions(@PathVariable String id) {
        return R.ok(alertCenterService.actions(id));
    }

    @PostMapping("/alerts/{id}/ack")
    @RequireRole({"platform_admin"})
    public R<Void> ackAlert(@PathVariable String id,
                            @RequestHeader("X-User-Id") Long operatorUserId,
                            @RequestBody(required = false) AlertActionNoteRequest request) {
        alertCenterService.ack(id, operatorUserId, request);
        return R.ok();
    }

    @PostMapping("/alerts/{id}/assign")
    @RequireRole({"platform_admin"})
    public R<Void> assignAlert(@PathVariable String id,
                               @RequestHeader("X-User-Id") Long operatorUserId,
                               @Valid @RequestBody AlertAssignRequest request) {
        alertCenterService.assign(id, operatorUserId, request);
        return R.ok();
    }

    @PostMapping("/alerts/{id}/silence")
    @RequireRole({"platform_admin"})
    public R<Void> silenceAlert(@PathVariable String id,
                                @RequestHeader("X-User-Id") Long operatorUserId,
                                @RequestBody(required = false) AlertSilenceRequest request) {
        alertCenterService.silence(id, operatorUserId, request == null ? new AlertSilenceRequest() : request);
        return R.ok();
    }

    @PostMapping("/alerts/{id}/resolve")
    @RequireRole({"platform_admin"})
    public R<Void> resolveAlert(@PathVariable String id,
                                @RequestHeader("X-User-Id") Long operatorUserId,
                                @RequestBody(required = false) AlertResolveRequest request) {
        alertCenterService.resolve(id, operatorUserId, request == null ? new AlertResolveRequest() : request);
        return R.ok();
    }

    @PostMapping("/alerts/{id}/reopen")
    @RequireRole({"platform_admin"})
    public R<Void> reopenAlert(@PathVariable String id,
                               @RequestHeader("X-User-Id") Long operatorUserId,
                               @RequestBody(required = false) AlertActionNoteRequest request) {
        alertCenterService.reopen(id, operatorUserId, request);
        return R.ok();
    }

    @PostMapping("/alerts/batch-action")
    @RequireRole({"platform_admin"})
    public R<Void> batchAlertAction(@RequestHeader("X-User-Id") Long operatorUserId,
                                    @Valid @RequestBody AlertBatchActionRequest request) {
        alertCenterService.batchAction(operatorUserId, request);
        return R.ok();
    }

    @GetMapping("/traces")
    @RequirePermission({"monitor:view"})
    public R<PageResult<TraceSpan>> traces(PageQuery query) {
        return R.ok(monitoringService.traces(query));
    }

    @PostMapping("/alert-rules")
    @RequireRole({"platform_admin"})
    public R<String> createAlertRule(@Valid @RequestBody AlertRuleCreateRequest request) {
        return R.ok(alertRuleService.create(request));
    }

    @PutMapping("/alert-rules/{id}")
    @RequireRole({"platform_admin"})
    public R<Void> updateAlertRule(@PathVariable String id, @Valid @RequestBody AlertRuleUpdateRequest request) {
        request.setId(id);
        alertRuleService.update(request);
        return R.ok();
    }

    @DeleteMapping("/alert-rules/{id}")
    @RequireRole({"platform_admin"})
    public R<Void> deleteAlertRule(@PathVariable String id) {
        alertRuleService.delete(id);
        return R.ok();
    }

    @GetMapping("/alert-rules/{id}")
    @RequirePermission({"monitor:view"})
    public R<AlertRule> getAlertRule(@PathVariable String id) {
        return R.ok(alertRuleService.getById(id));
    }

    @GetMapping("/alert-rules")
    @RequirePermission({"monitor:view"})
    public R<PageResult<AlertRule>> pageAlertRules(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String scopeType,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String severity) {
        String filter = StringUtils.hasText(keyword) ? keyword.trim() : name;
        return R.ok(alertRuleService.page(page, pageSize, filter, scopeType, resourceType, enabled, severity));
    }

    /**
     * 告警规则指标 id（平台级）；规则触发时可在 labels 中写入 resource_type 以便按五类资源分流告警。
     * 扩展指标可改为读配置或 DB。
     */
    @GetMapping("/alert-rule-metrics")
    @RequirePermission({"monitor:view"})
    public R<List<AlertRuleMetricOptionVO>> alertRuleMetrics() {
        return R.ok(alertRuleService.metricOptions());
    }

    @GetMapping("/alert-rule-scopes/options")
    @RequirePermission({"monitor:view"})
    public R<AlertRuleScopeOptionVO> alertRuleScopeOptions() {
        return R.ok(alertCenterService.scopeOptions());
    }

    @PostMapping("/alert-rules/{id}/dry-run")
    @RequireRole({"platform_admin"})
    public R<AlertRuleDryRunResult> dryRunAlertRule(
            @PathVariable String id,
            @Valid @RequestBody AlertRuleDryRunRequest request) {
        return R.ok(alertRuleService.dryRun(id, request));
    }
}
