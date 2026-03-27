package com.lantu.connect.auth.dto;

import com.lantu.connect.common.validation.PhoneCN;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 认证 RegisterRequest 数据传输对象
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
public class RegisterRequest {

    @NotBlank
    private String username;

    @Email
    private String email;

    @NotBlank
    @Size(min = 8)
    @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-zA-Z]).{8,}$", message = "密码至少8位且需包含字母与数字")
    private String password;

    @NotBlank
    private String confirmPassword;

    @PhoneCN
    private String phone;
    private String captcha;
}
