package com.lantu.connect.gateway.catalog;

import com.lantu.connect.gateway.config.SkillExternalCatalogProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubZipPackUrlMirrorTest {

    private final GitHubZipPackUrlMirror mirror = new GitHubZipPackUrlMirror();

    @Test
    void noneLeavesUrl() {
        SkillExternalCatalogProperties.GithubZipMirror cfg = new SkillExternalCatalogProperties.GithubZipMirror();
        cfg.setMode("none");
        cfg.setPrefix("https://proxy.example/");
        String u = "https://github.com/o/r/archive/refs/heads/main.zip";
        assertThat(mirror.applyIfConfigured(u, cfg)).isEqualTo(u);
    }

    @Test
    void prefixEncoded() {
        SkillExternalCatalogProperties.GithubZipMirror cfg = new SkillExternalCatalogProperties.GithubZipMirror();
        cfg.setMode("prefix-encoded");
        cfg.setPrefix("https://gh-proxy.test");
        String u = "https://github.com/o/r/archive/refs/heads/main.zip";
        String out = mirror.applyIfConfigured(u, cfg);
        assertThat(out).startsWith("https://gh-proxy.test/https%3A%2F%2Fgithub.com%2Fo%2Fr%2Farchive%2Frefs%2Fheads%2Fmain.zip");
    }

    @Test
    void relativePrefixIsIgnored() {
        SkillExternalCatalogProperties.GithubZipMirror cfg = new SkillExternalCatalogProperties.GithubZipMirror();
        cfg.setMode("prefix-encoded");
        cfg.setPrefix("admin");
        String u = "https://github.com/o/r/archive/refs/heads/main.zip";
        assertThat(mirror.applyIfConfigured(u, cfg)).isEqualTo(u);
    }

    @Test
    void repairsMistakenRelativePrefixEncoded() {
        String broken = "admin/https%3A%2F%2Fgithub.com%2Fo%2Fr%2Farchive%2Frefs%2Fheads%2Fmain.zip";
        SkillExternalCatalogProperties.GithubZipMirror cfg = new SkillExternalCatalogProperties.GithubZipMirror();
        cfg.setMode("none");
        assertThat(mirror.applyIfConfigured(broken, cfg))
                .isEqualTo("https://github.com/o/r/archive/refs/heads/main.zip");
    }
}
