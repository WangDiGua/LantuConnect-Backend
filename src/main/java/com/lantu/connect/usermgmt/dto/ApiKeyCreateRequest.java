package com.lantu.connect.usermgmt.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户管理 ApiKeyCreateRequest 数据传输对象
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
public class ApiKeyCreateRequest {

    @NotBlank
    private String name;

    /** 不传或空列表时，服务端存为 {@code ["*"]}（全量 catalog/resolve/invoke），与无「选权限」UI 的前端对齐。 */
    private List<String> scopes;

    private LocalDateTime expiresAt;
}
