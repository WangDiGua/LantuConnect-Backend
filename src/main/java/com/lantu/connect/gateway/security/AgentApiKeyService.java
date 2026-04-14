package com.lantu.connect.gateway.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.usermgmt.dto.ApiKeyResponse;
import com.lantu.connect.usermgmt.entity.ApiKey;
import com.lantu.connect.usermgmt.mapper.ApiKeyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentApiKeyService {

    private static final List<String> DEFAULT_SCOPES = List.of("models.read", "chat.invoke", "responses.invoke", "assistants.invoke");

    private final ApiKeyMapper apiKeyMapper;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(rollbackFor = Exception.class)
    public void ensureActiveKeyForAgent(Long agentId, Long operatorUserId) {
        assertAgentResource(agentId);
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_api_key WHERE owner_type = 'agent' AND owner_id = ? AND status = 'active'",
                Long.class,
                String.valueOf(agentId));
        if (count != null && count > 0) {
            return;
        }
        issueKey(agentId, operatorUserId);
    }

    @Transactional(rollbackFor = Exception.class)
    public ApiKeyResponse rotate(Long agentId, Long operatorUserId) {
        assertAgentResource(agentId);
        revoke(agentId);
        return issueKey(agentId, operatorUserId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long agentId) {
        assertAgentResource(agentId);
        List<ApiKey> keys = apiKeyMapper.selectList(new LambdaQueryWrapper<ApiKey>()
                .eq(ApiKey::getOwnerType, "agent")
                .eq(ApiKey::getOwnerId, String.valueOf(agentId))
                .eq(ApiKey::getStatus, "active"));
        for (ApiKey key : keys) {
            key.setStatus("revoked");
            apiKeyMapper.updateById(key);
        }
    }

    public List<ApiKey> list(Long agentId) {
        assertAgentResource(agentId);
        return apiKeyMapper.selectList(new LambdaQueryWrapper<ApiKey>()
                .eq(ApiKey::getOwnerType, "agent")
                .eq(ApiKey::getOwnerId, String.valueOf(agentId))
                .orderByDesc(ApiKey::getCreateTime));
    }

    private ApiKeyResponse issueKey(Long agentId, Long operatorUserId) {
        String plain = "nx-sk-" + UUID.randomUUID().toString().replace("-", "");
        String prefix = plain.substring(0, Math.min(16, plain.length()));
        ApiKey entity = new ApiKey();
        entity.setName("agent-" + agentId + "-key");
        entity.setScopes(DEFAULT_SCOPES);
        entity.setKeyHash(sha256Hex(plain));
        entity.setPrefix(prefix);
        entity.setMaskedKey(prefix.substring(0, Math.min(6, prefix.length())) + "****");
        entity.setStatus("active");
        entity.setOwnerType("agent");
        entity.setOwnerId(String.valueOf(agentId));
        entity.setCreatedBy(operatorUserId == null ? "system" : String.valueOf(operatorUserId));
        apiKeyMapper.insert(entity);
        return ApiKeyResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .scopes(entity.getScopes())
                .secretPlain(plain)
                .revoked(false)
                .build();
    }

    private void assertAgentResource(Long agentId) {
        if (agentId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "agentId 不能为空");
        }
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_resource WHERE id = ? AND resource_type = 'agent' AND deleted = 0",
                Integer.class,
                agentId);
        if (exists == null || exists == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "agent 资源不存在");
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
}

