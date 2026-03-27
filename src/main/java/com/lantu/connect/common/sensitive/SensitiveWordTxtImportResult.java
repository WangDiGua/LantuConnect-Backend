package com.lantu.connect.common.sensitive;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TXT 文件导入敏感词结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SensitiveWordTxtImportResult {

    /** 本次新入库条数 */
    private int added;

    /** 有效候选词行数（已排除空行、注释、超长；含文件内重复行） */
    private int candidates;

    /** 跳过的空行与注释行（# 开头） */
    private int skippedBlankOrComment;

    /** 跳过超长（单条超过 128 字符，与表字段一致） */
    private int skippedTooLong;

    /** 候选中因库中已存在而未写入的条数 */
    private int skippedDuplicate;
}
