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
        Long ownerUserId = resolveResourceOwnerUserId(resourceType, resourceId);
        if (ownerUserId == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "资源不存在或不可访问");
        }
        if (isApiKeyOwnedByUser(apiKey, ownerUserId)) {
            return;
        }
        if (callerUserId != null && callerUserId.equals(ownerUserId)) {
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
        if (!casbinAuthorizationService.canManageOwnerResource(operatorUserId, ownerUserId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅资源拥有者或平台管理员可管理授权");
        }
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
        String normalizedAction = normalizeAction(action);
        return grants.stream().anyMatch(grant -> {
            if (grant.getExpiresAt() != null && !grant.getExpiresAt().isAfter(now)) {
                return false;
            }
            List<String> actions = grant.getActions();
            return actions != null && (actions.contains("*") || actions.contains(normalizedAction));
        });
    }

    private Long resolveResourceOwnerUserId(String resourceType, Long resourceId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT created_by FROM t_resource WHERE deleted = 0 AND resource_type = ? AND id = ? LIMIT 1",
                normalizeType(resourceType), resourceId);
        if (rows.isEmpty()) {
            return null;
        }
        Object createdBy = rows.get(0).get("created_by");
        if (createdBy == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(createdBy));
        } catch (NumberFormatException ex) {
            return null;
        }
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
