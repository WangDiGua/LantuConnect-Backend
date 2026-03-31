package com.lantu.connect.gateway.dto;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 契约：目录列表与管理端返回的标签字段，供前端 market / 资源中心映射。
 */
class ResourceTagFieldsVOTest {

    @Test
    void catalogItemVoCarriesTagNames() {
        ResourceCatalogItemVO vo = ResourceCatalogItemVO.builder()
                .resourceType("agent")
                .resourceId("42")
                .resourceCode("demo")
                .displayName("Demo")
                .description("")
                .status("published")
                .updateTime(LocalDateTime.now())
                .tags(List.of("办公", "检索"))
                .build();
        assertEquals(2, vo.getTags().size());
        assertEquals("办公", vo.getTags().get(0));
    }

    @Test
    void manageVoCarriesCatalogTagNames() {
        ResourceManageVO vo = ResourceManageVO.builder()
                .id(1L)
                .resourceType("dataset")
                .resourceCode("ds")
                .displayName("DS")
                .status("draft")
                .catalogTagNames(List.of("数据集", "公开"))
                .tags(List.of("raw-only"))
                .build();
        assertEquals(2, vo.getCatalogTagNames().size());
        assertEquals(1, vo.getTags().size());
    }
}
