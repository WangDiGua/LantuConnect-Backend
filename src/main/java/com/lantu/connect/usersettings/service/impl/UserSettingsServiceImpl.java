package com.lantu.connect.usersettings.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lantu.connect.useractivity.entity.UsageRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.usermgmt.ApiKeyScopes;
import com.lantu.connect.usermgmt.dto.ApiKeyCreateRequest;
import com.lantu.connect.usermgmt.dto.ApiKeyResponse;
import com.lantu.connect.usermgmt.entity.ApiKey;
import com.lantu.connect.usermgmt.mapper.ApiKeyMapper;
import com.lantu.connect.useractivity.mapper.UsageRecordMapper;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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

    private final ApiKeyMapper apiKeyMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final UsageRecordMapper usageRecordMapper;
    private final SessionTrackerService sessionTrackerService;
    private final SystemNotificationFacade systemNotificationFacade;

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
