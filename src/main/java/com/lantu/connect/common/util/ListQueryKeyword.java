package com.lantu.connect.common.util;

import org.springframework.util.StringUtils;

/**
 * 管理端列表 keyword 参数规范化（长度上限，避免大 LIKE 压垮 DB）。
 */
public final class ListQueryKeyword {

    public static final int MAX_LEN = 100;

    private ListQueryKeyword() {
    }

    /**
     * @return trim 后的 keyword；空白返回 null；超长截断至 {@link #MAX_LEN}
     */
    public static String normalize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String t = raw.trim();
        return t.length() > MAX_LEN ? t.substring(0, MAX_LEN) : t;
    }
}
