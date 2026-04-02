package com.lantu.connect.gateway.skillhub;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillHubCatalogClientNormalizeTest {

    @Test
    void normalizesRootAndApiV1Base() {
        assertThat(SkillHubCatalogClient.normalizeApiBase("https://skillhub.tencent.com"))
                .isEqualTo("https://skillhub.tencent.com/api/v1");
        assertThat(SkillHubCatalogClient.normalizeApiBase("https://skillhub.tencent.com/api/v1/"))
                .isEqualTo("https://skillhub.tencent.com/api/v1");
        assertThat(SkillHubCatalogClient.normalizeApiBase("https://agentskillhub.dev/api/v1"))
                .isEqualTo("https://agentskillhub.dev/api/v1");
    }
}
