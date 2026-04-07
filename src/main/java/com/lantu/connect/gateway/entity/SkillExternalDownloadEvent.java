package com.lantu.connect.gateway.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_skill_external_download_event")
public class SkillExternalDownloadEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("item_key")
    private String itemKey;

    private Long userId;

    private LocalDateTime createTime;
}
