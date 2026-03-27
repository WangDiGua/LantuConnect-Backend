package com.lantu.connect.usermgmt.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 用户管理 CreateUserRequest 数据传输对象
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
public class CreateUserRequest {

    @NotBlank
    private String username;

    @NotBlank
    private String password;

    @NotBlank
    private String role;

    /**
     * 多角色场景可直接传 roleId 列表；传值时优先级高于 role。
     */
    private List<Long> roleIds;

    private String email;

    private String phone;

    private String department;
}
