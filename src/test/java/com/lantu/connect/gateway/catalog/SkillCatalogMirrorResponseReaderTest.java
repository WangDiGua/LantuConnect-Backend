package com.lantu.connect.gateway.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.gateway.dto.SkillExternalCatalogItemVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SkillCatalogMirrorResponseReaderTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void skill0Style_skillsArray_mapsGithubZipAndPagination() throws Exception {
        String json = """
                {
                  "skills": [
                    {
                      "id": "x-y-z",
                      "name": "skill-writer",
                      "author": "pytorch",
                      "description": "Hello",
                      "githubUrl": "https://github.com/pytorch/pytorch/tree/main/.claude/skills/skill-writer",
                      "stars": 100,
                      "branch": "main"
                    }
                  ],
                  "pagination": {"page": 1, "totalPages": 3, "hasNext": true}
                }
                """;
        SkillCatalogMirrorResponseReader.ParsedPage p =
                SkillCatalogMirrorResponseReader.parseFirstPage(json, mapper, "https://skill0.atypica.ai/api/skills");
        assertThat(p.items()).hasSize(1);
        SkillExternalCatalogItemVO v = p.items().get(0);
        assertThat(v.getId()).isEqualTo("x-y-z");
        assertThat(v.getName()).isEqualTo("skill-writer");
        assertThat(v.getPackUrl()).isEqualTo(
                "https://github.com/pytorch/pytorch/archive/refs/heads/main.zip");
        assertThat(p.nextPageNumber()).contains(2);
    }

    @Test
    void rootArray_directVo() throws Exception {
        String json = """
                [{"id":"a","name":"n","summary":"s","packUrl":"https://example.com/a.zip"}]
                """;
        SkillCatalogMirrorResponseReader.ParsedPage p =
                SkillCatalogMirrorResponseReader.parseFirstPage(json, mapper, "https://x.test/list.json");
        assertThat(p.items()).hasSize(1);
        assertThat(p.nextPageNumber()).isEmpty();
    }

    @Test
    void dataWrapper_noPagination() throws Exception {
        String json = """
                {"data": [{"id":"a","name":"n","summary":"s","packUrl":"https://example.com/a.zip"}]}
                """;
        List<SkillExternalCatalogItemVO> items =
                SkillCatalogMirrorResponseReader.mapItemsFromArray(
                        SkillCatalogMirrorResponseReader.extractItemsArray(mapper.readTree(json)),
                        mapper);
        assertThat(items).hasSize(1);
    }

    @Test
    void listKey_downloadUrl_mapsPack() throws Exception {
        String json = """
                {"list": [{"id":"1","title":"T","slug":"s","downloadUrl":"https://dl.example.com/x.zip","description":"D"}]}
                """;
        List<SkillExternalCatalogItemVO> items =
                SkillCatalogMirrorResponseReader.mapItemsFromArray(
                        SkillCatalogMirrorResponseReader.extractItemsArray(mapper.readTree(json)),
                        mapper);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getPackUrl()).isEqualTo("https://dl.example.com/x.zip");
        assertThat(items.get(0).getName()).isEqualTo("T");
    }

    @Test
    void urlWithPageParam_replacesOrAdds() {
        assertThat(SkillCatalogMirrorResponseReader.urlWithPageParam(
                "https://skill0.atypica.ai/api/skills", 2))
                .isEqualTo("https://skill0.atypica.ai/api/skills?page=2");
        assertThat(SkillCatalogMirrorResponseReader.urlWithPageParam(
                "https://skill0.atypica.ai/api/skills?sortBy=stars", 3))
                .contains("page=3")
                .contains("sortBy=stars");
    }
}
