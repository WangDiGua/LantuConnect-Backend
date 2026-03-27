package com.lantu.connect.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 认证 TokenResponse 数据传输对象
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponse {

    private String token;
    private String refreshToken;
}
