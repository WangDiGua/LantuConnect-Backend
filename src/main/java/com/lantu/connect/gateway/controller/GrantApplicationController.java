package com.lantu.connect.gateway.controller;

import com.lantu.connect.common.annotation.AuditLog;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.R;
import com.lantu.connect.gateway.dto.GrantApplicationRequest;
import com.lantu.connect.gateway.dto.GrantApplicationVO;
import com.lantu.connect.gateway.dto.ResourceRejectRequest;
import com.lantu.connect.gateway.service.GrantApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 资源授权申请工单：申请人提交 → **资源 owner 优先审批**；本部 dept_admin、platform_admin 可代管/全量（权限见 {@link com.lantu.connect.gateway.service.impl.GrantApplicationServiceImpl}）。
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
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        String kw = StringUtils.hasText(keyword) ? keyword : q;
        return R.ok(grantApplicationService.pageMyApplications(userId, status, kw, page, pageSize));
    }

    @GetMapping("/pending")
    public R<PageResult<GrantApplicationVO>> pendingApplications(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        String kw = StringUtils.hasText(keyword) ? keyword : q;
        return R.ok(grantApplicationService.pagePendingApplications(userId, status, kw, page, pageSize));
    }

    @PostMapping("/{id}/approve")
    @AuditLog(action = "grant_application_approve", resource = "grant-applications")
    public R<Void> approve(@RequestHeader("X-User-Id") Long userId, @PathVariable Long id) {
        grantApplicationService.approve(userId, id);
        return R.ok();
    }

    @PostMapping("/{id}/reject")
    @AuditLog(action = "grant_application_reject", resource = "grant-applications")
    public R<Void> reject(@RequestHeader("X-User-Id") Long userId,
                          @PathVariable Long id,
                          @RequestBody ResourceRejectRequest body) {
        grantApplicationService.reject(userId, id, body != null ? body.getReason() : null);
        return R.ok();
    }
}
