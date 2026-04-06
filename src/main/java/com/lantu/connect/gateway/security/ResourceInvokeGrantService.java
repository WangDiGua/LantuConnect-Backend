package com.lantu.connect.gateway.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.security.CasbinAuthorizationService;
import com.lantu.connect.common.util.ListQueryKeyword;
import com.lantu.connect.common.util.UserDisplayNameResolver;
import com.lantu.connect.gateway.dto.ResourceGrantCreateRequest;
import com.lantu.connect.gateway.dto.ResourceGrantVO;
import com.lantu.connect.gateway.entity.ResourceInvokeGrant;
import com.lantu.connect.gateway.model.ResourceAccessPolicy;
import com.lantu.connect.gateway.mapper.ResourceInvokeGrantMapper;
import com.lantu.connect.notification.service.NotificationEventCodes;
import com.lantu.connect.notification.service.SystemNotificationFacade;
import com.lantu.connect.usermgmt.entity.ApiKey;
import com.lantu.connect.usermgmt.mapper.ApiKeyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ResourceInvokeGrantService {

    private static final Set<String> ALLOWED_ACTIONS = Set.of("catalog", "resolve", "invoke", "*");

    private final JdbcTemplate jdbcTemplate;
    private final ResourceInvokeGrantMapper resourceInvokeGrantMapper;
    private final ApiKeyMapper apiKeyMapper;
    private final CasbinAuthorizationService casbinAuthorizationService;
    private final UserDisplayNameResolver userDisplayNameResolver;
    private final SystemNotificationFacade systemNotificationFacade;

    public void ensureApiKeyGranted(ApiKey apiKey, String action, String resourceType, Long resourceId, Long callerUserId) {
        if (apiKey == null) {
            return;
        }
        if (isPlatformOwner(apiKey)) {
            return;
        }
        GrantContext ctx = loadGrantContext(resourceType, resourceId);
        if (ctx == null || ctx.ownerUserId() == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "资源不存在或不可访问");
        }
        Long ownerUserId = ctx.ownerUserId();
        if (isApiKeyOwnedByUser(apiKey, ownerUserId)) {
            return;
        }
        if (callerUserId != null && callerUserId.equals(ownerUserId)) {
            return;
        }
        if (isGrantWaivedByAccessPolicy(apiKey, ctx)) {
            return;
        }
        if (!hasActiveGrant(apiKey.getId(), action, resourceType, resourceId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "资源调用授权不足，请先由资源拥有者授予调用权限");
        }
    }

    public boolean canCatalog(ApiKey apiKey, String resourceType, Long resourceId, Long callerUserId) {
        try {
            ensureApiKeyGranted(apiKey, "catalog", resourceType, resourceId, callerUserId);
            return true;
        } catch (BusinessException ex) {
            if (ex.getCode() == ResultCode.FORBIDDEN.getCode()) {
                return false;
            }
            throw ex;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Long grant(Long operatorUserId, ResourceGrantCreateRequest request) {
        String resourceType = normalizeType(request.getResourceType());
        Long resourceId = request.getResourceId();
        Long ownerUserId = resolveResourceOwnerUserId(resourceType, resourceId);
        ensureCanManageGrant(operatorUserId, ownerUserId);

        ApiKey grantee = apiKeyMapper.selectById(request.getGranteeApiKeyId());
        if (grantee == null || !"active".equalsIgnoreCase(grantee.getStatus())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "被授权的 API Key 不存在或不可用");
        }

        List<String> actions = normalizeActions(request.getActions());
        LocalDateTime now = LocalDateTime.now();
        ResourceInvokeGrant existing = resourceInvokeGrantMapper.selectOne(
                new LambdaQueryWrapper<ResourceInvokeGrant>()
                        .eq(ResourceInvokeGrant::getResourceType, resourceType)
                        .eq(ResourceInvokeGrant::getResourceId, resourceId)
                        .eq(ResourceInvokeGrant::getGranteeType, "api_key")
                        .eq(ResourceInvokeGrant::getGranteeId, grantee.getId())
                        .eq(ResourceInvokeGrant::getStatus, "active")
                        .last("LIMIT 1"));
        if (existing != null) {
            existing.setActions(actions);
            existing.setExpiresAt(request.getExpiresAt());
            existing.setGrantedByUserId(operatorUserId);
            existing.setUpdateTime(now);
            resourceInvokeGrantMapper.updateById(existing);
            systemNotificationFacade.notifyResourceGrantChanged(
                    operatorUserId,
                    NotificationEventCodes.RESOURCE_GRANT_UPDATED,
                    resourceType,
                    resourceId,
                    grantee.getId());
            return existing.getId();
        }

        ResourceInvokeGrant grant = new ResourceInvokeGrant();
        grant.setResourceType(resourceType);
        grant.setResourceId(resourceId);
        grant.setGranteeType("api_key");
        grant.setGranteeId(grantee.getId());
        grant.setActions(actions);
        grant.setStatus("active");
        grant.setGrantedByUserId(operatorUserId);
        grant.setExpiresAt(request.getExpiresAt());
        grant.setCreateTime(now);
        grant.setUpdateTime(now);
        resourceInvokeGrantMapper.insert(grant);
        systemNotificationFacade.notifyResourceGrantChanged(
                operatorUserId,
                NotificationEventCodes.RESOURCE_GRANT_UPDATED,
                resourceType,
                resourceId,
                grantee.getId());
        return grant.getId();
    }

    /**
     * 按资源 + 被授权 API Key 查找当前生效的 grant 主键（用于历史工单未写入 created_grant_id 时的兜底撤销）。
     */
    public Long findActiveGrantId(String resourceType, Long resourceId, String granteeApiKeyId) {
        if (!StringUtils.hasText(granteeApiKeyId) || resourceId == null) {
            return null;
        }
        String rt = normalizeType(resourceType);
        ResourceInvokeGrant row = resourceInvokeGrantMapper.selectOne(
                new LambdaQueryWrapper<ResourceInvokeGrant>()
                        .eq(ResourceInvokeGrant::getResourceType, rt)
                        .eq(ResourceInvokeGrant::getResourceId, resourceId)
                        .eq(ResourceInvokeGrant::getGranteeType, "api_key")
                        .eq(ResourceInvokeGrant::getGranteeId, granteeApiKeyId.trim())
                        .eq(ResourceInvokeGrant::getStatus, "active")
                        .last("LIMIT 1"));
        return row != null ? row.getId() : null;
    }

    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long operatorUserId, Long grantId) {
        ResourceInvokeGrant grant = resourceInvokeGrantMapper.selectById(grantId);
        if (grant == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "授权记录不存在");
        }
        Long ownerUserId = resolveResourceOwnerUserId(grant.getResourceType(), grant.getResourceId());
        ensureCanManageGrant(operatorUserId, ownerUserId);
        grant.setStatus("revoked");
        grant.setUpdateTime(LocalDateTime.now());
        resourceInvokeGrantMapper.updateById(grant);
        systemNotificationFacade.notifyResourceGrantChanged(
                operatorUserId,
                NotificationEventCodes.RESOURCE_GRANT_REVOKED,
                grant.getResourceType(),
                grant.getResourceId(),
                grant.getGranteeId());
    }

    /**
     * 授权申请工单审批：资源 owner、全平台审核员(reviewer)、platform_admin/admin（与 Grant 管理一致）。
     */
    public void ensureMayReviewGrantApplication(Long operatorUserId, String resourceType, Long resourceId) {
        GrantContext ctx = loadGrantContext(resourceType, resourceId);
        if (ctx == null || ctx.ownerUserId() == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "资源不存在");
        }
        ensureCanManageGrant(operatorUserId, ctx.ownerUserId());
    }

    /**
     * 审核流发布（testing→published）：与 {@link #ensureCanManageGrant} 同款（owner、reviewer、platform_admin/admin）。
     */
    public void ensureMayPublishAuditedResource(Long operatorUserId, String resourceType, Long resourceId) {
        if (operatorUserId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未认证用户无法发布");
        }
        String type = normalizeType(resourceType);
        Long ownerUserId = resolveResourceOwnerUserId(type, resourceId);
        if (ownerUserId == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "资源不存在");
        }
        ensureCanManageGrant(operatorUserId, ownerUserId);
    }

    public List<ResourceGrantVO> listByResource(Long operatorUserId, String resourceType, Long resourceId, String keyword) {
        String normalizedType = normalizeType(resourceType);
        Long ownerUserId = resolveResourceOwnerUserId(normalizedType, resourceId);
        ensureCanManageGrant(operatorUserId, ownerUserId);
        LambdaQueryWrapper<ResourceInvokeGrant> q = new LambdaQueryWrapper<ResourceInvokeGrant>()
                .eq(ResourceInvokeGrant::getResourceType, normalizedType)
                .eq(ResourceInvokeGrant::getResourceId, resourceId);
        String kw = ListQueryKeyword.normalize(keyword);
        if (kw != null) {
            q.and(w -> w.like(ResourceInvokeGrant::getGranteeId, kw)
                    .or()
                    .like(ResourceInvokeGrant::getGranteeType, kw));
        }
        q.orderByDesc(ResourceInvokeGrant::getUpdateTime);
        List<ResourceInvokeGrant> grants = resourceInvokeGrantMapper.selectList(q);
        Map<Long, String> names = userDisplayNameResolver.resolveDisplayNames(
                grants.stream().map(ResourceInvokeGrant::getGrantedByUserId).toList());
        return grants
                .stream()
                .map(grant -> ResourceGrantVO.builder()
                        .id(grant.getId())
                        .resourceType(grant.getResourceType())
                        .resourceId(grant.getResourceId())
                        .granteeType(grant.getGranteeType())
                        .granteeId(grant.getGranteeId())
                        .actions(grant.getActions())
                        .status(grant.getStatus())
                        .grantedByUserId(grant.getGrantedByUserId())
                        .grantedByName(names.get(grant.getGrantedByUserId()))
                        .expiresAt(grant.getExpiresAt())
                        .createTime(grant.getCreateTime())
                        .updateTime(grant.getUpdateTime())
                        .build())
                .toList();
    }

    private void ensureCanManageGrant(Long operatorUserId, Long ownerUserId) {
        if (operatorUserId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未认证用户无法管理授权");
        }
        if (ownerUserId != null && ownerUserId.equals(operatorUserId)) {
            return;
        }
        if (casbinAuthorizationService.hasAnyRole(operatorUserId, new String[]{"platform_admin", "admin"})) {
            return;
        }
        if (casbinAuthorizationService.hasAnyRole(operatorUserId, new String[]{"reviewer"})) {
            return;
        }
        throw new BusinessException(ResultCode.FORBIDDEN, "仅资源拥有者或管理员可管理授权");
    }

    private boolean hasActiveGrant(String apiKeyId, String action, String resourceType, Long resourceId) {
        LocalDateTime now = LocalDateTime.now();
        List<ResourceInvokeGrant> grants = resourceInvokeGrantMapper.selectList(
                new LambdaQueryWrapper<ResourceInvokeGrant>()
                        .eq(ResourceInvokeGrant::getResourceType, normalizeType(resourceType))
                        .eq(ResourceInvokeGrant::getResourceId, resourceId)
                        .eq(ResourceInvokeGrant::getGranteeType, "api_key")
                        .eq(ResourceInvokeGrant::getGranteeId, apiKeyId)
                        .eq(ResourceInvokeGrant::getStatus, "active"));
        if (grants.isEmpty()) {
            return false;
        }
        String grantAction = grantActionForStoredGrant(action);
        return grants.stream().anyMatch(grant -> {
            if (grant.getExpiresAt() != null && !grant.getExpiresAt().isAfter(now)) {
                return false;
            }
            List<String> actions = grant.getActions();
            return actions != null && (actions.contains("*") || actions.contains(grantAction));
        });
    }

    /**
     * 与 {@code t_resource_invoke_grant.actions} 对齐：目录详情使用 catalog_read，存库多为 catalog。
     */
    private static String grantActionForStoredGrant(String action) {
        String a = normalizeAction(action);
        if ("catalog_read".equals(a)) {
            return "catalog";
        }
        return a;
    }

    private GrantContext loadGrantContext(String resourceType, Long resourceId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                        SELECT created_by, access_policy FROM t_resource
                        WHERE deleted = 0 AND resource_type = ? AND id = ? LIMIT 1
                        """,
                normalizeType(resourceType), resourceId);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        Object createdBy = row.get("created_by");
        Long ownerUserId = null;
        if (createdBy != null) {
            try {
                ownerUserId = Long.valueOf(String.valueOf(createdBy));
            } catch (NumberFormatException ignored) {
                ownerUserId = null;
            }
        }
        ResourceAccessPolicy policy = ResourceAccessPolicy.fromStored(row.get("access_policy"));
        return new GrantContext(ownerUserId, policy);
    }

    /**
     * {@code open_platform}：任意已通过上层 scope 校验的非平台 Key 免 Grant。
     * {@code open_org}：仅用户 Key，且 Key 所属用户与资源 owner 的 menuId 一致时免 Grant。
     */
    private boolean isGrantWaivedByAccessPolicy(ApiKey apiKey, GrantContext ctx) {
        return switch (ctx.accessPolicy()) {
            case OPEN_PLATFORM -> true;
            case OPEN_ORG -> openOrgGrantWaived(apiKey, ctx.ownerUserId());
            case GRANT_REQUIRED -> false;
        };
    }

    /** 以 API Key 所属用户的 menuId 与资源 owner 对齐（非 user 归属 Key 不适用 open_org）。 */
    private boolean openOrgGrantWaived(ApiKey apiKey, Long ownerUserId) {
        if (ownerUserId == null || apiKey == null) {
            return false;
        }
        if (!"user".equalsIgnoreCase(apiKey.getOwnerType()) || !StringUtils.hasText(apiKey.getOwnerId())) {
            return false;
        }
        Long keyUserId;
        try {
            keyUserId = Long.valueOf(apiKey.getOwnerId().trim());
        } catch (NumberFormatException e) {
            return false;
        }
        Long ownerMenu = casbinAuthorizationService.userDepartmentMenuId(ownerUserId);
        Long keyMenu = casbinAuthorizationService.userDepartmentMenuId(keyUserId);
        return ownerMenu != null && ownerMenu.equals(keyMenu);
    }

    private Long resolveResourceOwnerUserId(String resourceType, Long resourceId) {
        GrantContext ctx = loadGrantContext(resourceType, resourceId);
        return ctx == null ? null : ctx.ownerUserId();
    }

    private record GrantContext(Long ownerUserId, ResourceAccessPolicy accessPolicy) {
    }

    private static List<String> normalizeActions(List<String> actions) {
        List<String> normalized = actions.stream()
                .map(ResourceInvokeGrantService::normalizeAction)
                .distinct()
                .toList();
        boolean invalid = normalized.stream().anyMatch(action -> !ALLOWED_ACTIONS.contains(action));
        if (invalid) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "actions 仅支持 catalog/resolve/invoke/*");
        }
        return normalized;
    }

    private static String normalizeType(String type) {
        if (!StringUtils.hasText(type)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "resourceType 不能为空");
        }
        return type.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeAction(String action) {
        if (!StringUtils.hasText(action)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "action 不能为空");
        }
        return action.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isPlatformOwner(ApiKey apiKey) {
        return "platform".equalsIgnoreCase(apiKey.getOwnerType())
                || "system".equalsIgnoreCase(apiKey.getOwnerType());
    }

    private static boolean isApiKeyOwnedByUser(ApiKey apiKey, Long userId) {
        if (!"user".equalsIgnoreCase(apiKey.getOwnerType())) {
            return false;
        }
        if (userId == null || !StringUtils.hasText(apiKey.getOwnerId())) {
            return false;
        }
        return String.valueOf(userId).equals(apiKey.getOwnerId().trim());
    }
}
