package com.lantu.connect.gateway.support;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 前端传入的 itemKey（通常为 {@code SkillExternalCatalogDedupeKeys} 值）查询参数归一化。
 */
public final class SkillExternalItemKeyCodec {

    private SkillExternalItemKeyCodec() {
    }

    public static String normalizeQueryParam(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return "";
        }
        try {
            s = URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            // keep as-is
        }
        return s.trim().toLowerCase(Locale.ROOT);
    }
}
