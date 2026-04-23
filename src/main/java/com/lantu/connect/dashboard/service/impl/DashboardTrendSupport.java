package com.lantu.connect.dashboard.service.impl;

import java.util.LinkedHashMap;
import java.util.Map;

final class DashboardTrendSupport {

    private DashboardTrendSupport() {
    }

    static Map<String, Object> dailyTrend(long today, long yesterday, String basis) {
        long delta = today - yesterday;
        Map<String, Object> trend = new LinkedHashMap<>();
        trend.put("today", today);
        trend.put("yesterday", yesterday);
        trend.put("delta", delta);
        trend.put("direction", direction(delta));
        trend.put("basis", basis);
        return trend;
    }

    private static String direction(long delta) {
        if (delta > 0) {
            return "up";
        }
        if (delta < 0) {
            return "down";
        }
        return "flat";
    }
}
