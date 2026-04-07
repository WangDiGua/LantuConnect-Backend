package com.lantu.connect.gateway.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_skill_external_review")
public class SkillExternalReviewEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("item_key")
    private String itemKey;

    private Long userId;

    private String userName;

    private String avatar;

    private Integer rating;

    private String comment;

    @TableField("parent_id")
    private Long parentId;

    @TableField("helpful_count")
    private Integer helpfulCount;

    private Integer deleted;

    private LocalDateTime createTime;
}
