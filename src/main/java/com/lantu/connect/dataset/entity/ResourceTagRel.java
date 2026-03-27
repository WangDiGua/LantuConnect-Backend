package com.lantu.connect.dataset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 数据集 ResourceTagRel 实体
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
@TableName("t_resource_tag_rel")
public class ResourceTagRel {

    public static final String RESOURCE_DATASET = "DATASET";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String resourceType;
    private Long resourceId;
    private Long tagId;
}
