package com.lantu.connect.usermgmt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户管理 TokenResponse 数据传输对象
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponse {

    private String id;

    private String name;

    private String type;

    private List<String> scopes;

    private String tokenPlain;

    private LocalDateTime expiresAt;

    private Boolean revoked;
}
