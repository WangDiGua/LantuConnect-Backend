package com.lantu.connect.common.sensitive;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 敏感词分类及词条数（管理端筛选/下拉用）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SensitiveWordCategoryStat {

    private String category;

    private Integer count;
}
