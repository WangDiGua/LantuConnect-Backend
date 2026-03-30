package com.lantu.connect.sysconfig.controller;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.R;
import com.lantu.connect.common.security.RequireRole;
import com.lantu.connect.sysconfig.dto.QuotaCreateRequest;
import com.lantu.connect.sysconfig.dto.QuotaUpdateRequest;
import com.lantu.connect.sysconfig.entity.Quota;
import com.lantu.connect.sysconfig.service.QuotaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 系统配置 Quota 控制器
 *
 * @author 王帝
 * @date 2026-03-21
 */
@RestController
@RequestMapping("/quotas")
@RequiredArgsConstructor
@RequireRole({"platform_admin"})
public class QuotaController {

    private final QuotaService quotaService;

    @PostMapping
    public R<Long> create(@Valid @RequestBody QuotaCreateRequest request) {
        return R.ok(quotaService.create(request));
    }

    @PutMapping
    public R<Void> update(@Valid @RequestBody QuotaUpdateRequest request) {
        quotaService.update(request);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        quotaService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<Quota> get(@PathVariable Long id) {
        return R.ok(quotaService.getById(id));
    }

    @GetMapping
    public R<PageResult<Quota>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) String keyword) {
        return R.ok(quotaService.page(page, pageSize, subjectType, keyword));
    }
}
