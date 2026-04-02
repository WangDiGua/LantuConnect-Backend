package com.lantu.connect.gateway.service;

import com.lantu.connect.gateway.config.SkillExternalCatalogProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SkillExternalCatalogMirrorUrlsResolutionTest {

    @Test
    void mergesLegacyUrlsWithCatalogHttpSourcesAndDedupes() {
        SkillExternalCatalogProperties p = new SkillExternalCatalogProperties();
        p.setMirrorCatalogUrl(" https://a.example/catalog.json ");
        p.setMirrorCatalogUrls(List.of("https://b.example/x.json", " https://a.example/catalog.json "));
        SkillExternalCatalogProperties.CatalogHttpSource c = new SkillExternalCatalogProperties.CatalogHttpSource();
        c.setUrl("https://c.example/skill0");
        c.setFormat("SKILL0");
        p.setCatalogHttpSources(List.of(c));
        List<SkillExternalCatalogProperties.CatalogHttpSource> src =
                SkillExternalCatalogService.resolvedCatalogHttpSources(p);
        assertThat(src).hasSize(3);
        assertThat(src.get(0).getUrl()).isEqualTo("https://a.example/catalog.json");
        assertThat(src.get(0).getFormat()).isEqualTo("AUTO");
        assertThat(src.get(1).getUrl()).isEqualTo("https://b.example/x.json");
        assertThat(src.get(2).getUrl()).isEqualTo("https://c.example/skill0");
        assertThat(src.get(2).getFormat()).isEqualTo("SKILL0");
    }

    @Test
    void emptyWhenUnset() {
        SkillExternalCatalogProperties p = new SkillExternalCatalogProperties();
        assertThat(SkillExternalCatalogService.resolvedCatalogHttpSources(p)).isEmpty();
    }
}
