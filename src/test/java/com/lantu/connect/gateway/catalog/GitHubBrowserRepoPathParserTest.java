package com.lantu.connect.gateway.catalog;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubBrowserRepoPathParserTest {

    @Test
    void parseTreeWithSubdir() {
        Optional<GitHubBrowserRepoPathParser.BrowserLayout> lay = GitHubBrowserRepoPathParser.parseBrowserUrl(
                "https://github.com/o/r/tree/main/pkg/foo");
        assertThat(lay).isPresent();
        assertThat(lay.get().ref()).isEqualTo("main");
        assertThat(lay.get().treeSubdir()).isEqualTo("pkg/foo");
        assertThat(lay.get().blobFilePath()).isEmpty();
        List<String> c = GitHubBrowserRepoPathParser.skillMarkdownCandidates(lay.get());
        assertThat(c).containsExactly("pkg/foo/SKILL.md", "pkg/foo/skill.md", "SKILL.md");
    }

    @Test
    void extractRefFromArchive() {
        assertThat(GitHubBrowserRepoPathParser.extractRefFromArchiveZipUrl(
                "https://github.com/o/r/archive/refs/heads/develop.zip"))
                .contains("develop");
    }

    @Test
    void resolveRefUsesArchiveWhenTreeMissingRef() {
        GitHubBrowserRepoPathParser.BrowserLayout root =
                new GitHubBrowserRepoPathParser.BrowserLayout("o", "r", "", "", "");
        assertThat(GitHubBrowserRepoPathParser.resolveRef(root, "topic", "main")).isEqualTo("topic");
    }
}
