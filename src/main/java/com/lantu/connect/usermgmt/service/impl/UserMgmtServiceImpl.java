package com.lantu.connect.usermgmt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lantu.connect.auth.entity.OrgMenu;
import com.lantu.connect.auth.entity.PlatformRole;
import com.lantu.connect.auth.entity.User;
import com.lantu.connect.auth.entity.UserRoleRel;
import com.lantu.connect.auth.mapper.OrgMenuMapper;
import com.lantu.connect.auth.mapper.PlatformRoleMapper;
import com.lantu.connect.auth.mapper.UserMapper;
import com.lantu.connect.auth.mapper.UserRoleRelMapper;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.PageResults;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.util.ListQueryKeyword;
import com.lantu.connect.common.util.UserDisplayNameResolver;
import com.lantu.connect.notification.service.SystemNotificationFacade;
import com.lantu.connect.usermgmt.ApiKeyScopes;
import com.lantu.connect.usermgmt.dto.*;
import com.lantu.connect.usermgmt.entity.AccessToken;
import com.lantu.connect.usermgmt.entity.ApiKey;
import com.lantu.connect.usermgmt.mapper.AccessTokenMapper;
import com.lantu.connect.usermgmt.mapper.ApiKeyMapper;
import com.lantu.connect.usermgmt.service.UserMgmtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 用户管理UserMgmt服务实现
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Service
@RequiredArgsConstructor
public class UserMgmtServiceImpl implements UserMgmtService {

    private final UserMapper userMapper;
    private final PlatformRoleMapper platformRoleMapper;
    private final UserRoleRelMapper userRoleRelMapper;
    private final OrgMenuMapper orgMenuMapper;
    private final ApiKeyMapper apiKeyMapper;
    private final AccessTokenMapper accessTokenMapper;
    private final UserDisplayNameResolver userDisplayNameResolver;
    private final SystemNotificationFacade systemNotificationFacade;

    @Override
    public PageResult<User> listUsers(UserQueryRequest request, Long operatorUserId) {
        Page<User> page = new Page<>(request.getPage(), request.getPageSize());
        if (StringUtils.hasText(request.getSortBy())) {
            boolean asc = "asc".equalsIgnoreCase(request.getSortOrder());
            page.addOrder(asc ? OrderItem.asc(com.baomidou.mybatisplus.core.toolkit.StringUtils.camelToUnderline(request.getSortBy())) : OrderItem.desc(com.baomidou.mybatisplus.core.toolkit.StringUtils.camelToUnderline(request.getSortBy())));
        }
        LambdaQueryWrapper<User> w = new LambdaQueryWrapper<>();
        if (operatorUserId != null && shouldScopeUsersByDeptMenu(operatorUserId)) {
            User op = userMapper.selectById(operatorUserId);
            if (op != null && op.getMenuId() != null) {
                w.eq(User::getMenuId, op.getMenuId());
            }
        }
        if (StringUtils.hasText(request.getStatus()) && !"all".equalsIgnoreCase(request.getStatus().trim())) {
            w.eq(User::getStatus, request.getStatus().trim());
        }
        String kw = ListQueryKeyword.normalize(request.getKeyword());
        if (kw != null) {
            w.and(x -> x.like(User::getUsername, kw)
                    .or()
                    .like(User::getRealName, kw)
                    .or()
                    .like(User::getMobile, kw)
                    .or()
                    .like(User::getMail, kw)
                    .or()
                    .apply("CAST(user_id AS CHAR) LIKE {0}", "%" + kw + "%"));
        }
        Page<User> result = userMapper.selectPage(page, w);
        return PageResults.from(result);
    }

