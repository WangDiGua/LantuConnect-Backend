package com.lantu.connect.gateway.catalog;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubSkillMdPathHeuristicsTest {

    @Test
    void codingAgentNameAddsSkillsSubdir() {
        List<String> c = GitHubSkillMdPathHeuristics.monorepoCandidates("coding-agent", null);
        assertThat(c).contains("skills/coding-agent/SKILL.md");
    }

    @Test
    void skillsMpIdExtractsSlug() {
        assertThat(GitHubSkillMdPathHeuristics.skillSlugFromSkillsMpStyleId(
                "openclaw-openclaw-skills-coding-agent-skill-md")).isEqualTo("coding-agent");
    }

    @Test
    void monorepoMergesIdWhenDifferentFromName() {
        List<String> c = GitHubSkillMdPathHeuristics.monorepoCandidates(
                "other-name", "openclaw-openclaw-skills-coding-agent-skill-md");
        assertThat(c).contains("skills/coding-agent/SKILL.md");
    }

    @Test
    void openclawExtensionsAcpxLayoutFromCatalogId() {
        List<String> c = GitHubSkillMdPathHeuristics.monorepoCandidates("",
                "openclaw-openclaw-extensions-acpx-skills-acp-router-skill-md");
        assertThat(c).contains("extensions/acpx/skills/acp-router/SKILL.md");
    }
}
