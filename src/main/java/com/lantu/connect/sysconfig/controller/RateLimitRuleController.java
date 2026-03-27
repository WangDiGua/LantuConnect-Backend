package com.lantu.connect.sysconfig.controller;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.R;
import com.lantu.connect.common.security.RequireRole;
import com.lantu.connect.sysconfig.dto.RateLimitRuleCreateRequest;
import com.lantu.connect.sysconfig.dto.RateLimitRuleUpdateRequest;
import com.lantu.connect.sysconfig.entity.RateLimitRule;
import com.lantu.connect.sysconfig.service.RateLimitRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 系统配置 RateLimitRule 控制器
 *
 * @author 王帝
 * @date 2026-03-21
 */
@RestController
@RequestMapping("/system-config/rate-limits")
@RequiredArgsConstructor
@RequireRole({"platform_admin"})
public class RateLimitRuleController {

    private final RateLimitRuleService rateLimitRuleService;

    @PostMapping
    public R<String> create(@Valid @RequestBody RateLimitRuleCreateRequest request) {
        return R.ok(rateLimitRuleService.create(request));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable String id, @Valid @RequestBody RateLimitRuleUpdateRequest request) {
        request.setId(id);
        rateLimitRuleService.update(request);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        rateLimitRuleService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<RateLimitRule> get(@PathVariable String id) {
        return R.ok(rateLimitRuleService.getById(id));
    }

    @GetMapping
    public R<PageResult<RateLimitRule>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String name) {
        return R.ok(rateLimitRuleService.page(page, pageSize, name));
    }
}
