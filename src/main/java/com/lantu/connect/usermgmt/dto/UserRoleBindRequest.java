package com.lantu.connect.usermgmt.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 用户角色增量绑定请求
 */
@Data
public class UserRoleBindRequest {

    @NotEmpty
    private List<Long> roleIds;
}
