package com.lantu.connect.common.sensitive;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 敏感词Mapper
 *
 * @author 王帝
 * @date 2026-03-23
 */
@Mapper
public interface SensitiveWordMapper extends BaseMapper<SensitiveWord> {

    @Select("SELECT `word` FROM t_sensitive_word WHERE enabled = 1")
    List<String> selectAllEnabledWords();

    @Select("SELECT DISTINCT `category` FROM t_sensitive_word WHERE enabled = 1")
    List<String> selectAllCategories();

    @Select("SELECT `category`, COUNT(*) AS cnt FROM t_sensitive_word GROUP BY `category` ORDER BY `category`")
    @Results({
        @Result(property = "category", column = "category"),
        @Result(property = "count", column = "cnt")
    })
    List<SensitiveWordCategoryStat> selectCategoryCounts();
}
