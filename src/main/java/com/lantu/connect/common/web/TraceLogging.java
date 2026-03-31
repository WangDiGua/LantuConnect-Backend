package com.lantu.connect.common.web;

import org.slf4j.MDC;

/**
 * 全链路日志辅助：从 MDC 取 traceId，便于全局异常与过滤器统一格式。
 */
public final class TraceLogging {

    private TraceLogging() {
    }

    public static String traceIdOrDash() {
        String t = MDC.get("traceId");
        return t != null && !t.isBlank() ? t : "-";
    }
}
