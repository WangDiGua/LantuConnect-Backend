package com.lantu.connect.sysconfig.controller;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.R;
import com.lantu.connect.common.annotation.AuditLog;
import com.lantu.connect.common.security.RequireRole;
import com.lantu.connect.sysconfig.dto.AuditLogQueryRequest;
import com.lantu.connect.sysconfig.dto.SecuritySettingUpsertRequest;
import com.lantu.connect.sysconfig.dto.SystemParamUpsertRequest;
import com.lantu.connect.sysconfig.entity.SecuritySetting;
import com.lantu.connect.sysconfig.entity.SystemParam;
import com.lantu.connect.sysconfig.service.SystemParamFacadeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 系统配置 SystemParam 控制器
 *
 * @author 王帝
 * @date 2026-03-21
 */
@RestController
@RequestMapping("/system-config")
@RequiredArgsConstructor
@RequireRole({"platform_admin"})
public class SystemParamController {

    private final SystemParamFacadeService systemParamFacadeService;

    @GetMapping("/params")
    public R<List<SystemParam>> listParams() {
        return R.ok(systemParamFacadeService.listParams());
    }

    @PutMapping("/params")
    @AuditLog(action = "put_system_param", resource = "system-config")
    public R<Void> putParam(@RequestHeader("X-User-Id") Long operatorUserId,
                            @Valid @RequestBody SystemParamUpsertRequest request) {
        systemParamFacadeService.putParam(operatorUserId, request);
        return R.ok();
    }

    @GetMapping("/security")
    public R<List<SecuritySetting>> listSecurity() {
        return R.ok(systemParamFacadeService.listSecurity());
    }

    @PutMapping("/security")
    @AuditLog(action = "put_security_setting", resource = "system-config")
    public R<Void> putSecurity(@RequestHeader("X-User-Id") Long operatorUserId,
                               @Valid @RequestBody SecuritySettingUpsertRequest request) {
        systemParamFacadeService.putSecurity(operatorUserId, request);
        return R.ok();
    }

    @GetMapping("/audit-logs")
    public R<PageResult<com.lantu.connect.sysconfig.entity.AuditLog>> auditLogs(AuditLogQueryRequest request) {
        return R.ok(systemParamFacadeService.pageAuditLogs(request));
    }

    @GetMapping("/acl")
    public R<Map<String, Object>> aclRules() {
        return R.ok(systemParamFacadeService.getAclRules());
    }

    @GetMapping("/audit-actions")
    public R<List<String>> auditActions() {
        return R.ok(systemParamFacadeService.listDistinctAuditActions());
    }

    @PostMapping("/network/apply")
    @AuditLog(action = "apply_network", resource = "system-config")
    public R<Map<String, Object>> applyNetwork(@RequestHeader("X-User-Id") Long operatorUserId) {
        return R.ok(systemParamFacadeService.applyNetwork(operatorUserId));
    }

    @PostMapping("/acl/publish")
    @AuditLog(action = "publish_acl", resource = "system-config")
    public R<Map<String, Object>> publishAcl(@RequestHeader("X-User-Id") Long operatorUserId) {
        return R.ok(systemParamFacadeService.publishAcl(operatorUserId));
    }
}
