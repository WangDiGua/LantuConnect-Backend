package com.lantu.connect.common.storage;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * 本地目录与 MinIO 的路径解析与读对象流辅助。
 * 写入由 {@link com.lantu.connect.common.service.FileStorageService} 按 {@code file.storage-type} 二选一；
 * 技能包等受控下载由 {@link com.lantu.connect.gateway.service.SkillArtifactDownloadService} 同样按 {@code file.storage-type} 只走对应后端，不混合回退。
 */
@Component
public class FileStorageSupport {
    private static final Pattern SAFE_OBJECT_KEY = Pattern.compile("^[A-Za-z0-9._\\-/]{1,512}$");

    @Value("${file.upload-dir:/data/nexusai/uploads}")
    private String uploadDir;

    @Value("${file.minio.endpoint:}")
    private String minioEndpoint;

    @Value("${file.minio.access-key:}")
    private String minioAccessKey;

    @Value("${file.minio.secret-key:}")
    private String minioSecretKey;

    @Value("${file.minio.bucket:nexusai-connect}")
    private String minioBucket;

    public String getUploadDir() {
        return uploadDir;
    }

    public MinioClient createMinioClient() {
        if (!StringUtils.hasText(minioEndpoint) || !StringUtils.hasText(minioAccessKey) || !StringUtils.hasText(minioSecretKey)) {
            throw new BusinessException(ResultCode.FILE_STORAGE_ERROR, "MinIO 未正确配置（endpoint / access-key / secret-key）");
        }
        return MinioClient.builder()
                .endpoint(minioEndpoint.trim())
                .credentials(minioAccessKey, minioSecretKey)
                .build();
    }

    /**
     * 解析本地写入路径（防目录穿越），非法相对路径直接抛业务异常。
     */
    public Path resolveLocalWritePath(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "文件路径不能为空");
        }
        if (relativePath.indexOf('\0') >= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "文件路径非法");
        }
        String normalizedRel = relativePath.replace('\\', '/');
        if (normalizedRel.startsWith("/") || normalizedRel.contains("..") || normalizedRel.contains("//")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "文件路径非法");
        }
        Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path full = base.resolve(normalizedRel).normalize();
        if (!full.startsWith(base)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "文件路径非法");
        }
        return full;
    }

    /**
     * 校验并规范化 MinIO Object Key，防止对象键注入与穿越语义。
     */
    public String normalizeObjectKey(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "对象键不能为空");
        }
        String normalized = objectKey.replace('\\', '/').trim();
        if (!SAFE_OBJECT_KEY.matcher(normalized).matches()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "对象键非法");
        }
        if (normalized.startsWith("/") || normalized.contains("..") || normalized.contains("//")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "对象键非法");
        }
        return normalized;
    }

    /** artifact_uri 为 {@code /uploads/...} 时解析为本地绝对路径（防目录穿越）；否则返回 null。 */
    public Path resolveLocalArtifactPath(String artifactUri) {
        if (!StringUtils.hasText(artifactUri) || !artifactUri.startsWith("/uploads/")) {
            return null;
        }
        String rel = artifactUri.substring("/uploads/".length());
        Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path full = base.resolve(rel).normalize();
        if (!full.startsWith(base)) {
            return null;
        }
        return full;
    }

    /**
     * 当 artifact_uri 为 {@code <endpoint>/<bucket>/<objectKey>} 形态（与配置的 endpoint、bucket 一致）时返回 object key。
     */
    public String extractMinioObjectKey(String artifactUri) {
        if (!StringUtils.hasText(minioEndpoint) || !StringUtils.hasText(minioBucket) || !StringUtils.hasText(artifactUri)) {
            return null;
        }
        String ep = minioEndpoint.replaceAll("/+$", "");
        String prefix = ep + "/" + minioBucket + "/";
        if (artifactUri.startsWith(prefix)) {
            return artifactUri.substring(prefix.length());
        }
        return null;
    }

    public InputStream openMinioObject(String objectKey) {
        try {
            return createMinioClient().getObject(
                    GetObjectArgs.builder().bucket(minioBucket).object(objectKey).build());
        } catch (Exception e) {
            throw new BusinessException(ResultCode.FILE_STORAGE_ERROR, "对象存储读取失败");
        }
    }
}
