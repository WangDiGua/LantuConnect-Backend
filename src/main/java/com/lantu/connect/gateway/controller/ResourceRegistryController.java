package com.lantu.connect.gateway.controller;

import com.lantu.connect.common.annotation.AuditLog;
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
import com.lantu.connect.gateway.dto.SkillPackChunkInitRequest;
import com.lantu.connect.gateway.dto.SkillPackChunkInitResponse;
import com.lantu.connect.gateway.dto.SkillPackChunkStatusResponse;
import com.lantu.connect.gateway.dto.SkillPackJsonUploadRequest;
import com.lantu.connect.gateway.dto.SkillPackUrlImportRequest;
import com.lantu.connect.gateway.security.ApiKeyScopeService;
import com.lantu.connect.gateway.service.McpConnectivityProbeService;
import com.lantu.connect.gateway.service.ResourceRegistryService;
import com.lantu.connect.gateway.service.SkillArtifactDownloadService;
import com.lantu.connect.gateway.service.SkillPackChunkedUploadService;
import com.lantu.connect.gateway.service.SkillPackUploadService;
import com.lantu.connect.usermgmt.entity.ApiKey;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 统一资源注册中心：注册、更新、提审、下线、版本管理。
 */
@RestController
@RequestMapping("/resource-center/resources")
@RequiredArgsConstructor
public class ResourceRegistryController {

    private final ResourceRegistryService resourceRegistryService;
    private final SkillPackUploadService skillPackUploadService;
    private final SkillPackChunkedUploadService skillPackChunkedUploadService;
    private final SkillArtifactDownloadService skillArtifactDownloadService;
    private final ApiKeyScopeService apiKeyScopeService;
    private final McpConnectivityProbeService mcpConnectivityProbeService;

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
                                                @RequestParam(defaultValue = "20") Integer pageSize) {
        return R.ok(resourceRegistryService.pageMine(userId, resourceType, status, keyword, sortBy, sortOrder, page, pageSize));
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

    /**
     * 上传 Anthropic 式技能包：支持 .zip、.tar.gz/.tgz、裸 .tar，或单个 Markdown（SKILL.md / frontmatter）。
     * 服务端归一为标准 zip 后校验 SKILL.md、写入制品与 manifest，并更新 pack_validation_*。
     */
    @PostMapping(value = "/skills/package-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @AuditLog(action = "skill_pack_upload", resource = "resource-center")
    public R<ResourceManageVO> uploadSkillPackage(@RequestHeader("X-User-Id") Long userId,
                                                    @RequestParam("file") MultipartFile file,
                                                    @RequestParam(value = "resourceId", required = false) Long resourceId,
                                                    @RequestParam(value = "skillRoot", required = false) String skillRoot) {
        return R.ok(skillPackUploadService.uploadPack(userId, file, resourceId, skillRoot));
    }

    /**
     * 与 multipart 版同一 URL：部分前端/代理只发 JSON，可将 zip 字节经 Base64 放在 {@code file} / {@code fileBase64}。
     */
    @PostMapping(value = "/skills/package-upload", consumes = MediaType.APPLICATION_JSON_VALUE)
    @AuditLog(action = "skill_pack_upload", resource = "resource-center")
    public R<ResourceManageVO> uploadSkillPackageJson(@RequestHeader("X-User-Id") Long userId,
                                                      @Valid @RequestBody SkillPackJsonUploadRequest body) {
        byte[] raw = SkillPackUploadService.decodeUploadBase64(body.getFileBase64());
        String hint = body.getFilename();
        return R.ok(skillPackUploadService.uploadPackBytes(userId, raw, hint, body.getResourceId(), body.getSkillRoot()));
    }

    /**
     * 从 HTTPS（可配置允许 HTTP）URL 拉取技能包（zip/tar.gz/tar/单文件 gzip 的 md 等），归一化后校验与落库与 package-upload 一致；新建时 sourceType 为 cloud。
     */
    @PostMapping(value = "/skills/package-import-url", consumes = MediaType.APPLICATION_JSON_VALUE)
    @AuditLog(action = "skill_pack_import_url", resource = "resource-center")
    public R<ResourceManageVO> importSkillPackageFromUrl(@RequestHeader("X-User-Id") Long userId,
                                                         @Valid @RequestBody SkillPackUrlImportRequest body) {
        return R.ok(skillPackUploadService.importPackFromUrl(userId, body.getUrl(), body.getResourceId(), body.getSkillRoot()));
    }

    /** 分片上传初始化（断点续传）；每片大小见响应 chunkSize（通常为 4MB）。 */
    @PostMapping(value = "/skills/package-upload/chunk/init", consumes = MediaType.APPLICATION_JSON_VALUE)
    public R<SkillPackChunkInitResponse> initSkillPackageChunkSession(@RequestHeader("X-User-Id") Long userId,
                                                                       @Valid @RequestBody SkillPackChunkInitRequest body) {
        return R.ok(skillPackChunkedUploadService.init(userId, body));
    }

    @GetMapping("/skills/package-upload/chunk/{uploadId}/status")
    public R<SkillPackChunkStatusResponse> skillPackageChunkStatus(@RequestHeader("X-User-Id") Long userId,
                                                                    @PathVariable String uploadId) {
        return R.ok(skillPackChunkedUploadService.status(userId, uploadId));
    }

    @PostMapping(value = "/skills/package-upload/chunk/{uploadId}/{chunkIndex}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<Void> uploadSkillPackageChunk(@RequestHeader("X-User-Id") Long userId,
                                           @PathVariable String uploadId,
                                           @PathVariable int chunkIndex,
                                           @RequestParam("file") MultipartFile file) throws IOException {
        skillPackChunkedUploadService.putChunk(userId, uploadId, chunkIndex, file.getBytes());
        return R.ok();
    }

    @PostMapping("/skills/package-upload/chunk/{uploadId}/complete")
    @AuditLog(action = "skill_pack_upload", resource = "resource-center")
    public R<ResourceManageVO> completeSkillPackageChunk(@RequestHeader("X-User-Id") Long userId,
                                                         @PathVariable String uploadId) {
        return R.ok(skillPackChunkedUploadService.complete(userId, uploadId));
    }

    @DeleteMapping("/skills/package-upload/chunk/{uploadId}")
    public R<Void> abortSkillPackageChunk(@RequestHeader("X-User-Id") Long userId,
                                          @PathVariable String uploadId) {
        skillPackChunkedUploadService.abort(userId, uploadId);
        return R.ok();
    }

    /**
     * 下载技能包（主要用于 isPublic=0 时在 resolve 中不直接返回 artifact URL 的场景）。
     */
    @GetMapping("/{id}/skill-artifact")
    public void downloadSkillArtifact(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                      @RequestHeader(value = "X-Api-Key", required = false) String apiKeyRaw,
                                      @PathVariable Long id,
                                      HttpServletResponse response) throws IOException {
        ApiKey apiKey = apiKeyScopeService.authenticateOrNull(apiKeyRaw);
        skillArtifactDownloadService.streamArtifact(id, userId, apiKey, response);
    }
}

