package com.lantu.connect.common.service;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.config.FileBootstrapProperties;
import com.lantu.connect.common.storage.FileStorageSupport;
import com.lantu.connect.sysconfig.runtime.RuntimeAppConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 文件持久化：仅写入 {@code file.upload-dir} 下，对外返回 {@code /uploads/...} 路径前缀。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "pdf", "docx", "csv", "json", "parquet", "xlsx", "jpg", "jpeg", "png", "gif", "svg", "zip");
    private static final Pattern SAFE_CATEGORY = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,39}$");

    private final FileStorageSupport storageSupport;
    private final RuntimeAppConfigService runtimeAppConfigService;

    private FileBootstrapProperties f() {
        return runtimeAppConfigService.file();
    }

    private Set<String> allowedCategories() {
        String allowedCategoriesRaw = f().getAllowedCategories();
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
        return Set.copyOf(parsed);
    }

    public String store(MultipartFile file, String category) {
        if (file.isEmpty()) throw new BusinessException(ResultCode.PARAM_ERROR, "文件不能为空");
        int maxSizeMb = f().getMaxSizeMb();
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

    private String normalizeCategory(String category) {
        String raw = category == null ? "" : category.trim().toLowerCase(Locale.ROOT);
        if (!SAFE_CATEGORY.matcher(raw).matches()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "非法文件分类");
        }
        if (!allowedCategories().contains(raw)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不允许的文件分类: " + raw);
        }
        return raw;
    }
}
