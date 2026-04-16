package com.lantu.connect.monitoring.controller;

import com.lantu.connect.common.config.SecurityProperties;
import com.lantu.connect.common.result.R;
import com.lantu.connect.common.security.RequirePermission;
import com.lantu.connect.common.security.RequireRole;
import com.lantu.connect.monitoring.dto.CircuitBreakerManualRequest;
import com.lantu.connect.monitoring.dto.CircuitBreakerUpdateRequest;
import com.lantu.connect.monitoring.dto.HealthConfigUpsertRequest;
import com.lantu.connect.monitoring.dto.ResourceHealthPolicyUpdateRequest;
import com.lantu.connect.monitoring.dto.ResourceHealthSnapshotVO;
import com.lantu.connect.monitoring.entity.CircuitBreaker;
import com.lantu.connect.monitoring.entity.HealthConfig;
import com.lantu.connect.monitoring.service.HealthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
public class HealthController {

    private final HealthService healthService;
    private final SecurityProperties securityProperties;

    @GetMapping("/configs")
    @RequirePermission({"monitor:view"})
    public R<List<HealthConfig>> configs() {
        return R.ok(healthService.listConfigs());
    }

    @PostMapping("/configs")
    @RequireRole({"platform_admin"})
    public R<Long> saveConfig(@Valid @RequestBody HealthConfigUpsertRequest request) {
        return R.ok(healthService.saveConfig(request));
    }

    @PutMapping("/configs/{id}")
    @RequireRole({"platform_admin"})
    public R<Long> updateConfig(@PathVariable Long id, @Valid @RequestBody HealthConfigUpsertRequest request) {
        request.setId(id);
        return R.ok(healthService.saveConfig(request));
    }

    @DeleteMapping("/configs/{id}")
    @RequireRole({"platform_admin"})
    public R<Void> deleteConfig(@PathVariable Long id) {
        healthService.deleteConfig(id);
        return R.ok();
    }

    @GetMapping("/circuit-breakers")
    @RequirePermission({"monitor:view"})
    public R<List<CircuitBreaker>> circuitBreakers() {
        return R.ok(healthService.listCircuitBreakers());
    }

    @GetMapping("/resources")
    @RequirePermission({"monitor:view"})
    public R<List<ResourceHealthSnapshotVO>> resources(@RequestParam(required = false) String resourceType,
                                                       @RequestParam(required = false) String healthStatus,
                                                       @RequestParam(required = false) String callabilityState,
                                                       @RequestParam(required = false) String probeStrategy) {
        return R.ok(healthService.listResourceHealth(resourceType, healthStatus, callabilityState, probeStrategy));
    }

    @GetMapping("/resources/{resourceId}")
    @RequirePermission({"monitor:view"})
    public R<ResourceHealthSnapshotVO> resource(@PathVariable Long resourceId) {
        return R.ok(healthService.getResourceHealth(resourceId));
    }

    @PutMapping("/resources/{resourceId}/policy")
    @RequireRole({"platform_admin"})
    public R<ResourceHealthSnapshotVO> updateResourcePolicy(@PathVariable Long resourceId,
                                                            @Valid @RequestBody ResourceHealthPolicyUpdateRequest request) {
        return R.ok(healthService.updateResourcePolicy(resourceId, request));
    }

    @PostMapping("/resources/{resourceId}/probe")
    @RequireRole({"platform_admin"})
    public R<ResourceHealthSnapshotVO> probeResource(@PathVariable Long resourceId) {
        return R.ok(healthService.probeResourceHealth(resourceId));
    }

    @PostMapping("/resources/{resourceId}/break")
    @RequireRole({"platform_admin"})
    public R<ResourceHealthSnapshotVO> manualBreakResource(@PathVariable Long resourceId,
                                                           @RequestParam(required = false) Integer openDurationSeconds) {
        return R.ok(healthService.manualBreakResource(resourceId, openDurationSeconds));
    }

    @PostMapping("/resources/{resourceId}/recover")
    @RequireRole({"platform_admin"})
    public R<ResourceHealthSnapshotVO> manualRecoverResource(@PathVariable Long resourceId) {
        return R.ok(healthService.manualRecoverResource(resourceId));
    }

    @PutMapping("/circuit-breakers/{id}")
    @RequireRole({"platform_admin"})
    public R<Void> updateCircuitBreaker(@PathVariable Long id, @Valid @RequestBody CircuitBreakerUpdateRequest request) {
        healthService.updateCircuitBreaker(id, request);
        return R.ok();
    }

    @PostMapping("/circuit-breakers/{id}/break")
    @RequireRole({"platform_admin"})
    public R<Void> manualBreak(@PathVariable Long id, @RequestBody(required = false) CircuitBreakerManualRequest request) {
        healthService.manualBreakById(id, request != null ? request.getOpenDurationSeconds() : null);
        return R.ok();
    }

    @PostMapping("/circuit-breakers/{id}/recover")
    @RequireRole({"platform_admin"})
    public R<Void> recover(@PathVariable Long id) {
        healthService.recoverById(id);
        return R.ok();
    }

    @GetMapping("/security-config")
    @RequireRole({"platform_admin"})
    @SuppressWarnings("deprecation")
    public R<Map<String, Object>> securityConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("jwtEnabled", securityProperties.isJwtEnabled());
        config.put("allowHeaderUserIdFallback", securityProperties.isAllowHeaderUserIdFallback());
        config.put("permitPrometheusWithoutAuth", securityProperties.isPermitPrometheusWithoutAuth());
        config.put("productionReady", !securityProperties.isAllowHeaderUserIdFallback());
        return R.ok(config);
    }
}
