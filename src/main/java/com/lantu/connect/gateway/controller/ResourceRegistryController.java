package com.lantu.connect.gateway.controller;

import com.lantu.connect.common.annotation.AuditLog;
import com.lantu.connect.common.dto.LongIdsRequest;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.R;
import com.lantu.connect.gateway.dto.ResourceManageVO;
import com.lantu.connect.gateway.dto.LifecycleTimelineVO;
import com.lantu.connect.gateway.dto.ObservabilitySummaryVO;
import com.lantu.connect.gateway.dto.ResourceUpsertRequest;
import com.lantu.connect.gateway.dto.ResourceVersionCreateRequest;
import com.lantu.connect.gateway.dto.McpConnectivityProbeRequest;
import com.lantu.connect.gateway.dto.McpConnectivityProbeResult;
import com.lantu.connect.gateway.dto.ResourceVersionVO;
import com.lantu.connect.gateway.dto.AgentKeyMetaVO;
import com.lantu.connect.gateway.service.McpConnectivityProbeService;
import com.lantu.connect.gateway.service.ResourceRegistryService;
import com.lantu.connect.gateway.security.AgentApiKeyService;
import com.lantu.connect.usermgmt.dto.ApiKeyResponse;
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
    private final McpConnectivityProbeService mcpConnectivityProbeService;
    private final AgentApiKeyService agentApiKeyService;

    /**
     * 登记前探测用户自管 MCP 是否可达（JSON-RPC initialize，短超时；不落库、不托管服务）。
     */
    @PostMapping("/mcp/connectivity-probe")
    public R<McpConnectivityProbeResult> probeMcpConnectivity(@RequestHeader("X-User-Id") Long userId,
                                                              @Valid @RequestBody McpConnectivityProbeRequest body) {
        return R.ok(mcpConnectivityProbeService.probe(body));
    }

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
    public R<ResourceManageVO> submit(@RequestHeader("X-User-Id") Long userId, @PathVariable Long id) {
        return R.ok(resourceRegistryService.submitForAudit(userId, id));
    }

    @PostMapping("/{id}/deprecate")
    @AuditLog(action = "resource_deprecate", resource = "resource-center")
    public R<ResourceManageVO> deprecate(@RequestHeader("X-User-Id") Long userId, @PathVariable Long id) {
        return R.ok(resourceRegistryService.deprecate(userId, id));
    }

    @GetMapping("/mine")
    public R<PageResult<ResourceManageVO>> mine(@RequestHeader("X-User-Id") Long userId,
                                                @RequestParam(required = false) String resourceType,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) String sortBy,
                                                @RequestParam(required = false) String sortOrder,
                                                @RequestParam(defaultValue = "1") Integer page,
                                                @RequestParam(defaultValue = "20") Integer pageSize,
                                                @RequestParam(required = false) Long forResourceId) {
        return R.ok(resourceRegistryService.pageMine(userId, resourceType, status, keyword, sortBy, sortOrder, page, pageSize, forResourceId));
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
    public R<ResourceManageVO> switchVersion(@RequestHeader("X-User-Id") Long userId,
                                             @PathVariable Long id,
                                             @PathVariable String version) {
        return R.ok(resourceRegistryService.switchVersion(userId, id, version));
    }

    @PostMapping("/{id}/versions/{version}/apply-to-working-copy")
    @AuditLog(action = "resource_version_apply", resource = "resource-center")
    public R<ResourceManageVO> applyVersionToWorkingCopy(@RequestHeader("X-User-Id") Long userId,
                                                       @PathVariable Long id,
                                                       @PathVariable String version) {
        return R.ok(resourceRegistryService.applyVersionSnapshotToWorkingCopy(userId, id, version));
    }

    @PostMapping("/{id}/agent-key/rotate")
    @AuditLog(action = "agent_key_rotate", resource = "resource-center")
    public R<ApiKeyResponse> rotateAgentKey(@RequestHeader("X-User-Id") Long userId,
                                            @PathVariable Long id) {
        return R.ok(agentApiKeyService.rotate(id, userId));
    }

    @PostMapping("/{id}/agent-key/revoke")
    @AuditLog(action = "agent_key_revoke", resource = "resource-center")
    public R<Void> revokeAgentKey(@RequestHeader("X-User-Id") Long userId,
                                  @PathVariable Long id) {
        agentApiKeyService.revoke(id);
        return R.ok();
    }

    @GetMapping("/{id}/agent-key/meta")
    public R<List<AgentKeyMetaVO>> listAgentKeys(@RequestHeader("X-User-Id") Long userId,
                                                  @PathVariable Long id) {
        List<AgentKeyMetaVO> rows = agentApiKeyService.list(id).stream()
                .map(it -> AgentKeyMetaVO.builder()
                        .id(it.getId())
                        .maskedKey(it.getMaskedKey())
                        .status(it.getStatus())
                        .scopes(it.getScopes())
                        .createTime(it.getCreateTime())
                        .lastUsedAt(it.getLastUsedAt())
                        .build())
                .toList();
        return R.ok(rows);
    }

    @GetMapping("/{id}/versions")
    public R<List<ResourceVersionVO>> versions(@RequestHeader("X-User-Id") Long userId,
                                               @PathVariable Long id) {
        return R.ok(resourceRegistryService.listVersions(userId, id));
    }

    @PostMapping("/{id}/withdraw")
    @AuditLog(action = "resource_withdraw", resource = "resource-center")
    public R<ResourceManageVO> withdraw(@RequestHeader("X-User-Id") Long userId, @PathVariable Long id) {
        return R.ok(resourceRegistryService.withdraw(userId, id));
    }

    @PostMapping("/batch-withdraw")
    @AuditLog(action = "resource_batch_withdraw", resource = "resource-center")
    public R<Void> batchWithdraw(@RequestHeader("X-User-Id") Long userId, @Valid @RequestBody LongIdsRequest body) {
        resourceRegistryService.batchWithdraw(userId, body.getIds());
        return R.ok();
    }

    @GetMapping("/{id}/lifecycle-timeline")
    public R<LifecycleTimelineVO> lifecycleTimeline(@RequestHeader("X-User-Id") Long userId,
                                                    @PathVariable Long id) {
        return R.ok(resourceRegistryService.lifecycleTimeline(userId, id));
    }

    @GetMapping("/{type}/{id}/observability-summary")
    public R<ObservabilitySummaryVO> observabilitySummary(@RequestHeader("X-User-Id") Long userId,
                                                          @PathVariable String type,
                                                          @PathVariable Long id) {
        return R.ok(resourceRegistryService.observabilitySummary(userId, type, id));
    }
}
