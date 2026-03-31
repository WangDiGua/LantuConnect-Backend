package com.lantu.connect.common.geo;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 公网 IP 地理位置（ip-api.com，无密钥；仅接受字面值 IP，避免 DNS 解析导致 SSRF）。
 */
@Slf4j
@Service
public class GeoIpLookupService {

    @Value("${geoip.enabled:true}")
    private boolean enabled;

    @Value("${geoip.timeout-ms:2000}")
    private int timeoutMs;

    /**
     * @return 可读文本，如「深圳市, 广东省, 中国」；不可用则 null
     */
    public String lookup(String ip) {
        if (!enabled || !StringUtils.hasText(ip)) {
            return null;
        }
        ip = ip.trim();
        if (ip.length() > 45) {
            return null;
        }
        if (!looksLikeLiteralNumericIp(ip)) {
            return null;
        }
        try {
            InetAddress addr = InetAddress.getByName(ip);
            if (addr.isAnyLocalAddress()
                    || addr.isLoopbackAddress()
                    || addr.isSiteLocalAddress()
                    || addr.isLinkLocalAddress()
                    || addr.isMulticastAddress()) {
                return "局域网";
            }
            String enc = URLEncoder.encode(addr.getHostAddress(), StandardCharsets.UTF_8);
            String url = "http://ip-api.com/json/" + enc + "?fields=status,message,country,regionName,city&lang=zh-CN";
            String body = HttpRequest.get(url).timeout(timeoutMs).execute().body();
            if (!StringUtils.hasText(body)) {
                return null;
            }
            JSONObject o = JSONUtil.parseObj(body);
            if (!"success".equals(o.getStr("status"))) {
                return null;
            }
            String city = o.getStr("city");
            String region = o.getStr("regionName");
            String country = o.getStr("country");
            StringBuilder sb = new StringBuilder();
            if (StringUtils.hasText(city)) {
                sb.append(city);
            }
            if (StringUtils.hasText(region)) {
                if (!sb.isEmpty()) {
                    sb.append(", ");
                }
                sb.append(region);
            }
            if (StringUtils.hasText(country)) {
                if (!sb.isEmpty()) {
                    sb.append(", ");
                }
                sb.append(country);
            }
            return !sb.isEmpty() ? sb.toString() : null;
        } catch (Exception e) {
            log.debug("GeoIP lookup failed for ip={}: {}", ip, e.getMessage());
            return null;
        }
    }

    /**
     * 仅允许 IPv4 点分十进制或仅含十六进制数字与 {@code : . %} 的 IPv6 字面量，拒绝主机名以免触发解析。
     */
    private static boolean looksLikeLiteralNumericIp(String ip) {
        if (ip.indexOf(':') >= 0) {
            return ip.matches("^[0-9a-fA-F:.%]+$");
        }
        return ip.matches("^(\\d{1,3}\\.){3}\\d{1,3}$");
    }
}
