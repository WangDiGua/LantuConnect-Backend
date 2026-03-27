package com.lantu.connect.common.geo;

import cn.hutool.core.net.NetUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 公网 IP 地理位置（ip-api.com，无密钥；内网 IP 不请求外网）。
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
        if (isPrivateOrLocal(ip)) {
            return "局域网";
        }
        try {
            String enc = URLEncoder.encode(ip, StandardCharsets.UTF_8);
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

    private static boolean isPrivateOrLocal(String ip) {
        if ("127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return true;
        }
        return NetUtil.isInnerIP(ip);
    }
}
