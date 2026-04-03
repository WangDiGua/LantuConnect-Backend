package com.lantu.connect.gateway.service;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.gateway.config.SkillPackImportProperties;
import com.lantu.connect.sysconfig.runtime.RuntimeAppConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * 技能包从公网拉取 / Git 克隆前的 SSRF 与主机策略校验。
 */
@Component
@RequiredArgsConstructor
public class SkillPackRemoteUriValidator {

    private final RuntimeAppConfigService runtimeAppConfigService;

    private SkillPackImportProperties p() {
        return runtimeAppConfigService.skillPackImport();
    }

    public void assertHostSuffixPolicyConfigured() {
        if (!p().isRequireAllowedHostSuffixes()) {
            return;
        }
        List<String> s = p().getAllowedHostSuffixes();
        boolean any = s != null && s.stream().anyMatch(x -> x != null && !x.isBlank());
        if (!any) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "已启用 require-allowed-host-suffixes，但未配置有效的 lantu.skill-pack-import.allowed-host-suffixes");
        }
    }

    public void assertUriSafeForRemote(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "url 须包含协议");
        }
        String s = scheme.toLowerCase(Locale.ROOT);
        if (p().isHttpsOnly() && !"https".equals(s)) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "仅允许 https（可在配置 lantu.skill-pack-import.https-only=false 关闭）");
        }
        if (!"https".equals(s) && !"http".equals(s)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "仅支持 http/https");
        }
        String host = uri.getHost();
        if (!StringUtils.hasText(host)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "url 缺少主机名");
        }
        String h = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(h) || h.endsWith(".localhost")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "禁止访问本地主机");
        }
        List<String> suffixes = p().getAllowedHostSuffixes();
        if (suffixes != null && !suffixes.isEmpty()) {
            boolean ok = false;
            for (String suf : suffixes) {
                if (suf == null || suf.isBlank()) {
                    continue;
                }
                String sl = suf.trim().toLowerCase(Locale.ROOT);
                if (h.equals(sl) || h.endsWith("." + sl)) {
                    ok = true;
                    break;
                }
            }
            if (!ok) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "主机不在允许列表（lantu.skill-pack-import.allowed-host-suffixes）");
            }
        }
        try {
            InetAddress[] addrs = InetAddress.getAllByName(host);
            for (InetAddress a : addrs) {
                if (a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()
                        || a.isAnyLocalAddress() || a.isMulticastAddress()) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "禁止访问内网或保留地址");
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "无法解析主机: " + e.getMessage());
        }
    }
}
