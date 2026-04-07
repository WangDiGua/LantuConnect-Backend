package com.lantu.connect.gateway.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_skill_external_favorite")
public class SkillExternalFavorite {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    @TableField("item_key")
    private String itemKey;

    private LocalDateTime createTime;
}
