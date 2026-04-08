package com.lantu.connect.gateway.controller;

import com.lantu.connect.common.annotation.AuditLog;
import com.lantu.connect.common.dto.IdsWithReasonRequest;
import com.lantu.connect.common.dto.LongIdsRequest;
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
 * 资源授权申请工单：申请人提交 → **资源 owner 优先审批**；reviewer、platform_admin/admin 可代管/全量（权限见 {@link com.lantu.connect.gateway.service.impl.GrantApplicationServiceImpl}）。
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

    /**
     * 撤销该已通过申请所建立的生效资源授权（权限与审批相同：资源 owner / reviewer / 平台管理员）。
     */
    @PostMapping("/{id}/revoke-grant")
    @AuditLog(action = "grant_application_revoke_grant", resource = "grant-applications")
    public R<Void> revokeEffectiveGrant(@RequestHeader("X-User-Id") Long userId, @PathVariable Long id) {
        grantApplicationService.revokeEffectiveGrant(userId, id);
        return R.ok();
    }

    @PostMapping("/batch-approve")
    @AuditLog(action = "grant_application_batch_approve", resource = "grant-applications")
    public R<Void> batchApprove(@RequestHeader("X-User-Id") Long userId, @Valid @RequestBody LongIdsRequest body) {
        grantApplicationService.batchApprove(userId, body.getIds());
        return R.ok();
    }

    @PostMapping("/batch-reject")
    @AuditLog(action = "grant_application_batch_reject", resource = "grant-applications")
    public R<Void> batchReject(@RequestHeader("X-User-Id") Long userId, @Valid @RequestBody IdsWithReasonRequest body) {
        grantApplicationService.batchReject(userId, body.getIds(), body.getReason());
        return R.ok();
    }

    @PostMapping("/batch-revoke-grant")
    @AuditLog(action = "grant_application_batch_revoke_grant", resource = "grant-applications")
    public R<Void> batchRevokeEffectiveGrant(@RequestHeader("X-User-Id") Long userId, @Valid @RequestBody LongIdsRequest body) {
        grantApplicationService.batchRevokeEffectiveGrant(userId, body.getIds());
        return R.ok();
    }
}
