package com.lantu.connect.gateway.skillhub;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillHubTencentApiBaseSanitizerTest {

    @Test
    void detectsOfficialTencentHostWithOrWithoutPath() {
        assertThat(SkillHubTencentApiBaseSanitizer.shouldReplaceWithAgentskillhubDev("https://skillhub.tencent.com"))
                .isTrue();
        assertThat(SkillHubTencentApiBaseSanitizer.shouldReplaceWithAgentskillhubDev("https://skillhub.tencent.com/"))
                .isTrue();
        assertThat(SkillHubTencentApiBaseSanitizer.shouldReplaceWithAgentskillhubDev(
                        "https://skillhub.tencent.com/api/v1"))
                .isTrue();
        assertThat(SkillHubTencentApiBaseSanitizer.shouldReplaceWithAgentskillhubDev(
                        "http://www.skillhub.tencent.com/api/v1/"))
                .isTrue();
    }

    @Test
    void leavesRealApiHost() {
        assertThat(SkillHubTencentApiBaseSanitizer.shouldReplaceWithAgentskillhubDev("https://agentskillhub.dev"))
                .isFalse();
        assertThat(SkillHubTencentApiBaseSanitizer.shouldReplaceWithAgentskillhubDev(
                        "https://agentskillhub.dev/api/v1"))
                .isFalse();
        assertThat(SkillHubTencentApiBaseSanitizer.shouldReplaceWithAgentskillhubDev("")).isFalse();
        assertThat(SkillHubTencentApiBaseSanitizer.shouldReplaceWithAgentskillhubDev(null)).isFalse();
    }
}
