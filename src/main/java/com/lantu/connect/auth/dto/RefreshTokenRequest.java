package com.lantu.connect.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 认证 RefreshTokenRequest 数据传输对象
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
public class RefreshTokenRequest {

    @NotBlank
    private String refreshToken;
}
