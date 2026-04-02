package com.lantu.connect.gateway.controller;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.R;
import com.lantu.connect.common.security.RequirePermission;
import com.lantu.connect.gateway.dto.SkillExternalCatalogItemVO;
import com.lantu.connect.gateway.service.SkillExternalCatalogService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统一资源中心 · 技能 · 在线市场：SkillHub 公开搜索 + SkillsMP + 镜像 JSON + 静态配置（平台管理员）。
 */
@RestController
@RequestMapping("/resource-center/skill-external-catalog")
@RequiredArgsConstructor
public class SkillExternalCatalogController {

    private final SkillExternalCatalogService skillExternalCatalogService;

    /**
     * 分页列表；keyword 在名称、简介、ZIP、来源、许可说明中不区分大小写包含匹配。
     */
    @GetMapping
    @RequirePermission("system:config")
    public R<PageResult<SkillExternalCatalogItemVO>> list(
            HttpServletResponse response,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int pageSize) {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        return R.ok(skillExternalCatalogService.listCatalogPage(keyword, page, pageSize));
    }
}
