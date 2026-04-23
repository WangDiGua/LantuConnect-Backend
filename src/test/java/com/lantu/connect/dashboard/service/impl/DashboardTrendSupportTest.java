package com.lantu.connect.dashboard.service.impl;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DashboardTrendSupportTest {

    @Test
    void dailyTrendIncludesDirectionAndDelta() {
        Map<String, Object> up = DashboardTrendSupport.dailyTrend(5L, 2L, "daily_new");
        Map<String, Object> flat = DashboardTrendSupport.dailyTrend(3L, 3L, "daily_calls");
        Map<String, Object> down = DashboardTrendSupport.dailyTrend(1L, 4L, "daily_active_users");

        assertEquals(5L, up.get("today"));
        assertEquals(2L, up.get("yesterday"));
        assertEquals(3L, up.get("delta"));
        assertEquals("up", up.get("direction"));
        assertEquals("daily_new", up.get("basis"));

        assertEquals(0L, flat.get("delta"));
        assertEquals("flat", flat.get("direction"));

        assertEquals(-3L, down.get("delta"));
        assertEquals("down", down.get("direction"));
    }
}
