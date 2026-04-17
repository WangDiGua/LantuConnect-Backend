package com.lantu.connect.usersettings.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.audit.entity.SensitiveActionAudit;
import com.lantu.connect.audit.mapper.SensitiveActionAuditMapper;
import com.lantu.connect.auth.entity.User;
import com.lantu.connect.auth.mapper.UserMapper;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.security.RedisAuthRateLimiter;
import com.lantu.connect.common.session.SessionTrackerService;
import com.lantu.connect.common.util.SensitiveDataEncryptor;
import com.lantu.connect.gateway.dto.ResourceGrantVO;
import com.lantu.connect.integrationpackage.dto.IntegrationPackageOptionVO;
import com.lantu.connect.integrationpackage.dto.IntegrationPackageUpsertRequest;
import com.lantu.connect.integrationpackage.dto.IntegrationPackageVO;
import com.lantu.connect.integrationpackage.entity.IntegrationPackage;
import com.lantu.connect.integrationpackage.mapper.IntegrationPackageMapper;
import com.lantu.connect.integrationpackage.service.IntegrationPackageMembershipService;
import com.lantu.connect.integrationpackage.service.IntegrationPackageService;
import com.lantu.connect.notification.service.SystemNotificationFacade;
import com.lantu.connect.useractivity.entity.UsageRecord;
import com.lantu.connect.useractivity.mapper.UsageRecordMapper;
import com.lantu.connect.usermgmt.ApiKeyScopes;
import com.lantu.connect.usermgmt.dto.ApiKeyCreateRequest;
import com.lantu.connect.usermgmt.dto.ApiKeyDetailResponse;
import com.lantu.connect.usermgmt.dto.ApiKeyIntegrationPackagePatchRequest;
import com.lantu.connect.usermgmt.dto.ApiKeyResponse;
import com.lantu.connect.usermgmt.entity.ApiKey;
import com.lantu.connect.usermgmt.mapper.ApiKeyMapper;
import com.lantu.connect.usersettings.dto.ApiKeyRevokeRequest;
import com.lantu.connect.usersettings.dto.InvokeEligibilityRequest;
import com.lantu.connect.usersettings.dto.InvokeEligibilityResponse;
import com.lantu.connect.usersettings.dto.UserStatsVO;
import com.lantu.connect.usersettings.dto.WorkspaceSettingsVO;
import com.lantu.connect.usersettings.dto.WorkspaceUpdateRequest;
import com.lantu.connect.usersettings.service.UserSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserSettingsServiceImpl implements UserSettingsService {

    private static final String OWNER_USER = "user";
    private static final String WORKSPACE_KEY_PREFIX = "lantu:usersettings:workspace:";
    private static final long WORKSPACE_TTL_DAYS = 365;

    private final ApiKeyMapper apiKeyMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final UsageRecordMapper usageRecordMapper;
    private final SessionTrackerService sessionTrackerService;
    private final SystemNotificationFacade systemNotificationFacade;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final SensitiveActionAuditMapper sensitiveActionAuditMapper;
    private final RedisAuthRateLimiter redisAuthRateLimiter;
    private final IntegrationPackageMapper integrationPackageMapper;
    private final IntegrationPackageMembershipService integrationPackageMembershipService;
    private final IntegrationPackageService integrationPackageService;
    private final SensitiveDataEncryptor sensitiveDataEncryptor;

    @Override
    public WorkspaceSettingsVO getWorkspace(Long userId) {
        return loadWorkspace(userId);
    }

    @Override
    public void updateWorkspace(Long userId, WorkspaceUpdateRequest request) {
        WorkspaceSettingsVO current = loadWorkspace(userId);
        if (request.getTheme() != null) {
            current.setTheme(request.getTheme());
        }
        if (request.getLocale() != null) {
            current.setLocale(request.getLocale());
        }
        if (request.getLayout() != null) {
            current.setLayout(new LinkedHashMap<>(request.getLayout()));
        }
        if (request.getPreferences() != null) {
            Map<String, Object> merged = current.getPreferences() != null
                    ? new LinkedHashMap<>(current.getPreferences())
                    : new LinkedHashMap<>();
            merged.putAll(request.getPreferences());
            merged.putIfAbsent("userId", userId);
            current.setPreferences(merged);
        }
        storeWorkspace(userId, current);
    }

    @Override
    public List<ApiKey> listApiKeys(Long userId) {
        return apiKeyMapper.selectList(new LambdaQueryWrapper<ApiKey>()
                .eq(ApiKey::getOwnerType, OWNER_USER)
                .eq(ApiKey::getOwnerId, String.valueOf(userId))
                .orderByDesc(ApiKey::getCreateTime));
    }

    @Override
    public List<IntegrationPackageOptionVO> listActiveIntegrationPackages(Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Authentication required");
        }
        return integrationPackageService.listOwnedForUser(userId);
    }

    @Override
    public IntegrationPackageVO getOwnedIntegrationPackage(Long userId, String packageId) {
        return integrationPackageService.getOwnedByUser(packageId, userId);
    }

    @Override
    public IntegrationPackageVO createOwnedIntegrationPackage(Long userId, IntegrationPackageUpsertRequest request) {
        return integrationPackageService.createOwnedByUser(userId, request);
    }

    @Override
    public IntegrationPackageVO updateOwnedIntegrationPackage(Long userId, String packageId, IntegrationPackageUpsertRequest request) {
        return integrationPackageService.updateOwnedByUser(packageId, userId, request);
    }

    @Override
    public void deleteOwnedIntegrationPackage(Long userId, String packageId) {
        integrationPackageService.deleteOwnedByUser(packageId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiKeyResponse createApiKey(Long userId, ApiKeyCreateRequest request) {
        String plain = "sk_" + UUID.randomUUID().toString().replace("-", "");
        ApiKey entity = new ApiKey();
        entity.setName(request.getName());
        entity.setScopes(ApiKeyScopes.defaultIfUnspecified(request.getScopes()));
        entity.setKeyHash(sha256Hex(plain));
        entity.setSecretCiphertext(sensitiveDataEncryptor.encrypt(plain));
        String prefix = plain.length() > 16 ? plain.substring(0, 16) : plain;
        entity.setPrefix(prefix);
        entity.setMaskedKey(prefix.length() > 4 ? prefix.substring(0, 4) + "****" : "****");
        entity.setExpiresAt(request.getExpiresAt());
        entity.setStatus("active");
        entity.setOwnerType(OWNER_USER);
        entity.setOwnerId(String.valueOf(userId));
        entity.setCreatedBy(String.valueOf(userId));
        if (StringUtils.hasText(request.getIntegrationPackageId())) {
            String packageId = request.getIntegrationPackageId().trim();
            IntegrationPackage pkg = integrationPackageMapper.selectById(packageId);
            assertUserOwnsActivePackage(userId, pkg);
            entity.setIntegrationPackageId(packageId);
        }
        apiKeyMapper.insert(entity);
        systemNotificationFacade.notifyApiKeyChanged(userId, entity.getId(), entity.getName(), true);
        return ApiKeyResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .scopes(entity.getScopes())
                .secretPlain(plain)
                .expiresAt(entity.getExpiresAt())
                .revoked(!"active".equalsIgnoreCase(entity.getStatus()))
                .integrationPackageId(entity.getIntegrationPackageId())
                .build();
    }

    @Override
    public ApiKeyDetailResponse getApiKeyDetail(Long userId, String apiKeyId) {
        ApiKey key = apiKeyMapper.selectById(apiKeyId);
        if (key == null || !OWNER_USER.equals(key.getOwnerType())
                || !String.valueOf(userId).equals(key.getOwnerId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "API key not found");
        }
        return buildApiKeyDetailResponse(key);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void patchApiKeyIntegrationPackage(Long userId, String apiKeyId, ApiKeyIntegrationPackagePatchRequest request) {
        if (!StringUtils.hasText(apiKeyId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "API key id is required");
        }
        ApiKey key = apiKeyMapper.selectById(apiKeyId.trim());
        if (key == null || !OWNER_USER.equals(key.getOwnerType())
                || !String.valueOf(userId).equals(key.getOwnerId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "API key not found");
        }
        String previousPackageId = key.getIntegrationPackageId();
        String raw = request != null ? request.getIntegrationPackageId() : null;
        if (!StringUtils.hasText(raw)) {
            key.setIntegrationPackageId(null);
        } else {
            String packageId = raw.trim();
            IntegrationPackage pkg = integrationPackageMapper.selectById(packageId);
            assertUserOwnsActivePackage(userId, pkg);
            key.setIntegrationPackageId(packageId);
        }
        apiKeyMapper.updateById(key);
        if (StringUtils.hasText(previousPackageId)) {
            integrationPackageMembershipService.evict(previousPackageId);
        }
        if (StringUtils.hasText(key.getIntegrationPackageId())) {
            integrationPackageMembershipService.evict(key.getIntegrationPackageId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteApiKey(Long userId, String apiKeyId) {
        ApiKey key = apiKeyMapper.selectById(apiKeyId);
        if (key == null || !OWNER_USER.equals(key.getOwnerType())
                || !String.valueOf(userId).equals(key.getOwnerId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "API key not found");
        }
        key.setStatus("revoked");
        apiKeyMapper.updateById(key);
        systemNotificationFacade.notifyApiKeyChanged(userId, key.getId(), key.getName(), false);
    }

    @Override
    public List<ResourceGrantVO> listResourceGrantsForApiKey(Long userId, String apiKeyId, String resourceType) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Authentication required");
        }
        if (!StringUtils.hasText(apiKeyId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "API key id is required");
        }
        ApiKey key = apiKeyMapper.selectById(apiKeyId.trim());
        if (key == null || !OWNER_USER.equalsIgnoreCase(key.getOwnerType())
                || !String.valueOf(userId).equals(String.valueOf(key.getOwnerId()).trim())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "API key not found");
        }
        return List.of();
    }

    @Override
    public InvokeEligibilityResponse invokeEligibilityForApiKey(Long userId, String apiKeyId, InvokeEligibilityRequest request) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Authentication required");
        }
        if (!StringUtils.hasText(apiKeyId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "API key id is required");
        }
        ApiKey key = apiKeyMapper.selectById(apiKeyId.trim());
        if (key == null || !OWNER_USER.equalsIgnoreCase(key.getOwnerType())
                || !String.valueOf(userId).equals(String.valueOf(key.getOwnerId()).trim())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "API key not found");
        }
        if (!"active".equalsIgnoreCase(key.getStatus())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "API key is not active");
        }
        String resourceType = request.getResourceType().trim().toLowerCase(Locale.ROOT);
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (String rawResourceId : request.getResourceIds()) {
            if (!StringUtils.hasText(rawResourceId)) {
                continue;
            }
            String trimmed = rawResourceId.trim();
            Long resourceId;
            try {
                resourceId = Long.valueOf(trimmed);
            } catch (NumberFormatException e) {
                result.put(trimmed, false);
                continue;
            }
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(1) FROM t_resource
                    WHERE deleted = 0 AND resource_type = ? AND id = ? AND status = 'published'
                    """, Integer.class, resourceType, resourceId);
            result.put(String.valueOf(resourceId), count != null && count > 0);
        }
        return InvokeEligibilityResponse.builder().byResourceId(result).build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeApiKey(Long userId, String apiKeyId, ApiKeyRevokeRequest request, String clientIp) {
        ApiKey key = assertOwnedApiKeyAfterCredentialCheck(userId, apiKeyId, request, clientIp, "api_key_revoke");
        key.setStatus("revoked");
        apiKeyMapper.updateById(key);
        systemNotificationFacade.notifyApiKeyChanged(userId, key.getId(), key.getName(), false);
        insertAudit(userId, "api_key_revoke", apiKeyId, true, null, clientIp);
    }

    @Override
    public UserStatsVO getStats(Long userId) {
        long agents = countResourceByTypeAndCreator("agent", userId);
        long skills = countResourceByTypeAndCreator("skill", userId);
        long usage = usageRecordMapper.selectCount(
                new LambdaQueryWrapper<UsageRecord>().eq(UsageRecord::getUserId, userId));
        Long bytes = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(ext.file_size),0) FROM t_resource r JOIN t_resource_dataset_ext ext ON r.id = ext.resource_id "
                        + "WHERE r.deleted = 0 AND r.resource_type = 'dataset' AND r.created_by = ?",
                Long.class,
                userId);
        long storageMb = bytes != null ? Math.max(0L, bytes / (1024L * 1024L)) : 0L;
        long activeSessions = sessionTrackerService.getActiveSessionCount(userId);
        return UserStatsVO.builder()
                .totalAgents(agents)
                .totalWorkflows(skills)
                .totalApiCalls(usage)
                .storageUsedMb(storageMb)
                .activeSessions(activeSessions)
                .period("30d")
                .build();
    }

    private ApiKey assertOwnedApiKeyAfterCredentialCheck(
            Long userId,
            String apiKeyId,
            ApiKeyRevokeRequest request,
            String clientIp,
            String auditActionType) {
        if (request == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Revoke request is required");
        }
        redisAuthRateLimiter.checkApiKeyRevokeByUser(userId);
        ApiKey key = apiKeyMapper.selectById(apiKeyId);
        if (key == null || !OWNER_USER.equals(key.getOwnerType())
                || !String.valueOf(userId).equals(key.getOwnerId())) {
            insertAudit(userId, auditActionType, apiKeyId, false, "API key not found", clientIp);
            throw new BusinessException(ResultCode.NOT_FOUND, "API key not found");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            insertAudit(userId, auditActionType, apiKeyId, false, "User not found", clientIp);
            throw new BusinessException(ResultCode.NOT_FOUND, "User not found");
        }
        try {
            if (!StringUtils.hasText(user.getPasswordHash())) {
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        "No login password is configured for this account");
            }
            if (!StringUtils.hasText(request.getPassword())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "Password is required");
            }
            if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                throw new BusinessException(ResultCode.PASSWORD_ERROR);
            }
        } catch (BusinessException e) {
            String reason = StringUtils.hasText(e.getMessage()) ? e.getMessage() : ("code=" + e.getCode());
            insertAudit(userId, auditActionType, apiKeyId, false, reason, clientIp);
            throw e;
        }
        return key;
    }

    private void insertAudit(Long userId, String actionType, String targetId, boolean success, String failReason, String clientIp) {
        SensitiveActionAudit row = new SensitiveActionAudit();
        row.setUserId(userId);
        row.setActionType(actionType);
        row.setTargetId(targetId);
        row.setSuccess(success ? 1 : 0);
        if (StringUtils.hasText(failReason) && failReason.length() > 512) {
            row.setFailReason(failReason.substring(0, 512));
        } else {
            row.setFailReason(failReason);
        }
        row.setClientIp(clientIp);
        row.setCreatedAt(LocalDateTime.now());
        sensitiveActionAuditMapper.insert(row);
    }

    private long countResourceByTypeAndCreator(String type, Long userId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_resource WHERE deleted = 0 AND resource_type = ? AND created_by = ?",
                Long.class,
                type,
                userId);
        return count == null ? 0L : count;
    }

    private WorkspaceSettingsVO loadWorkspace(Long userId) {
        String raw = stringRedisTemplate.opsForValue().get(WORKSPACE_KEY_PREFIX + userId);
        if (!StringUtils.hasText(raw)) {
            return defaultWorkspace(userId);
        }
        try {
            WorkspaceSettingsVO vo = objectMapper.readValue(raw, WorkspaceSettingsVO.class);
            if (vo.getLayout() == null) {
                vo.setLayout(new LinkedHashMap<>());
            }
            if (vo.getPreferences() == null) {
                vo.setPreferences(new LinkedHashMap<>());
            }
            vo.getPreferences().putIfAbsent("userId", userId);
            if (!StringUtils.hasText(vo.getTheme())) {
                vo.setTheme("system");
            }
            if (!StringUtils.hasText(vo.getLocale())) {
                vo.setLocale("zh-CN");
            }
            return vo;
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse workspace settings from redis, userId={}", userId, e);
            return defaultWorkspace(userId);
        }
    }

    private void storeWorkspace(Long userId, WorkspaceSettingsVO vo) {
        try {
            String json = objectMapper.writeValueAsString(vo);
            stringRedisTemplate.opsForValue().set(
                    WORKSPACE_KEY_PREFIX + userId, json, WORKSPACE_TTL_DAYS, TimeUnit.DAYS);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "Failed to serialize workspace settings");
        }
    }

    private static WorkspaceSettingsVO defaultWorkspace(Long userId) {
        Map<String, Object> preferences = new LinkedHashMap<>();
        preferences.put("userId", userId);
        return WorkspaceSettingsVO.builder()
                .theme("system")
                .locale("zh-CN")
                .layout(new LinkedHashMap<>())
                .preferences(preferences)
                .build();
    }

    private static void assertUserOwnsActivePackage(Long userId, IntegrationPackage pkg) {
        if (pkg == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Integration package not found");
        }
        if (pkg.getOwnerUserId() == null || !userId.equals(pkg.getOwnerUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "Integration package does not belong to current user");
        }
        if (!"active".equalsIgnoreCase(pkg.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Integration package is not active");
        }
    }

    private ApiKeyDetailResponse buildApiKeyDetailResponse(ApiKey key) {
        boolean secretAvailable = StringUtils.hasText(key.getSecretCiphertext());
        String secretPlain = null;
        if (secretAvailable) {
            try {
                secretPlain = sensitiveDataEncryptor.decrypt(key.getSecretCiphertext());
            } catch (RuntimeException e) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "Failed to decrypt API key secret");
            }
        }
        return ApiKeyDetailResponse.builder()
                .id(key.getId())
                .name(key.getName())
                .prefix(key.getPrefix())
                .maskedKey(key.getMaskedKey())
                .scopes(key.getScopes())
                .status(key.getStatus())
                .expiresAt(key.getExpiresAt())
                .lastUsedAt(key.getLastUsedAt())
                .callCount(key.getCallCount())
                .createdBy(key.getCreatedBy())
                .createdByName(key.getCreatedByName())
                .createdAt(key.getCreateTime())
                .integrationPackageId(key.getIntegrationPackageId())
                .secretPlain(secretPlain)
                .secretAvailable(secretAvailable)
                .build();
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
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "SHA-256 is not available");
        }
    }
}
