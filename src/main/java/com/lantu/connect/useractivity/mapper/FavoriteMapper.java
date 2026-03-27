package com.lantu.connect.useractivity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lantu.connect.useractivity.entity.Favorite;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户活动 Favorite 数据访问层
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {
}
