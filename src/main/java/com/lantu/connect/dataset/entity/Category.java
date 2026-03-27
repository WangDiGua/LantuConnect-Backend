package com.lantu.connect.dataset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分类实体
 *
 * @author 王帝
 * @date 2026-03-23
 */
@Data
@TableName("t_category")
public class Category {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String categoryCode;
    private String categoryName;
    private Long parentId;
    private String icon;
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
