package com.lantu.connect.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.Collections;
import java.util.List;

/**
 * MyBatis-Plus {@link IPage} → 对外统一的 {@link PageResult}，避免直接把 {@code Page} 交给 Jackson 导致序列化异常。
 */
public final class PageResults {

    private PageResults() {
    }

    public static <T> PageResult<T> from(IPage<T> page) {
        if (page == null) {
            return new PageResult<>(Collections.emptyList(), 0, 1, 20);
        }
        List<T> records = page.getRecords();
        if (records == null) {
            records = Collections.emptyList();
        }
        return PageResult.of(
                records,
                page.getTotal(),
                (int) page.getCurrent(),
                (int) page.getSize());
    }
}
