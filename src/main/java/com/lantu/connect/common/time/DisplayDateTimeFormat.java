package com.lantu.connect.common.time;

import java.time.format.DateTimeFormatter;

/**
 * API 与运营侧统一的时间展示格式：年月日时分秒。
 */
public final class DisplayDateTimeFormat {

    public static final String PATTERN = "yyyy-MM-dd HH:mm:ss";

    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern(PATTERN);

    private DisplayDateTimeFormat() {
    }
}
