package com.lantu.connect.common.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ServletContextPathUtilTest {

    @Test
    void join_stripsTrailingSlashOnBase() {
        assertThat(ServletContextPathUtil.join("/regis/", "/foo")).isEqualTo("/regis/foo");
    }

    @Test
    void join_mergesContextAndPath() {
        assertThat(ServletContextPathUtil.join("/regis", "/catalog/apps/launch")).isEqualTo("/regis/catalog/apps/launch");
    }
}
