package com.lantu.connect.gateway.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.integrationpackage.service.IntegrationPackageMembershipService;
import com.lantu.connect.usermgmt.entity.ApiKey;
import com.lantu.connect.usermgmt.mapper.ApiKeyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * API Key 鉴权：按 scope 或集成套餐白名单判定「是否允许对该资源发起 catalog/resolve/invoke 动作」。
 * <p>
 * <strong>不变量</strong>：本层只做授权裁剪；真实网关调用（{@code POST /invoke}、{@code POST /invoke-stream} 等）在
 * {@link com.lantu.connect.gateway.service.impl.UnifiedGatewayServiceImpl} 中<strong>另行</strong>校验资源已发布（{@code published}）
 * 与健康状态（非 {@code down}/{@code disabled}，熔断另见治理逻辑）。即：无论直配 scope 还是绑定集成套餐，均不能绕过「已上线且可调用健康态」约束。
 */
@Service
@RequiredArgsConstructor
public class ApiKeyScopeService {

    private final ApiKeyMapper apiKeyMapper;
    private final IntegrationPackageMembershipService integrationPackageMembershipService;

    public ApiKey authenticateOrNull(String rawApiKey) {
        if (!StringUtils.hasText(rawApiKey)) {
            return null;
        }
        String key = rawApiKey.trim();
        String hash = sha256Hex(key);
        ApiKey row = apiKeyMapper.selectOne(new LambdaQueryWrapper<ApiKey>().eq(ApiKey::getKeyHash, hash));
        if (row == null || !"active".equalsIgnoreCase(row.getStatus())) {
            // 浏览器端常在全局 Header 里携带历史 Key；与 JWT 并存时按「未提供 Key」处理，由目录层走登录态。
            return null;
        }
        if (row.getExpiresAt() != null && row.getExpiresAt().isBefore(LocalDateTime.now())) {
            return null;
        }
        return row;
    }

    public void ensureCatalogAllowed(ApiKey apiKey, String resourceType, String resourceId) {
        ensureAllowed(apiKey, "catalog", resourceType, resourceId);
    }

    public void ensureResolveAllowed(ApiKey apiKey, String resourceType, String resourceId) {
        ensureAllowed(apiKey, "resolve", resourceType, resourceId);
    }

    public void ensureInvokeAllowed(ApiKey apiKey, String resourceType, String resourceId) {
        ensureAllowed(apiKey, "invoke", resourceType, resourceId);
    }

    public boolean canCatalog(ApiKey apiKey, String resourceType, String resourceId) {
        return canAccess(apiKey, "catalog", resourceType, resourceId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markUsed(ApiKey apiKey) {
        if (apiKey == null || !StringUtils.hasText(apiKey.getId())) {
            return;
        }
        // 禁止 updateById(完整实体)：会把认证时加载的 status 等字段写回库，
        // 若在调用过程中密钥已被撤销，会把 revoked 误改回 active。
        LocalDateTime now = LocalDateTime.now();
        apiKeyMapper.update(
                null,
                new LambdaUpdateWrapper<ApiKey>()
                        .eq(ApiKey::getId, apiKey.getId())
                        .set(ApiKey::getLastUsedAt, now)
                        .setSql("call_count = IFNULL(call_count, 0) + 1"));
        apiKey.setLastUsedAt(now);
        apiKey.setCallCount((apiKey.getCallCount() == null ? 0L : apiKey.getCallCount()) + 1L);
    }

    private void ensureAllowed(ApiKey apiKey, String action, String resourceType, String resourceId) {
        if (!canAccess(apiKey, action, resourceType, resourceId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "API Key scope 不允许访问该资源");
        }
    }

    private boolean canAccess(ApiKey apiKey, String action, String resourceType, String resourceId) {
        if (StringUtils.hasText(apiKey.getIntegrationPackageId())) {
            return integrationPackageMembershipService.contains(apiKey.getIntegrationPackageId(), resourceType, resourceId);
        }
        List<String> scopes = apiKey.getScopes();
        if (scopes == null || scopes.isEmpty()) {
            return false;
        }
        String type = resourceType == null ? "" : resourceType.trim().toLowerCase();
        String rid = resourceId == null ? "" : resourceId.trim();

        if (scopes.contains("*") || scopes.contains(action + ":*")) {
            return true;
        }
        if (StringUtils.hasText(type) && scopes.contains(action + ":type:" + type)) {
            return true;
        }
        if (StringUtils.hasText(type) && StringUtils.hasText(rid) && scopes.contains(action + ":id:" + type + ":" + rid)) {
            return true;
        }

        // Backward-compatible broad scopes from existing role-like semantics.
        if ("agent".equals(type) && scopes.contains("agent:read")) return true;
        if ("skill".equals(type) && scopes.contains("skill:read")) return true;
        if ("app".equals(type) && scopes.contains("app:view")) return true;
        if ("dataset".equals(type) && scopes.contains("dataset:read")) return true;
        if ("mcp".equals(type) && scopes.contains("skill:read")) return true;

        // 集成方常只配 catalog（如 catalog:type:skill）即可列目录；POST /sdk/v1/resolve 原需 resolve:* 才能拉 contextPrompt，
        // 导致门户只能用到列表里的短 description。resolve 对 skill/dataset/agent/mcp 均为读规范，与 catalog 风险同级，故允许 catalog 覆盖 resolve。
        if ("resolve".equals(action)) {
            return canAccess(apiKey, "catalog", resourceType, resourceId);
        }

        return false;
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
