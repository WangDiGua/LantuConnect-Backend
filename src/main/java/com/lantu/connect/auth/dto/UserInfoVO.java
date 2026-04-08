package com.lantu.connect.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 认证 UserInfoVO 数据传输对象
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoVO {

    private String id;
    private String username;
    private String email;
    private String phone;
    private String avatar;
    private String nickname;
    private String role;
    private String status;
    private String department;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 界面语言偏好（t_user.language，未设置时接口中可能为默认值） */
    private String language;

    /** 与 Casbin 一致的权限点列表（库表角色 permissions 合并），供控制台菜单与 JWT 主角色解耦 */
    private List<String> permissions;
}
