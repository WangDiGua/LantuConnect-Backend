package com.lantu.connect.common.result;

import lombok.Data;
import java.util.Collections;
import java.util.List;

/**
 * 分页响应结果
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
public class PageResult<T> {

    private List<T> list;
    private long total;
    private int page;
    private int pageSize;

    public PageResult() {
        this.list = Collections.emptyList();
    }

    public PageResult(List<T> list, long total, int page, int pageSize) {
        this.list = list;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
    }

    public static <T> PageResult<T> of(List<T> list, long total, int page, int pageSize) {
        return new PageResult<>(list, total, page, pageSize);
    }

    public static <T> PageResult<T> empty(int page, int pageSize) {
        return new PageResult<>(Collections.emptyList(), 0, page, pageSize);
    }
}
