package com.lantu.connect.gateway.catalog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubPackUrlUnwrapperTest {

    @Test
    void unwrapsEncodedMirrorTail() {
        String wrapped = "https://mirror.example/https%3A%2F%2Fgithub.com%2Facme%2Fskill%2Farchive%2Frefs%2Fheads%2Fmain.zip";
        assertThat(GitHubPackUrlUnwrapper.unwrapToGithubArchiveZip(wrapped))
                .isEqualTo("https://github.com/acme/skill/archive/refs/heads/main.zip");
    }

    @Test
    void keepsDirectGithubUrl() {
        String u = "https://github.com/acme/skill/archive/refs/heads/main.zip";
        assertThat(GitHubPackUrlUnwrapper.unwrapToGithubArchiveZip(u)).isEqualTo(u);
    }
}
