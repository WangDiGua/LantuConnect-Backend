package com.lantu.connect.common.web;

import com.lantu.connect.common.config.SecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 解析客户端 IP。仅在反向代理可信时启用 X-Forwarded-For，否则易被伪造。
 */
@Component
@RequiredArgsConstructor
public class ClientIpResolver {

    private final SecurityProperties securityProperties;

    public String resolve(HttpServletRequest request) {
        if (securityProperties.isTrustProxyForwardedHeaders()) {
            String xff = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(xff)) {
                return xff.split(",")[0].trim();
            }
        }
        String addr = request.getRemoteAddr();
        return addr != null ? addr : "";
    }
}
