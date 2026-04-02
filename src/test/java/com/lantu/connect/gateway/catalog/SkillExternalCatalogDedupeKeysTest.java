package com.lantu.connect.gateway.catalog;

import com.lantu.connect.gateway.dto.SkillExternalCatalogItemVO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillExternalCatalogDedupeKeysTest {

    @Test
    void fromVo_prefersIdSoSamePackUrlCanBeDistinctRows() {
        String zip = "https://github.com/anthropics/skills/archive/refs/heads/main.zip";
        SkillExternalCatalogItemVO a = SkillExternalCatalogItemVO.builder()
                .id("anthropics-skills-docx")
                .name("A")
                .packUrl(zip)
                .build();
        SkillExternalCatalogItemVO b = SkillExternalCatalogItemVO.builder()
                .id("anthropics-skills-pdf")
                .name("B")
                .packUrl(zip)
                .build();
        assertThat(SkillExternalCatalogDedupeKeys.fromVo(a)).isNotEqualTo(SkillExternalCatalogDedupeKeys.fromVo(b));
        assertThat(SkillExternalCatalogDedupeKeys.fromVo(a)).isEqualTo("id:anthropics-skills-docx");
    }

    @Test
    void fromVo_fallsBackToPackUrlWhenIdBlank() {
        String zip = "https://github.com/o/r/archive/refs/heads/main.zip";
        SkillExternalCatalogItemVO v = SkillExternalCatalogItemVO.builder()
                .id("")
                .name("n")
                .packUrl(zip)
                .build();
        assertThat(SkillExternalCatalogDedupeKeys.fromVo(v)).isEqualTo(zip.toLowerCase());
    }
}
