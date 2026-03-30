package com.lantu.connect.audit.controller;

import com.lantu.connect.audit.entity.AuditItem;
import com.lantu.connect.audit.service.AuditService;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.R;
import com.lantu.connect.common.security.RequireRole;
import com.lantu.connect.gateway.dto.ResourceRejectRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 审核 Audit 控制器 — 两级审核模型：
 * dept_admin 负责 approve / reject（部门审核），
 * platform_admin 负责 publish（平台上线）。
 */
@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping("/resources")
    @RequireRole({"platform_admin", "dept_admin"})
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
    @RequireRole({"platform_admin", "dept_admin"})
    public R<PageResult<AuditItem>> pendingAgents(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(auditService.pagePendingAgents(userId, page, pageSize));
    }

    @GetMapping("/skills")
    @RequireRole({"platform_admin", "dept_admin"})
    public R<PageResult<AuditItem>> pendingSkills(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(auditService.pagePendingSkills(userId, page, pageSize));
    }

    @PostMapping("/agents/{id}/approve")
    @RequireRole({"platform_admin", "dept_admin"})
    public R<Void> approveAgent(@RequestHeader("X-User-Id") Long userId, @PathVariable Long id) {
        auditService.approveAgent(id, userId);
        return R.ok();
    }

    @PostMapping("/skills/{id}/approve")
    @RequireRole({"platform_admin", "dept_admin"})
    public R<Void> approveSkill(@RequestHeader("X-User-Id") Long userId, @PathVariable Long id) {
        auditService.approveSkill(id, userId);
        return R.ok();
    }

    @PostMapping("/agents/{id}/reject")
    @RequireRole({"platform_admin", "dept_admin"})
    public R<Void> rejectAgent(@RequestHeader("X-User-Id") Long userId,
                               @PathVariable Long id, @RequestBody RejectBody body) {
        auditService.rejectAgent(id, body != null ? body.getReason() : null, userId);
        return R.ok();
    }

    @PostMapping("/skills/{id}/reject")
    @RequireRole({"platform_admin", "dept_admin"})
    public R<Void> rejectSkill(@RequestHeader("X-User-Id") Long userId,
                               @PathVariable Long id, @RequestBody RejectBody body) {
        auditService.rejectSkill(id, body != null ? body.getReason() : null, userId);
        return R.ok();
    }

    @PostMapping("/agents/{id}/publish")
    @RequireRole({"platform_admin"})
    public R<Void> publishAgent(@RequestHeader("X-User-Id") Long userId, @PathVariable Long id) {
        auditService.publishAgent(id, userId);
        return R.ok();
    }

    @PostMapping("/skills/{id}/publish")
    @RequireRole({"platform_admin"})
    public R<Void> publishSkill(@RequestHeader("X-User-Id") Long userId, @PathVariable Long id) {
        auditService.publishSkill(id, userId);
        return R.ok();
    }

    @PostMapping("/resources/{id}/approve")
    @RequireRole({"platform_admin", "dept_admin"})
    public R<Void> approveResource(@RequestHeader("X-User-Id") Long userId, @PathVariable Long id) {
        auditService.approveResource(id, userId);
        return R.ok();
    }

    @PostMapping("/resources/{id}/reject")
    @RequireRole({"platform_admin", "dept_admin"})
    public R<Void> rejectResource(@RequestHeader("X-User-Id") Long userId,
                                  @PathVariable Long id, @RequestBody ResourceRejectRequest body) {
        auditService.rejectResource(id, body != null ? body.getReason() : null, userId);
        return R.ok();
    }

    @PostMapping("/resources/{id}/publish")
    @RequireRole({"platform_admin"})
    public R<Void> publishResource(@RequestHeader("X-User-Id") Long userId, @PathVariable Long id) {
        auditService.publishResource(id, userId);
        return R.ok();
    }

    @Data
    public static class RejectBody {
        private String reason;
    }
}
