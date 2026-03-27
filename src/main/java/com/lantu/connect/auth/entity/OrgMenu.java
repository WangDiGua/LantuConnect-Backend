package com.lantu.connect.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 组织架构实体
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
@TableName("t_org_menu")
public class OrgMenu {

    @TableId(value = "menu_id", type = IdType.AUTO)
    private Long menuId;

    private String menuName;
    private Long menuParentId;
    private Integer menuLevel;
    private Integer ifXy;
    private Integer headCount;
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
