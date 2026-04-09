package com.lantu.connect.common.storage;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.sysconfig.runtime.RuntimeAppConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地上传目录的路径解析（防目录穿越）。写入由 {@link com.lantu.connect.common.service.FileStorageService} 完成。
 */
@Component
@RequiredArgsConstructor
public class FileStorageSupport {

    private final RuntimeAppConfigService runtimeAppConfigService;

    public String getUploadDir() {
        return runtimeAppConfigService.file().getUploadDir();
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
        Path base = Paths.get(getUploadDir()).toAbsolutePath().normalize();
        Path full = base.resolve(normalizedRel).normalize();
        if (!full.startsWith(base)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "文件路径非法");
        }
        return full;
    }
}
