package com.lantu.connect.usermgmt.dto;

import lombok.Data;

import java.util.List;

/**
 * 用户管理 RoleUpdateRequest 数据传输对象
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
public class RoleUpdateRequest {

    private String name;

    private String code;

    private String description;

    private List<String> permissions;
}
