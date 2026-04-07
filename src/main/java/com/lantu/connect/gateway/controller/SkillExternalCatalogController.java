package com.lantu.connect.gateway.controller;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.R;
import com.lantu.connect.common.security.RequirePermission;
import com.lantu.connect.gateway.dto.SkillExternalCatalogItemVO;
import com.lantu.connect.gateway.dto.SkillExternalCatalogSyncStatusResponse;
import com.lantu.connect.gateway.dto.SkillExternalSkillMdResponse;
import com.lantu.connect.gateway.service.SkillExternalCatalogService;
import com.lantu.connect.gateway.service.SkillExternalSkillMdService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统一资源中心 · 技能 · 在线市场列表：聚合远程源；配置仍见 {@link SkillExternalCatalogSettingsController}（超管）。
 */
@RestController
@RequestMapping("/resource-center/skill-external-catalog")
@RequiredArgsConstructor
public class SkillExternalCatalogController {

    private final SkillExternalCatalogService skillExternalCatalogService;
    private final SkillExternalSkillMdService skillExternalSkillMdService;

    /**
     * 分页列表；须具备 skill:read（与目录读口径一致）。keyword 在名称、简介、ZIP、来源、许可说明中不区分大小写包含匹配。
     * <p>
     * 可选筛选：{@code minStars}、{@code maxStars}（含端点，按 {@code IFNULL(stars,0)}）；
     * {@code source}：{@code skillhub} | {@code skillsmp} | {@code mirror}（镜像/静态 YAML 等许可字段不含前两者关键字时归入 mirror）。
     */
    @GetMapping
    @RequirePermission("skill:read")
    public R<PageResult<SkillExternalCatalogItemVO>> list(
            HttpServletResponse response,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int pageSize,
            @RequestParam(required = false) Integer minStars,
            @RequestParam(required = false) Integer maxStars,
            @RequestParam(required = false) String source,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        return R.ok(skillExternalCatalogService.listCatalogPage(
                keyword, page, pageSize, userId, minStars, maxStars, source));
    }

    /**
     * 单条详情（与列表字段一致 + 聚合统计）；{@code key} 为与 {@code itemKey}/{@code dedupe_key} 一致的归一化值（建议 URL 编码传递）。
     */
    @GetMapping("/item")
    @RequirePermission("skill:read")
    public R<SkillExternalCatalogItemVO> getItem(
            HttpServletResponse response,
            @RequestParam("key") String key,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        return R.ok(skillExternalCatalogService.getCatalogItemWithStats(key, userId));
    }

    /**
     * 代拉 GitHub raw 的 SKILL.md（受出站代理与超时约束）；非 GitHub 或无文件时 {@code markdown} 为空，详见 {@code hint}。
     */
    @GetMapping("/item/skill-md")
    @RequirePermission("skill:read")
    public R<SkillExternalSkillMdResponse> getItemSkillMd(
            HttpServletResponse response,
            @RequestParam("key") String key) {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        return R.ok(skillExternalSkillMdService.fetchForItemKey(key));
    }

    /**
     * 库镜像同步状态（只读）；便于前端展示「同步中 / 待补跑」等提示。
     */
    @GetMapping("/sync-status")
    @RequirePermission("skill:read")
    public R<SkillExternalCatalogSyncStatusResponse> syncStatus(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        return R.ok(skillExternalCatalogService.getExternalCatalogSyncStatusHint());
    }
}
