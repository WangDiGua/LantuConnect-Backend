package com.lantu.connect.usersettings.controller;

import com.lantu.connect.common.result.R;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.web.ClientIpResolver;
import com.lantu.connect.gateway.dto.ResourceGrantVO;
import com.lantu.connect.usermgmt.dto.ApiKeyCreateRequest;
import com.lantu.connect.usermgmt.dto.ApiKeyResponse;
import com.lantu.connect.usermgmt.entity.ApiKey;
import com.lantu.connect.usersettings.dto.ApiKeyRevokeRequest;
import com.lantu.connect.usersettings.dto.UserStatsVO;
import com.lantu.connect.usersettings.dto.WorkspaceSettingsVO;
import com.lantu.connect.usersettings.dto.WorkspaceUpdateRequest;
import com.lantu.connect.usersettings.service.UserSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    private final ClientIpResolver clientIpResolver;

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
    public ResponseEntity<R<Void>> deleteApiKey(@PathVariable String id) {
        return ResponseEntity.status(HttpStatus.GONE).body(R.fail(ResultCode.API_KEY_DELETE_USE_REVOKE));
    }

    @GetMapping("/api-keys/{apiKeyId}/resource-grants")
    public R<List<ResourceGrantVO>> listApiKeyResourceGrants(@RequestHeader("X-User-Id") Long userId,
                                                               @PathVariable String apiKeyId,
                                                               @RequestParam(required = false) String resourceType) {
        return R.ok(userSettingsService.listResourceGrantsForApiKey(userId, apiKeyId, resourceType));
    }

    @PostMapping("/api-keys/revoke/send-sms")
    public R<Void> sendRevokeApiKeySms(@RequestHeader("X-User-Id") Long userId, HttpServletRequest request) {
        userSettingsService.sendRevokeApiKeySms(userId, clientIpResolver.resolve(request));
        return R.ok();
    }

    @PostMapping("/api-keys/{id}/revoke")
    public R<Void> revokeApiKey(@RequestHeader("X-User-Id") Long userId,
                                  @PathVariable String id,
                                  @RequestBody ApiKeyRevokeRequest body,
                                  HttpServletRequest request) {
        userSettingsService.revokeApiKey(userId, id, body, clientIpResolver.resolve(request));
        return R.ok();
    }

    @GetMapping("/stats")
    public R<UserStatsVO> stats(@RequestHeader("X-User-Id") Long userId) {
        return R.ok(userSettingsService.getStats(userId));
    }
}
