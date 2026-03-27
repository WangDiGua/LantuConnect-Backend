package com.lantu.connect.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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

    /** 界面语言偏好（Redis，未设置则为 null） */
    private String language;
    /** 是否开启两步验证（Redis，未设置则为 null） */
    private Boolean twoFactorEnabled;
}
