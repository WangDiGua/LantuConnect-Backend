package com.lantu.connect.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ListQueryKeywordTest {

    @Test
    void normalizeReturnsNullForBlank() {
        assertThat(ListQueryKeyword.normalize(null)).isNull();
        assertThat(ListQueryKeyword.normalize("")).isNull();
        assertThat(ListQueryKeyword.normalize("   ")).isNull();
    }

    @Test
    void normalizeTrimsAndTruncates() {
        assertThat(ListQueryKeyword.normalize("  abc  ")).isEqualTo("abc");
        String longInput = "x".repeat(ListQueryKeyword.MAX_LEN + 50);
        assertThat(ListQueryKeyword.normalize(longInput).length()).isEqualTo(ListQueryKeyword.MAX_LEN);
    }
}
