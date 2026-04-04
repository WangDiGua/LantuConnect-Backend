package com.lantu.connect.usermgmt.controller;

import com.lantu.connect.auth.entity.PlatformRole;
import com.lantu.connect.auth.entity.User;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.R;
import com.lantu.connect.common.security.RequirePermission;
import com.lantu.connect.common.security.RequireRole;
import com.lantu.connect.usermgmt.dto.*;
import com.lantu.connect.usermgmt.entity.AccessToken;
import com.lantu.connect.usermgmt.entity.ApiKey;
import com.lantu.connect.usermgmt.service.UserMgmtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理 UserMgmt 控制器
 *
 * @author 王帝
 * @date 2026-03-21
 */
@RestController
@RequestMapping("/user-mgmt")
@RequiredArgsConstructor
@Validated
public class UserMgmtController {

    private final UserMgmtService userMgmtService;

    @GetMapping("/users")
    @RequirePermission("user:read")
    public R<PageResult<User>> listUsers(UserQueryRequest request,
                                         @RequestHeader("X-User-Id") Long operatorId) {
        return R.ok(userMgmtService.listUsers(request, operatorId));
    }

    @PostMapping("/users")
    @RequirePermission("user:create")
    public R<User> createUser(@Valid @RequestBody CreateUserRequest request) {
        return R.ok(userMgmtService.createUser(request));
    }

    @PutMapping("/users/{id}")
    @RequirePermission("user:update")
    public R<Void> updateUser(@PathVariable Long id, @RequestBody UpdateUserRequest request) {
        userMgmtService.updateUser(id, request);
        return R.ok();
    }

    @GetMapping("/users/{id}")
    @RequirePermission("user:read")
    public R<UserDetailVO> getUser(@PathVariable Long id) {
        return R.ok(userMgmtService.getUserDetail(id));
    }

    @DeleteMapping("/users/{id}")
    @RequirePermission("user:delete")
    @RequireRole({"platform_admin"})
    public R<Void> deleteUser(@PathVariable Long id) {
        userMgmtService.deleteUser(id);
        return R.ok();
    }

    @GetMapping("/roles")
    @RequirePermission("role:read")
    public R<List<PlatformRole>> listRoles() {
        return R.ok(userMgmtService.listRoles());
    }

    @PostMapping("/roles")
    @RequirePermission("role:create")
    @RequireRole({"platform_admin"})
    public R<PlatformRole> createRole(@Valid @RequestBody RoleCreateRequest request) {
        return R.ok(userMgmtService.createRole(request));
    }

    @PutMapping("/roles/{id}")
    @RequirePermission("role:update")
    @RequireRole({"platform_admin"})
    public R<Void> updateRole(@PathVariable Long id, @RequestBody RoleUpdateRequest request) {
        userMgmtService.updateRole(id, request);
        return R.ok();
    }

    @DeleteMapping("/roles/{id}")
    @RequirePermission("role:delete")
    @RequireRole({"platform_admin"})
    public R<Void> deleteRole(@PathVariable Long id) {
        userMgmtService.deleteRole(id);
        return R.ok();
    }

    @GetMapping("/api-keys")
    @RequirePermission("apikey:read")
    public R<List<ApiKey>> listApiKeys() {
        return R.ok(userMgmtService.listApiKeys());
    }

    @PostMapping("/api-keys")
    @RequirePermission("apikey:create")
    public R<ApiKeyResponse> createApiKey(@Valid @RequestBody ApiKeyCreateRequest request) {
        return R.ok(userMgmtService.createApiKey(request));
    }

    @PatchMapping("/api-keys/{id}/revoke")
    @RequirePermission("apikey:delete")
    public R<Void> revokeApiKey(@PathVariable String id) {
        userMgmtService.revokeApiKey(id);
        return R.ok();
    }

    @GetMapping("/tokens")
    @RequirePermission("apikey:read")
    public R<PageResult<AccessToken>> listTokens(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int pageSize,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) String status) {
        return R.ok(userMgmtService.pageTokens(page, pageSize, keyword, status));
    }

    @PatchMapping("/tokens/{id}/revoke")
    @RequirePermission("apikey:delete")
    public R<Void> revokeToken(@PathVariable String id) {
        userMgmtService.revokeToken(id);
        return R.ok();
    }

    @GetMapping("/org-tree")
    @RequirePermission("org:read")
    public R<List<OrgNodeVO>> orgTree() {
        return R.ok(userMgmtService.getOrgTree());
    }

    @GetMapping("/orgs/{id}")
    @RequirePermission("org:read")
    public R<OrgNodeVO> getOrg(@PathVariable Long id) {
        return R.ok(userMgmtService.getOrgById(id));
    }

    @PostMapping("/orgs")
    @RequirePermission("org:create")
    @RequireRole({"platform_admin"})
    public R<OrgNodeVO> createOrg(@Valid @RequestBody OrgCreateRequest request) {
        return R.ok(userMgmtService.createOrg(request));
    }

    @PutMapping("/orgs/{id}")
    @RequirePermission("org:update")
    @RequireRole({"platform_admin"})
    public R<Void> updateOrg(@PathVariable Long id, @RequestBody OrgUpdateRequest request) {
        userMgmtService.updateOrg(id, request);
        return R.ok();
    }

    @DeleteMapping("/orgs/{id}")
    @RequirePermission("org:delete")
    @RequireRole({"platform_admin"})
    public R<Void> deleteOrg(@PathVariable Long id) {
        userMgmtService.deleteOrg(id);
        return R.ok();
    }

    @GetMapping("/users/{id}/org")
    @RequirePermission("user:read")
    public R<UserOrgVO> getUserOrg(@PathVariable Long id) {
        return R.ok(userMgmtService.getUserOrg(id));
    }

    @PutMapping("/users/{id}/org")
    @RequirePermission("user:update")
    public R<Void> bindUserOrg(@PathVariable Long id, @Valid @RequestBody UserOrgBindRequest request) {
        userMgmtService.bindUserOrg(id, request);
        return R.ok();
    }

    @DeleteMapping("/users/{id}/org")
    @RequirePermission("user:update")
    public R<Void> unbindUserOrg(@PathVariable Long id) {
        userMgmtService.unbindUserOrg(id);
        return R.ok();
    }

    @GetMapping("/users/{id}/roles")
    @RequirePermission("user:read")
    public R<List<PlatformRole>> getUserRoles(@PathVariable Long id) {
        return R.ok(userMgmtService.getUserRoles(id));
    }

    @PostMapping("/users/{id}/roles")
    @RequirePermission("user:update")
    public R<Void> bindUserRoles(@PathVariable Long id, @Valid @RequestBody UserRoleBindRequest request) {
        userMgmtService.bindUserRoles(id, request);
        return R.ok();
    }

    @PutMapping("/users/{id}/roles")
    @RequirePermission("user:update")
    public R<Void> replaceUserRoles(@PathVariable Long id, @Valid @RequestBody UserRoleReplaceRequest request) {
        userMgmtService.replaceUserRoles(id, request);
        return R.ok();
    }

    @DeleteMapping("/users/{id}/roles/{roleId}")
    @RequirePermission("user:update")
    public R<Void> unbindUserRole(@PathVariable Long id, @PathVariable Long roleId) {
        userMgmtService.unbindUserRole(id, roleId);
        return R.ok();
    }
}