    private boolean shouldScopeUsersByDeptMenu(Long operatorUserId) {
        List<PlatformRole> roles = platformRoleMapper.selectRolesByUserId(operatorUserId);
        boolean dept = roles.stream().anyMatch(r -> "dept_admin".equals(r.getRoleCode()));
        boolean platform = roles.stream().anyMatch(r -> {
            String c = r.getRoleCode();
            return "platform_admin".equals(c) || "admin".equals(c);
        });
        return dept && !platform;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User createUser(CreateUserRequest request) {
        Long cnt = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
        if (cnt != null && cnt > 0) {
            throw new BusinessException(ResultCode.DUPLICATE_NAME, "用户名已存在");
        }
        List<Long> roleIds = resolveRoleIdsFromCreateRequest(request);
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(new BCryptPasswordEncoder(12).encode(request.getPassword()));
        user.setRealName(request.getUsername());
        user.setMail(request.getEmail());
        user.setMobile(request.getPhone());
        user.setSex(0);
        user.setSchoolId(1L);
        user.setRole(0);
        user.setStatus("active");
        userMapper.insert(user);
        replaceUserRolesInternal(user.getUserId(), roleIds);
        return userMapper.selectById(user.getUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(Long id, UpdateUserRequest request) {
        User existing = userMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        String oldStatus = existing.getStatus();
        if (StringUtils.hasText(request.getPassword())) {
            existing.setPasswordHash(new BCryptPasswordEncoder(12).encode(request.getPassword()));
        }
        if (request.getEmail() != null) {
            existing.setMail(request.getEmail());
        }
        if (request.getPhone() != null) {
            existing.setMobile(request.getPhone());
        }
        if (request.getStatus() != null) {
            existing.setStatus(request.getStatus());
        }
        userMapper.updateById(existing);
        if (request.getStatus() != null && !Objects.equals(oldStatus, request.getStatus())) {
            systemNotificationFacade.notifyUserStatusChanged(id, null, oldStatus, request.getStatus());
        }
        if (request.getRoleIds() != null) {
            replaceUserRolesInternal(id, request.getRoleIds());
        } else if (StringUtils.hasText(request.getRole())) {
            PlatformRole role = findRoleByCodeOrName(request.getRole());
            replaceUserRolesInternal(id, List.of(role.getId()));
        }
    }

    @Override
    public UserDetailVO getUserDetail(Long id) {
        User user = ensureUserExists(id);
        List<PlatformRole> roles = platformRoleMapper.selectRolesByUserId(id);
        UserOrgVO org = buildUserOrgVO(user.getMenuId());
        return UserDetailVO.builder()
                .user(user)
                .roles(roles)
                .org(org)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long id) {
        User existing = userMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        userRoleRelMapper.delete(new LambdaQueryWrapper<UserRoleRel>().eq(UserRoleRel::getUserId, id));
        userMapper.deleteById(id);
        systemNotificationFacade.notifyUserDeleted(id, null, existing.getUsername());
    }

    @Override
    public List<PlatformRole> listRoles() {
        return platformRoleMapper.selectList(new LambdaQueryWrapper<>());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlatformRole createRole(RoleCreateRequest request) {
        Long cnt = platformRoleMapper.selectCount(new LambdaQueryWrapper<PlatformRole>().eq(PlatformRole::getRoleCode, request.getCode()));
        if (cnt != null && cnt > 0) {
            throw new BusinessException(ResultCode.DUPLICATE_NAME, "角色编码已存在");
        }
        PlatformRole role = new PlatformRole();
        role.setRoleName(request.getName());
        role.setRoleCode(request.getCode());
        role.setDescription(request.getDescription());
        role.setPermissions(request.getPermissions() != null ? request.getPermissions() : List.of());
        role.setIsSystem(Boolean.FALSE);
        platformRoleMapper.insert(role);
        systemNotificationFacade.notifyRoleChanged(null, role.getId(), role.getRoleCode(), "创建角色");
        return platformRoleMapper.selectById(role.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(Long id, RoleUpdateRequest request) {
        PlatformRole role = platformRoleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在");
        }
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new BusinessException(ResultCode.CANNOT_DELETE_SYSTEM_ROLE, "不能修改系统内置角色");
        }
        if (StringUtils.hasText(request.getName())) {
            role.setRoleName(request.getName());
        }
        if (StringUtils.hasText(request.getCode())) {
            Long cnt = platformRoleMapper.selectCount(
                    new LambdaQueryWrapper<PlatformRole>().eq(PlatformRole::getRoleCode, request.getCode()).ne(PlatformRole::getId, id));
            if (cnt != null && cnt > 0) {
                throw new BusinessException(ResultCode.DUPLICATE_NAME, "角色编码已存在");
            }
            role.setRoleCode(request.getCode());
        }
        if (request.getDescription() != null) {
            role.setDescription(request.getDescription());
        }
        if (request.getPermissions() != null) {
            role.setPermissions(request.getPermissions());
        }
        platformRoleMapper.updateById(role);
        systemNotificationFacade.notifyRoleChanged(null, role.getId(), role.getRoleCode(), "更新角色");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long id) {
        PlatformRole role = platformRoleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在");
        }
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new BusinessException(ResultCode.CANNOT_DELETE_SYSTEM_ROLE, "不能删除系统内置角色");
        }
        userRoleRelMapper.delete(new LambdaQueryWrapper<UserRoleRel>().eq(UserRoleRel::getRoleId, id));
        platformRoleMapper.deleteById(id);
        systemNotificationFacade.notifyRoleChanged(null, role.getId(), role.getRoleCode(), "删除角色");
    }

    @Override
    public List<ApiKey> listApiKeys() {
        List<ApiKey> keys = apiKeyMapper.selectList(new LambdaQueryWrapper<ApiKey>().orderByDesc(ApiKey::getCreateTime));
        enrichApiKeyCreatorNames(keys);
        return keys;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiKeyResponse createApiKey(ApiKeyCreateRequest request) {
        String plain = "sk_" + UUID.randomUUID().toString().replace("-", "");
        ApiKey entity = new ApiKey();
        entity.setName(request.getName());
        entity.setScopes(ApiKeyScopes.defaultIfUnspecified(request.getScopes()));
        entity.setKeyHash(sha256Hex(plain));
        String prefix = plain.length() > 16 ? plain.substring(0, 16) : plain;
        entity.setPrefix(prefix);
        entity.setMaskedKey(prefix.length() > 4 ? prefix.substring(0, 4) + "****" : "****");
        entity.setExpiresAt(request.getExpiresAt());
        entity.setStatus("active");
        entity.setOwnerType("platform");
        entity.setOwnerId("0");
        entity.setCreatedBy("platform");
        apiKeyMapper.insert(entity);
        systemNotificationFacade.notifyApiKeyChanged(0L, entity.getId(), entity.getName(), true);
        return ApiKeyResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .scopes(entity.getScopes())
                .secretPlain(plain)
                .expiresAt(entity.getExpiresAt())
                .revoked(!"active".equalsIgnoreCase(entity.getStatus()))
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeApiKey(String id) {
        ApiKey key = apiKeyMapper.selectById(id);
        if (key == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "API Key 不存在");
        }
        key.setStatus("revoked");
        apiKeyMapper.updateById(key);
        systemNotificationFacade.notifyApiKeyChanged(0L, key.getId(), key.getName(), false);
    }

    @Override
    public PageResult<AccessToken> pageTokens(int page, int pageSize, String keyword, String status) {
        Page<AccessToken> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<AccessToken> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status) && !"all".equalsIgnoreCase(status.trim())) {
            String st = status.trim().toLowerCase(Locale.ROOT);
            if ("expired".equals(st)) {
                LocalDateTime now = LocalDateTime.now();
                w.and(x -> x.eq(AccessToken::getStatus, "expired")
                        .or(x2 -> x2.eq(AccessToken::getStatus, "active").lt(AccessToken::getExpiresAt, now)));
            } else if ("active".equals(st)) {
                w.eq(AccessToken::getStatus, "active").ge(AccessToken::getExpiresAt, LocalDateTime.now());
            } else {
                w.eq(AccessToken::getStatus, st);
            }
        }
        String kw = ListQueryKeyword.normalize(keyword);
        if (kw != null) {
            w.and(x -> x.like(AccessToken::getName, kw)
                    .or()
                    .like(AccessToken::getMaskedToken, kw)
                    .or()
                    .like(AccessToken::getId, kw)
                    .or()
                    .like(AccessToken::getType, kw)
                    .or()
                    .like(AccessToken::getCreatedBy, kw));
        }
        w.orderByDesc(AccessToken::getCreateTime);
        Page<AccessToken> result = accessTokenMapper.selectPage(p, w);
        return PageResults.from(result);
    }

    @Override
    public void revokeToken(String id) {
        AccessToken token = accessTokenMapper.selectById(id);
        if (token == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Token 不存在");
        }
        token.setStatus("revoked");
        accessTokenMapper.updateById(token);
    }

    @Override
    public List<OrgNodeVO> getOrgTree() {
        List<OrgMenu> all = orgMenuMapper.selectList(new LambdaQueryWrapper<>());
        Map<Long, OrgNodeVO> map = new HashMap<>();
        for (OrgMenu m : all) {
            OrgNodeVO vo = new OrgNodeVO();
            vo.setMenuId(m.getMenuId());
            vo.setMenuName(m.getMenuName());
            vo.setMenuParentId(m.getMenuParentId());
            vo.setMenuLevel(m.getMenuLevel());
            vo.setIfXy(m.getIfXy());
            vo.setChildren(new ArrayList<>());
            map.put(m.getMenuId(), vo);
        }
        List<OrgNodeVO> roots = new ArrayList<>();
        for (OrgMenu m : all) {
            OrgNodeVO node = map.get(m.getMenuId());
            Long pid = m.getMenuParentId();
            if (pid == null || pid == 0L || !map.containsKey(pid)) {
                roots.add(node);
            } else {
                map.get(pid).getChildren().add(node);
            }
        }
        return roots;
    }

    @Override
    public OrgNodeVO getOrgById(Long id) {
        OrgMenu org = orgMenuMapper.selectById(id);
        if (org == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "组织不存在");
        }
        OrgNodeVO vo = new OrgNodeVO();
        vo.setMenuId(org.getMenuId());
        vo.setMenuName(org.getMenuName());
        vo.setMenuParentId(org.getMenuParentId());
        vo.setMenuLevel(org.getMenuLevel());
        vo.setIfXy(org.getIfXy());
        vo.setChildren(new ArrayList<>());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrgNodeVO createOrg(OrgCreateRequest request) {
        Long parentId = request.getMenuParentId() == null ? 0L : request.getMenuParentId();
        Integer level = 1;
        if (parentId != 0L) {
            OrgMenu parent = orgMenuMapper.selectById(parentId);
            if (parent == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "父组织不存在");
            }
            level = parent.getMenuLevel() == null ? 2 : parent.getMenuLevel() + 1;
        }
        OrgMenu entity = new OrgMenu();
        entity.setMenuName(request.getMenuName());
        entity.setMenuParentId(parentId);
        entity.setMenuLevel(level);
        entity.setIfXy(request.getIfXy() != null ? request.getIfXy() : 0);
        entity.setHeadCount(0);
        entity.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        orgMenuMapper.insert(entity);
        return getOrgById(entity.getMenuId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOrg(Long id, OrgUpdateRequest request) {
        OrgMenu existing = orgMenuMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "组织不存在");
        }
        if (StringUtils.hasText(request.getMenuName())) {
            existing.setMenuName(request.getMenuName());
        }
        boolean levelChanged = false;
        if (request.getMenuParentId() != null) {
            Long parentId = request.getMenuParentId();
            if (Objects.equals(parentId, id)) {
                throw new BusinessException(ResultCode.CONFLICT, "组织不能挂载到自身");
            }
            Integer level = 1;
            if (parentId != 0L) {
                OrgMenu parent = orgMenuMapper.selectById(parentId);
                if (parent == null) {
                    throw new BusinessException(ResultCode.NOT_FOUND, "父组织不存在");
                }
                if (isDescendant(parentId, id)) {
                    throw new BusinessException(ResultCode.CONFLICT, "不能挂载到子组织下");
                }
                level = parent.getMenuLevel() == null ? 2 : parent.getMenuLevel() + 1;
            }
            levelChanged = !Objects.equals(existing.getMenuLevel(), level);
            existing.setMenuParentId(parentId);
            existing.setMenuLevel(level);
        }
        if (request.getIfXy() != null) {
            existing.setIfXy(request.getIfXy());
        }
        if (request.getSortOrder() != null) {
            existing.setSortOrder(request.getSortOrder());
        }
        orgMenuMapper.updateById(existing);
        if (levelChanged) {
            refreshChildLevels(existing.getMenuId(), existing.getMenuLevel());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOrg(Long id) {
        OrgMenu existing = orgMenuMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "组织不存在");
        }
        if (orgMenuMapper.countChildren(id) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "存在子组织，无法删除");
        }
        Long bindCount = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getMenuId, id));
        if (bindCount != null && bindCount > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "组织下存在用户，无法删除");
        }
        orgMenuMapper.deleteById(id);
    }

    @Override
    public UserOrgVO getUserOrg(Long userId) {
        User user = ensureUserExists(userId);
        return buildUserOrgVO(user.getMenuId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindUserOrg(Long userId, UserOrgBindRequest request) {
        User user = ensureUserExists(userId);
        OrgMenu org = orgMenuMapper.selectById(request.getOrgId());
        if (org == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "组织不存在");
        }
        user.setMenuId(org.getMenuId());
        userMapper.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindUserOrg(Long userId) {
        User user = ensureUserExists(userId);
        user.setMenuId(null);
        userMapper.updateById(user);
    }

    @Override
    public List<PlatformRole> getUserRoles(Long userId) {
        ensureUserExists(userId);
        return platformRoleMapper.selectRolesByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindUserRoles(Long userId, UserRoleBindRequest request) {
        ensureUserExists(userId);
        List<Long> roleIds = normalizeRoleIds(request.getRoleIds());
        ensureRolesExist(roleIds);
        Set<Long> existingRoleIds = new HashSet<>(userRoleRelMapper.selectRoleIdsByUserId(userId));
        for (Long roleId : roleIds) {
            if (existingRoleIds.contains(roleId)) {
                continue;
            }
            UserRoleRel rel = new UserRoleRel();
            rel.setUserId(userId);
            rel.setRoleId(roleId);
            userRoleRelMapper.insert(rel);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceUserRoles(Long userId, UserRoleReplaceRequest request) {
        ensureUserExists(userId);
        replaceUserRolesInternal(userId, request.getRoleIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindUserRole(Long userId, Long roleId) {
        ensureUserExists(userId);
        PlatformRole role = platformRoleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在");
        }
        userRoleRelMapper.delete(new LambdaQueryWrapper<UserRoleRel>()
                .eq(UserRoleRel::getUserId, userId)
                .eq(UserRoleRel::getRoleId, roleId));
    }

    private List<Long> resolveRoleIdsFromCreateRequest(CreateUserRequest request) {
        if (!CollectionUtils.isEmpty(request.getRoleIds())) {
            List<Long> roleIds = normalizeRoleIds(request.getRoleIds());
            ensureRolesExist(roleIds);
            return roleIds;
        }
        if (!StringUtils.hasText(request.getRole())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "角色不能为空");
        }
        PlatformRole role = findRoleByCodeOrName(request.getRole());
        return List.of(role.getId());
    }

    private PlatformRole findRoleByCodeOrName(String roleCodeOrName) {
        PlatformRole role = platformRoleMapper.selectOne(
                new LambdaQueryWrapper<PlatformRole>().eq(PlatformRole::getRoleCode, roleCodeOrName));
        if (role == null) {
            role = platformRoleMapper.selectOne(
                    new LambdaQueryWrapper<PlatformRole>().eq(PlatformRole::getRoleName, roleCodeOrName));
        }
        if (role == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在");
        }
        return role;
    }

    private User ensureUserExists(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private UserOrgVO buildUserOrgVO(Long orgId) {
        if (orgId == null) {
            return null;
        }
        OrgMenu org = orgMenuMapper.selectById(orgId);
        if (org == null) {
            return null;
        }
        return UserOrgVO.builder()
                .menuId(org.getMenuId())
                .menuName(org.getMenuName())
                .menuParentId(org.getMenuParentId())
                .menuLevel(org.getMenuLevel())
                .build();
    }

    private List<Long> normalizeRoleIds(List<Long> roleIds) {
        if (CollectionUtils.isEmpty(roleIds)) {
            return List.of();
        }
        LinkedHashSet<Long> dedup = new LinkedHashSet<>();
        for (Long roleId : roleIds) {
            if (roleId != null) {
                dedup.add(roleId);
            }
        }
        return new ArrayList<>(dedup);
    }

    private void ensureRolesExist(List<Long> roleIds) {
        if (CollectionUtils.isEmpty(roleIds)) {
            return;
        }
        List<PlatformRole> roles = platformRoleMapper.selectBatchIds(roleIds);
        if (roles.size() != roleIds.size()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "存在无效角色");
        }
    }

    private void replaceUserRolesInternal(Long userId, List<Long> roleIds) {
        List<Long> normalized = normalizeRoleIds(roleIds);
        ensureRolesExist(normalized);
        Set<Long> targetIds = new HashSet<>(normalized);
        List<Long> existingRoleIds = userRoleRelMapper.selectRoleIdsByUserId(userId);
        Set<Long> existingSet = new HashSet<>(existingRoleIds);

        for (Long existingId : existingRoleIds) {
            if (!targetIds.contains(existingId)) {
                userRoleRelMapper.delete(new LambdaQueryWrapper<UserRoleRel>()
                        .eq(UserRoleRel::getUserId, userId)
                        .eq(UserRoleRel::getRoleId, existingId));
            }
        }

        for (Long targetId : normalized) {
            if (existingSet.contains(targetId)) {
                continue;
            }
            UserRoleRel rel = new UserRoleRel();
            rel.setUserId(userId);
            rel.setRoleId(targetId);
            userRoleRelMapper.insert(rel);
        }
    }

    private boolean isDescendant(Long candidateId, Long ancestorId) {
        Long current = candidateId;
        int depthGuard = 0;
        while (current != null && current != 0L && depthGuard < 2000) {
            if (Objects.equals(current, ancestorId)) {
                return true;
            }
            OrgMenu node = orgMenuMapper.selectById(current);
            if (node == null) {
                return false;
            }
            current = node.getMenuParentId();
            depthGuard++;
        }
        return false;
    }

    private void refreshChildLevels(Long parentId, Integer parentLevel) {
        List<OrgMenu> children = orgMenuMapper.selectList(
                new LambdaQueryWrapper<OrgMenu>().eq(OrgMenu::getMenuParentId, parentId));
        for (OrgMenu child : children) {
            Integer nextLevel = (parentLevel == null ? 1 : parentLevel) + 1;
            child.setMenuLevel(nextLevel);
            orgMenuMapper.updateById(child);
            refreshChildLevels(child.getMenuId(), nextLevel);
        }
    }

    private static String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "SHA-256 不可用");
        }
    }

    private void enrichApiKeyCreatorNames(List<ApiKey> keys) {
        if (CollectionUtils.isEmpty(keys)) {
            return;
        }
        List<Long> userIds = keys.stream()
                .map(ApiKey::getCreatedBy)
                .map(UserMgmtServiceImpl::parseLong)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, String> names = userDisplayNameResolver.resolveDisplayNames(userIds);
        for (ApiKey key : keys) {
            Long userId = parseLong(key.getCreatedBy());
            if (userId != null) {
                key.setCreatedByName(names.get(userId));
            } else if (StringUtils.hasText(key.getCreatedBy())) {
                key.setCreatedByName(key.getCreatedBy());
            }
        }
    }

    private static Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
