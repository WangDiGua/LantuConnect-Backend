package com.lantu.connect.audit.controller;

import com.lantu.connect.audit.entity.AuditItem;
import com.lantu.connect.audit.service.AuditService;
import com.lantu.connect.common.annotation.AuditLog;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.R;
import com.lantu.connect.common.security.RequireRole;
import com.lantu.connect.gateway.dto.ResourceRejectRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 审核 Audit 控制器：
 * reviewer / platform_admin 负责 approve、reject（全平台队列，不按部门隔离）；
 * publish（testing→published）由资源 owner、reviewer、platform_admin/admin 执行；
 * 平台对任意已上架资源的强制下架见 {@code /resources/{id}/platform-force-deprecate}。
 */
@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping("/resources")
    @RequireRole({"platform_admin", "reviewer"})
    public R<PageResult<AuditItem>> pendingResources(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(auditService.pagePendingResources(userId, resourceType, status, keyword, page, pageSize));
    }

    @GetMapping("/agents")
    @RequireRole({"platform_admin", "reviewer"})
    public R<PageResult<AuditItem>> pendingAgents(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(auditService.pagePendingAgents(userId, page, pageSize));
    }

    @GetMapping("/skills")
    @RequireRole({"platform_admin", "reviewer"})
    public R<PageResult<AuditItem>> pendingSkills(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(auditService.pagePendingSkills(userId, page, pageSize));
    }

    @PostMapping("/agents/{id}/approve")
    @RequireRole({"platform_admin", "reviewer"})
    @AuditLog(action = "audit_approve_agent", resource = "audit")
    public R<Void> approveAgent(@RequestHeader("X-User-Id") Long userId, @PathVariable Long id) {
        auditService.approveAgent(id, userId);
        return R.ok();
    }

    @PostMapping("/skills/{id}/approve")
    @RequireRole({"platform_admin", "reviewer"})
    @AuditLog(action = "audit_approve_skill", resource = "audit")
    public R<Void> approveSkill(@RequestHeader("X-User-Id") Long userId, @PathVariable Long id) {
        auditService.approveSkill(id, userId);
        return R.ok();
    }

    @PostMapping("/agents/{id}/reject")
    @RequireRole({"platform_admin", "reviewer"})
    @AuditLog(action = "audit_reject_agent", resource = "audit")
    public R<Void> rejectAgent(@RequestHeader("X-User-Id") Long userId,
                               @PathVariable Long id, @RequestBody RejectBody body) {
        auditService.rejectAgent(id, body != null ? body.getReason() : null, userId);
        return R.ok();
    }

    @PostMapping("/skills/{id}/reject")
    @RequireRole({"platform_admin", "reviewer"})
    @AuditLog(action = "audit_reject_skill", resource = "audit")
    public R<Void> rejectSkill(@RequestHeader("X-User-Id") Long userId,
                               @PathVariable Long id, @RequestBody RejectBody body) {
        auditService.rejectSkill(id, body != null ? body.getReason() : null, userId);
        return R.ok();
    }

    @PostMapping("/agents/{id}/publish")
    @RequireRole({"platform_admin", "admin", "reviewer", "developer"})
    @AuditLog(action = "audit_publish_agent", resource = "audit")
    public R<Void> publishAgent(@RequestHeader("X-User-Id") Long userId, @PathVariable Long id) {
        auditService.publishAgent(id, userId);
        return R.ok();
    }

    @PostMapping("/skills/{id}/publish")
    @RequireRole({"platform_admin", "admin", "reviewer", "developer"})
    @AuditLog(action = "audit_publish_skill", resource = "audit")
    public R<Void> publishSkill(@RequestHeader("X-User-Id") Long userId, @PathVariable Long id) {
        auditService.publishSkill(id, userId);
        return R.ok();
    }

    @PostMapping("/resources/{id}/approve")
    @RequireRole({"platform_admin", "reviewer"})
    @AuditLog(action = "audit_approve_resource", resource = "audit")
    public R<Void> approveResource(@RequestHeader("X-User-Id") Long userId, @PathVariable Long id) {
        auditService.approveResource(id, userId);
        return R.ok();
    }

    @PostMapping("/resources/{id}/reject")
    @RequireRole({"platform_admin", "reviewer"})
    @AuditLog(action = "audit_reject_resource", resource = "audit")
    public R<Void> rejectResource(@RequestHeader("X-User-Id") Long userId,
                                  @PathVariable Long id, @RequestBody ResourceRejectRequest body) {
        auditService.rejectResource(id, body != null ? body.getReason() : null, userId);
        return R.ok();
    }

    @PostMapping("/resources/{id}/publish")
    @RequireRole({"platform_admin", "admin", "reviewer", "developer"})
    @AuditLog(action = "audit_publish_resource", resource = "audit")
    public R<Void> publishResource(@RequestHeader("X-User-Id") Long userId, @PathVariable Long id) {
        auditService.publishResource(id, userId);
        return R.ok();
    }

    /**
     * 平台强制下架（跨部门）：仅 platform_admin；开发者自助下线请使用 {@code POST /resource-center/resources/{id}/deprecate}。
     */
    @PostMapping("/resources/{id}/platform-force-deprecate")
    @RequireRole({"platform_admin"})
    @AuditLog(action = "platform_force_deprecate", resource = "audit")
    public R<Void> platformForceDeprecateResource(@RequestHeader("X-User-Id") Long userId,
                                                  @PathVariable Long id,
                                                  @RequestBody(required = false) ResourceRejectRequest body) {
        auditService.platformForceDeprecateResource(id, userId, body != null ? body.getReason() : null);
        return R.ok();
    }

    @Data
    public static class RejectBody {
        private String reason;
    }
}
