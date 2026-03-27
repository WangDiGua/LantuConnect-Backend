package com.lantu.connect.useractivity.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户收藏实体（与 t_favorite 一致，无逻辑删除与 update_time）
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
@TableName("t_favorite")
public class Favorite {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String targetType;

    private Long targetId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
