package com.lantu.connect.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 认证 ChangePasswordRequest 数据传输对象
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
public class ChangePasswordRequest {

    @NotBlank
    private String oldPassword;

    @NotBlank
    @Size(min = 8)
    @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-zA-Z]).{8,}$", message = "密码至少8位且需包含字母与数字")
    private String newPassword;
}
