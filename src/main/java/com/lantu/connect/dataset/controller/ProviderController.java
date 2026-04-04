package com.lantu.connect.dataset.controller;

import com.lantu.connect.common.annotation.AuditLog;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.R;
import com.lantu.connect.common.security.RequireRole;
import com.lantu.connect.dataset.dto.ProviderCreateRequest;
import com.lantu.connect.dataset.dto.ProviderUpdateRequest;
import com.lantu.connect.dataset.entity.Provider;
import com.lantu.connect.dataset.service.ProviderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 服务提供商管理端（分页 + CRUD；keyword / status 与 handoff 01 对齐）.
 */
@RestController
@RequestMapping({"/providers", "/dataset/providers"})
@RequiredArgsConstructor
@RequireRole({"platform_admin", "admin"})
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

    @GetMapping("/{id}")
    public R<Provider> getById(@PathVariable long id) {
        return R.ok(providerService.getById(id));
    }

    @PostMapping
    @AuditLog(action = "provider_create", resource = "dataset-provider")
    public R<Provider> create(@Valid @RequestBody ProviderCreateRequest body) {
        return R.ok(providerService.create(body));
    }

    @PutMapping("/{id}")
    @AuditLog(action = "provider_update", resource = "dataset-provider")
    public R<Provider> update(@PathVariable long id, @Valid @RequestBody ProviderUpdateRequest body) {
        return R.ok(providerService.update(id, body));
    }

    @DeleteMapping("/{id}")
    @AuditLog(action = "provider_delete", resource = "dataset-provider")
    public R<Void> delete(@PathVariable long id) {
        providerService.delete(id);
        return R.ok();
    }
}
