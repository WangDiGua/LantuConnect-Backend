package com.lantu.connect.review.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评论实体
 *
 * @author 王帝
 * @date 2026-03-23
 */
@Data
@TableName("t_review")
public class Review {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String targetType;
    private Long targetId;
    /** 父评论 id，顶级为 null */
    private Long parentId;
    private Long userId;
    private String userName;
    private String avatar;
    private Integer rating;
    private String comment;
    private Integer helpfulCount;
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
