package com.lantu.connect.dataset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lantu.connect.dataset.entity.Category;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据集 Category 数据访问层
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
}
