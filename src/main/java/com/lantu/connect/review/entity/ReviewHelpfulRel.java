package com.lantu.connect.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评论有用关联实体
 *
 * @author 王帝
 * @date 2026-03-23
 */
@Data
@TableName("t_review_helpful_rel")
public class ReviewHelpfulRel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reviewId;
    private Long userId;
    private LocalDateTime createTime;
}
