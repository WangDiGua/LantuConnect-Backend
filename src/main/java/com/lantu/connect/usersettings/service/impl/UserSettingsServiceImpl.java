package com.lantu.connect.usersettings.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lantu.connect.audit.entity.SensitiveActionAudit;
import com.lantu.connect.audit.mapper.SensitiveActionAuditMapper;
import com.lantu.connect.auth.entity.SmsVerifyCode;
import com.lantu.connect.auth.entity.User;
import com.lantu.connect.auth.mapper.SmsVerifyCodeMapper;
import com.lantu.connect.auth.mapper.UserMapper;
import com.lantu.connect.useractivity.entity.UsageRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.security.RedisAuthRateLimiter;
import com.lantu.connect.gateway.dto.ResourceGrantVO;
import com.lantu.connect.gateway.security.ResourceInvokeGrantService;
import com.lantu.connect.usermgmt.ApiKeyScopes;
import com.lantu.connect.usermgmt.dto.ApiKeyCreateRequest;
import com.lantu.connect.usermgmt.dto.ApiKeyResponse;
import com.lantu.connect.usermgmt.entity.ApiKey;
import com.lantu.connect.usermgmt.mapper.ApiKeyMapper;
import com.lantu.connect.useractivity.mapper.UsageRecordMapper;
import com.lantu.connect.usersettings.dto.ApiKeyRevokeRequest;
import com.lantu.connect.usersettings.dto.UserStatsVO;
import com.lantu.connect.usersettings.dto.WorkspaceSettingsVO;
import com.lantu.connect.usersettings.dto.WorkspaceUpdateRequest;
import com.lantu.connect.usersettings.service.UserSettingsService;
import com.lantu.connect.common.session.SessionTrackerService;
import com.lantu.connect.notification.service.SystemNotificationFacade;
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
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 用户设置UserSettings服务实现
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSettingsServiceImpl implements UserSettingsService {

    private static final String OWNER_USER = "user";
    private static final String WORKSPACE_KEY_PREFIX = "lantu:usersettings:workspace:";
    /** 工作区偏好 Redis 过期（天），避免无限堆积；到期后回到默认，可再保存 */
    private static final long WORKSPACE_TTL_DAYS = 365;
    private static final String SMS_PURPOSE_REVOKE_API_KEY = "revoke_api_key";
    private static final String SMS_RATE_PREFIX = "sms:ratelimit:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ApiKeyMapper apiKeyMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final UsageRecordMapper usageRecordMapper;
    private final SessionTrackerService sessionTrackerService;
    private final SystemNotificationFacade systemNotificationFacade;
    private final ResourceInvokeGrantService resourceInvokeGrantService;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final SmsVerifyCodeMapper smsVerifyCodeMapper;
    private final SensitiveActionAuditMapper sensitiveActionAuditMapper;
    private final RedisAuthRateLimiter redisAuthRateLimiter;

    /**
     * 读取工作区偏好：优先 Redis，缺省回退到系统默认模板。
     */
    @Override
    public WorkspaceSettingsVO getWorkspace(Long userId) {
        return loadWorkspace(userId);
    }

    /**
     * 更新工作区偏好：按字段增量合并并持久化到 Redis。
     */
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

    /**
     * 创建用户 API Key：生成明文一次性回传，库内仅保存哈希与掩码。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiKeyResponse createApiKey(Long userId, ApiKeyCreateRequest request) {
        String plain = "sk_" + UUID.randomUUID().toString().replace("-", "");
        ApiKey entity = new ApiKey();
        entity.setName(request.getName());
        // 未指定 scope 时默认全量，避免创建后无法 catalog/resolve/invoke（仅保存 id 误当密钥的客户仍会 401）
        entity.setScopes(ApiKeyScopes.defaultIfUnspecified(request.getScopes()));
        entity.setKeyHash(sha256Hex(plain));
        String prefix = plain.length() > 16 ? plain.substring(0, 16) : plain;
        entity.setPrefix(prefix);
        entity.setMaskedKey(prefix.length() > 4 ? prefix.substring(0, 4) + "****" : "****");
        entity.setExpiresAt(request.getExpiresAt());
        entity.setStatus("active");
        entity.setOwnerType(OWNER_USER);
        entity.setOwnerId(String.valueOf(userId));
        entity.setCreatedBy(String.valueOf(userId));
        apiKeyMapper.insert(entity);
        systemNotificationFacade.notifyApiKeyChanged(userId, entity.getId(), entity.getName(), true);
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
    public void deleteApiKey(Long userId, String apiKeyId) {
        ApiKey key = apiKeyMapper.selectById(apiKeyId);
        if (key == null || !OWNER_USER.equals(key.getOwnerType())
                || !String.valueOf(userId).equals(key.getOwnerId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "API Key 不存在");
        }
        key.setStatus("revoked");
        apiKeyMapper.updateById(key);
        systemNotificationFacade.notifyApiKeyChanged(userId, key.getId(), key.getName(), false);
    }

    @Override
    public List<ResourceGrantVO> listResourceGrantsForApiKey(Long userId, String apiKeyId, String resourceType) {
        ApiKey key = apiKeyMapper.selectById(apiKeyId);
        if (key == null || !OWNER_USER.equals(key.getOwnerType())
                || !String.valueOf(userId).equals(key.getOwnerId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "API Key 不存在");
        }
        String rt = StringUtils.hasText(resourceType) ? resourceType.trim() : null;
        return resourceInvokeGrantService.listActiveGrantsForGranteeApiKey(apiKeyId, rt);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendRevokeApiKeySms(Long userId, String clientIp) {
        redisAuthRateLimiter.checkSendSms(clientIp);
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        String phone = user.getMobile();
        if (!StringUtils.hasText(phone)) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "未绑定手机号，无法接收撤销验证码；请先绑定手机或设置登录密码");
        }
        phone = phone.trim();
        String rateKey = SMS_RATE_PREFIX + phone;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(rateKey))) {
            throw new BusinessException(ResultCode.SMS_RATE_LIMITED);
        }
        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        SmsVerifyCode row = new SmsVerifyCode();
        row.setPhone(phone);
        row.setCode(code);
        row.setPurpose(SMS_PURPOSE_REVOKE_API_KEY);
        row.setStatus("pending");
        row.setExpireTime(LocalDateTime.now().plusMinutes(5));
        smsVerifyCodeMapper.insert(row);
        stringRedisTemplate.opsForValue().set(rateKey, "1", Duration.ofSeconds(60));
        log.info("[SMS mock revoke_api_key] userId={} phone={} code={}", userId, phone, code);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeApiKey(Long userId, String apiKeyId, ApiKeyRevokeRequest request, String clientIp) {
        if (request == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请求体不能为空");
        }
        redisAuthRateLimiter.checkApiKeyRevokeByUser(userId);
        ApiKey key = apiKeyMapper.selectById(apiKeyId);
        if (key == null || !OWNER_USER.equals(key.getOwnerType())
                || !String.valueOf(userId).equals(key.getOwnerId())) {
            insertAudit(userId, apiKeyId, false, "API Key 不存在", clientIp);
            throw new BusinessException(ResultCode.NOT_FOUND, "API Key 不存在");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            insertAudit(userId, apiKeyId, false, "用户不存在", clientIp);
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        boolean hasPassword = StringUtils.hasText(user.getPasswordHash());
        try {
            if (hasPassword) {
                if (!StringUtils.hasText(request.getPassword())) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "请输入登录密码以撤销 API Key");
                }
                if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                    throw new BusinessException(ResultCode.PASSWORD_ERROR);
                }
            } else {
                if (!StringUtils.hasText(request.getSmsCode())) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "账户未设置本地密码，请输入短信验证码");
                }
                if (!StringUtils.hasText(user.getMobile())) {
                    throw new BusinessException(ResultCode.PARAM_ERROR,
                            "未绑定手机号，无法校验撤销；请先绑定手机或设置密码");
                }
                verifySmsForRevoke(user.getMobile().trim(), request.getSmsCode().trim());
            }
        } catch (BusinessException e) {
            String reason = StringUtils.hasText(e.getMessage()) ? e.getMessage() : ("code=" + e.getCode());
            insertAudit(userId, apiKeyId, false, reason, clientIp);
            throw e;
        }
        key.setStatus("revoked");
        apiKeyMapper.updateById(key);
        systemNotificationFacade.notifyApiKeyChanged(userId, key.getId(), key.getName(), false);
        insertAudit(userId, apiKeyId, true, null, clientIp);
    }

    private void verifySmsForRevoke(String phone, String code) {
        SmsVerifyCode row = smsVerifyCodeMapper.selectOne(
                new LambdaQueryWrapper<SmsVerifyCode>()
                        .eq(SmsVerifyCode::getPhone, phone)
                        .eq(SmsVerifyCode::getPurpose, SMS_PURPOSE_REVOKE_API_KEY)
                        .eq(SmsVerifyCode::getStatus, "pending")
                        .gt(SmsVerifyCode::getExpireTime, LocalDateTime.now())
                        .orderByDesc(SmsVerifyCode::getCreateTime)
                        .last("LIMIT 1"));
        if (row == null || !code.equals(row.getCode())) {
            throw new BusinessException(ResultCode.SMS_CODE_ERROR);
        }
        row.setStatus("verified");
        row.setVerifyTime(LocalDateTime.now());
        smsVerifyCodeMapper.updateById(row);
    }

    private void insertAudit(Long userId, String targetId, boolean success, String failReason, String clientIp) {
        SensitiveActionAudit row = new SensitiveActionAudit();
        row.setUserId(userId);
        row.setActionType("api_key_revoke");
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

    /**
     * 用户统计：从统一资源模型与调用日志聚合工作区关键指标。
     */
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

    private long countResourceByTypeAndCreator(String type, Long userId) {
        Long cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_resource WHERE deleted = 0 AND resource_type = ? AND created_by = ?",
                Long.class,
                type,
                userId);
        return cnt == null ? 0L : cnt;
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
            log.warn("解析工作区 Redis 失败 userId={}, 使用默认", userId, e);
            return defaultWorkspace(userId);
        }
    }

    private void storeWorkspace(Long userId, WorkspaceSettingsVO vo) {
        try {
            String json = objectMapper.writeValueAsString(vo);
            stringRedisTemplate.opsForValue().set(
                    WORKSPACE_KEY_PREFIX + userId, json, WORKSPACE_TTL_DAYS, TimeUnit.DAYS);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "工作区设置序列化失败");
        }
    }

    private static WorkspaceSettingsVO defaultWorkspace(Long userId) {
        Map<String, Object> pref = new LinkedHashMap<>();
        pref.put("userId", userId);
        return WorkspaceSettingsVO.builder()
                .theme("system")
                .locale("zh-CN")
                .layout(new LinkedHashMap<>())
                .preferences(pref)
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
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "SHA-256 不可用");
        }
    }
}
