package com.lantu.connect.gateway.controller;

import com.lantu.connect.common.annotation.AuditLog;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.R;
import com.lantu.connect.gateway.dto.ResourceManageVO;
import com.lantu.connect.gateway.dto.ResourceUpsertRequest;
import com.lantu.connect.gateway.dto.ResourceVersionCreateRequest;
import com.lantu.connect.gateway.dto.ResourceVersionVO;
import com.lantu.connect.gateway.service.ResourceRegistryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 统一资源注册中心：注册、更新、提审、下线、版本管理。
 */
@RestController
@RequestMapping("/resource-center/resources")
@RequiredArgsConstructor
public class ResourceRegistryController {

    private final ResourceRegistryService resourceRegistryService;

    @PostMapping
    @AuditLog(action = "resource_create", resource = "resource-center")
    public R<ResourceManageVO> create(@RequestHeader("X-User-Id") Long userId,
                                      @Valid @RequestBody ResourceUpsertRequest request) {
        return R.ok(resourceRegistryService.create(userId, request));
    }

    @PutMapping("/{id}")
    @AuditLog(action = "resource_update", resource = "resource-center")
    public R<ResourceManageVO> update(@RequestHeader("X-User-Id") Long userId,
                                      @PathVariable Long id,
                                      @Valid @RequestBody ResourceUpsertRequest request) {
        return R.ok(resourceRegistryService.update(userId, id, request));
    }

    @DeleteMapping("/{id}")
    @AuditLog(action = "resource_delete", resource = "resource-center")
    public R<Void> delete(@RequestHeader("X-User-Id") Long userId, @PathVariable Long id) {
        resourceRegistryService.delete(userId, id);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    @AuditLog(action = "resource_submit", resource = "resource-center")
    public R<Void> submit(@RequestHeader("X-User-Id") Long userId, @PathVariable Long id) {
        resourceRegistryService.submitForAudit(userId, id);
        return R.ok();
    }

    @PostMapping("/{id}/deprecate")
    @AuditLog(action = "resource_deprecate", resource = "resource-center")
    public R<Void> deprecate(@RequestHeader("X-User-Id") Long userId, @PathVariable Long id) {
        resourceRegistryService.deprecate(userId, id);
        return R.ok();
    }

    @GetMapping("/mine")
    public R<PageResult<ResourceManageVO>> mine(@RequestHeader("X-User-Id") Long userId,
                                                @RequestParam(required = false) String resourceType,
                                                @RequestParam(defaultValue = "1") Integer page,
                                                @RequestParam(defaultValue = "20") Integer pageSize) {
        return R.ok(resourceRegistryService.pageMine(userId, resourceType, page, pageSize));
    }

    @GetMapping("/{id}")
    public R<ResourceManageVO> getById(@RequestHeader("X-User-Id") Long userId,
                                       @PathVariable Long id) {
        return R.ok(resourceRegistryService.getById(userId, id));
    }

    @PostMapping("/{id}/versions")
    @AuditLog(action = "resource_version_create", resource = "resource-center")
    public R<ResourceVersionVO> createVersion(@RequestHeader("X-User-Id") Long userId,
                                              @PathVariable Long id,
                                              @Valid @RequestBody ResourceVersionCreateRequest request) {
        return R.ok(resourceRegistryService.createVersion(userId, id, request));
    }

    @PostMapping("/{id}/versions/{version}/switch")
    @AuditLog(action = "resource_version_switch", resource = "resource-center")
    public R<Void> switchVersion(@RequestHeader("X-User-Id") Long userId,
                                 @PathVariable Long id,
                                 @PathVariable String version) {
        resourceRegistryService.switchVersion(userId, id, version);
        return R.ok();
    }

    @GetMapping("/{id}/versions")
    public R<List<ResourceVersionVO>> versions(@RequestHeader("X-User-Id") Long userId,
                                               @PathVariable Long id) {
        return R.ok(resourceRegistryService.listVersions(userId, id));
    }

    @PostMapping("/{id}/withdraw")
    @AuditLog(action = "resource_withdraw", resource = "resource-center")
    public R<Void> withdraw(@RequestHeader("X-User-Id") Long userId, @PathVariable Long id) {
        resourceRegistryService.withdraw(userId, id);
        return R.ok();
    }
}

