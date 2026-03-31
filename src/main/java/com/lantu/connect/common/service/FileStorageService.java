package com.lantu.connect.common.service;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.storage.FileStorageSupport;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

/**
 * 文件持久化：通过 {@code file.storage-type} 选择 <strong>local</strong>（默认）或 <strong>minio</strong>。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "pdf", "docx", "csv", "json", "parquet", "xlsx", "jpg", "jpeg", "png", "gif", "svg", "zip");
    private static final Pattern SAFE_CATEGORY = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,39}$");

    private final FileStorageSupport storageSupport;

    @Value("${file.upload-dir:/data/nexusai/uploads}")
    private String uploadDir;

    @Value("${file.max-size-mb:50}")
    private int maxSizeMb;

    @Value("${file.skill-pack-max-mb:100}")
    private int skillPackMaxMb;

    /** {@code local}（默认）或 {@code minio} */
    @Value("${file.storage-type:local}")
    private String storageType;

    @Value("${file.minio.endpoint:}")
    private String minioEndpoint;

    @Value("${file.minio.bucket:nexusai-connect}")
    private String minioBucket;

    @Value("${file.allowed-categories:document,avatar,image,attachment,temp,dataset}")
    private String allowedCategoriesRaw;

    private Set<String> allowedCategories = Set.of("document", "avatar", "image", "attachment", "temp", "dataset");

    @PostConstruct
    void initAllowedCategories() {
        Set<String> parsed = Arrays.stream(allowedCategoriesRaw.split(","))
                .map(String::trim)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        if (parsed.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "file.allowed-categories 不能为空");
        }
        for (String c : parsed) {
            if (!SAFE_CATEGORY.matcher(c).matches()) {
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        "file.allowed-categories 含非法分类: " + c);
            }
        }
        this.allowedCategories = Set.copyOf(parsed);
    }

    public String store(MultipartFile file, String category) {
        if (file.isEmpty()) throw new BusinessException(ResultCode.PARAM_ERROR, "文件不能为空");
        if (file.getSize() > (long) maxSizeMb * 1024 * 1024)
            throw new BusinessException(ResultCode.FILE_SIZE_EXCEEDED, "文件大小超过限制 (" + maxSizeMb + "MB)");

        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains("."))
            ext = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
        if (!ALLOWED_TYPES.contains(ext))
            throw new BusinessException(ResultCode.UNSUPPORTED_FILE_TYPE, "不支持的文件类型: " + ext);

        String safeCategory = normalizeCategory(category);
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String relativePath = safeCategory + "/" + datePath + "/" + UUID.randomUUID() + "." + ext;

        if (isMinioMode()) {
            return storeToMinio(file, relativePath);
        }
        return storeToLocal(file, relativePath);
    }

    /**
     * 持久化 Anthropic 式技能 zip，单文件上限可高于通用上传（默认 100MB）。
     */
    public String storeSkillPack(byte[] data, String originalFilename) {
        if (data == null || data.length == 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "文件不能为空");
        }
        if (data.length > (long) skillPackMaxMb * 1024 * 1024) {
            throw new BusinessException(ResultCode.FILE_SIZE_EXCEEDED, "技能包超过大小限制 (" + skillPackMaxMb + "MB)");
        }
        if (originalFilename != null && originalFilename.contains(".")) {
            String ext = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
            if (!"zip".equals(ext)) {
                throw new BusinessException(ResultCode.UNSUPPORTED_FILE_TYPE, "技能包须为 zip");
            }
        }
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String relativePath = "skill-pack/" + datePath + "/" + UUID.randomUUID() + ".zip";
        if (isMinioMode()) {
            return storeSkillPackToMinio(data, relativePath);
        }
        return storeSkillPackToLocal(data, relativePath);
    }

    private boolean isMinioMode() {
        if ("minio".equalsIgnoreCase(storageType)) {
            return true;
        }
        if ("local".equalsIgnoreCase(storageType)) {
            return false;
        }
        throw new BusinessException(ResultCode.PARAM_ERROR,
                "file.storage-type 仅支持 local（默认）或 minio，当前值: " + storageType);
    }

    private String storeSkillPackToLocal(byte[] data, String relativePath) {
        Path fullPath = storageSupport.resolveLocalWritePath(relativePath);
        try {
            Files.createDirectories(fullPath.getParent());
            Files.write(fullPath, data);
        } catch (IOException e) {
            log.error("技能包存储失败: {}", fullPath, e);
            throw new BusinessException(ResultCode.FILE_STORAGE_ERROR);
        }
        return "/uploads/" + relativePath;
    }

    private String storeSkillPackToMinio(byte[] data, String relativePath) {
        String key = storageSupport.normalizeObjectKey(relativePath);
        try {
            MinioClient client = storageSupport.createMinioClient();
            ensureBucket(client);
            try (InputStream is = new ByteArrayInputStream(data)) {
                client.putObject(PutObjectArgs.builder()
                        .bucket(minioBucket)
                        .object(key)
                        .stream(is, data.length, -1)
                        .contentType("application/zip")
                        .build());
            }
            return normalizedMinioPublicPrefix() + relativePath;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("MinIO 技能包存储失败: {}", relativePath, e);
            throw new BusinessException(ResultCode.FILE_STORAGE_ERROR, "对象存储服务异常");
        }
    }

    private String storeToLocal(MultipartFile file, String relativePath) {
        Path fullPath = storageSupport.resolveLocalWritePath(relativePath);
        try {
            Files.createDirectories(fullPath.getParent());
            file.transferTo(fullPath);
        } catch (IOException e) {
            log.error("文件存储失败: {}", fullPath, e);
            throw new BusinessException(ResultCode.FILE_STORAGE_ERROR);
        }
        return "/uploads/" + relativePath;
    }

    private String storeToMinio(MultipartFile file, String relativePath) {
        String key = storageSupport.normalizeObjectKey(relativePath);
        try {
            MinioClient client = storageSupport.createMinioClient();
            ensureBucket(client);
            try (InputStream is = file.getInputStream()) {
                client.putObject(PutObjectArgs.builder()
                        .bucket(minioBucket)
                        .object(key)
                        .stream(is, file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build());
            }
            return normalizedMinioPublicPrefix() + key;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("MinIO 存储失败: {}", relativePath, e);
            throw new BusinessException(ResultCode.FILE_STORAGE_ERROR, "对象存储服务异常");
        }
    }

    private void ensureBucket(MinioClient client) throws Exception {
        boolean bucketExists = client.bucketExists(BucketExistsArgs.builder().bucket(minioBucket).build());
        if (!bucketExists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(minioBucket).build());
        }
    }

    /** 与 {@link com.lantu.connect.common.storage.FileStorageSupport#extractMinioObjectKey} 使用的前缀一致 */
    private String normalizedMinioPublicPrefix() {
        String ep = minioEndpoint.replaceAll("/+$", "");
        return ep + "/" + minioBucket + "/";
    }

    private String normalizeCategory(String category) {
        String raw = category == null ? "" : category.trim().toLowerCase(Locale.ROOT);
        if (!SAFE_CATEGORY.matcher(raw).matches()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "非法文件分类");
        }
        if (!allowedCategories.contains(raw)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不允许的文件分类: " + raw);
        }
        return raw;
    }
}
