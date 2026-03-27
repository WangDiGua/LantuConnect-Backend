package com.lantu.connect.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lantu.connect.review.entity.Review;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评论 Review 数据访问层
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Mapper
public interface ReviewMapper extends BaseMapper<Review> {
}
