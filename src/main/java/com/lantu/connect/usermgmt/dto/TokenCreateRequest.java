package com.lantu.connect.usermgmt.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户管理 TokenCreateRequest 数据传输对象
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
public class TokenCreateRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String type;

    private List<String> scopes;

    private LocalDateTime expiresAt;
}
