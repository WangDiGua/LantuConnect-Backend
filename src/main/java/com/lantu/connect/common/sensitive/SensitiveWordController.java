package com.lantu.connect.common.sensitive;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.PageResults;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.dto.LongIdsRequest;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.result.R;
import com.lantu.connect.common.security.RequireRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 敏感词管理控制器
 *
 * @author 王帝
 * @date 2026-03-23
 */
@RestController
@RequestMapping("/sensitive-words")
@RequiredArgsConstructor
@Validated
public class SensitiveWordController {

    private final SensitiveWordService sensitiveWordService;

    @GetMapping
    @RequireRole({"platform_admin"})
    public R<PageResult<SensitiveWord>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword) {
        Page<SensitiveWord> p = sensitiveWordService.list(page, pageSize, category, enabled, keyword);
        return R.ok(PageResults.from(p));
    }

    @GetMapping("/categories")
    @RequireRole({"platform_admin"})
    public R<List<SensitiveWordCategoryStat>> listCategories() {
        return R.ok(sensitiveWordService.listCategories());
    }

    @GetMapping("/count")
    @RequireRole({"platform_admin"})
    public R<Map<String, Integer>> getCount() {
        return R.ok(Map.of("count", sensitiveWordService.getWordCount()));
    }

    @PostMapping
    @RequireRole({"platform_admin"})
    public R<SensitiveWord> add(@Valid @RequestBody AddRequest request,
                                @RequestHeader("X-User-Id") Long userId) {
        return R.ok(sensitiveWordService.add(
                request.getWord(),
                request.getCategory(),
                request.getSeverity(),
                request.getSource(),
                userId));
    }

    @PostMapping("/batch")
    @RequireRole({"platform_admin"})
    public R<Void> batchAdd(@Valid @RequestBody BatchAddRequest request,
                            @RequestHeader("X-User-Id") Long userId) {
        sensitiveWordService.batchAdd(
                request.getWords(),
                request.getCategory(),
                request.getSeverity(),
                request.getSource(),
                userId);
        return R.ok();
    }

    /**
     * 上传 TXT 批量导入：每行一词，UTF-8；{@code #} 开头为注释；空行忽略。
     */
    @PostMapping(value = "/import-txt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireRole({"platform_admin"})
    public R<SensitiveWordTxtImportResult> importTxt(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer severity,
            @RequestParam(required = false) String source,
            @RequestHeader("X-User-Id") Long userId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请上传非空 txt 文件");
        }
        String filename = file.getOriginalFilename();
        if (filename != null && !filename.toLowerCase().endsWith(".txt")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "仅支持 .txt 文件");
        }
        String text;
        try {
            text = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "读取上传文件失败");
        }
        return R.ok(sensitiveWordService.importFromTxt(text, category, severity, source, userId));
    }

    /**
     * 统一文件导入：支持 txt/csv/xlsx，默认取首列作为敏感词。
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireRole({"platform_admin"})
    public R<SensitiveWordTxtImportResult> importFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer severity,
            @RequestParam(required = false) String source,
            @RequestHeader("X-User-Id") Long userId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请上传文件");
        }
        String filename = file.getOriginalFilename();
        try {
            return R.ok(sensitiveWordService.importFromFile(file.getBytes(), filename, category, severity, source, userId));
        } catch (java.io.IOException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "读取上传文件失败");
        }
    }

    @PutMapping("/{id}")
    @RequireRole({"platform_admin"})
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody UpdateRequest request) {
        sensitiveWordService.update(id, request.getWord(), request.getCategory(), request.getSeverity(),
                request.getEnabled(), request.getSource());
        return R.ok();
    }

    @PutMapping("/batch")
    @RequireRole({"platform_admin"})
    public R<Void> batchSetEnabled(@Valid @RequestBody SensitiveWordBatchEnableRequest body) {
        sensitiveWordService.batchSetEnabled(body.getIds(), Boolean.TRUE.equals(body.getEnabled()));
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @RequireRole({"platform_admin"})
    public R<Void> delete(@PathVariable Long id) {
        sensitiveWordService.delete(id);
        return R.ok();
    }

    @PostMapping("/batch-delete")
    @RequireRole({"platform_admin"})
    public R<Void> batchDelete(@Valid @RequestBody LongIdsRequest body) {
        sensitiveWordService.batchDelete(body.getIds());
        return R.ok();
    }

    @PostMapping("/check")
    public R<CheckResult> check(@Valid @RequestBody CheckRequest request) {
        CheckResult result = new CheckResult();
        result.setText(request.getText());
        result.setContainsSensitive(sensitiveWordService.contains(request.getText()));
        result.setSensitiveWords(sensitiveWordService.findSensitiveWords(request.getText()));
        result.setFilteredText(sensitiveWordService.filter(request.getText()));
        return R.ok(result);
    }

    @Data
    public static class AddRequest {
        @NotBlank
        private String word;
        private String category;
        private Integer severity;
        private String source;
    }

    @Data
    public static class BatchAddRequest {
        @NotEmpty(message = "words 不能为空")
        private List<String> words;
        private String category;
        private Integer severity;
        private String source;
    }

    @Data
    public static class UpdateRequest {
        /** 可选；传入则更新词面（规范化后与去重校验） */
        private String word;
        private String category;
        private Integer severity;
        /** 可选；传入非空则更新来源（与录入/导入字典一致） */
        private String source;
        private Boolean enabled;
    }

    @Data
    public static class CheckRequest {
        @NotBlank
        private String text;
    }

    @Data
    public static class CheckResult {
        private String text;
        private Boolean containsSensitive;
        private java.util.Set<String> sensitiveWords;
        private String filteredText;
    }
}
