package com.lantu.connect.gateway.skillsmp;

import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;

/**
 * 从 SkillsMP 返回的 githubUrl 中解析 owner/repo，用于拼 GitHub archive zip。
 */
public final class GitHubRepoRef {

    private final String owner;
    private final String repo;

    public GitHubRepoRef(String owner, String repo) {
        this.owner = owner;
        this.repo = repo;
    }

    public String getOwner() {
        return owner;
    }

    public String getRepo() {
        return repo;
    }

    public String defaultBranchArchiveZip(String branch) {
        String b = StringUtils.hasText(branch) ? branch.trim() : "main";
        return String.format(Locale.ROOT,
                "https://github.com/%s/%s/archive/refs/heads/%s.zip",
                owner, repo, b);
    }

    public String dedupeKey() {
        return owner.toLowerCase(Locale.ROOT) + "/" + repo.toLowerCase(Locale.ROOT);
    }

    public static Optional<GitHubRepoRef> parse(String githubUrl) {
        if (!StringUtils.hasText(githubUrl)) {
            return Optional.empty();
        }
        try {
            URI u = URI.create(githubUrl.trim());
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
            if (!StringUtils.hasText(owner) || !StringUtils.hasText(repo)) {
                return Optional.empty();
            }
            if (repo.endsWith(".git")) {
                repo = repo.substring(0, repo.length() - 4);
            }
            return Optional.of(new GitHubRepoRef(owner, repo));
        } catch (IllegalArgumentException | SecurityException e) {
            return Optional.empty();
        }
    }
}
