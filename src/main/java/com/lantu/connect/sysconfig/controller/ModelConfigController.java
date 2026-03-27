package com.lantu.connect.sysconfig.controller;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.R;
import com.lantu.connect.common.security.RequireRole;
import com.lantu.connect.sysconfig.dto.ModelConfigCreateRequest;
import com.lantu.connect.sysconfig.dto.ModelConfigQueryRequest;
import com.lantu.connect.sysconfig.dto.ModelConfigUpdateRequest;
import com.lantu.connect.sysconfig.entity.ModelConfig;
import com.lantu.connect.sysconfig.service.ModelConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 系统配置 ModelConfig 控制器
 *
 * @author 王帝
 * @date 2026-03-21
 */
@RestController
@RequestMapping("/system-config/model-configs")
@RequiredArgsConstructor
@RequireRole({"platform_admin"})
public class ModelConfigController {

    private final ModelConfigService modelConfigService;

    @PostMapping
    public R<String> create(@Valid @RequestBody ModelConfigCreateRequest request) {
        return R.ok(modelConfigService.create(request));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable String id, @Valid @RequestBody ModelConfigUpdateRequest request) {
        request.setId(id);
        modelConfigService.update(request);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        modelConfigService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<ModelConfig> get(@PathVariable String id) {
        return R.ok(modelConfigService.getById(id));
    }

    @GetMapping
    public R<PageResult<ModelConfig>> page(ModelConfigQueryRequest request) {
        return R.ok(modelConfigService.page(request));
    }
}
