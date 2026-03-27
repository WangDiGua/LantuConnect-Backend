package com.lantu.connect.gateway.controller;

import com.lantu.connect.common.annotation.AuditLog;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.R;
import com.lantu.connect.common.security.RequireRole;
import com.lantu.connect.gateway.dto.GrantApplicationRequest;
import com.lantu.connect.gateway.dto.GrantApplicationVO;
import com.lantu.connect.gateway.dto.ResourceRejectRequest;
import com.lantu.connect.gateway.service.GrantApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 资源授权申请工单：开发者申请 → 平台管理员审批 → 自动授权。
 */
@RestController
@RequestMapping("/grant-applications")
@RequiredArgsConstructor
public class GrantApplicationController {

    private final GrantApplicationService grantApplicationService;

    @PostMapping
    @AuditLog(action = "grant_application_apply", resource = "grant-applications")
    public R<Map<String, Long>> apply(@RequestHeader("X-User-Id") Long userId,
                                      @Valid @RequestBody GrantApplicationRequest request) {
        Long appId = grantApplicationService.apply(userId, request);
        return R.ok(Map.of("applicationId", appId));
    }

    @GetMapping("/mine")
    public R<PageResult<GrantApplicationVO>> myApplications(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(grantApplicationService.pageMyApplications(userId, status, page, pageSize));
    }

    @GetMapping("/pending")
    @RequireRole({"platform_admin"})
    public R<PageResult<GrantApplicationVO>> pendingApplications(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(grantApplicationService.pagePendingApplications(status, page, pageSize));
    }

    @PostMapping("/{id}/approve")
    @RequireRole({"platform_admin"})
    @AuditLog(action = "grant_application_approve", resource = "grant-applications")
    public R<Void> approve(@RequestHeader("X-User-Id") Long userId, @PathVariable Long id) {
        grantApplicationService.approve(userId, id);
        return R.ok();
    }

    @PostMapping("/{id}/reject")
    @RequireRole({"platform_admin"})
    @AuditLog(action = "grant_application_reject", resource = "grant-applications")
    public R<Void> reject(@RequestHeader("X-User-Id") Long userId,
                          @PathVariable Long id,
                          @RequestBody ResourceRejectRequest body) {
        grantApplicationService.reject(userId, id, body != null ? body.getReason() : null);
        return R.ok();
    }
}
