package com.lantu.connect.gateway.controller;

import com.lantu.connect.common.dto.LongIdsRequest;
import com.lantu.connect.common.result.R;
import com.lantu.connect.common.annotation.AuditLog;
import com.lantu.connect.gateway.dto.ResourceGrantCreateRequest;
import com.lantu.connect.gateway.dto.ResourceGrantVO;
import com.lantu.connect.gateway.security.ResourceInvokeGrantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 资源调用授权管理接口（授予/撤销/查询）。
 */
@RestController
@RequestMapping("/resource-grants")
@RequiredArgsConstructor
public class ResourceGrantController {

    private final ResourceInvokeGrantService resourceInvokeGrantService;

    @PostMapping
    @AuditLog(action = "resource_grant_upsert", resource = "resource-grants")
    public R<Map<String, Long>> grant(@RequestHeader("X-User-Id") Long userId,
                                      @Valid @RequestBody ResourceGrantCreateRequest request) {
        Long grantId = resourceInvokeGrantService.grant(userId, request);
        return R.ok(Map.of("grantId", grantId));
    }

    @DeleteMapping("/{grantId}")
    @AuditLog(action = "resource_grant_revoke", resource = "resource-grants")
    public R<Void> revoke(@RequestHeader("X-User-Id") Long userId,
                          @PathVariable Long grantId) {
        resourceInvokeGrantService.revoke(userId, grantId);
        return R.ok();
    }

    @PostMapping("/batch-delete")
    @AuditLog(action = "resource_grant_batch_revoke", resource = "resource-grants")
    public R<Void> batchRevoke(@RequestHeader("X-User-Id") Long userId, @Valid @RequestBody LongIdsRequest body) {
        for (Long grantId : body.getIds()) {
            resourceInvokeGrantService.revoke(userId, grantId);
        }
        return R.ok();
    }

    @GetMapping
    public R<List<ResourceGrantVO>> list(@RequestHeader("X-User-Id") Long userId,
                                         @RequestParam String resourceType,
                                         @RequestParam Long resourceId,
                                         @RequestParam(required = false) String keyword) {
        return R.ok(resourceInvokeGrantService.listByResource(userId, resourceType, resourceId, keyword));
    }
}
