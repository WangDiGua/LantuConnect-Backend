package com.lantu.connect.usermgmt.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 用户管理 RoleCreateRequest 数据传输对象
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
public class RoleCreateRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String code;

    private String description;

    private List<String> permissions;
}
