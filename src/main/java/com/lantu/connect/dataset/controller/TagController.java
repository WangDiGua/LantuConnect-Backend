package com.lantu.connect.dataset.controller;

import com.lantu.connect.common.result.R;
import com.lantu.connect.common.security.RequireRole;
import com.lantu.connect.dataset.dto.TagCreateRequest;
import com.lantu.connect.dataset.dto.TagUpdateRequest;
import com.lantu.connect.dataset.entity.Tag;
import com.lantu.connect.dataset.service.TagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 数据集 Tag 控制器
 *
 * @author 王帝
 * @date 2026-03-21
 */
@RestController
@RequestMapping("/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @GetMapping
    public R<List<Tag>> list() {
        return R.ok(tagService.list());
    }

    @PostMapping
    @RequireRole({"platform_admin"})
    public R<Long> create(@Valid @RequestBody TagCreateRequest request) {
        return R.ok(tagService.create(request));
    }

    @PostMapping("/batch")
    @RequireRole({"platform_admin"})
    public R<List<Long>> batchCreate(@Valid @RequestBody List<TagCreateRequest> requests) {
        return R.ok(tagService.batchCreate(requests));
    }

    @PutMapping("/{id}")
    @RequireRole({"platform_admin"})
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody TagUpdateRequest request) {
        tagService.update(id, request);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @RequireRole({"platform_admin"})
    public R<Void> delete(@PathVariable Long id) {
        tagService.delete(id);
        return R.ok();
    }
}
