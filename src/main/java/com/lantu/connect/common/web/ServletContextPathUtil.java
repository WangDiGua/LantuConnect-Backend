package com.lantu.connect.common.web;

import org.springframework.util.StringUtils;

public final class ServletContextPathUtil {

    private ServletContextPathUtil() {
    }

    public static String join(String contextPath, String pathWithinContext) {
        String base = contextPath == null ? "" : contextPath.trim();
        if (base.endsWith("/") && base.length() > 1) {
            base = base.substring(0, base.length() - 1);
        }
        if (!StringUtils.hasText(pathWithinContext)) {
            return base.isEmpty() ? "/" : base;
        }
        String p = pathWithinContext.trim();
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        return base.isEmpty() ? p : base + p;
    }
}
