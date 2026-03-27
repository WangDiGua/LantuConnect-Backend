package com.lantu.connect.common.controller;

import com.lantu.connect.common.result.R;
import com.lantu.connect.common.service.FileStorageService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 文件上传控制器
 *
 * @author 王帝
 * @date 2026-03-21
 */
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    @PostMapping("/upload")
    @RateLimiter(name = "fileUpload", fallbackMethod = "uploadFallback")
    public R<Map<String, String>> upload(@RequestParam("file") MultipartFile file,
                                         @RequestParam(value = "category", defaultValue = "document") String category) {
        String url = fileStorageService.store(file, category);
        return R.ok(Map.of("url", url, "fileName", file.getOriginalFilename()));
    }

    public R<Map<String, String>> uploadFallback(MultipartFile file, String category, Exception e) {
        return R.fail(429, "上传请求过于频繁，请稍后重试");
    }
}
