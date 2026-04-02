package com.lantu.connect.gateway.catalog;

import com.lantu.connect.gateway.dto.SkillExternalCatalogItemVO;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Objects;

/**
 * 与 {@link com.lantu.connect.gateway.service.SkillExternalCatalogService} 中合并逻辑一致，供库镜像主键使用。
 * 优先按 {@code id} 去重，使同一 GitHub 仓库下多条 SkillHub/市场技能（不同 slug）可各占一行；无 {@code id} 时回退为 packUrl。
 */
public final class SkillExternalCatalogDedupeKeys {

    private SkillExternalCatalogDedupeKeys() {
    }

    public static String fromVo(SkillExternalCatalogItemVO v) {
        if (v == null) {
            return "id:";
        }
        if (StringUtils.hasText(v.getId())) {
            return "id:" + v.getId().trim().toLowerCase(Locale.ROOT);
        }
        if (v.getPackUrl() != null && StringUtils.hasText(v.getPackUrl())) {
            return v.getPackUrl().trim().toLowerCase(Locale.ROOT);
        }
        return "id:" + Objects.toString(v.getId(), "");
    }
}
