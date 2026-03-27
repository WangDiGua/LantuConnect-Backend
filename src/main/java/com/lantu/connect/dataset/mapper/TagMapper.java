package com.lantu.connect.dataset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lantu.connect.dataset.entity.Tag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据集 Tag 数据访问层
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Mapper
public interface TagMapper extends BaseMapper<Tag> {
}
