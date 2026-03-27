package com.lantu.connect.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户角色关联实体
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
@TableName("t_user_role_rel")
public class UserRoleRel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long roleId;
    private LocalDateTime createTime;
}
