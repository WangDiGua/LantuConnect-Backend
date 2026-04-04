package com.lantu.connect.gateway.service;

import com.lantu.connect.auth.entity.PlatformRole;
import com.lantu.connect.auth.mapper.PlatformRoleMapper;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.storage.FileStorageSupport;
import com.lantu.connect.gateway.security.ApiKeyScopeService;
import com.lantu.connect.gateway.security.GatewayUserPermissionService;
import com.lantu.connect.gateway.security.ResourceInvokeGrantService;
import com.lantu.connect.gateway.service.support.ResourceLifecycleStateMachine;
import com.lantu.connect.usermgmt.entity.ApiKey;
import com.lantu.connect.sysconfig.runtime.RuntimeAppConfigService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 技能包受控下载：私有技能不直接暴露 artifact URL（见 {@code UnifiedGatewayServiceImpl#resolveSkill}）。
 * 读取严格遵循 {@code file.storage-type}：<strong>local</strong> 仅读本地 {@code /uploads/...}；
 * <strong>minio</strong> 仅从 {@code endpoint/bucket/objectKey} 形态读对象，不再混合回退。
 */
@Service
@RequiredArgsConstructor
public class SkillArtifactDownloadService {

    private final JdbcTemplate jdbcTemplate;
    private final GatewayUserPermissionService gatewayUserPermissionService;
    private final ApiKeyScopeService apiKeyScopeService;
    private final ResourceInvokeGrantService resourceInvokeGrantService;
    private final PlatformRoleMapper platformRoleMapper;
    private final FileStorageSupport fileStorageSupport;
    private final RuntimeAppConfigService runtimeAppConfigService;

    private String storageType() {
        return runtimeAppConfigService.file().getStorageType();
    }

    public void streamArtifact(Long resourceId, Long userId, ApiKey apiKey, HttpServletResponse response) throws java.io.IOException {
        if (userId == null && apiKey == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "需要登录或提供 X-Api-Key");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                        SELECT r.resource_type, r.status, r.resource_code, r.created_by, se.artifact_uri, se.is_public
                        FROM t_resource r
                        INNER JOIN t_resource_skill_ext se ON se.resource_id = r.id
                        WHERE r.id = ? AND r.deleted = 0
                        LIMIT 1
                        """,
                resourceId);
        if (rows.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "技能资源不存在");
        }
        Map<String, Object> row = rows.get(0);
        if (!"skill".equalsIgnoreCase(stringValue(row.get("resource_type")))) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "非技能资源");
        }
        String artifactUri = stringValue(row.get("artifact_uri"));
        if (!StringUtils.hasText(artifactUri)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "未配置技能包");
        }
        ensureCanDownload(resourceId, userId, apiKey, row);

        String code = stringValue(row.get("resource_code"));
        String filename = (StringUtils.hasText(code) ? code : "skill-" + resourceId) + ".zip";
        response.setContentType("application/zip");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

        String uri = artifactUri.trim();
        if ("minio".equalsIgnoreCase(storageType())) {
            String objectKey = fileStorageSupport.extractMinioObjectKey(uri);
            if (!StringUtils.hasText(objectKey)) {
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        "当前 storage-type= minio，artifact_uri 须为 MinIO 对象地址（与 file.minio.endpoint、bucket 一致的前缀 + objectKey）。"
                                + " 若为历史本地路径 /uploads/...，请迁移对象并更新库中 artifact_uri");
            }
            try (InputStream in = fileStorageSupport.openMinioObject(objectKey); OutputStream out = response.getOutputStream()) {
                in.transferTo(out);
            }
            recordSkillPackDownload(resourceId, longNullable(row.get("created_by")), userId, apiKey);
            return;
        }
        if ("local".equalsIgnoreCase(storageType())) {
            Path local = fileStorageSupport.resolveLocalArtifactPath(uri);
            if (local == null) {
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        "当前 storage-type= local，artifact_uri 须以 /uploads/ 开头且落在 file.upload-dir 下。"
                                + " 若为历史 MinIO 地址，请切换配置或迁回本地并更新库中 artifact_uri");
            }
            if (!Files.isRegularFile(local)) {
                throw new BusinessException(ResultCode.NOT_FOUND, "技能包文件在本地存储中不存在");
            }
            try (InputStream in = Files.newInputStream(local); OutputStream out = response.getOutputStream()) {
                in.transferTo(out);
            }
            recordSkillPackDownload(resourceId, longNullable(row.get("created_by")), userId, apiKey);
            return;
        }
        throw new BusinessException(ResultCode.PARAM_ERROR,
                "file.storage-type 仅支持 local（默认）或 minio，当前值: " + storageType());
    }

    private void ensureCanDownload(Long resourceId, Long userId, ApiKey apiKey, Map<String, Object> row) {
        String status = stringValue(row.get("status"));
        String normalizedStatus = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
        boolean isPublic = truthy(row.get("is_public"));
        Long createdBy = longNullable(row.get("created_by"));
        boolean owner = createdBy != null && createdBy.equals(userId);
        boolean admin = isAdmin(userId);

        if (owner || admin) {
            return;
        }
        if (!ResourceLifecycleStateMachine.STATUS_PUBLISHED.equals(normalizedStatus)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅资源拥有者或管理员可下载未发布技能包");
        }
        if (isPublic) {
            gatewayUserPermissionService.ensureAccess(userId, "skill");
            return;
        }
        if (apiKey == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "私有技能包下载需要提供已获授权的 X-Api-Key");
        }
        apiKeyScopeService.ensureResolveAllowed(apiKey, "skill", String.valueOf(resourceId));
        resourceInvokeGrantService.ensureApiKeyGranted(apiKey, "resolve", "skill", resourceId, userId);
    }

    private void recordSkillPackDownload(Long resourceId, Long ownerUserId, Long downloaderUserId, ApiKey apiKey) {
        if (ownerUserId == null || resourceId == null) {
            return;
        }
        jdbcTemplate.update("""
                        INSERT INTO t_skill_pack_download_event(
                            resource_id, resource_type, owner_user_id, downloader_user_id, downloader_api_key_id)
                        VALUES (?, 'skill', ?, ?, ?)
                        """,
                resourceId,
                ownerUserId,
                downloaderUserId,
                apiKey != null ? apiKey.getId() : null);
    }

    private boolean isAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        List<PlatformRole> roles = platformRoleMapper.selectRolesByUserId(userId);
        return roles.stream().map(PlatformRole::getRoleCode).anyMatch(code ->
                "platform_admin".equals(code) || "admin".equals(code) || "reviewer".equals(code));
    }

    private static boolean truthy(Object o) {
        if (o == null) {
            return false;
        }
        if (o instanceof Boolean b) {
            return b;
        }
        if (o instanceof Number n) {
            return n.intValue() != 0;
        }
        return false;
    }

    private static String stringValue(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static Long longNullable(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }
}
