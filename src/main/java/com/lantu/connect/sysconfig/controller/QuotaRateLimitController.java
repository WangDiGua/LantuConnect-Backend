package com.lantu.connect.sysconfig.controller;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.R;
import com.lantu.connect.common.security.RequireRole;
import com.lantu.connect.sysconfig.dto.QuotaRateLimitCreateRequest;
import com.lantu.connect.sysconfig.dto.QuotaRateLimitToggleRequest;
import com.lantu.connect.sysconfig.entity.QuotaRateLimit;
import com.lantu.connect.sysconfig.service.QuotaRateLimitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 系统配置 QuotaRateLimit 控制器
 *
 * @author 王帝
 * @date 2026-03-21
 */
@RestController
@RequestMapping("/rate-limits")
@RequiredArgsConstructor
@RequireRole({"platform_admin"})
public class QuotaRateLimitController {

    private final QuotaRateLimitService quotaRateLimitService;

    @PostMapping
    public R<Long> create(@Valid @RequestBody QuotaRateLimitCreateRequest request) {
        return R.ok(quotaRateLimitService.create(request));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        quotaRateLimitService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<QuotaRateLimit> get(@PathVariable Long id) {
        return R.ok(quotaRateLimitService.getById(id));
    }

    @GetMapping
    public R<PageResult<QuotaRateLimit>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long quotaId,
            @RequestParam(required = false) String keyword) {
        return R.ok(quotaRateLimitService.page(page, pageSize, quotaId, keyword));
    }

    @PatchMapping("/{id}")
    public R<Void> toggle(@PathVariable Long id, @Valid @RequestBody QuotaRateLimitToggleRequest request) {
        quotaRateLimitService.toggle(id, request.getEnabled());
        return R.ok();
    }
}
