package com.lantu.connect.common.sensitive;

import java.util.List;

/**
 * 敏感词业务<strong>专用</strong>分类字典（写死）。
 * <p>
 * <strong>注意：</strong>与资源目录、五类市场、{@code t_tag} 等「资源标签/分类」无关，禁止复用 agent/skill/mcp 等资源 type 作为敏感词分类。
 */
public final class SensitiveWordFixedCategories {

    private SensitiveWordFixedCategories() {
    }

    /**
     * 固定代码顺序即管理端下拉默认顺序；库中无词条时 count 仍为 0。
     */
    public static final List<String> CODES = List.of(
            "default",
            "general",
            "review",
            "user_profile",
            "announcement",
            "other"
    );
}
