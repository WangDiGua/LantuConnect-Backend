package com.lantu.connect.gateway.skillhub;

import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Locale;

/**
 * skillhub.tencent.com 为中文SkillHub官网，对公开搜索路径通常返回整页 HTML，不可当作 JSON API base。
 * 与文档一致的搜索基址为 {@value #RECOMMENDED_JSON_API_ROOT}（见 Agent Skill Hub API Reference）。
 */
public final class SkillHubTencentApiBaseSanitizer {

    public static final String RECOMMENDED_JSON_API_ROOT = "https://agentskillhub.dev";

    private SkillHubTencentApiBaseSanitizer() {}

    /**
     * 是否应把 baseUrl / fallback 从腾讯官网域改用 {@link #RECOMMENDED_JSON_API_ROOT}。
     */
    public static boolean shouldReplaceWithAgentskillhubDev(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return false;
        }
        try {
            URI uri = URI.create(baseUrl.trim());
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            host = host.toLowerCase(Locale.ROOT);
            return host.equals("skillhub.tencent.com") || host.equals("www.skillhub.tencent.com");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
