package com.lantu.connect.gateway.catalog;

import com.lantu.connect.gateway.config.SkillExternalCatalogProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 将直连 GitHub archive zip 转为可配置的镜像前缀（便于国内或大内网访问）。
 */
@Component
public class GitHubZipPackUrlMirror {

    public String applyIfConfigured(String packUrl, SkillExternalCatalogProperties.GithubZipMirror cfg) {
        if (!StringUtils.hasText(packUrl) || cfg == null) {
            return packUrl;
        }
        String mode = cfg.getMode() == null ? "none" : cfg.getMode().trim().toLowerCase(Locale.ROOT);
        if ("none".equals(mode) || !StringUtils.hasText(cfg.getPrefix())) {
            return packUrl;
        }
        String trimmed = packUrl.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!lower.contains("github.com") || !lower.contains("/archive/")) {
            return packUrl;
        }
        String prefix = cfg.getPrefix().trim();
        if (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        if ("prefix-encoded".equals(mode) || "mirror-prefix-encoded".equals(mode)) {
            return prefix + "/" + URLEncoder.encode(trimmed, StandardCharsets.UTF_8);
        }
        if ("prefix-raw".equals(mode) || "mirror-prefix-raw".equals(mode)) {
            return prefix + "/" + trimmed;
        }
        return packUrl;
    }
}
