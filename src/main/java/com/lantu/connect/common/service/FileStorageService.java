package com.lantu.connect.common.service;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "pdf", "docx", "csv", "json", "parquet", "xlsx", "jpg", "jpeg", "png", "gif", "svg");

    @Value("${file.upload-dir:/data/lantu/uploads}")
    private String uploadDir;

    @Value("${file.max-size-mb:50}")
    private int maxSizeMb;

    @Value("${file.storage-type:local}")
    private String storageType;

    @Value("${file.minio.endpoint:}")
    private String minioEndpoint;

    @Value("${file.minio.access-key:}")
    private String minioAccessKey;

    @Value("${file.minio.secret-key:}")
    private String minioSecretKey;

    @Value("${file.minio.bucket:lantu-connect}")
    private String minioBucket;

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

        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String relativePath = category + "/" + datePath + "/" + UUID.randomUUID() + "." + ext;

        if ("minio".equalsIgnoreCase(storageType)) {
            return storeToMinio(file, relativePath);
        }
        return storeToLocal(file, relativePath);
    }

    private String storeToLocal(MultipartFile file, String relativePath) {
        Path fullPath = Paths.get(uploadDir, relativePath);
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
        try {
            MinioClient client = MinioClient.builder()
                    .endpoint(minioEndpoint)
                    .credentials(minioAccessKey, minioSecretKey)
                    .build();

            boolean bucketExists = client.bucketExists(BucketExistsArgs.builder().bucket(minioBucket).build());
            if (!bucketExists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(minioBucket).build());
            }

            try (InputStream is = file.getInputStream()) {
                client.putObject(PutObjectArgs.builder()
                        .bucket(minioBucket)
                        .object(relativePath)
                        .stream(is, file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build());
            }

            return minioEndpoint + "/" + minioBucket + "/" + relativePath;
        } catch (Exception e) {
            log.error("MinIO 存储失败: {}", relativePath, e);
            throw new BusinessException(ResultCode.FILE_STORAGE_ERROR, "对象存储服务异常");
        }
    }
}
