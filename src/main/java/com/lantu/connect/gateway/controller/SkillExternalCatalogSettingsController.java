package com.lantu.connect.gateway.controller;

import com.lantu.connect.common.annotation.AuditLog;
import com.lantu.connect.common.result.R;
import com.lantu.connect.common.security.RequirePermission;
import com.lantu.connect.gateway.config.SkillExternalCatalogProperties;
import com.lantu.connect.gateway.dto.SkillExternalCatalogSettingsResponse;
import com.lantu.connect.gateway.service.SkillExternalCatalogRuntimeConfigService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 技能在线市场配置（超管）：读写 {@code t_system_param.skill_external_catalog}，运行时覆盖 YAML 默认。
 */
@RestController
@RequestMapping("/resource-center/skill-external-catalog")
@RequiredArgsConstructor
public class SkillExternalCatalogSettingsController {

    private final SkillExternalCatalogRuntimeConfigService runtimeConfigService;

    @GetMapping("/settings")
    @RequirePermission("system:config")
    public R<SkillExternalCatalogSettingsResponse> getSettings(HttpServletResponse response) {
        // 避免管理员保存后浏览器/反向代理仍返回旧 JSON，导致前端表单未刷新
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        return R.ok(runtimeConfigService.getForAdmin());
    }

    @PutMapping("/settings")
    @RequirePermission("system:config")
    @AuditLog(action = "skill_external_catalog_settings_put", resource = "resource-center")
    public R<Void> putSettings(@RequestHeader("X-User-Id") Long operatorUserId,
                               @Valid @RequestBody SkillExternalCatalogProperties body) {
        runtimeConfigService.saveFromAdmin(operatorUserId, body);
        return R.ok();
    }
}
