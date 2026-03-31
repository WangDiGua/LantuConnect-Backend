package com.lantu.connect.common.util;

import org.springframework.util.StringUtils;

/**
 * 将字符串安全嵌入 JSON 字符串字面量（用于手写 JSON 响应体）。
 */
public final class JsonStringEscaper {

    private JsonStringEscaper() {
    }

    public static String escape(String s) {
        if (!StringUtils.hasText(s)) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
