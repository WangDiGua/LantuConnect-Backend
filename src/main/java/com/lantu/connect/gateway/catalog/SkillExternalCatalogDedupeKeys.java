package com.lantu.connect.gateway.catalog;

import com.lantu.connect.gateway.dto.SkillExternalCatalogItemVO;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Objects;

/**
 * 与 {@link com.lantu.connect.gateway.service.SkillExternalCatalogService} 中 pack 去重逻辑一致，供库镜像主键使用。
 */
public final class SkillExternalCatalogDedupeKeys {

    private SkillExternalCatalogDedupeKeys() {
    }

    public static String fromVo(SkillExternalCatalogItemVO v) {
        if (v == null) {
            return "id:";
        }
        if (v.getPackUrl() != null && StringUtils.hasText(v.getPackUrl())) {
            return v.getPackUrl().trim().toLowerCase(Locale.ROOT);
        }
        return "id:" + Objects.toString(v.getId(), "");
    }
}
