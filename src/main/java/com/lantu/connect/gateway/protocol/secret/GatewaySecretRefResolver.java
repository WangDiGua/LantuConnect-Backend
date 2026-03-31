package com.lantu.connect.gateway.protocol.secret;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 将 {@code auth_config} 中的 secret 引用解析为明文字符串（仅进程内使用，禁止写入 API 响应）。
 * <ul>
 *   <li>{@code env:NAME} — Spring {@link Environment#getProperty}，再回退 {@code System.getenv(NAME)}</li>
 *   <li>{@code prop:key} — 仅属性，如 {@code prop:lantu.integrations.mcp.xxx}</li>
 * </ul>
 * {@code vault:logicalId}：读取配置属性 {@code lantu.secret.vault.<logicalId>}（可由环境变量注入，便于接外部 Vault 同步任务）。
 */
@Component
public class GatewaySecretRefResolver {

    private final Environment environment;

    public GatewaySecretRefResolver(Environment environment) {
        this.environment = environment;
    }

    public String resolveRequired(String ref) {
        String v = resolve(ref);
        if (!StringUtils.hasText(v)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "secretRef 无法解析或为空: " + ref);
        }
        return v;
    }

    public String resolve(String ref) {
        if (!StringUtils.hasText(ref)) {
            return null;
        }
        String r = ref.trim();
        if (r.toLowerCase().startsWith("env:")) {
            String name = r.substring(4).trim();
            if (!StringUtils.hasText(name)) {
                return null;
            }
            String v = environment.getProperty(name);
            if (StringUtils.hasText(v)) {
                return v;
            }
            return System.getenv(name);
        }
        if (r.toLowerCase().startsWith("prop:")) {
            String key = r.substring(5).trim();
            return StringUtils.hasText(key) ? environment.getProperty(key) : null;
        }
        if (r.toLowerCase().startsWith("vault:")) {
            String id = r.substring(6).trim();
            if (!StringUtils.hasText(id)) {
                return null;
            }
            return environment.getProperty("lantu.secret.vault." + id);
        }
        return null;
    }
}
