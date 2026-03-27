package com.lantu.connect.usermgmt.dto;

import lombok.Data;

import java.util.List;

/**
 * 用户管理 UpdateUserRequest 数据传输对象
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
public class UpdateUserRequest {

    private String password;

    private String email;

    private String phone;

    private String department;

    private String role;

    /**
     * 多角色场景下用于全量替换用户角色。
     */
    private List<Long> roleIds;

    private String status;
}
