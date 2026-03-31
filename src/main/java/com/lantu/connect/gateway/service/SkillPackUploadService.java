package com.lantu.connect.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.service.FileStorageService;
import com.lantu.connect.gateway.dto.ResourceManageVO;
import com.lantu.connect.gateway.dto.ResourceUpsertRequest;
import com.lantu.connect.gateway.service.support.AnthropicSkillPackValidator;
import com.lantu.connect.gateway.service.support.ResourceLifecycleStateMachine;
import com.lantu.connect.gateway.service.support.SkillPackValidationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SkillPackUploadService {

    private final FileStorageService fileStorageService;
    private final ResourceRegistryService resourceRegistryService;
    private final SkillPackUrlFetcher skillPackUrlFetcher;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public ResourceManageVO uploadPack(Long operatorUserId, MultipartFile file, Long resourceIdOpt) {
        if (operatorUserId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未认证");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请上传 zip 文件");
        }
        final byte[] zipBytes;
        try {
            zipBytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "读取文件失败");
        }
        final String filename = file.getOriginalFilename();
        return transactionTemplate.execute(status -> ingestPack(operatorUserId, zipBytes, filename, resourceIdOpt, "internal", "由技能包上传创建"));
    }

    /**
     * 从公网 URL 拉取 zip，校验与落库逻辑与 {@link #uploadPack} 一致；新建资源时 {@code sourceType} 为 {@code cloud}。
     * HTTP 在事务外执行，避免长时间占用数据库连接。
     */
    public ResourceManageVO importPackFromUrl(Long operatorUserId, String url, Long resourceIdOpt) {
        if (operatorUserId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未认证");
        }
        SkillPackUrlFetcher.FetchedPack fetched = skillPackUrlFetcher.fetch(url);
        String safeRef = truncateUrlForDesc(sanitizeUrlForDescription(url.trim()));
        String desc = "由 URL 导入: " + safeRef;
        return transactionTemplate.execute(status -> ingestPack(operatorUserId, fetched.bytes(), fetched.filenameForStorage(), resourceIdOpt, "cloud", desc));
    }

    private ResourceManageVO ingestPack(Long operatorUserId, byte[] zipBytes, String originalFilename,
                                        Long resourceIdOpt, String sourceTypeForCreate, String descriptionForCreate) {
        AnthropicSkillPackValidator.PackValidationOutcome outcome = AnthropicSkillPackValidator.validate(zipBytes);
        String shaHex = sha256Hex(zipBytes);
        String storedPath = fileStorageService.storeSkillPack(zipBytes, originalFilename);
        Map<String, Object> manifest = new LinkedHashMap<>(outcome.manifest());

        if (resourceIdOpt == null) {
            String code = uniqueSkillCode(shaHex);
            String displayName;
            if (outcome.manifest().containsKey("name") && StringUtils.hasText(String.valueOf(outcome.manifest().get("name")))) {
                displayName = String.valueOf(outcome.manifest().get("name")).trim();
            } else {
                displayName = "技能包 " + code;
            }
            ResourceUpsertRequest req = new ResourceUpsertRequest();
            req.setResourceType("skill");
            req.setResourceCode(code);
            req.setDisplayName(displayName);
            req.setDescription(descriptionForCreate);
            req.setSourceType(sourceTypeForCreate);
            req.setSkillType("anthropic_v1");
            req.setArtifactUri(storedPath);
            req.setArtifactSha256(shaHex);
            req.setManifest(manifest);
            req.setEntryDoc(outcome.entryDoc());
            ResourceManageVO vo = resourceRegistryService.create(operatorUserId, req);
            applyPackValidationAfterUpload(vo.getId(), outcome);
            resourceRegistryService.recomputeCurrentVersionSnapshot(operatorUserId, vo.getId());
            return resourceRegistryService.getById(operatorUserId, vo.getId());
        }

        ResourceManageVO existing = resourceRegistryService.getById(operatorUserId, resourceIdOpt);
        if (!"skill".equalsIgnoreCase(existing.getResourceType())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "resourceId 须为 skill 类型资源");
        }
        ResourceLifecycleStateMachine.ensureEditable(existing.getStatus());
        writeManifestAndArtifact(resourceIdOpt, storedPath, shaHex, manifest, outcome.entryDoc(), outcome);
        resourceRegistryService.recomputeCurrentVersionSnapshot(operatorUserId, resourceIdOpt);
        return resourceRegistryService.getById(operatorUserId, resourceIdOpt);
    }

    /**
     * 写入描述时去掉 query、fragment，避免预签名 URL 等凭证进入库表与审计。
     */
    static String sanitizeUrlForDescription(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        try {
            URI u = new URI(url.trim());
            String scheme = u.getScheme();
            String host = u.getHost();
            if (!StringUtils.hasText(scheme) || !StringUtils.hasText(host)) {
                return "[无法解析的地址]";
            }
            String s = scheme.toLowerCase(Locale.ROOT) + "://" + host.toLowerCase(Locale.ROOT);
            int port = u.getPort();
            if (port > 0) {
                s += ":" + port;
            }
            String path = u.getPath();
            if (StringUtils.hasText(path)) {
                s += path;
            }
            return s;
        } catch (URISyntaxException e) {
            return "[无法解析的地址]";
        }
    }

    private static String truncateUrlForDesc(String url) {
        if (url == null) {
            return "";
        }
        if (url.length() <= 500) {
            return url;
        }
        return url.substring(0, 500) + "...";
    }

    private void applyPackValidationAfterUpload(Long resourceId, AnthropicSkillPackValidator.PackValidationOutcome outcome) {
        jdbcTemplate.update("""
                        UPDATE t_resource_skill_ext
                        SET pack_validation_status = ?, pack_validated_at = NOW(), pack_validation_message = ?
                        WHERE resource_id = ?
                        """,
                outcome.valid() ? SkillPackValidationStatus.VALID : SkillPackValidationStatus.INVALID,
                outcome.valid() ? null : truncateMessage(outcome.message()),
                resourceId);
    }

    private void writeManifestAndArtifact(Long resourceId, String uri, String shaHex, Map<String, Object> manifest,
                                          String entryDoc, AnthropicSkillPackValidator.PackValidationOutcome outcome) {
        String manifestJson;
        try {
            manifestJson = objectMapper.writeValueAsString(manifest);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "manifest 序列化失败");
        }
        jdbcTemplate.update("""
                        UPDATE t_resource_skill_ext
                        SET artifact_uri = ?, artifact_sha256 = ?, manifest_json = CAST(? AS JSON), entry_doc = ?,
                            pack_validation_status = ?, pack_validated_at = NOW(), pack_validation_message = ?
                        WHERE resource_id = ?
                        """,
                uri,
                shaHex,
                manifestJson,
                entryDoc,
                outcome.valid() ? SkillPackValidationStatus.VALID : SkillPackValidationStatus.INVALID,
                outcome.valid() ? null : truncateMessage(outcome.message()),
                resourceId);
    }

    private static String truncateMessage(String msg) {
        if (msg == null) {
            return null;
        }
        if (msg.length() <= 4000) {
            return msg;
        }
        return msg.substring(0, 4000) + "...";
    }

    private String uniqueSkillCode(String shaHex) {
        String digest = shaHex == null ? "" : shaHex.toLowerCase(Locale.ROOT);
        String base = "sk-pkg-" + digest.substring(0, Math.min(12, digest.length()));
        String code = base;
        for (int i = 0; i < 100; i++) {
            Integer cnt = jdbcTemplate.queryForObject("""
                            SELECT COUNT(1) FROM t_resource WHERE deleted = 0 AND resource_type = 'skill' AND resource_code = ?
                            """,
                    Integer.class,
                    code);
            if (cnt == null || cnt == 0) {
                return code;
            }
            code = base + "-" + i;
        }
        throw new BusinessException(ResultCode.INTERNAL_ERROR, "生成资源编码失败");
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(data);
            StringBuilder sb = new StringBuilder(dig.length * 2);
            for (byte b : dig) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "SHA-256 不可用");
        }
    }
}
