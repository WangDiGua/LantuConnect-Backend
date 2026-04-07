package com.lantu.connect.gateway.catalog;

import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * 解析 GitHub 浏览器地址（repo 主页、tree、blob），用于拼接 {@code raw.githubusercontent.com} 路径。
 */
public final class GitHubBrowserRepoPathParser {

    private GitHubBrowserRepoPathParser() {
    }

    /**
     * @param treeSubdir 不含首尾斜杠的子目录；空表示仓库根 tree 页。
     * @param blobFilePath 自 ref 起的 blob 相对路径（含文件名）；若非 .md 则调用方可忽略。
     */
    public record BrowserLayout(String owner, String repo, String ref, String treeSubdir, String blobFilePath) {
    }

    public static Optional<BrowserLayout> parseBrowserUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return Optional.empty();
        }
        try {
            URI u = URI.create(url.trim());
            String scheme = u.getScheme();
            if (scheme == null || (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme))) {
                return Optional.empty();
            }
            String host = u.getHost();
            if (host == null) {
                return Optional.empty();
            }
            String h = host.toLowerCase(Locale.ROOT);
            if (!"github.com".equals(h) && !"www.github.com".equals(h)) {
                return Optional.empty();
            }
            String path = u.getPath();
            if (!StringUtils.hasText(path)) {
                return Optional.empty();
            }
            String[] seg = path.split("/");
            if (seg.length < 3) {
                return Optional.empty();
            }
            String owner = seg[1];
            String repo = seg[2];
            if (repo.endsWith(".git")) {
                repo = repo.substring(0, repo.length() - 4);
            }
            if (!StringUtils.hasText(owner) || !StringUtils.hasText(repo)) {
                return Optional.empty();
            }
            if (seg.length >= 5 && "tree".equalsIgnoreCase(seg[3])) {
                String ref = seg[4];
                if (!StringUtils.hasText(ref)) {
                    return Optional.empty();
                }
                String sub = joinSegments(seg, 5);
                return Optional.of(new BrowserLayout(owner, repo, ref, sub, ""));
            }
            if (seg.length >= 5 && "blob".equalsIgnoreCase(seg[3])) {
                String ref = seg[4];
                if (!StringUtils.hasText(ref)) {
                    return Optional.empty();
                }
                String blobPath = joinSegments(seg, 5);
                return Optional.of(new BrowserLayout(owner, repo, ref, "", blobPath));
            }
            // https://github.com/owner/repo — 无 ref，交由 archive zip 推断分支
            return Optional.of(new BrowserLayout(owner, repo, "", "", ""));
        } catch (IllegalArgumentException | SecurityException e) {
            return Optional.empty();
        }
    }

    public static Optional<String> extractRefFromArchiveZipUrl(String archiveUrl) {
        if (!StringUtils.hasText(archiveUrl)) {
            return Optional.empty();
        }
        String u = archiveUrl.trim();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("/archive/refs/heads/([^/]+)\\.zip", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(u);
        if (m.find()) {
            return Optional.of(m.group(1));
        }
        java.util.regex.Matcher m2 = java.util.regex.Pattern
                .compile("/archive/([^/]+)\\.zip", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(u);
        if (m2.find()) {
            return Optional.of(m2.group(1));
        }
        return Optional.empty();
    }

    /**
     * 生成按顺序尝试的仓库相对路径（已规范化，不含 {@code ..}）。
     */
    public static List<String> skillMarkdownCandidates(BrowserLayout layout) {
        List<String> paths = new ArrayList<>();
        String blob = layout.blobFilePath() == null ? "" : layout.blobFilePath().trim();
        if (StringUtils.hasText(blob)) {
            if (blob.toLowerCase(Locale.ROOT).endsWith(".md") && safeRepoRelativePath(blob)) {
                paths.add(blob);
                return paths;
            }
        }
        String sub = layout.treeSubdir() == null ? "" : layout.treeSubdir().trim();
        if (StringUtils.hasText(sub) && safeRepoRelativePath(sub)) {
            paths.add(sub + "/SKILL.md");
            paths.add(sub + "/skill.md");
        }
        if (safeRepoRelativePath("SKILL.md")) {
            paths.add("SKILL.md");
        }
        return paths;
    }

    public static String resolveRef(BrowserLayout layout, String refFromArchive, String defaultBranch) {
        if (StringUtils.hasText(layout.ref())) {
            return layout.ref().trim();
        }
        if (StringUtils.hasText(refFromArchive)) {
            return refFromArchive.trim();
        }
        if (StringUtils.hasText(defaultBranch)) {
            return defaultBranch.trim();
        }
        return "main";
    }

    private static String joinSegments(String[] seg, int fromInclusive) {
        if (fromInclusive >= seg.length) {
            return "";
        }
        StringJoiner j = new StringJoiner("/");
        for (int i = fromInclusive; i < seg.length; i++) {
            if (StringUtils.hasText(seg[i])) {
                j.add(seg[i]);
            }
        }
        return j.toString();
    }

    public static boolean safeRepoRelativePath(String p) {
        if (!StringUtils.hasText(p)) {
            return false;
        }
        if (p.charAt(0) == '/' || p.contains("..") || p.contains("\0")) {
            return false;
        }
        String low = p.toLowerCase(Locale.ROOT);
        if (low.startsWith("//")) {
            return false;
        }
        return true;
    }
}
