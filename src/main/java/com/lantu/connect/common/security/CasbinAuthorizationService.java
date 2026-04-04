package com.lantu.connect.common.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lantu.connect.auth.entity.PlatformRole;
import com.lantu.connect.auth.entity.User;
import com.lantu.connect.auth.mapper.PlatformRoleMapper;
import com.lantu.connect.auth.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.casbin.jcasbin.main.Enforcer;
import org.casbin.jcasbin.model.Model;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Casbin 授权门面：
 * - RBAC：用户-角色、角色-权限（以 {@code t_user_role_rel} + 角色 permissions JSON 为准）
 * - 未绑定任何平台角色时，与自助注册一致按 {@code user} 权限集评估（库中须存在 {@code role_code=user}）
 * - ABAC：提供 owner/department 维度辅助判断
 */
@Service
@RequiredArgsConstructor
public class CasbinAuthorizationService {

    private final PlatformRoleMapper platformRoleMapper;
    private final UserMapper userMapper;

    /**
     * 为当前用户构建 Enforcer（含一次角色/策略加载）。高流量路径应对每个请求复用同一实例，避免重复查库与构建模型。
     */
    public Enforcer loadEnforcerForUser(Long userId) {
        return buildEnforcer(userId);
    }

    public boolean hasAnyRole(Long userId, String[] requiredRoles) {
        if (userId == null || requiredRoles == null || requiredRoles.length == 0) {
            return false;
        }
        return hasAnyRole(userId, requiredRoles, loadEnforcerForUser(userId));
    }

    /**
     * 使用已加载的 Enforcer 做角色判断（须与 userId 对应策略一致）。
     */
    public boolean hasAnyRole(Long userId, String[] requiredRoles, Enforcer enforcer) {
        if (userId == null || requiredRoles == null || requiredRoles.length == 0 || enforcer == null) {
            return false;
        }
        String subj = subject(userId);
        return Arrays.stream(requiredRoles)
                .filter(StringUtils::hasText)
                .map(this::normalize)
                .anyMatch(role -> enforcer.enforce(subj, "role", role, "*"));
    }

    public boolean hasPermissions(Long userId, String[] requiredPermissions, RequirePermission.LogicalOperator operator) {
        if (userId == null || requiredPermissions == null || requiredPermissions.length == 0) {
            return false;
        }
        return hasPermissions(userId, requiredPermissions, operator, loadEnforcerForUser(userId));
    }

    public boolean hasPermissions(Long userId, String[] requiredPermissions, RequirePermission.LogicalOperator operator,
                                    Enforcer enforcer) {
        if (userId == null || requiredPermissions == null || requiredPermissions.length == 0 || enforcer == null) {
            return false;
        }
        String subj = subject(userId);
        if (operator == RequirePermission.LogicalOperator.AND) {
            for (String p : requiredPermissions) {
                if (!enforcePermission(enforcer, subj, p)) {
                    return false;
                }
            }
            return true;
        }
        for (String p : requiredPermissions) {
            if (enforcePermission(enforcer, subj, p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * ABAC：资源 owner 或管理员可操作
     */
    public boolean canManageOwnerResource(Long operatorUserId, Long ownerUserId) {
        if (operatorUserId == null) {
            return false;
        }
        if (ownerUserId != null && ownerUserId.equals(operatorUserId)) {
            return true;
        }
        return hasAnyRole(operatorUserId, new String[]{"platform_admin", "reviewer"});
    }

    public Long userDepartmentMenuId(Long userId) {
        if (userId == null) {
            return null;
        }
        User user = userMapper.selectById(userId);
        return user != null ? user.getMenuId() : null;
    }

    /**
     * 与 {@link #buildEnforcer(Long)} 相同来源：用户绑定角色的 permissions JSON 合并（无绑定时回落到 {@code user} 角色）。
     * 字符串经 {@link #normalize} 归一化，与 Casbin 评估一致，供前端菜单与 JWT 主角色展示解耦。
     */
    public List<String> effectivePermissionStrings(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<PlatformRole> roles = new ArrayList<>(platformRoleMapper.selectRolesByUserId(userId));
        if (roles.isEmpty()) {
            PlatformRole endUser = platformRoleMapper.selectOne(
                    new LambdaQueryWrapper<PlatformRole>().eq(PlatformRole::getRoleCode, "user"));
            if (endUser != null) {
                roles.add(endUser);
            }
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (PlatformRole role : roles) {
            List<String> permissions = role.getPermissions();
            if (permissions == null) {
                continue;
            }
            for (String perm : permissions) {
                if (!StringUtils.hasText(perm)) {
                    continue;
                }
                out.add(normalize(perm));
            }
        }
        return new ArrayList<>(out);
    }

    private boolean enforcePermission(Enforcer enforcer, String subject, String permission) {
        if (!StringUtils.hasText(permission)) {
            return false;
        }
        String normalized = normalize(permission);
        String[] parts = normalized.split(":", 2);
        String obj = parts.length == 2 ? parts[0] : normalized;
        String act = parts.length == 2 ? parts[1] : "*";
        return enforcer.enforce(subject, obj, act, "*");
    }

    private Enforcer buildEnforcer(Long userId) {
        Model model = new Model();
        model.loadModelFromText("""
                [request_definition]
                r = sub, obj, act, dom

                [policy_definition]
                p = sub, obj, act, dom

                [role_definition]
                g = _, _

                [policy_effect]
                e = some(where (p.eft == allow))

                [matchers]
                m = g(r.sub, p.sub) && (p.obj == "*" || p.obj == r.obj) && (p.act == "*" || p.act == r.act) && (p.dom == "*" || p.dom == r.dom)
                """);
        Enforcer enforcer = new Enforcer(model);

        String subject = subject(userId);
        List<PlatformRole> roles = new ArrayList<>(platformRoleMapper.selectRolesByUserId(userId));
        // 无 t_user_role_rel 时与自助注册一致：按 end-user（user）权限评估（库中须存在 role_code=user）
        if (roles.isEmpty()) {
            PlatformRole endUser = platformRoleMapper.selectOne(
                    new LambdaQueryWrapper<PlatformRole>().eq(PlatformRole::getRoleCode, "user"));
            if (endUser != null) {
                roles.add(endUser);
            }
        }
        for (PlatformRole role : roles) {
            attachPlatformRole(enforcer, subject, role);
        }
        return enforcer;
    }

    private void attachPlatformRole(Enforcer enforcer, String userSubject, PlatformRole role) {
        String roleCode = normalize(role.getRoleCode());
        if (!StringUtils.hasText(roleCode)) {
            return;
        }
        String roleSubject = "role:" + roleCode;
        enforcer.addRoleForUser(userSubject, roleSubject);
        enforcer.addPermissionForUser(roleSubject, "role", roleCode, "*");
        List<String> permissions = role.getPermissions();
        if (permissions == null) {
            return;
        }
        for (String perm : permissions) {
            if (!StringUtils.hasText(perm)) {
                continue;
            }
            String[] parts = normalize(perm).split(":", 2);
            String obj = parts.length == 2 ? parts[0] : parts[0];
            String act = parts.length == 2 ? parts[1] : "*";
            enforcer.addPermissionForUser(roleSubject, obj, act, "*");
        }
    }

    private static String subject(Long userId) {
        return "user:" + userId;
    }

    private String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }
}

