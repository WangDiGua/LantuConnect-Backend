package com.lantu.connect.dataset.controller;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.R;
import com.lantu.connect.common.security.RequireRole;
import com.lantu.connect.dataset.entity.Provider;
import com.lantu.connect.dataset.service.ProviderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 服务提供商管理端列表（keyword / status 与 handoff 01 对齐）.
 */
@RestController
@RequestMapping({"/providers", "/dataset/providers"})
@RequiredArgsConstructor
@RequireRole({"platform_admin", "dept_admin"})
public class ProviderController {

    private final ProviderService providerService;

    @GetMapping
    public R<PageResult<Provider>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return R.ok(providerService.page(page, pageSize, keyword, status));
    }
}
