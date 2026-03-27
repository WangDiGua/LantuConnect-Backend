package com.lantu.connect.usersettings.controller;

import com.lantu.connect.common.result.R;
import com.lantu.connect.usermgmt.dto.ApiKeyCreateRequest;
import com.lantu.connect.usermgmt.dto.ApiKeyResponse;
import com.lantu.connect.usermgmt.entity.ApiKey;
import com.lantu.connect.usersettings.dto.UserStatsVO;
import com.lantu.connect.usersettings.dto.WorkspaceSettingsVO;
import com.lantu.connect.usersettings.dto.WorkspaceUpdateRequest;
import com.lantu.connect.usersettings.service.UserSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户设置 UserSettings 控制器
 *
 * @author 王帝
 * @date 2026-03-21
 */
@RestController
@RequestMapping("/user-settings")
@RequiredArgsConstructor
@Validated
public class UserSettingsController {

    private final UserSettingsService userSettingsService;

    @GetMapping("/workspace")
    public R<WorkspaceSettingsVO> getWorkspace(@RequestHeader("X-User-Id") Long userId) {
        return R.ok(userSettingsService.getWorkspace(userId));
    }

    @PutMapping("/workspace")
    public R<Void> updateWorkspace(@RequestHeader("X-User-Id") Long userId, @RequestBody WorkspaceUpdateRequest request) {
        userSettingsService.updateWorkspace(userId, request);
        return R.ok();
    }

    @GetMapping("/api-keys")
    public R<List<ApiKey>> listApiKeys(@RequestHeader("X-User-Id") Long userId) {
        return R.ok(userSettingsService.listApiKeys(userId));
    }

    @PostMapping("/api-keys")
    public R<ApiKeyResponse> createApiKey(@RequestHeader("X-User-Id") Long userId, @Valid @RequestBody ApiKeyCreateRequest request) {
        return R.ok(userSettingsService.createApiKey(userId, request));
    }

    @DeleteMapping("/api-keys/{id}")
    public R<Void> deleteApiKey(@RequestHeader("X-User-Id") Long userId, @PathVariable String id) {
        userSettingsService.deleteApiKey(userId, id);
        return R.ok();
    }

    @GetMapping("/stats")
    public R<UserStatsVO> stats(@RequestHeader("X-User-Id") Long userId) {
        return R.ok(userSettingsService.getStats(userId));
    }
}
