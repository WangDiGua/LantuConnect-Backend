package com.lantu.connect.gateway.skillsmp;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubRepoRefTest {

    @Test
    void parseStandardRepoUrl() {
        Optional<GitHubRepoRef> r = GitHubRepoRef.parse("https://github.com/anthropics/claude-code");
        assertThat(r).isPresent();
        assertThat(r.get().getOwner()).isEqualTo("anthropics");
        assertThat(r.get().getRepo()).isEqualTo("claude-code");
        assertThat(r.get().defaultBranchArchiveZip("main"))
                .isEqualTo("https://github.com/anthropics/claude-code/archive/refs/heads/main.zip");
    }

    @Test
    void parseTreePathStripsToRepoRoot() {
        Optional<GitHubRepoRef> r = GitHubRepoRef.parse(
                "https://github.com/foo/bar/tree/main/plugins/some-skill");
        assertThat(r).isPresent();
        assertThat(r.get().getOwner()).isEqualTo("foo");
        assertThat(r.get().getRepo()).isEqualTo("bar");
    }

    @Test
    void parseGitSuffix() {
        Optional<GitHubRepoRef> r = GitHubRepoRef.parse("https://github.com/a/b.git");
        assertThat(r).isPresent();
        assertThat(r.get().getRepo()).isEqualTo("b");
    }
}
