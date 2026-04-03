package com.lantu.connect.common.web;

import com.lantu.connect.common.config.SecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 解析客户端 IP。仅在反向代理可信时启用 X-Forwarded-For，否则易被伪造。
 * <p>
 * 将 IPv6 环回（如 {@code ::1} / {@code 0:0:0:0:0:0:0:1}）规范为 {@code 127.0.0.1}，便于展示与 Geo 推断；
 * {@code ::ffff:x.x.x.x} 展开为 IPv4 字面量。
 */
@Component
@RequiredArgsConstructor
public class ClientIpResolver {

    private final SecurityProperties securityProperties;

    public String resolve(HttpServletRequest request) {
        String raw;
        if (securityProperties.isTrustProxyForwardedHeaders()) {
            String xff = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(xff)) {
                raw = xff.split(",")[0].trim();
            } else {
                raw = request.getRemoteAddr();
            }
        } else {
            raw = request.getRemoteAddr();
        }
        if (raw == null) {
            return "";
        }
        return normalizeClientIp(raw);
    }

    static String normalizeClientIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return "";
        }
        String t = ip.trim();
        if ("unknown".equalsIgnoreCase(t)) {
            return "";
        }
        try {
            InetAddress addr = InetAddress.getByName(sanitizeHostForInet(t));
            if (addr.isLoopbackAddress()) {
                return "127.0.0.1";
            }
            if (addr instanceof Inet6Address) {
                byte[] b = addr.getAddress();
                if (b.length == 16 && b[10] == (byte) 0xff && b[11] == (byte) 0xff) {
                    return (b[12] & 0xff) + "." + (b[13] & 0xff) + "." + (b[14] & 0xff) + "." + (b[15] & 0xff);
                }
            }
        } catch (UnknownHostException ignored) {
            // 保持原样，避免误伤非标准但可存的字符串
        }
        return t;
    }

    /** {@code InetAddress#getByName} 对带端口或 zone 的字符串需预处理 */
    private static String sanitizeHostForInet(String host) {
        String h = host;
        int pct = h.indexOf('%');
        if (pct >= 0) {
            h = h.substring(0, pct);
        }
        if (h.startsWith("[") && h.contains("]")) {
            int end = h.indexOf(']');
            return h.substring(1, end);
        }
        int colon = h.indexOf(':');
        int lastColon = h.lastIndexOf(':');
        if (colon >= 0 && lastColon > colon && h.chars().filter(c -> c == ':').count() == 1) {
            // IPv4:port
            return h.substring(0, lastColon);
        }
        return h;
    }
}
