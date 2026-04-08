package com.lantu.connect.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lantu.connect.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户实体
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_user")
public class User extends BaseEntity {

    @TableId(value = "user_id", type = IdType.AUTO)
    private Long userId;

    private String username;

    @JsonIgnore
    private String passwordHash;

    private String realName;
    private Integer sex;
    private Long schoolId;
    private Long menuId;
    private String major;

    @TableField("`class`")
    private String className;

    private Integer role;
    private String mobile;
    private String mail;
    private String headImage;
    private String zw;
    private String zc;
    private LocalDate birthday;
    private String status;
    private LocalDateTime lastLoginTime;
    private String language;

    /**
     * 列表/详情扩展：平台角色绑定（非表字段），由 UserMgmt 等在查询后填充。
     */
    @TableField(exist = false)
    private List<PlatformRole> platformRoles;
}
