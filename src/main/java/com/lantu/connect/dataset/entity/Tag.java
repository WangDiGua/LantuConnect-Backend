package com.lantu.connect.dataset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 标签实体
 *
 * @author 王帝
 * @date 2026-03-23
 */
@Data
@TableName("t_tag")
public class Tag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String category;
    private Integer usageCount;
    private LocalDateTime createTime;
}
