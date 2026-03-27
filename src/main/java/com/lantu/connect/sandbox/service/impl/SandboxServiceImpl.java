package com.lantu.connect.sandbox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.gateway.dto.InvokeRequest;
import com.lantu.connect.gateway.dto.InvokeResponse;
import com.lantu.connect.gateway.security.ApiKeyScopeService;
import com.lantu.connect.gateway.service.UnifiedGatewayService;
import com.lantu.connect.sandbox.dto.SandboxSessionCreateRequest;
import com.lantu.connect.sandbox.dto.SandboxSessionVO;
import com.lantu.connect.sandbox.entity.SandboxSession;
import com.lantu.connect.sandbox.mapper.SandboxSessionMapper;
import com.lantu.connect.sandbox.service.SandboxService;
import com.lantu.connect.usermgmt.entity.ApiKey;
import com.lantu.connect.usermgmt.mapper.ApiKeyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SandboxServiceImpl implements SandboxService {

    private static final Set<String> ALLOWED_RESOURCE_TYPES = Set.of("agent", "skill", "mcp", "app", "dataset");

    private final SandboxSessionMapper sandboxSessionMapper;
    private final ApiKeyScopeService apiKeyScopeService;
    private final ApiKeyMapper apiKeyMapper;
    private final UnifiedGatewayService unifiedGatewayService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SandboxSessionVO createSession(Long userId, String apiKeyRaw, SandboxSessionCreateRequest request) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "创建沙箱会话必须提供 X-User-Id");
        }
        ApiKey apiKey = apiKeyScopeService.authenticateOrNull(apiKeyRaw);
        if (apiKey == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "创建沙箱会话必须提供 X-Api-Key");
        }

        int ttlMinutes = clampOrDefault(request == null ? null : request.getTtlMinutes(), 30, 5, 240);
        int maxCalls = clampOrDefault(request == null ? null : request.getMaxCalls(), 100, 1, 5000);
        int maxTimeoutSec = clampOrDefault(request == null ? null : request.getMaxTimeoutSec(), 30, 1, 120);
        List<String> resourceTypes = normalizeTypes(request == null ? null : request.getAllowedResourceTypes());

        SandboxSession session = new SandboxSession();
        session.setSessionToken(UUID.randomUUID().toString().replace("-", ""));
        session.setOwnerUserId(userId);
        session.setApiKeyId(apiKey.getId());
        session.setApiKeyPrefix(apiKey.getPrefix());
        session.setStatus("active");
        session.setAllowedResourceTypes(resourceTypes);
        session.setMaxCalls(maxCalls);
        session.setUsedCalls(0);
        session.setMaxTimeoutSec(maxTimeoutSec);
        session.setExpiresAt(LocalDateTime.now().plusMinutes(ttlMinutes));
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        sandboxSessionMapper.insert(session);
        return toVO(session);
    }

    @Override
    public List<SandboxSessionVO> mySessions(Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "查询沙箱会话必须提供 X-User-Id");
        }
        List<SandboxSession> rows = sandboxSessionMapper.selectList(
                new LambdaQueryWrapper<SandboxSession>()
                        .eq(SandboxSession::getOwnerUserId, userId)
                        .orderByDesc(SandboxSession::getCreateTime));
        List<SandboxSessionVO> out = new ArrayList<>();
        for (SandboxSession row : rows) {
            out.add(toVO(row));
        }
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvokeResponse sandboxInvoke(String sessionToken, String traceId, String ip, InvokeRequest request) {
        if (!StringUtils.hasText(sessionToken)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "缺少 X-Sandbox-Token");
        }
        SandboxSession session = sandboxSessionMapper.selectOne(
                new LambdaQueryWrapper<SandboxSession>()
                        .eq(SandboxSession::getSessionToken, sessionToken.trim())
                        .last("LIMIT 1"));
        if (session == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "沙箱会话不存在");
        }
        if (!"active".equalsIgnoreCase(session.getStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "沙箱会话不可用");
        }
        if (session.getExpiresAt() != null && session.getExpiresAt().isBefore(LocalDateTime.now())) {
            session.setStatus("expired");
            session.setUpdateTime(LocalDateTime.now());
            sandboxSessionMapper.updateById(session);
            throw new BusinessException(ResultCode.UNAUTHORIZED, "沙箱会话已过期");
        }
        String type = request.getResourceType() == null ? "" : request.getResourceType().trim().toLowerCase(Locale.ROOT);
        if (!sessionAllowed(session, type)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "沙箱会话不允许调用该资源类型");
        }
        int maxTimeout = session.getMaxTimeoutSec() == null ? 30 : Math.max(1, session.getMaxTimeoutSec());
        if (request.getTimeoutSec() == null || request.getTimeoutSec() > maxTimeout) {
            request.setTimeoutSec(maxTimeout);
        }

        ApiKey apiKey = apiKeyMapper.selectById(session.getApiKeyId());
        if (apiKey == null || !"active".equalsIgnoreCase(apiKey.getStatus())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "沙箱会话绑定的 API Key 已失效");
        }

        int affected = sandboxSessionMapper.incrementUsedCallsIfAllowed(session.getId());
        if (affected == 0) {
            throw new BusinessException(ResultCode.QUOTA_EXCEEDED, "沙箱会话调用次数已耗尽");
        }
        InvokeResponse response = unifiedGatewayService.invoke(
                session.getOwnerUserId(),
                traceId,
                ip,
                request,
                apiKey
        );
        return response;
    }

    private static boolean sessionAllowed(SandboxSession session, String resourceType) {
        List<String> allowed = session.getAllowedResourceTypes();
        if (allowed == null || allowed.isEmpty()) {
            return false;
        }
        return allowed.contains(resourceType);
    }

    private static int clampOrDefault(Integer value, int defaultValue, int min, int max) {
        int x = value == null ? defaultValue : value;
        return Math.max(min, Math.min(max, x));
    }

    private static List<String> normalizeTypes(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>(ALLOWED_RESOURCE_TYPES);
        }
        List<String> out = new ArrayList<>();
        for (String s : raw) {
            if (!StringUtils.hasText(s)) {
                continue;
            }
            String type = s.trim().toLowerCase(Locale.ROOT);
            if (!ALLOWED_RESOURCE_TYPES.contains(type)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的资源类型: " + s);
            }
            if (!out.contains(type)) {
                out.add(type);
            }
        }
        if (out.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "allowedResourceTypes 不能为空");
        }
        return out;
    }

    private static SandboxSessionVO toVO(SandboxSession row) {
        return SandboxSessionVO.builder()
                .sessionToken(row.getSessionToken())
                .apiKeyPrefix(row.getApiKeyPrefix())
                .maxCalls(row.getMaxCalls())
                .usedCalls(row.getUsedCalls())
                .maxTimeoutSec(row.getMaxTimeoutSec())
                .allowedResourceTypes(row.getAllowedResourceTypes())
                .expiresAt(row.getExpiresAt())
                .lastInvokeAt(row.getLastInvokeAt())
                .status(row.getStatus())
                .build();
    }
}
