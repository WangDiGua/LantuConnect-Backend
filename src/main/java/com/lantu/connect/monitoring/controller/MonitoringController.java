package com.lantu.connect.monitoring.controller;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.R;
import com.lantu.connect.common.security.RequirePermission;
import com.lantu.connect.common.security.RequireRole;
import com.lantu.connect.monitoring.dto.AlertRuleCreateRequest;
import com.lantu.connect.monitoring.dto.AlertRuleDryRunRequest;
import com.lantu.connect.monitoring.dto.AlertRuleDryRunResult;
import com.lantu.connect.monitoring.dto.AlertRuleUpdateRequest;
import com.lantu.connect.monitoring.dto.KpiMetric;
import com.lantu.connect.monitoring.dto.PageQuery;
import com.lantu.connect.monitoring.entity.AlertRecord;
import com.lantu.connect.monitoring.entity.AlertRule;
import com.lantu.connect.monitoring.entity.CallLog;
import com.lantu.connect.monitoring.entity.TraceSpan;
import com.lantu.connect.monitoring.service.AlertRuleService;
import com.lantu.connect.monitoring.service.MonitoringService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    @GetMapping("/kpis")
    @RequirePermission({"monitor:view"})
    public R<List<KpiMetric>> kpis() {
        return R.ok(monitoringService.kpis());
    }

    @GetMapping("/performance")
    @RequirePermission({"monitor:view"})
    public R<List<Map<String, Object>>> performance() {
        return R.ok(monitoringService.performance());
    }

    @GetMapping("/call-logs")
    @RequirePermission({"monitor:view"})
    public R<PageResult<CallLog>> callLogs(PageQuery query) {
        return R.ok(monitoringService.callLogs(query));
    }

    @GetMapping("/alerts")
    @RequirePermission({"monitor:view"})
    public R<PageResult<AlertRecord>> alerts(PageQuery query) {
        return R.ok(monitoringService.alerts(query));
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
            @RequestParam(required = false) String keyword) {
        String filter = StringUtils.hasText(keyword) ? keyword.trim() : name;
        return R.ok(alertRuleService.page(page, pageSize, filter));
    }

    /** 告警规则指标 id，与前端 METRIC_OPTIONS / handoff 02 对齐；扩展指标可改为读配置或 DB */
    @GetMapping("/alert-rule-metrics")
    @RequirePermission({"monitor:view"})
    public R<List<String>> alertRuleMetrics() {
        return R.ok(List.of("http_5xx_rate", "latency_p99", "error_rate"));
    }

    @PostMapping("/alert-rules/{id}/dry-run")
    @RequireRole({"platform_admin"})
    public R<AlertRuleDryRunResult> dryRunAlertRule(
            @PathVariable String id,
            @Valid @RequestBody AlertRuleDryRunRequest request) {
        return R.ok(alertRuleService.dryRun(id, request.getSampleValue()));
    }
}
