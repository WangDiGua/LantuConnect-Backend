package com.lantu.connect.gateway.catalog;

import com.lantu.connect.gateway.config.SkillExternalCatalogProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 将直连 GitHub archive zip 转为可配置的镜像前缀（便于国内或大内网访问）。
 * prefix 必须为绝对地址（https:// 或 http://）；误填相对路径（如 admin）会产生非法 packUrl，已自动忽略并尽力还原。
 */
@Component
@Slf4j
public class GitHubZipPackUrlMirror {

    public String applyIfConfigured(String packUrl, SkillExternalCatalogProperties.GithubZipMirror cfg) {
        if (!StringUtils.hasText(packUrl)) {
            return packUrl;
        }
        String trimmed = repairAccidentalRelativeMirrorPrefix(packUrl.trim());
        if (cfg == null) {
            return trimmed;
        }
        String mode = cfg.getMode() == null ? "none" : cfg.getMode().trim().toLowerCase(Locale.ROOT);
        if ("none".equals(mode) || !StringUtils.hasText(cfg.getPrefix())) {
            return trimmed;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!lower.contains("github.com") || !lower.contains("/archive/")) {
            return trimmed;
        }
        String prefix = cfg.getPrefix().trim();
        if (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        if (!isAbsoluteHttpPrefix(prefix)) {
            log.warn(
                    "github-zip-mirror.prefix 须为 https:// 或 http:// 开头的绝对镜像基址，相对路径「{}」已忽略（避免 packUrl 无法作为直链使用）",
                    prefix);
            return trimmed;
        }
        if ("prefix-encoded".equals(mode) || "mirror-prefix-encoded".equals(mode)) {
            return prefix + "/" + URLEncoder.encode(trimmed, StandardCharsets.UTF_8);
        }
        if ("prefix-raw".equals(mode) || "mirror-prefix-raw".equals(mode)) {
            return prefix + "/" + trimmed;
        }
        return trimmed;
    }

    static boolean isAbsoluteHttpPrefix(String prefix) {
        if (!StringUtils.hasText(prefix)) {
            return false;
        }
        String p = prefix.trim().toLowerCase(Locale.ROOT);
        return p.startsWith("https://") || p.startsWith("http://");
    }

    /**
     * 修复误配相对 prefix-encoded 产生的「一节路径 + 整段 URL 百分号编码」形态，例如
     * {@code admin/https%3A%2F%2Fgithub.com%2Fo%2Fr%2Farchive%2Frefs%2Fheads%2Fmain.zip}
     */
    /** 供库内快照读出等路径复用，无需经过完整 mirror 改写。 */
    public static String repairAccidentalRelativeMirrorPrefix(String packUrl) {
        if (!StringUtils.hasText(packUrl)) {
            return packUrl;
        }
        String s = packUrl.trim();
        if (s.startsWith("https://") || s.startsWith("http://")) {
            return s;
        }
        int slash = s.indexOf('/');
        if (slash <= 0 || slash >= s.length() - 1) {
            return s;
        }
        String head = s.substring(0, slash);
        if (head.contains(":")) {
            return s;
        }
        String encodedTail = s.substring(slash + 1);
        String low = encodedTail.toLowerCase(Locale.ROOT);
        if (!low.startsWith("https%3a") && !low.startsWith("http%3a")) {
            return s;
        }
        try {
            String decoded = URLDecoder.decode(encodedTail, StandardCharsets.UTF_8);
            if (decoded.startsWith("https://") || decoded.startsWith("http://")) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
            // keep original
        }
        return s;
    }
}
